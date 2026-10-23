# 第三方依赖

## Xposed API 82

- 来源：`https://api.xposed.info/de/robv/android/xposed/api/82/api-82.jar`
- 许可证：Apache License 2.0，依据同路径下 `api-82.pom` 的许可证声明。
- 用途：仅用于编译 LSPosed 兼容 Hook 入口；API 桩不打包到 APK，也不包含在源码压缩包中。运行时由用户已有的 LSPosed/Xposed 框架提供。
- 下载文件 SHA-256：`f48c635f1c7469fdec0e00ad2ea0b7a6b2f5b55065784a35b7ca3a84615e8e25`。

## Android 界面与测试

- AndroidX Activity、Compose UI/Foundation/Material 3、Lifecycle：Apache License 2.0，由 Google Maven 提供。版本固定在 `module/build.gradle`；这些运行依赖随 APK 打包。
- Kotlin 标准库与协程：Apache License 2.0，由 Maven Central 提供。
- JUnit 4.13.2：Eclipse Public License 1.0，仅测试使用。
- AndroidX Test 与 Compose UI Test：Apache License 2.0，仅测试 APK 使用。

## 构建工具

Gradle Wrapper 8.14.5 使用 Apache License 2.0。Android Gradle Plugin 8.11.1 和 Kotlin Gradle Plugin 2.2.10 按固定版本获取。Android SDK、JDK 和 SDK 构建工具由构建环境提供。

本仓库和发布附件不包含 OPPO 互联 APK、反编译代码、手机应用数据、设备安装日志或签名私钥。
