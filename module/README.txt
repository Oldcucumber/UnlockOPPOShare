OPPO互联全应用投屏 LSPosed 模块 v1.0
作者：Old_cucumber
包名：com.oldcucumber.oppocastunlock

无 Activity、无启动图标、无 UI、无配置项。
安装后在 LSPosed 中启用，勾选 OPPO 互联（com.oplus.linker），再重启互联进程或重启手机。
自动合并当前用户的全部第三方应用到内存白名单，保留原有系统应用名单。
覆盖列表读取、配置重载、白名单赋值以及应用列表缓存刷新；兼容旧 PCSynergy 名单路径。
不修改 XML、不挂载、不改互联 APK。无需选择系统框架作用域。
模块仅解除已定位的应用内白名单筛选；实际投屏效果由用户验证。
日志在 LSPosed 模块日志中，前缀为 [OPPOCastUnlock]。

本次只进行编译、APK 结构和签名校验、安装确认。未进行投屏或运行时 Hook 效果测试。
Xposed API 82 仅作为编译依赖，不打包进 APK。
