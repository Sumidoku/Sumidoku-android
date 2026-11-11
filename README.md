# sumidoku-android

Sumidoku 的原生 Android 客户端（Kotlin + Jetpack Compose）。

- 架构设计：[docs/android-client.md](docs/android-client.md)——定位（与 webui 功能全量对齐的薄计算客户端）、模块分层、技术栈、API codegen、下载/离线、认证、SSE、里程碑 M0–M9。
- 界面设计稿：[design/preview/index.html](design/preview/index.html)——主要屏幕的 HTML 静态预览（浏览器直接打开）。

服务端仓库：Sumidoku（REST `/api/v1`，OpenAPI 契约）。设计血统：Aidoku 内置源模式（Komga/Kavita/Suwayomi 把服务器包装成统一 Source 抽象）。

## 构建

需要 JDK 17+ 与 Android SDK（compileSdk 37，minSdk 26）。首次构建：

```powershell
.\gradlew.bat assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`。

## 已实现（按里程碑）

- **M0 骨架**：20 模块 Gradle 工程（app / core:{common,model,api,network,data,source,stream,download} / feature:{servers,library,browse,search,manga,reader,source,repos,updates,history,downloads,migrate,trackers,settings,backups}）；OpenAPI codegen（`api/openapi.json` 快照 + sha256，openapi-generator kotlin/jvm-retrofit2，含 6 项针对生成怪癖的 patch 任务）；AGP 9.5.1 / Kotlin 2.4.21 / Compose BOM 2026.09.00。
- **M0 服务器管理**：多服务器 + 镜像故障转移（连接级失败按序重试镜像，HTTP 响应不重试——Aidoku KomgaHelper 模式）、API key bearer 认证、版本探测。
- **M1 书库/浏览**：书库网格（分类/过滤组弹层、5 种排序、下拉刷新、哨兵翻页、未读角标）；浏览页 Aidoku 式分组（可更新/置顶/已安装，⚠ 缺失仓库徽标，⚙ 设置入口，全部更新）；添加图源聚合列表（一键安装）；跨源搜索（三态筛选 + NDJSON 渐进渲染）。
- **M2 详情 + 阅读器**：详情页（无痕开关、继续阅读、章节三态、批量已读）；阅读器（HorizontalPager + 缩放仲裁、点按区五布局、Webtoon 连续滚动、文本页渲染、章节过渡页、pull-to-next-chapter、失败重试、700ms 防抖进度回写、离线进度队列、双页/拆宽页、预取）。
- **M3 下载**：Tier A 服务端队列管理（SSE 驱动）；Tier B 本机离线引擎（CBZ 拉取解压、离线阅读直接读本地文件、存储空间检查、全部清除、WorkManager 续传）。
- **M4 实时/后台**：SSE 通道（core:stream，Last-Event-ID + 指数退避）；WorkManager 每 6h 更新检查 + 系统通知；NDJSON 渐进搜索（opt-in，异常回退非流式）。
- **M5 图源管理**：图源主页（HomeLayout 组件栈 + 过滤器面板）、schema 驱动设置编辑器、仓库管理、内部源创建（local/OPDS）、.aix 导入、登录三法（basic 表单 / WebView 抓 cookie / OAuth 回调拦截——反超 webui 粘贴流）、置顶/停用/置顶顺序、本地回收站、任务队列查看器。
- **M6 元数据 + tracker**：编辑元数据（字段锁）、匹配元数据（预览勾选 + bind）、备选封面、恢复图源元数据；tracker 链接（绑定/同步/解绑）与账号绑定（WebView OAuth 拦截 code/state）、sync-pull preview 确认。
- **M7 迁移与管理**：批量迁移向导（来源源→多选→多目标→自动匹配→确认→执行）+ 单条迁移（详情页）+ 三种模式；分类管理、过滤组管理、服务器设置编辑器、缓存管理、API key 管理与修改密码。
- **M8 备份**：导出完整备份 JSON 到本机、从文件恢复、服务器定时备份列表恢复、Aidoku .aib 与 Mihon .tachibk 导入向导（dry-run 预览 + 逐源映射）、计划备份设置。
- **M9 打磨**：NavigationSuiteScaffold（窄屏底栏/宽屏导航轨）、ListDetailPaneScaffold 书库双栏（≥840dp）、自适应网格；双页布局（含不含封面变体）；点按区五布局 + 图形预览；页面缩放四模式、背景四选项、亮度/灰度/棕褐滤镜；主题（跟随系统/深色/浅色）；CI（android.yml 构建检查 + release.yml 签名发布）。

## 已知缺口

Button 类型的图源设置（生成器丢弃了 button 变体，落兜底行）、清图源缓存按钮、deep-link 分享解析、设置页双栏与折叠屏铰链细节避让。
