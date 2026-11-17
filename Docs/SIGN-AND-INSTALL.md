# 用全能签自签安装 VoyageTV

> 前提：手上有一个**未签名 IPA**（由 GitHub Actions 云端编译产出，见 `BUILD-WITHOUT-MAC.md` 路线 A）。
> 全能签负责签名与安装，本工程负责把包做出来。

---

## 1. 为什么这个 IPA 是"未签名"的

`xcodebuild archive` 时带了 `CODE_SIGNING_ALLOWED=NO`，所以：

- 包结构是标准的 `Payload/VoyageTV.app`，能被任何签名工具识别；
- **里面不含 Apple 的描述文件、不含 CodeSignature**，签名这一步完全交给全能签；
- 也**不含 entitlement**（这一点很关键，见第 4 节）。

## 2. 签名前先确认包是好的

拿到 `VoyageTV-unsigned.ipa` 后，可以在 Windows 上快速验证结构：

```powershell
# 把 ipa 当 zip 解开看一眼
Expand-Archive -LiteralPath .\VoyageTV-unsigned.ipa -DestinationPath .\ipa-check -Force
Get-ChildItem .\ipa-check\Payload
# 期望看到：VoyageTV.app
```

正确的包一定满足：

- 顶层有 `Payload/` 目录（大小写敏感，不能是 `payload`）；
- `Payload/VoyageTV.app/Info.plist` 存在；
- `Payload/VoyageTV.app/VoyageTV` 是可执行文件。

如果全能签提示"不是有效的 IPA"，九成是 `Payload` 层级不对或被重新压缩破坏了结构。

## 3. 全能签签名步骤（通用流程）

1. 全能签里准备好证书：`.p12` + 描述文件 `.mobileprovision`（或全能签内置的共享证书）。
2. 导入 `VoyageTV-unsigned.ipa`。
3. 选择证书 → 开始签名。
4. 安装。首次打开若提示"未受信任的开发者"，到
   「设置 → 通用 → VPN与设备管理」里信任对应证书。
5. 打开 App，按 `README` 的顺序添加订阅。

## 4. 关键限制：CarPlay 不会随自签一起出现

这不是本工程的 bug，是 Apple 的机制：

- 车机上要出现 App 图标，App 必须带 **`com.apple.developer.carplay-audio`** 这个 entitlement；
- entitlement 只能来自**包含该能力的描述文件**；
- 而该能力**只对通过 Apple 审批的 App ID 发放**；
- 全能签用的共享证书 / 个人证书里没有这个能力，所以签出来的包**一定没有 CarPlay 入口**。

结论：

| 场景 | 免费/共享证书自签 | 付费账号 + CarPlay 审批后的描述文件 |
| --- | --- | --- |
| iPhone 上使用（导入/播放/收藏/节目单） | ✅ | ✅ |
| 车机出现图标、车机选台 | ❌ | ✅ |
| 有效期 | 7 天 / 视证书 | 1 年 |

所以建议的推进节奏是：

1. **先自签跑通 iPhone 端**（这一版就能做，验证播放、Xtream、EPG 是否正常）；
2. 同时去申请 CarPlay Audio（材料见 `CARPLAY-ENTITLEMENT.md`）；
3. 批下来后，用带该能力的描述文件重新签一次（届时用云端 Mac 打一次带签名的包，或让签名工具注入 profile）。

## 5. 自签常见问题

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 安装到一半失败 | 证书过期 / 设备数超限 | 换证书或换全能签内置证书 |
| 打开闪退 | 描述文件与 Bundle ID 不匹配 | 签名时把 Bundle ID 改成 profile 里的 App ID（`com.voyagetv.app` 或改成你自己的） |
| 提示"App 不可用" / 掉签 | 共享企业证书被吊销 | 换个人证书重签；这是共享证书的常态 |
| 播放没声音 | 音频会话被系统打断 | 先播一次再切后台；确认没开静音开关 |
| 导入订阅失败 | 源站挡 UA / 需要 referer | 换源，或在列表里带 `#EXTVLCOPT` 头 |
| CarPlay 里找不到 App | 无 CarPlay entitlement | 正常，见第 4 节 |

## 6. 签名时 Bundle ID 的注意点

工程默认 `com.voyagetv.app`（见 `project.yml`）。如果全能签用的描述文件绑定了别的 App ID，签名会失败。两个选择：

- **改工程**：把 `project.yml` 里的 `PRODUCT_BUNDLE_IDENTIFIER` 改成你 profile 里的 ID，重新跑一次云编译；
- **改签名**：在签名工具里把 Bundle ID 覆盖成 profile 里的 ID（多数工具支持）。

自用验证推荐后者，省一次云编译。
