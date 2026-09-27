# 折叠屏分屏助手 Plus

给 vivo 折叠屏（OriginOS）用的多窗工具，通过 [Shizuku](https://shizuku.rikka.app/) 获得系统权限，把系统自带的分屏、小窗开放给原本被挡在外面的应用，并可调整应用在内屏的显示比例。

它不自己做分屏或小窗，而是把应用加进 OriginOS 的允许名单，之后仍然用系统原生的入口（最近任务 → 分屏 / 小窗）。

> 基于 xch20 的「折叠屏分屏助手」（`com.xch20.foldsplit`）重写，核心的系统调用方式沿用原版，在此基础上增加了批量操作、备份还原、重启后重新应用等功能。

## 功能

- **应用列表**：带图标，可搜索、多选，可按用户应用 / 全部 / 已修改筛选；每个应用显示当前内屏比例和分屏状态
- **内屏显示比例**：全屏布局优化、全屏、4:3、16:9、与外屏相同（21:9）；可批量设置；自动记录修改前的比例，随时一键恢复；单个应用设置后自动重新打开
- **原生分屏**：把应用加入 OriginOS 左右分屏 + 上下分屏兼容列表；检测分屏状态；移除（实验，依赖固件提供移除命令）
- **全应用小窗**：打开系统小窗相关开关并把所有应用加入小窗允许列表；首次开启前自动备份开关，可一键还原
- **重启后重新应用**：通过开机次数检测手机是否重启过，提示或自动重新应用；另提供下拉栏快捷开关「多窗重新应用」
- **诊断报告**：导出机型、系统版本、相关开关当前值、splitconfig 帮助、vivo 多窗接口签名，便于排查

## 适用范围

- 依赖 vivo OriginOS 私有接口（`cmd activity splitconfig`、`VivoFreeformManager`、`fold_inner_screen_ratio_modified_by_user`），**只适用于 vivo 设备**，主要面向 X Fold 系列；其他品牌上这些功能会直接报错，不会造成改动
- Android 12 及以上
- 需要 Shizuku（ADB 或 Root 模式）

## 使用

1. 安装并启动 Shizuku
2. 打开本 App，点「启动 / 授权 Shizuku」完成授权
3. 在列表里勾选应用，再执行对应操作
4. 手机重启后先启动 Shizuku，再打开本 App 或点下拉栏快捷开关重新应用（内屏比例保存在系统设置里，重启不会丢）

## 编译

需要 JDK 17+ 和 Android SDK（compileSdk 35）。

```bash
# local.properties 里写 sdk.dir=<Android SDK 路径>
./gradlew assembleRelease
```

发布签名从 `keystore/keystore.properties` 读取（不在仓库中），文件格式：

```properties
storeFile=foldsplit-plus.jks
storePassword=...
keyAlias=foldsplit
keyPassword=...
```

没有这个文件时 release 构建不带签名，可以改用 `./gradlew assembleDebug`。

## 代码结构

| 文件 | 作用 |
|---|---|
| `PrivilegedShellService` | 在 Shizuku 拉起的 shell 身份进程里执行命令、反射调用 vivo 系统接口、读写 Secure 设置 |
| `ShizukuShell` | App 进程侧，绑定 Shizuku UserService 并转发调用 |
| `FoldOps` | 所有多窗操作的同步实现，界面和快捷开关共用 |
| `InnerRatioConfig` | 内屏比例配置字符串（`:包名,比例:` 格式）的解析与改写 |
| `Store` | 本地记录：开关备份、修改过的应用、原比例、上次应用时的开机次数 |
| `MainActivity` / `AppListAdapter` | 界面 |
| `ReapplyTileService` | 下拉栏快捷开关 |
