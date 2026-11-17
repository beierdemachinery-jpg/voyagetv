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

## 5.1 把 App 名字 / 图标 / Bundle ID 改成和 APTV 一样，能骗过系统吗

不能。系统的判定链是固定的，名字只影响显示文案，不参与任何授权判断：

1. 安装时先校验签名：证书是否有效、描述文件是否覆盖这个 **App ID（Bundle ID）**；
2. 描述文件里挂着绑定在这个 App ID 上的 capability 清单，CarPlay 只认其中的 `com.apple.developer.carplay-audio`；
3. 车机端只加载"已安装且描述文件里带 CarPlay capability"的 App，并按 App ID 去匹配它声明的场景配置。

所以：

- 改名、换图标、换显示名：车机侧毫无变化，仍然不出现；
- 把 Bundle ID 直接写成别人的（例如 APTV 的 `identifier`）：签名里的 App ID 对不上，**安装阶段就会失败**；
  就算在越狱环境强行跳过签名校验，CarPlay 仍按 App ID + capability 判定，依旧不出现；
- 冒用他人 App 的名称或图标还额外构成商标/仿冒问题，App Store 审核会直接以 5.2.1（仿冒）驳回。

结论：**改名不是"绕过授权"的路径，它连第一步（签名匹配）都过不了。** 想要图标只有 §3 的审批路，想要车上能听只有 §4.1 的蓝牙路。

## 6. 与"驾驶中视频"的关系（红线）

- Apple 不允许第三方 App 在车辆行驶中播放视频，本项目也不做任何规避；
- 我们的实现是**结构性保证**：CarPlay 连接期间不创建视频渲染视图，而不是"检测到车速再遮罩"；
- 因此不存在一个开关能让行驶中出画面，也就不会被审核质疑"是否可被绕过"。

## 7. 车上"看"视频：iOS 27 起的官方通道，但门槛在车机上

Apple 在 WWDC26 增加了 CarPlay 视频 App（`com.apple.developer.carplay-video`），规则要点：

1. **只在停车时出画面**。车辆行驶中，车机会主动告知"视频不可用"，App 必须降级为纯音频继续播
   （官方原话是：这时候正好可以继续听播客或球赛解说）。所以"行驶中看视频"在官方体系里根本不存在。
2. **必须由车厂启用**。车机要上报支持 `videoPlayback`（即"video in car"能力，底层走 AirPlay 视频）。
   App 侧读 `CPSessionConfiguration.supportsVideoPlayback` 就知道这台车支不支持；
   不支持时，带视频权限的 App 连 CarPlay 主屏都不会出现。
3. **要两个权限**。内容既能听又能看的 App，应同时声明 `carplay-audio` 与 `carplay-video`：
   前者保证任何车上都能出现，后者在有能力的车上解锁停车视频。
4. 视频 App 还需支持 **AirPlay 视频串流** + 用 CarPlay 框架做浏览界面（列表、缩略图、MiniPlayer 等）。

### 7.1 三十秒判断 ID.3 支不支持（不用改代码）

在 iPhone 上播放任意一段视频（相册视频或 Apple TV），点 AirPlay / 隔空播放：

- 列表里出现**你的车机名字**，说明这台车支持 video in car，值得去申请权限 + 做视频 UI；
- 列表里只有音箱/电视、没有车机，说明车端没开这项能力——此时无论审批与否、无论怎么改 App，
  车机上都不会出画面，只能手机看。

### 7.2 结论对本项目的含义

- 想要"停车时在车机屏看频道"，三条必须同时满足：付费账号 + `carplay-video` 审批 +
  ID.3 上报 `supportsVideoPlayback`。少一条都不成。
- 行驶中出视频，官方通道本身就不提供，本项目也不会做任何绕过。
- 停车时想立刻看，最省事的是手机横屏全屏（App 现有播放页已支持）。
