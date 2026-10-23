# 第三方依赖

## Xposed API 82

- 来源：`https://api.xposed.info/de/robv/android/xposed/api/82/api-82.jar`
- 许可证：Apache License 2.0，依据同路径下 `api-82.pom` 的许可证声明。
- 用途：仅用于编译 LSPosed 兼容 Hook 入口；API 桩不打包到 APK，也不包含在源码压缩包中。运行时由用户已有的 LSPosed/Xposed 框架提供。
- 下载文件 SHA-256：`f48c635f1c7469fdec0e00ad2ea0b7a6b2f5b55065784a35b7ca3a84615e8e25`。

## 构建工具

本项目调用本机 JDK 和 Android SDK 中的 javac、D8、aapt2、zipalign、apksigner，不在仓库或 Release 中分发这些工具。

本仓库和发布附件不包含 OPPO 互联 APK、反编译代码、手机应用数据、设备安装日志或签名私钥。
