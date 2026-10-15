# 拾账：交接

更新日期：2026-10-15（Asia/Shanghai）。

## 当前任务与目标

用户已要求“根据新的方案重新开发应用”。主流程改为主动导入支付详情或多笔账单列表截图，识别、分类、记账，并通过问题查询真实账本。用户明确选择“清楚的自动入账，缺失、模糊或疑似重复的先确认”，不要再询问。

当前 `0.2.3-screenshot`：用户选定应用名称「拾账」，已更新桌面、分享入口、设置页名称及暖金色票据自适应图标，系统启动画面使用相同图标与暖纸底色。延续 0.2.2 的字号、四页布局、插画与动画。无年份的月日默认当年，显式年份和手动修改仍优先。仍需用户在小米 15 安装验收。实时采集验证不再是开发前置条件；旧任务 #2 未取得的真机验证仍不能标记完成。

## 用户偏好与约束

- Kotlin、Compose、Material 3，Android 12+，Room 正式账本，人民币整数分；暖白、沙色、琥珀色、深棕文字。
- 图片优先本地识别，不自动上传、另存或删除相册原图；用户已改为只有月日时默认当年，截图明确年份优先，可手动修改默认年份。不推测遮挡金额，不按同金额删除真实交易。
- 退款抵扣消费，内部转账排除统计；只有正式账单计入统计与问答。模型不计算金额、不执行 SQL、不写入账本。
- DeepSeek 可选且默认关闭；仅在手机自行填写密钥、明确开启后，才会在本地不能理解问题时发送当次问题、日期和分类选项。问题中自带的商户或金额会随问题发送。密钥用 Keystore 加密，不显示原值。
- 本轮只实现聊天只读查账；聊天写指令会引导到账本页面，通过明确界面操作确认新增、修改或删除。
- 用户此前通过文件传输安装，无需以 adb 未连接阻塞交付。真机结果没有取得前，不声称兼容。
- 用户强调页面协调、文字不要过大，可以增加插画和动画；回复简短，不额外展开技术汇报。
- 软件名称已明确选择「拾账」，不重复询问。本轮「封面」按桌面图标和系统启动画面实现。

## 工作区状态

- `D:\Acounting-agent-app`，分支 `main`；本次提交前基线 `cb61840`，最新提交以 `git log -1` 为准。
- 项目代码、Gradle、文档等此前大量未跟踪。2026-10-15 用户要求将相关文件推送仓库，已按授权整理源码、测试、Room schema、插画、图标、构建脚本和文档用于提交到现有 `origin/main`。同步状态以 `git status -sb` 和远端提交为准。
- 推送前已检查 54 个候选文件和常见密钥格式，未发现疑似密钥；APK、签名、本机 SDK 配置、测试截图、工具链与缓存均由 `.gitignore` 排除。没有新增用户真实支付图片或账单导出。
- 本机 JDK 17、SDK 36、Gradle 缓存和原调试签名在被忽略的 `.tools/`，SDK 配置为被忽略的 `local.properties`；不要删除签名。
- 原 GitHub #1–#6 保留历史状态。本轮没有发布新规格、任务或评论，没有关闭旧任务；跟踪文档已注明截图版不沿用旧实时采集依赖。

## 已实现

- `ScreenshotImporter.kt`：图片选择与相册分享入口的数据处理，本地中文 ML Kit OCR，文件 SHA256、单张 32 MB 上限、解码像素限制，处理后释放图片。
- `ScreenshotParser.kt`：按 OCR 位置合并同行、逐笔解析日期分组和交易明细；排除日合计，处理单笔详情、正负金额、退款、缺少年份、遮挡金额、未完成状态；保留日期提示供核对。
- `LedgerRules.kt`、`LedgerDatabase.kt`：Room 账本、待确认、原子导入与图片标记、交易号去重、跨图片疑似重复、确认纠错、编辑删除、清空、记住商户分类、统一统计口径。
- `LedgerQuestions.kt`：本地日期/分类/商户/方向/类型过滤，明细、汇总、笔数、最大一笔；无效或未理解的条件提示澄清，不能静默忽略条件。金额由同一账本规则计算。
- `DeepSeekQuery.kt`：固定 HTTPS 端点的可选只读条件理解，有限字段/日期/枚举校验、超时与响应长度限制；Android Keystore 密钥存储。没有在开发机配置或读取真实密钥，没有调用真实服务。
- `AccountingViewModel.kt`、`MainActivity.kt`、`AccountingScreens.kt`：账本、统计、查账、设置四页，系统图片选择器、分享导入、当年默认与可选年份修改、核对与重复提示、手动记账、搜索、编辑删除、密钥设置及清除本机数据。
- Manifest 已取消通知监听与无障碍服务注册，移除其无用资源；旧采集类与测试保留历史代码，未接入新入口。无全相册读取权限；有用于可选 DeepSeek 的 INTERNET 权限。
- 更新 README、截图规格、安装说明、术语表与任务跟踪说明。用户真实图片、文字、商户和金额未复制进仓库。
- 0.2.2 视觉改造集中于 `MainActivity.kt`、`AccountingScreens.kt` 和新增的 `AccountingVisuals.kt`：标题 20sp、正文 14–15sp；票据总览、按日明细、统计环图、查账引导和折叠设置。小屏或大字号下首页操作卡上下排列。未改变业务逻辑或数据库结构。
- 两张生成的透明插画已内置于 `app/src/main/res/drawable-nodpi/receipt_illustration.png` 和 `fox_illustration.png`；完整提示词、配色和动效参数见 `docs/ui-design.md`。未使用用户支付图片生成资产。
- 0.2.3 修改 Manifest、`res/values/strings.xml`、`themes.xml`、`drawable/ic_launcher*.xml` 和 `mipmap-anydpi/ic_launcher.xml`，使用原生矢量分层，包含 Android 13+ 单色主题图标。设置页复用名称资源，未添加依赖或等待页。包名、签名、Room schema 保持原值。

## 验证与产物

- 已实际运行多次 TDD 红绿循环；相关失败与通过日志保存在被忽略的 `.tools/`。
- 最终 `./scripts/build.ps1 -Offline`：28 项测试，0 失败/错误，日志 `.tools/branding-verified.log`。包含旧采集 13、截图解析 6、入账统计 2、查询及模型校验 3、真实 Room 持久化 1、实际 Compose 页面操作 3。保留列表与详情的当年默认、显式年份和手动年份优先覆盖。本轮为名称与资源替换，没有新增行为测试。
- Room 测试在 Robolectric Android 12.1 环境验证关闭重开、确认、编辑删除、重复图片标记、分类规则和清空。Compose 测试从实际页面确认手动入账，再查账并核对金额。二者均不是手机或 Android 模拟器实测。
- 最终 `./scripts/build.ps1 -Offline` 成功：测试、lint 与 assembleDebug 成功；lint 为 **0 errors, 5 warnings**，分别是 kapt/KSP 性能建议和 SharedPreferences KTX 建议，不能写成“lint 无问题”。
- APK：`app/build/outputs/apk/debug/app-debug.apk`，同内容交付副本 `screenshot-accounting-0.2.3.apk`，77,124,523 字节。包名 `com.xixi.accounting`，versionCode 6，minSdk 31，targetSdk 36，名称“拾账”。已用 `aapt dump badging` 核对包名、名称、版本与图标资源。旧版副本保留，非本轮交付包。
- 当前 APK 的 SHA256 为 `B9686BCEF8B33C022B011B84A7F934A971F1BA6E83A243F6EF03BD317540B813`。重建后需要重算。
- `apksigner verify --verbose --print-certs` 通过（v2）；签名证书 SHA256 为 `e59722f650340d3519a479fea1893c3da5419a1ffd4807bdcf5f264e9e7f7f73`。
- `zipalign -c -P 16 4` 通过，仅为 ZIP 对齐验证，不代表 native ELF 或指定手机已验收。
- 已检查最终合并 Manifest 的版本、最低系统、权限、禁止备份和禁止明文网络，确认不注册旧微信通知/无障碍服务。debug 包仍含现有 Compose 调试预览组件。
- 已检查 tracked diff 和新增源码/文档，确认 APK、签名及本机 SDK 配置被 Git 忽略；没有运行真机安装或真实 DeepSeek 服务验证。
- Compose 使用 Robolectric 原生图形渲染，已检查 `.tools/ui-previews/` 中首页（空账本和人工示例）、统计、查账、设置、小屏大字号和横屏截图。三笔人工示例净支出 87.45，餐饮查账返回 23.45。已验证 320dp / 1.3 倍字体 / 关闭动画下入口可达，以及横屏编辑取消与导航；这些均不能代替手机实测。
- 从图标矢量路径生成 `.tools/ui-previews/brand-preview.png` 并实际查看，包含圆形、圆角、48/64px 和单色预览；预览脚本 `.scratch/preview-brand.cjs`。这是资源预览，未实际观察系统桌面或启动动画。

## 未完成与限制

- 小米 15 的图片选择、相册分享、真实图片 OCR 效果、安装、键盘/旋转/大字体、重启和后续覆盖更新仍未实测。验收清单见 `docs/screenshot-validation.md`，保持“未实测”。
- 不能保证识别任意账单布局；缺少月日、金额、方向或明确完成状态的记录需要确认，只有月日时默认当年。单张只显示部分日明细时，不用日合计推算缺项。
- 本地问题每次独立，只支持单组筛选，不继承前一问，也不做多组比较、预算或预测。无法理解可提示重写，或在用户开启后尝试 DeepSeek。
- Keystore 真机行为、真实 DeepSeek 返回、失效密钥、断网和超时仍需实际验证；现有测试验证查询条件校验和失败不写账本的结构边界，不能声称完成联网验收。
- Room 当前 schema 为 **1**，已导出到 `app/schemas/`。此 APK 交付后改变持久化字段必须提升版本并提供迁移，不得以破坏性重建丢弃账本。

## 下一步

1. 用户将 `screenshot-accounting-0.2.3.apk` 传到手机覆盖安装，检查名称、桌面图标和启动画面。先走本地截图与查账，不需要通知或无障碍权限，也不需要密钥。
2. 按截图版验收清单记录真实布局、错误提示和字段缺失场景；反馈使用脱敏说明，不把真实截图和账单发布到 GitHub。
3. 若要验证可选联网理解，让用户在手机自己填写 DeepSeek 密钥并开启；不要求其把密钥发到聊天。
4. 按真实失败补回归并修复，串行运行测试、lint 和构建，重新验证签名/对齐/哈希。手机结果未取得前不关闭安装验收。

## 避免重犯

- 不并发运行 Gradle 构建，曾出现 Kotlin cache 冲突和文件锁。需要构建时使用项目脚本，不另起竞争任务。
- 默认沙箱阻止 Gradle 连接本机 daemon；本轮构建在自动批准的提升权限环境执行。不能把 daemon 连接失败当成测试用例失败。
- 图标资源目录移动后 Gradle 增量缓存曾未识别新路径；清理时 lint 缓存被 daemon 占用。使用同一项目环境先 `gradlew.bat --stop`，再 `:app:clean`，最后项目构建脚本，已恢复。不要并发清理和构建。
- 首次 Robolectric 会下载额外 Android 测试运行时（本轮已缓存 Android 12.1 和用于资源加载的 Android 15），下载数分钟不等于 Room 死锁。已用只读线程栈确认是下载等待。
- LazyColumn 尚未展示的节点需要先滚动到目标再操作；页面查询异步完成后再断言结果。首条 UI 测试因此修正测试滚动方式，未以删掉断言绕过。
- 不把旧无障碍 OCR 关闭选择解释为禁止主动导入图片；不要回到旧计划继续要求实时交易测试。
- Robolectric 截图用 `decorView.draw(Canvas(bitmap))`，`captureToImage` 的 PixelCopy 会超时。横屏 qualifiers 顺序为 `w800dp-h360dp-land-xhdpi`。Dialog 属于独立窗口，`editor-landscape.png` 仅展示背后 Activity，弹窗通过语义断言验证，不能说 PNG 展示了弹窗。
