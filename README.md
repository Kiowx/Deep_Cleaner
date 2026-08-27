# Deep Cleaner

<p align="center">
  <img src="assets/deep-cleaner-logo.png" alt="Deep Cleaner Logo" width="180" />
</p>

Deep Cleaner 是一款面向 Android 8.0–16 的本地深度清理与存储管理工具。扫描、分类、摘要计算和文件操作均在设备本地完成，不上传文件名、路径或内容。

当前版本：**1.1.0**

## 功能

### 清理与空间管理

- 智能清理：临时文件、缩略图、旧日志、缓存、安装包和空文件
- 空间分析：按图片、视频、音频、文档、压缩包和安装包统计，并显示目录占用排行
- 大文件：支持 100 MB、256 MB、512 MB 和 1 GB 阈值
- 重复文件：先按大小分组，再使用 SHA-256 全量校验
- 空文件夹：自底向上扫描，删除前再次确认目录仍为空
- 下载管理：显示文件类型、目录和修改时间，支持归档或删除
- 扫描结果：支持名称/路径搜索、大小/时间/名称排序和风险等级筛选，并预估清理后可用空间
- 自定义规则：按路径、扩展名、最小大小和保留天数建立规则，可控制是否默认勾选
- 空目录与残留：合并展示空目录和疑似已卸载应用的共享存储残留
- 文件时间线：查看今天、本周、本月新增文件及长期未修改的大文件
- 压缩包检查：识别内容重复、可能已解压和超过 180 天未修改的压缩包

### 媒体与安装包

- 相似照片：使用感知哈希、亮度差和清晰度分析识别相似图、截图和模糊照片
- 相册质量检查：补充过暗、低清晰度和连拍命名识别
- 截图与录屏：按月份整理系统截图和屏幕录像
- 重复视频：组合时长、分辨率、首尾摘要和 SHA-256 全量摘要复核
- 媒体瘦身：为大图片生成 JPEG 压缩副本，为大视频生成 720p H.264/AAC 副本；原文件保持不变
- APK 管理：识别包名、版本、CPU 架构和签名，标记旧版、重复版和已安装版本

### 应用与隐私

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
- 清理方案：支持安全清理、最大释放和仅整理下载三套方案
- 清理报告：记录分类明细、失败数量、释放空间和执行耗时

### Android 集成

- 外部存储：通过 Storage Access Framework 连接并分析 SD 卡、U 盘和云端文档目录
- 桌面小组件：显示存储使用率、可用空间并快速启动扫描
- 存储趋势：最多保存 90 个本地空间快照，用折线趋势定位异常增长
- 文件保险箱：使用 Android Keystore 管理的 AES-256-GCM 加密文件，导入和导出前使用系统身份验证
- 配置备份：导入或导出自定义规则、保护名单和主要设置
- Root 全局模式：默认关闭；在设置中开启并授权后，智能扫描和自动任务会扩展到应用私有缓存
- 签名规则更新：从项目仓库下载规则，并使用内置 RSA 公钥验证签名后启用
- 应用更新：仅在软件启动时或设置页手动触发检查；从项目仓库读取远程 `update/update.json`，下载后校验 SHA-256、应用 ID 和版本号，再交给 Android 系统安装器确认
- 小组件增强：显示最近一次发现的可清理空间，并提供一键安全扫描
- 自适应布局：手机使用底部导航，平板、折叠屏和桌面窗口使用侧边导航
- Android 16：`compileSdk = 36`、`targetSdk = 36`、边到边布局和预测返回兼容

### 扫描性能

- 扫描过程只读取一次目录项，并使用累计大小更新进度，避免反复汇总候选列表
- 重复文件先执行首尾 64 KB 快速摘要，仅对可能重复的文件进行 SHA-256 全量校验

## 安全模型

1. 只扫描用户授权的共享存储或外部目录，跳过符号链接、存储根目录和应用保护目录。
2. 保护名单中的路径、扩展名和应用目录不会进入清理候选。
3. 相似媒体、APK、隐私文件、大文件、下载内容和媒体压缩候选默认不勾选。
4. 永久删除前显示数量和大小；媒体瘦身只生成副本，不替换原文件。
5. Android 不允许普通应用静默清除其他应用私有数据，相关操作使用系统授权页面。
6. Root 全局模式只接受严格匹配的 `/data/user/<id>/<package>/cache` 目录，且不会进入回收站。
7. 远程规则校验失败、格式异常或超过大小限制时不会落盘或参与扫描。
8. 应用更新只接受本项目 GitHub Release 的 HTTPS 地址，APK 上限为 300 MB；校验失败的文件会立即删除。

## 权限

- `MANAGE_EXTERNAL_STORAGE`：扫描和管理共享存储；Google Play 发布需要申报核心用途
- `QUERY_ALL_PACKAGES`：显示已安装应用和安装包版本对照
- `PACKAGE_USAGE_STATS`：可选，由用户在系统设置中授权，用于应用大小和最后使用时间
- `POST_NOTIFICATIONS`：可选，用于自动扫描或清理结果通知
- `REQUEST_INSTALL_PACKAGES`：可选，仅在用户确认安装已下载更新时使用；Android 仍会显示系统安装确认

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

## 自动发布与应用内更新

工作流位于 `.github/workflows/release.yml`。向 `main` 推送应用或构建相关改动后，工作流会读取 `versionName` 并在对应 Release 尚不存在时自动创建 tag（例如 `v1.2.0`），然后执行：

1. 单元测试、Lint 和 Release 构建。
2. 使用仓库 Secrets 中的固定生产密钥签名 APK。
3. 生成 APK SHA-256 和 `update.json`，并将更新清单提交到仓库的 `update/update.json`。
4. 获取最新提交的正文，完整同步到 GitHub Release，并将正文中 `---` 之前的精简条目同步到 `update.json`。
5. 创建或更新 GitHub Release，Release 仅上传 APK 和 SHA-256 校验文件。

提交信息建议使用以下格式；没有正文时会回退使用提交标题，手动运行工作流也可以直接填写发布说明：

```text
feat: release 1.2.0

- 新增功能 A
- 优化功能 B
- 修复问题 C
---
这里可以写只展示在 GitHub Release、但不展示在应用更新弹窗中的补充说明。
```

如果当前 `versionName` 已有 Release，普通推送会跳过构建；发布新版本前必须同时递增 `versionName` 和 `versionCode`。

需要在仓库 `Settings → Secrets and variables → Actions` 中添加：

- `ANDROID_KEYSTORE_BASE64`：JKS/PKCS12 文件的 Base64 内容
- `ANDROID_STORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

应用使用以下固定地址获取最新版 JSON：

```text
https://raw.githubusercontent.com/Kiowx/Deep_Cleaner/main/update/update.json
```

JSON 示例见 `update/update.example.json`，实际文件由 `tools/generate_update_manifest.py` 自动生成。自动检查默认开启，但每次应用进程启动最多检查一次；不会创建后台更新任务。手动检查入口位于设置页。

生产签名密钥必须永久保存且后续版本保持一致，否则 Android 会拒绝覆盖更新。当前本地测试证书安装的版本首次切换到生产签名版本时需要卸载后重装一次。

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

## License

本项目代码采用 Apache-2.0 License。
