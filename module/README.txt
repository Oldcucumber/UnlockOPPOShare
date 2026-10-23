UnlockOPPOshare LSPosed 模块 v2.0.0
作者：Old_cucumber
包名：com.oldcucumber.oppocastunlock

在 LSPosed 中启用模块，作用域选择 OPPO 互联（com.oplus.linker），重启互联进程或手机。
打开「UnlockOPPOshare」，搜索或拖动右侧字母索引找到应用，使用开关选择是否显示。
上滑收起大标题，搜索和筛选随列表滚动；顶部搜索按钮可返回搜索框。
默认显示全部用户应用和互联原有的系统应用，可筛选、批量选择或恢复默认。

构建：JDK 21、Android SDK 36、build-tools 36.0.0、Python 3。
运行 python module/build.py，或通过 GitHub Actions 构建。详细说明见仓库 README.md。
工作流上传 APK、源码、校验文件和测试报告，不自动发布 Release。

Hook 只更改内存名单与应用缓存。真实 LSPosed 注入和投屏效果需实机验证。
日志前缀：[OPPOCastUnlock]。
Xposed API 82 仅编译使用，不打包进 APK。
许可证：GPL-3.0-only，完整协议位于仓库根目录 LICENSE。
