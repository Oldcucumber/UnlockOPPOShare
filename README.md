# UnlockOPPOshare

用 LSPosed 选择在电脑端 OPPO 互联中显示的手机应用。版本 **2.0.0**，作者 Old_cucumber。

## 使用

1. 安装 APK，在 LSPosed 中启用模块，作用域选择 **OPPO 互联**（`com.oplus.linker`）。
2. 重启互联进程或手机。
3. 打开「UnlockOPPOshare」，开启想显示的应用，关闭想隐藏的应用。

页面使用 Material 3 Expressive，支持深浅色和动态配色。可按名称、包名或首字母搜索，拖动右侧 A–Z / # 索引定位；中文名称按拼音首字母分组。可切换用户/系统应用、筛选已显示/已隐藏，并批量选择或恢复默认。

向上滑动时大标题收起，统计、搜索和筛选随列表滚走。列表扩展到屏幕主体，点击顶部搜索按钮回到搜索框。选择即时保存并同步到互联。

默认显示全部用户应用和互联原有的系统应用。系统应用的默认名单由互联进程首次运行后同步。升级沿用原包名和签名，可覆盖安装。作用域无需选择系统框架。

## 构建

需要 JDK 21、Python 3、Android SDK platform 36 和 build-tools 36.0.0。Gradle Wrapper 已包含在仓库中。

```powershell
python -X utf8 module/build.py
```

脚本运行单元测试、Lint、签名构建和产物校验，输出位于 `dist/`。签名配置读取 `SIGNING_STORE_FILE`、`SIGNING_STORE_PASSWORD`、`SIGNING_KEY_ALIAS`、`SIGNING_KEY_PASSWORD`；本地也兼容原有的 `work/build/Old_cucumber.jks`。私钥不提交仓库。

正式 APK 启用 R8 代码裁剪、资源裁剪与 DEX 压缩，体积约 1.3 MB，构建时限制为 2 MiB。Debug APK 保留调试代码，体积较大。LSPosed 入口与 ViewModel 构造函数有专用保留规则。

只进行开发构建：

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

有 Android 设备或模拟器时，运行界面和持久化测试：

```sh
./gradlew connectedDebugAndroidTest
```

## 自动构建

推送 `main`、提交 Pull Request 或手动运行 **Android Build** 都会触发 GitHub Actions。

- 构建任务执行单元测试、Lint、APK 构建、签名及 APK 结构校验。
- 界面任务使用 Android 15 模拟器，验证搜索、选择、字母索引、中文分组、折叠滚动和配置存储，并保存截图与报告。随后安装实际构建的 APK，检查裁剪后的启动和应用加载。
- 在 Actions 对应运行的 Artifacts 中下载 APK、源码、构建记录和 SHA-256 校验文件。它们保留 30 天。
- 仓库配置 `OPPO_SIGNING_KEYSTORE`（Base64）、`OPPO_SIGNING_STORE_PASSWORD`、`OPPO_SIGNING_KEY_ALIAS`、`OPPO_SIGNING_KEY_PASSWORD` 后，主分支生成原签名 APK；PR 和未配置签名的副本生成 Debug APK。

工作流只构建和上传附件，不创建 Tag 或 Release。2.0.0 暂不发布。

## 实现与验证

Hook 点来自 `com.oplus.linker` 17.7.30：在内存中合并原始名单、用户应用和手动选择，覆盖配置重载与缓存刷新；兼容旧 PCSynergy 名单路径。配置通过模块自己的 ContentProvider 传给互联进程。代码位于 `module/`，不改互联 APK、不改 XML、不挂载文件。

自动化检查覆盖名单策略、界面和配置存储。真实设备上的 LSPosed 注入、电脑端刷新与实际投屏仍需实机验证；模拟器界面测试不代表 OPPO 投屏测试。日志前缀为 `[OPPOCastUnlock]`。

## 协议

Copyright (C) 2026 Old_cucumber。采用 **GPL-3.0-only**，见 `LICENSE`。第三方依赖见 `THIRD_PARTY.md`。
