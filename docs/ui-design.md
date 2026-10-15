# 截图记账视觉规范

2026-10-15：用户要求界面更美观、协调，缩小过大的文字，并增加插画与动画。版本 `0.2.2-screenshot`。

## 视觉与排版

- 暖纸底色 `#F8F6F0`、卡片 `#FFFDFA`、深棕文字 `#3B342A`、琥珀主色 `#8C5B16`、蜂蜜底色 `#F2E3BF`、鼠尾草绿 `#54715B`。
- 使用系统无衬线中文字体。页面标题 20sp，正文 14–15sp，辅助文字 11–12sp；首页金额 26sp、统计金额 25sp，金额使用等宽数字特性而非整段等宽字体。
- 页面内边距 20dp，常规区块间距 14–16dp；普通卡片圆角 16–20dp，月度票据卡片 24dp。缩小字形而保留原生控件的触摸面积。小屏或较大系统字号下，首页操作卡片改为上下排列，避免字词挤成窄列。
- 首页以带虚线分隔的票据卡片为主视觉，操作入口并列、明细按日期分组。统计使用真实金额生成环图和分类列表；不注入示例交易到正式应用。
- 查账页采用插画引导、紧凑输入区和问题建议；设置页折叠长说明与可选密钥配置，配置展开后先展示联网数据范围说明。
- 表单、核对、确认、查询条件和账本数据规则沿用原实现。无年份的月日仍默认当年，Room schema 不变。

## 动效

- 页面切换和底部导航颜色过渡 180ms。
- 插画轻微上下浮动和旋转，往返周期 3.2 秒；装饰不接收点击，不遮挡文字和操作。
- 分类环图进入时展开 650ms；设置详情采用原生展开/收起动画。
- 使用 Compose 原生动画机制，遵循系统动画时长设置；保留关闭动画后的完整信息与操作入口。

## 插画资产

使用内置 `image_gen` 工具生成，无需本地 API 密钥。以下均为新生成的透明背景图片，没有使用用户支付截图。

### 票据与扫描框

保存位置：`app/src/main/res/drawable-nodpi/receipt_illustration.png`。首页使用。

成功生成的原始提示词：

> An isolated premium 3D clay and paper illustration for a bookkeeping app: a curved cream receipt with scalloped bottom standing inside a small rounded honey amber scanner frame, a sage checkmark badge and two small matte amber coins. Warm studio lighting, tactile paper texture, elegant rounded forms, no text or digits, no interface, full object centered on a genuinely transparent background, generous padding.

### 小狐狸账本助手

保存位置：`app/src/main/res/drawable-nodpi/fox_illustration.png`。查账页使用。

原始提示词：

> Create an elegant mascot illustration asset for a personal bookkeeping app on a genuinely transparent background. One small, friendly, rounded golden-amber fox, sitting in a calm thoughtful pose, holding a tiny cream notebook and an ivory receipt, with a little muted sage green speech bubble floating beside its head. Fox has cream muzzle and chest, tiny dark brown eyes, soft curved ears, graceful curled tail; mature charming editorial clay and paper 3D illustration, refined tactile matte finish, soft studio shadows and warm directional lighting, warm cream, sand, caramel amber and muted olive palette. Full figure visible and centered, generous transparent padding, square composition, beautiful minimal sculptural design, no text, no letters, no numerals, no interface, no watermark, no background.

透明度保留，项目消费的是仓库内副本，不引用生成工具默认目录。结构性图标和图表仍使用 Compose Canvas，保持统一描边。

## 视觉验证

通过 Robolectric 原生图形渲染实际 Compose 页面，由 Android 窗口直接生成 PNG，保存在被忽略的 `.tools/ui-previews/`；包括空账本、带人工示例交易的账本、统计、查账、设置，以及小屏和横屏。预览数据均来自测试中通过界面创建的替代商户，不是真实用户账单。

布局检查与运行结果见 `HANDOFF.md`。这些渲染与操作检查不能替代小米 15 上的真实滚动、键盘、动画流畅性和图片识别验收。

## 名称与应用图标

`0.2.3-screenshot`：用户选定名称「拾账」。桌面、相册分享入口和设置页共用 `app_name` 字符串。票据与鼠尾草绿确认徽标构成图标，暖金色渐变底与应用配色一致；原生矢量分层自适应圆形和圆角遮罩，Android 13+ 提供单色主题图标。Android 12+ 系统启动画面沿用相同图标和暖纸底色，不添加等待页。包名、签名和 Room schema 保持原值。
