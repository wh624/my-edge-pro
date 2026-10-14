// 项目路径含中文时，Windows 上单元测试进程会找不到测试类。
// 用法：gradlew testDebugUnitTest --init-script gradle/ascii-build-dir.init.gradle.kts
val asciiRoot = File(System.getProperty("java.io.tmpdir"), "edgepro-build")

allprojects {
    layout.buildDirectory.set(File(asciiRoot, project.name))
}
