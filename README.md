# OPPO 互联全应用投屏

作者：Old_cucumber

无界面的 LSPosed 模块。安装后在 LSPosed 中启用，勾选 `com.oplus.linker`（OPPO 互联），再重启互联进程或手机。

## 安装

从本仓库 Releases 下载 APK，安装后在 LSPosed 中启用模块，选择 **OPPO 互联** 作用域。无需选择系统框架。

模块没有启动图标、Activity 或配置页面。启用后合并当前用户的全部第三方应用包名，保留原有系统应用名单；配置重载时重新补齐名单，并处理应用列表缓存。

v1.0.1 去掉了应用显示名和下载文件名中的作者后缀。包名和签名保持与 v1.0 一致，可直接覆盖安装；作者署名保留在模块介绍和源码中。

源码在 `module/`。只改目标应用的内存白名单和缓存，不写 XML、不挂载、不改互联 APK。模块没有 Activity、启动入口或配置项。

构建：

```powershell
python -X utf8 module/build.py
```

需要 JDK、Android SDK platform 36 和 build-tools 36.0.0。脚本从官方仓库取得编译用 Xposed API 82，并校验固定 SHA-256；API 桩不打包进 APK。

当前构建脚本面向 Windows；优先使用 `ANDROID_SDK_ROOT` 或 `ANDROID_HOME`，否则使用 `%LOCALAPPDATA%\Android\Sdk`。文件名和构建记录中的版本来自 AndroidManifest。

产物在 `dist/`，中间文件在 `work/build/`。签名密钥为 `work/build/Old_cucumber.jks`，后续更新需保留这把密钥；不要提交该目录。

## 验证范围

Hook 点依据一加 13 上 `com.oplus.linker` 17.7.30 APK 定位，设备安装的 LSPosed 为 2.2.1 (7933)。当前版本仅完成编译、APK 结构、签名、压缩包与校验和检查。此前 v1.0 已安装到设备；v1.0.1 尚未进行设备安装、Hook 注入或实际投屏验证。

模块扩展的是互联应用内白名单。系统底层投屏能力、应用兼容性和安全窗口限制仍需实际验证。

## 协议

Copyright (C) 2026 Old_cucumber。

本项目采用 **GNU GPL v3.0（GPL-3.0-only）**，完整文本见 `LICENSE`。编译依赖说明见 `THIRD_PARTY.md`；源码压缩包包含协议与构建说明，不包含签名私钥。
