# 41 屏设计稿 → Compose 落地验收清单

对应 `design/preview/01–41`。状态：✅ 已落地 / 🟡 已落地但有已知简化 / ⛔ 受后端接口限制。

| # | 设计屏 | Compose 实现位置 | 状态 | 备注 |
|---|--------|------------------|------|------|
| 01 | 服务器 | `feature/servers/ServersScreen.kt` | ✅ | 多实例列表、新增/切换/删除、连接状态 |
| 02 | 书库（默认紧凑网格） | `feature/library/LibraryScreen.kt` `LibraryGrid`/`CompactCell` | ✅ | 标题压封面渐变 + 描影、Auto 列数 `GridCells.Adaptive(100.dp)` |
| 03 | 浏览 | `feature/browse/BrowseScreen.kt` | ✅ | 搜索框、置顶分组、更新胶囊、overflow（全部更新 / 添加导入 / 编辑置顶顺序） |
| 04 | 图源页 | `feature/source/SourceHomeScreen.kt` | ✅ | 大图轮播 + 圆点/页码、热门/最新/分类/排行榜、overflow（源信息/浏览器/设置/登录） |
| 05 | 聚合搜索 | `feature/search/SearchScreen.kt` | ✅ | 跨源结果 + 图源 chip + 过滤面板 |
| 06 | 漫画详情 | `feature/manga/MangaScreen.kt` | ✅ | 元数据/追踪/章节列表/批量/筛选 |
| 07 | 阅读器 · 分页 | `feature/reader/ReaderScreen.kt` `PagedReaderBody` | ✅ | 五键底栏、章节导航胶囊、RTL 页序、失败重试 |
| 08 | 阅读器 · 条漫 | `feature/reader/ReaderScreen.kt` `VerticalReaderBody` | ✅ | 右缘滚动条（thumb/气泡/章节刻度）、三键底栏 |
| 09 | 下载 | `feature/downloads/DownloadsScreen.kt` | 🟡 | 双 tab + 重试 + 章节页缓存卡已落；「源内串行位置」「速度」依赖服务端字段，未展示 |
| 10 | 更新 | `feature/updates/UpdatesScreen.kt` | ✅ | 按日分组 + 每漫画聚合角标 + 更新摘要卡 |
| 11 | 历史 | `feature/history/HistoryScreen.kt` | ✅ | 按日分组 + 读到 x/y 页 + 继续/读完 + 点按回章 |
| 12 | 设置 | `feature/settings/SettingsScreen.kt` | ✅ | 外观（主题/AMOLED/无痕/阅读器默认）、图源、服务器、关于（版本） |
| 13 | 图源设置 | `feature/source/SourceSettingsScreen.kt` | ✅ | 组头登录标签、BASIC 登出、schema 驱动表单 |
| 14 | Tracker | `feature/trackers/TrackersScreen.kt` | 🟡 | 绑定/解绑/默认追踪器 + 同步策略说明；「最近推送台账」无接口 |
| 15 | 迁移向导 | `feature/migrate/MigrateScreen.kt` | ✅ | 选源 → 选目标 → 自动匹配 → 迁移，可只迁进度 |
| 16 | 备份 | `feature/backups/BackupsScreen.kt` | ✅ | 导出/恢复/服务器定时备份/.aib 与 .tachibk（含 dryRun 预览与源映射向导） |
| 17 | 添加图源 | `feature/browse/AddSourceScreen.kt` | ✅ | 导入 .aix、内部源两张卡、分组头、已安装行保留显示「已安装」 |
| 18 | 图源仓库 | `feature/repos/ReposScreen.kt` | ✅ | 分组头、长按删除、stale 行、行级刷新、URL 中文校验 |
| 19 | 编辑元数据 | `feature/manga/*EditMetadataSheet` | ✅ | 字段级锁标记/解锁/恢复图源元数据 |
| 20 | 匹配元数据 | `feature/manga/*MatchMetadataSheet` | ✅ | 搜索 → 预览 diff → 应用 |
| 21 | 备选封面 | `feature/manga/*CoverPickerSheet` | ✅ | 网格选择 + 当前封面 |
| 22 | 单条迁移 | `feature/manga/*MigrateSheet` | ✅ | 多目标源匹配 + copy/migrate/历史 |
| 23 | 书库批量模式 | `feature/library/LibraryScreen.kt` 批量态 | ✅ | 长按进入、全选、设置分类（全量替换）/标为已读/移出书库 + 二次确认 |
| 24 | 阅读器设置 | 阅读器内 `ReaderSettingsSheet` + 设置页 `ReaderDefaultsScreen` | ✅ | 解析链、模式/布局/方向/点按区/缩放/背景/滤镜/亮度 |
| 25 | 阅读器章节抽屉 | `ReaderScreen.kt` `ChapterSheet` | ✅ | 定位当前话、缺章警示、失败重试、行内徽标 |
| 26 | 回收站 | `feature/settings/TrashScreen.kt` | ✅ | 恢复/彻底删除/清空 + 二次确认 |
| 27 | 设置 · 更新与缓存 | `feature/settings/ServerSettingsScreen.kt` | ✅ | skipTitles 三态、排除分类、自动下载、每章缓存预算与占用 |
| 28 | 设置 · 账户与同步 | `feature/settings/ApiKeysScreen.kt` | 🟡 | API key 新建/撤销齐全；实例间同步行禁用（服务端无 peer 接口） |
| 29 | 图源 listing | `feature/source/SourceHomeScreen.kt` listing 分支 | ✅ | 筛选面板 + 分页加载 |
| 30 | 添加内部源 | `AddSourceScreen` `InternalSourceDialog` | ✅ | 名称/路径（可多个）/清单文件/排除扫描开关 |
| 31 | 平板 / 折叠屏 | `app/SumidokuRoot.kt` | ✅ | 双栏 ListDetail 已移除，全宽自适应网格；导航轨 |
| 32 | 书库 · 舒适网格 | `ComfortableCell` | ✅ | 2:3 封面 + 标题/副标题在下 |
| 33 | 书库 · 列表 | `ListRow` | ✅ | 52dp 封面 + 两行标题 + 未读/下载徽标 |
| 34 | 书库 · 显示弹层 | `DisplaySheet` | ✅ | 形态三选 + 密度 Auto/2/3/4（横竖屏分别记忆）+ 徽标开关 |
| 35 | 书库 · 空态 | `LibraryEmptyState(false, …)` | ✅ | 去浏览图源 CTA |
| 36 | 书库 · 筛选无结果 | `LibraryEmptyState(true, labels, …)` | ✅ | 显示当前筛选 chips + 清除筛选 |
| 37 | 主题 · AMOLED | `ui/theme/Theme.kt` `SumidokuAmoled` | ✅ | 背景 #000，容器分层近黑，设置页开关 |
| 38 | 主题 · 浅色 | `SumidokuLight` | ✅ | 设置页三档 chips |
| 39 | 阅读器 · 横屏双页 | `PagedReaderBody` + `buildSpreads` | ✅ | AUTO=横屏双页、宽页独占、封面单独成页、RTL 交换 |
| 40 | 阅读器 · 过渡页 | `TransitionPage` | ✅ | 下一话/缺章/末话三态 + 拉动进下一章 |
| 41 | 阅读器 · 快捷面板 | `QuickPanelSheet` | ✅ | 模式/布局/方向即时生效并标注是否本漫画覆盖 |

## 已知遗留（非本轮可闭环）

1. **09 下载行**：设计稿的「源内串行位置」「下载速度」需要服务端在 `Download` DTO 增加字段，当前只有 downloaded/total/status。
2. **14 Tracker 台账**：`TrackersApi` 只暴露 OAuth 状态、绑定、解绑、默认追踪器，没有「最近推送记录」端点；本页以策略说明替代。
3. **28 实例间同步**：服务端尚无 peer 同步接口，保留禁用行并在副标题说明。
4. **17 已安装态**：按用户定稿保留该行并显示「已安装」，不做安装后消失。
5. **屏幕级目检**：本清单基于代码对照；真机截图核验需在设备/模拟器上进行。
