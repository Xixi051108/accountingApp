# 采集验证 APK

本阶段仅观察微信支付候选，不生成正式账单，不调用 DeepSeek。通知、页面识别及 OCR 是否可用，以小米 15 的实际版本和场景为准。

## 构建

Windows 需要 JDK 17、Android SDK 36 和 Build Tools 36.0.0。也可运行 `python scripts/bootstrap_android.py` 在忽略的 `.tools/` 安装私有工具链，再使用其中的 sdkmanager 安装 `platform-tools`、`platforms;android-36`、`build-tools;36.0.0`。SDK 许可需阅读并接受。

```powershell
$env:JAVA_HOME = "$PWD/.tools/jdk"
$env:ANDROID_HOME = "$PWD/.tools/android-sdk"
& "$env:ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager.bat" --sdk_root=$env:ANDROID_HOME --licenses
& "$env:ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager.bat" --sdk_root=$env:ANDROID_HOME 'platform-tools' 'platforms;android-36' 'build-tools;36.0.0'
./scripts/build.ps1
```

只运行单元测试用 `./scripts/build.ps1 -TestsOnly`；依赖已经下载后可加 `-Offline`。不要同时在同一个 checkout 执行两个构建。

输出：`app/build/outputs/apk/debug/app-debug.apk`。这是个人采集验证用 debug APK，不是完整记账版。调试签名保存在 `.tools/debug.keystore`；后续更新必须保留相同签名，卸载会删除本机数据。

当前版本为 0.1.1-capture（versionCode 2）；另提供同内容的 `wechat-capture-0.1.1.apk`，便于文件传输时区分旧版。使用系统安装器覆盖更新即可，保留原调试签名。

## 安装和权限

1. 将 APK 传到手机，使用系统安装器安装。可以通过 USB 安装：开启开发者选项和 USB 调试、确认电脑授权后，运行 `.tools/android-sdk/platform-tools/adb.exe install -r app/build/outputs/apk/debug/app-debug.apk`。
2. 打开 App 的「权限」页，进入系统通知监听设置，授权「微信支付通知采集」。监听只接收之后的新通知，不导入已有通知。
3. 需要测试页面文字时，阅读 App 的权限说明，进入无障碍设置开启「微信支付页面采集」。某些 Android/HyperOS 版本对侧载 App 有「受限设置」限制；在系统 App 信息中按系统指引允许受限设置后再授权。
4. OCR 默认关闭。若想测试，在已开启页面采集后单独开启本地 OCR。只在明确支付结果页缺少可读金额时尝试，系统禁止截图时不会绕过限制。
5. 按实际 HyperOS 版本在手机设置检查自启动及省电策略，并验证后台行为。不凭权限已授权就判断后台采集成功。

授权可随时在系统设置关闭。候选仅本机保存，最多 100 条，可清空；App 没有 INTERNET 权限，未启用系统自动备份。原始通知文字和截图不持久化；只保存派生候选字段。脱敏报告不导出交易金额、商户、交易号、截图或设备标识。

## 按场景实测

在「实测」页记录 HyperOS 版本，Android 与微信版本由 App 显示。逐项实际运行后再选择可用、受限或失败；初始都为未验证。字段勾选仅表示实际取得的字段，不代表自动入账成功。保存脱敏 Markdown 报告供后续调整规则；其他限制需要再提供不含真实金额、商户及个人信息的说明。

0.1.1 支持英文付款成功及等待对方确认收款结果文字；后者保留待核对，不等同于对方已收款。权限已开启但仍无候选时，查看「候选」或「权限」页的页面诊断提示。脱敏报告增加最近微信窗口的代码类名，仅用于适配，不含窗口标题、页面原文或联系人。OCR 按用户选择保持关闭；新版本不会自动开启它。

| 场景 | 观察方法 |
| --- | --- |
| 消费、退款、转账、收款、红包 | 分别观察新通知及实时完成页。记录金额、方向、完成状态、交易号哪些可取得。不要为测试额外进行不必要的真实转账。 |
| 待支付、请收款、红包待领取 | 不应标记完成。缺失金额保留为空。 |
| 普通聊天、非微信通知 | 不应生成候选。 |
| 微信历史账单详情 | 不应生成新的候选。 |
| 连续两笔真实同金额交易 | 可靠事件不同应保留两条；候选不是正式账单。 |
| OCR | 明确支付页面缺少可读金额才尝试。记录识别、失败或截图禁止；文字可读时不尝试 OCR。 |
| 锁屏、后台、重启 | 分别观察权限是否保持及新事件能否到达，重启后检查 App 中原有候选是否保留。 |

页面识别采用保守规则：微信钱包、转账、红包活动中的明确结果文字，排除历史详情及输入页。页面类名或文字变化可能导致跳过，需报告为受限。不能辨认支付页面用途时不截图。Android 14+ 使用窗口截图；Android 12/13 只能使用显示屏截图并立即裁剪支付窗口，建议关闭浮窗后测试。截图不写入文件，处理后释放内存。

采集验证完成前，GitHub [任务 #2](https://github.com/Xixi051108/accountingApp/issues/2) 保持打开。未验证的交易类型不能标记为自动采集成功，也不推进完整账本实现。
