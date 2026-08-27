# Deep Cleaner

<p align="center">
  <img src="assets/deep-cleaner-logo.png" alt="Deep Cleaner Logo" width="180" />
</p>

Deep Cleaner 是一款面向 Android 8.0–16 的本地深度清理与存储管理工具。扫描、分类、摘要计算和文件操作均在设备本地完成，不上传文件名、路径或内容。

## 功能

### 清理与空间管理

- 智能清理：临时文件、缩略图、旧日志、缓存、安装包和空文件
- 空间分析：按图片、视频、音频、文档、压缩包和安装包统计，并显示目录占用排行
- 大文件：支持 100 MB、256 MB、512 MB 和 1 GB 阈值
- 重复文件：先按大小分组，再使用 SHA-256 全量校验
- 空文件夹：自底向上扫描，删除前再次确认目录仍为空
- 下载管理：显示文件类型、目录和修改时间，支持归档或删除

### 媒体与安装包

- 相似照片：使用感知哈希、亮度差和清晰度分析识别相似图、截图和模糊照片
- 媒体瘦身：为大图片生成 JPEG 压缩副本，为大视频生成 720p H.264/AAC 副本；原文件保持不变
- APK 管理：识别包名、版本、CPU 架构和签名，标记旧版、重复版和已安装版本

### 应用与隐私

- QQ 专清：定向扫描 QQ 缓存、日志、缩略图、图片、视频、语音和接收文件
- 微信专清：定向扫描微信缓存、日志、缩略图、聊天媒体与接收文件
- 应用深度分析：显示应用、数据、缓存大小和最后使用时间
- 长期未用应用：筛选 90 天未使用的用户应用，并跳转到系统卸载或空间管理页面
- 隐私检查：定位日志、崩溃记录、数据库、备份、导出文件及疑似凭据文本
- 保护名单：按路径、扩展名或应用包名排除扫描与清理

### 安全与自动化

- 删除前复核：再次检查路径、文件类型、大小和重复文件摘要
- 回收站：默认关闭；开启后支持恢复、永久删除、自动过期和容量上限
- 清理历史：保存最近 200 次清理记录，并可通过回收站撤销
- 自动清理：支持每天/每周、仅充电、仅空闲、存储阈值和仅扫描模式
- 系统缓存：调用 Android 系统缓存清理界面，由用户确认操作

### Android 集成

- 外部存储：通过 Storage Access Framework 连接并分析 SD 卡、U 盘和云端文档目录
- 桌面小组件：显示存储使用率、可用空间并快速启动扫描
- 自适应布局：手机使用底部导航，平板、折叠屏和桌面窗口使用侧边导航
- Android 16：`compileSdk = 36`、`targetSdk = 36`、边到边布局和预测返回兼容

### 扫描性能

- QQ/微信专清只遍历已知共享目录，避免无关全盘扫描
- 扫描过程只读取一次目录项，并使用累计大小更新进度，避免反复汇总候选列表
- 重复文件先执行首尾 64 KB 快速摘要，仅对可能重复的文件进行 SHA-256 全量校验

## 安全模型

1. 只扫描用户授权的共享存储或外部目录，跳过符号链接、存储根目录和应用保护目录。
2. 保护名单中的路径、扩展名和应用目录不会进入清理候选。
3. QQ/微信聊天媒体与接收文件、相似媒体、APK、隐私文件、大文件、下载内容和媒体压缩候选默认不勾选。
4. 永久删除前显示数量和大小；媒体瘦身只生成副本，不替换原文件。
5. Android 不允许普通应用静默清除其他应用私有数据，相关操作使用系统授权页面。

## 权限

- `MANAGE_EXTERNAL_STORAGE`：扫描和管理共享存储；Google Play 发布需要申报核心用途
- `QUERY_ALL_PACKAGES`：显示已安装应用和安装包版本对照
- `PACKAGE_USAGE_STATS`：可选，由用户在系统设置中授权，用于应用大小和最后使用时间
- `POST_NOTIFICATIONS`：可选，用于自动扫描或清理结果通知

## 构建

要求 JDK 17 和 Android SDK Platform 36。

```powershell
./gradlew.bat testDebugUnitTest lintDebug assembleDebug
./gradlew.bat assembleRelease
```

输出目录：

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release.apk
```

## 项目结构

```text
app/src/main/java/com/kiowx/deepcleaner/
├── core/       # 扫描、分类、压缩、安全策略、历史、回收站和存储访问
├── ui/         # Compose 页面、自适应布局与组件
├── widget/     # 桌面小组件
├── worker/     # WorkManager 自动任务
├── MainActivity.kt
└── DeepCleanerViewModel.kt
```

## 社区与作者

- QQ 交流群：670804369
- 作者：[Kio](https://github.com/Kiowx)

## License

本项目代码采用 Apache-2.0 License。
