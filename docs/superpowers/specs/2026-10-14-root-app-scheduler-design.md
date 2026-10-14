# My Edge Pro —— Root 定时任务工具 设计文档

## 目标
在已 Root 的 Android 9+ 设备上，按定时规则自动执行一组有序步骤：启动应用（含系统多用户分身）、停止应用、清理后台、执行 Shell。纯 Root 实现，不依赖 Xposed。

## 范围
- 分身：仅系统级多用户（MIUI 双开 999、华为 128、工作资料等），通过 `am start --user N` 启动；改包名分身视为普通应用。
- 不做亮屏/解锁。
- debug 签名打包，minSdk 28。

## 数据模型
- `Task(id, name, enabled, trigger, steps, lastRunAt, lastSuccess)`
- `Trigger`
  - `Daily(times, weekdays)`：`times` 为原始字符串，如 `065000,123000`；接受 `HHmmss`、`HHmm`、`HH:mm[:ss]`，分隔符 `,` `，` `;` 空格。`weekdays` 为 ISO 星期 1..7，空集合表示每天。
  - `Cron(expression)`：5 段（分 时 日 月 周）或 6 段（秒 分 时 日 月 周）。支持 `*` `?` `,` `-` `/`，月份与星期支持英文缩写，周 0 与 7 都表示周日；日与周同时受限时按标准 cron 取“或”。
- `Step`
  - `Launch(apps, intervalMs)`、`Stop(apps, mode)`、`CleanBackground(whitelist, mode, includeSystem, allUsers)`、`Shell(script, timeoutSec)`、`Delay(seconds)`
  - `AppTarget(packageName, userId, label)`；`StopMode = FORCE_STOP | KILL`

## 组件
| 组件 | 职责 |
| --- | --- |
| `core.DailyTimes` / `core.CronExpression` / `core.TriggerCalculator` | 纯逻辑：解析与计算下次触发时间 |
| `core.ShellParsers` | 解析 `pm list users`、`pm list packages`、`ps`、`resolve-activity` 输出 |
| `root.RootShell` | 每次启动一个 `su` 进程执行脚本，带超时，收集输出与退出码 |
| `root.ShellCommands` | 生成启动/停止/清理命令，校验包名防注入 |
| `apps.AppRepository` | 用户空间与应用列表、名称图标 |
| `data.TaskStore` / `data.LogStore` | JSON 文件持久化，`StateFlow` 暴露 |
| `sched.Scheduler` | `AlarmManager` 精确闹钟（闹钟模式 `setAlarmClock`，或 `setExactAndAllowWhileIdle`） |
| `sched.TaskRunnerService` | 前台服务 + WakeLock，串行执行任务，写日志，重排下次闹钟 |
| `sched.SystemEventReceiver` | 开机、时间/时区变化、应用更新后重排全部闹钟 |
| `ui.*` | Compose 界面：任务列表、编辑、应用选择器、日志、Shell 控制台、设置 |

## 清理后台流程
1. 一次 root 脚本收集：`ps -A -o USER,NAME`、各用户第三方包列表、当前桌面、当前输入法。
2. 由 `ps` 的 `uN_aXXX` 推出 (包名, 用户) 运行中集合。
3. 排除：白名单、本应用、桌面、输入法、关键系统包；未勾选“包含系统应用”时只保留第三方包。
4. 逐个 `am force-stop --user N` 或 `am kill --user N`。

## 后台常驻（App 不打开也能执行）
MIUI/HyperOS 默认拒绝自启动：App 被划掉（强行停止会清空闹钟）、重启后未打开（开机广播被拦）、锁屏后被省电策略杀掉时，闹钟无法拉起进程。对策：
- **Root 守护进程** `/data/adb/edgepro/daemon.sh`：uid 0 运行，不受针对应用 uid 的杀进程影响。每 30 秒 `pidof` 检查 App 进程，不在则 `am broadcast --user 0 -n <pkg>/.sched.KeepAliveReceiver -f 0x10000020` 拉起；`KeepAliveReceiver` 不导出，仅 root 可发。卸载判定用解锁前就存在的 `/data/user_de/0/<pkg>`，不能用 `/data/data`（重启后未输入密码时它不存在，误判会把守护进程删掉）。
- **Direct Boot**：`android:directBootAware` + `defaultToDeviceProtectedStorage`。任务、设置、预期触发时间放在设备加密存储，升级时从凭证加密目录迁移。监听 `LOCKED_BOOT_COMPLETED`，重启后未解锁也能注册闹钟并执行不含「启动应用」的步骤。含启动应用的任务在用户未解锁时排队，收到 `USER_UNLOCKED` 后立即执行。无密码的滑动锁屏在开机时即视为已解锁，启动时附带 `--dismiss-keyguard-if-insecure` 和点亮屏幕。
- **开机自启** `/data/adb/service.d/edgepro.sh`（Magisk/KernelSU/APatch 通用），重启后无需打开 App。
- **自启动授权**：守护进程启动时及「一键保活优化」执行 `appops set <pkg> 10008 allow`（MIUI 自启动）、`10021`（后台弹出界面）、`RUN_ANY_IN_BACKGROUND`、电池优化白名单。
- **错过补执行**：`Scheduler` 在 SharedPreferences 记录每个任务的预期触发时间；`App.onCreate` 在重排前判定窗口内（默认 30 分钟，可设 0 关闭）错过且未执行的触发，延迟 5 秒补执行。执行服务按 `lastRunAt >= scheduledAt` 去重，避免闹钟与补执行重复。
- **运行诊断**：设置页一键输出守护进程、自启动、白名单、待机分组、已注册闹钟状态。

## 错误处理
- 无 Root：任务记录失败日志并提示。
- 单步失败不中断后续步骤，整体结果为失败；每步输出写入日志详情。
- 无法计算下次触发时间（非法表达式/永不触发）时不注册闹钟，界面显示错误。

## 测试
JVM 单元测试覆盖 `DailyTimes`、`CronExpression`、`TriggerCalculator`、`ShellParsers`、`ShellCommands`。
