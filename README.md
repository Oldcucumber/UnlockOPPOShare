# OPPO 互联全应用投屏

作者：Old_cucumber

无界面的 LSPosed 模块。安装后在 LSPosed 中启用，勾选 `com.oplus.linker`（OPPO 互联），再重启互联进程或手机。

源码在 `module/`。只改目标应用的内存白名单和缓存，不写 XML、不挂载、不改互联 APK。模块没有 Activity、启动入口或配置项。

构建：

```powershell
python -X utf8 module/build.py
```

需要 JDK、Android SDK platform 36 和 build-tools 36.0.0。脚本从官方仓库取得编译用 Xposed API 82，并校验固定 SHA-256；API 桩不打包进 APK。

产物在 `dist/`，中间文件在 `work/build/`。签名密钥为 `work/build/Old_cucumber.jks`，后续更新需保留这把密钥；不要提交该目录。

本次仅进行构建、APK 结构和签名校验、手机安装确认；实际 Hook 和投屏由用户自行测试。
