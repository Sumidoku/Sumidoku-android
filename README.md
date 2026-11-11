# sumidoku-android

Sumidoku 的原生 Android 客户端（设计阶段，尚无实现代码）。

- 架构设计：[docs/android-client.md](docs/android-client.md)——定位（与 webui 功能全量对齐的薄计算客户端）、模块分层、技术栈、API codegen、下载/离线、认证、SSE、里程碑 M0–M9。
- 界面设计稿：[design/preview/index.html](design/preview/index.html)——主要屏幕的 HTML 静态预览（浏览器直接打开）。

服务端仓库：Sumidoku（REST `/api/v1`，OpenAPI 契约）。设计血统：Aidoku 内置源模式（Komga/Kavita/Suwayomi 把服务器包装成统一 Source 抽象）。
