# CarPlay Audio 权限申请要点

## 1. 这是什么

CarPlay 不是"申请就自动给"的能力。第三方 App 要在车机上出现，必须：

1. 拥有 Apple Developer Program 付费账号；
2. 向 Apple 提交 **CarPlay 类别**申请，说明你的 App 属于哪一类（本项目属于 **Audio**）；
3. 审核通过后，Apple 会在开发者后台给你的 App ID 勾上 `CarPlay Audio` capability；
4. 你的描述文件（profile）重新生成后，`com.apple.developer.carplay-audio` 才会生效。

## 2. 本项目要报的类别

**Audio（音频类）**，场景描述建议这样写（可直接改用）：

> VoyageTV is an IPTV audio player for drivers. Users import their own M3U/M3U8 playlists on iPhone; in the car,
> the app exposes a browsable channel list (favorites, categories, search) on the CarPlay screen and plays the
> audio stream of the selected channel. The app never renders video in the car: when a CarPlay session is
> connected, the UI switches to audio-only and no video surface is created, so no video is shown while driving.
> Content is user-supplied; the app does not bundle or resell any channels.

要强调的三点（Apple 最关心的）：

- 用途是**音频**，不是视频；
- 驾驶中**不显示任何视频画面**（我们的实现方式是干脆不创建视频视图）；
- 内容由用户自带，App 不预置、不转售节目。

## 3. 申请流程

1. 登录 [developer.apple.com](https://developer.apple.com) → **Certificates, Identifiers & Profiles**
2. 先创建 App ID（Bundle ID 与工程一致，如 `com.yourcompany.voyagetv`）
3. 在 App ID 的 Capabilities 里勾选 **CarPlay**（Audio 类别）；若列表里没有该项，说明需要先申请
4. 通过 **CarPlay 申请入口**（developer.apple.com 的 CarPlay 页面 / 联系开发者支持）提交类别说明与上面的场景描述
5. 通过后重新生成 Provisioning Profile，Xcode 里重新签名

常见时间预期：数天到数周，被驳回通常是因为"没有说明为什么必须在车上使用"或"看不出音频属性"。

## 4. 没批下来之前怎么办

工程已经做到"没权限也能完整使用 iPhone 端"：

- 把 `Config/VoyageTV.entitlements` 里的 `com.apple.developer.carplay-audio` 置为 `<false/>`；
- iPhone 端：导入、分类、搜索、收藏、播放、锁屏控制全部可用；
- 车上：用**蓝牙音频**把手机声音接到车机，选台仍在手机上完成（这是最省事的过渡方案）；
- 代码不需要改动，权限批下来后把该键改回 `<true/>` 并重新签名即可。

### 4.1 蓝牙方案不是"将就"，代码里已经做全了

ID.3 车机把手机当"蓝牙媒体源"时，能拿到的信息和 CarPlay 音频模板几乎一样：

- `MPNowPlayingInfoCenter` 已写入：第一行 `MPMediaItemPropertyTitle` = 频道名，
  第二行 `MPMediaItemPropertyArtist` = 频道分组，第三行固定 `VoyageTV`，并带台标 `MPMediaItemArtwork`；
  直播标记 `MPNowPlayingInfoPropertyIsLiveStream = true`；
- `MPRemoteCommandCenter` 已把 `nextTrackCommand` / `previousTrackCommand` 接到"下一台 / 上一台"，
  方向盘左右键、车机上一曲下一曲按钮都能换台；
- `pauseCommand` / `playCommand` / `togglePlayPauseCommand` 正常；
- `UIBackgroundModes` 含 `audio`，屏幕熄灭、App 退到后台都能继续出声；
- `AVAudioSession` 用 `.playback`，不会因为静音键（响铃开关）把声音掐掉。

结论：**现在就能在车上听**，只是车机屏上显示的是"蓝牙媒体"卡片，而不是一个 App 图标。

## 5. 为什么自签 / 全能签 / 企业签都拿不到 CarPlay

CarPlay 的判定依据是**代码签名里的描述文件**，不是代码里写了什么：

- `com.apple.developer.carplay-audio` 必须出现在 App ID 的 capability 与描述文件里，否则
  要么签名失败，要么装上了但 CarPlay 永远不显示；
- 免费开发者账号（Personal Team）无法给 App ID 添加 CarPlay capability，也就无法申请；
- 全能签 / 第三方签用的是别人的证书与描述文件，里面既没有你的 App ID，更没有已获批的 CarPlay capability；
- 企业账号同样要逐个 App 向 Apple 申请，且不允许公开分发。

也就是说：**想在车机上出现 App 图标，只有"付费开发者账号 + 申请通过 + 用该 App ID 的描述文件签名"这一条路。**

## 6. 与"驾驶中视频"的关系（红线）

- Apple 不允许第三方 App 在车辆行驶中播放视频，本项目也不做任何规避；
- 我们的实现是**结构性保证**：CarPlay 连接期间不创建视频渲染视图，而不是"检测到车速再遮罩"；
- 因此不存在一个开关能让行驶中出画面，也就不会被审核质疑"是否可被绕过"。
