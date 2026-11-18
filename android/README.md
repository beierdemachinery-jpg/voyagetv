# VoyageTV for Android

和 iOS 版同一套思路的安卓播放器：导入自己的 M3U/M3U8 或 Xtream Codes 订阅，看直播。

用 Kotlin + Jetpack Compose + Media3（ExoPlayer）写的，最低 Android 7.0（API 24）。

## 能做什么

- 订阅导入：粘贴 `http://.../xxx.m3u` 地址，或直接粘贴整段 M3U 文本；也支持 Xtream Codes（服务器 + 用户名 + 密码）。
- 频道：分类筛选、搜索、收藏、最近观看。
- 播放：HLS（m3u8）；上一台 / 下一台 / 暂停。
- 节目单：订阅头里带 `x-tvg-url` / `url-tvg` 时自动拉 XMLTV，列表里显示「现在在播」，播放页显示「接下来」。
- 后台播放：MediaSessionService，切到后台/锁屏/连蓝牙车机都能继续出声，车机方向盘的上一首下一首也能用。
- 源的兼容处理：见下面「源兼容」。

## 源兼容（这块是重点）

你那个央视代理源的流地址长这样：

```
http://nas.vipgoodmedical.com:1122/ysp.php?id=cctv1
```

它有三处会让播放器直接失败：

| 现象 | 根因 | 这里的处理 |
|---|---|---|
| 被当成整段文件下载，播不了 | 地址没有 `.m3u8` 后缀，播放器按后缀猜类型 | 明确构造 `HlsMediaSource`，不靠后缀猜 |
| 解析报格式错误 | 清单里混了 `#EXT-X-REFRESH`、`#EXT-QQHLS-*`、`#EXT-SID`、`#EXT-X-ALLOW-CACHE`、`#EXT-X-PROGRAM-DATE-TIME` 这些私有/非标准标签 | `PlaylistRewriter` 按白名单重写清单 |
| 还是报格式错误 | 源站把 `#EXT-X-VERSION:3` 写成 `-X-VERSION:3`（少一个 `#`） | 补回 `#EXT`，修不回来的坏行丢掉 |

数据源层只拦「清单那一条请求」，分片直连上游，不额外耗带宽：

```
app/src/main/java/com/voyagetv/app/player/RewritingDataSource.kt
```

## 编译

CI 会自动编：push 到 `android/` 或手动跑 `Android Build` 就会产出
`VoyageTV-android-apk`（debug 签名，可直接装）。

本地编译（需要有 Android Studio / Android SDK，JDK 17）：

```bash
cd android
gradle assembleDebug     # 或者 ./gradlew，如果你自己加了 wrapper
```

产物：`android/app/build/outputs/apk/debug/app-debug.apk`

## 用全能签自签安装

1. 从 GitHub Actions 的制品里下载 `VoyageTV-android-apk`，解压出 `app-debug.apk`。
2. 把 apk 传到手机（微信/文件传输/AirDrop 都行）。
3. 全能签里「导入应用」选中这个 apk，用你自己的证书签名后安装。

Bundle ID / 包名是 `com.voyagetv.app`，重签不用改包名；换证书重签会覆盖安装
（如果提示签名冲突，先卸载旧版再装）。

## 车机：先说清楚能做到什么

- **蓝牙音频**：可以。手机在后台播、车机当媒体源接收声音，方向盘上一首/下一首可用 —— 不需要 Google 批准。
- **Android Auto 上出画面**：不行，这条路和 iOS 的 CarPlay 一样是「被平台授权卡住」的，不是代码问题：
  Android Auto 只认 Google 批准过的「车载分类」应用，个人自签的 apk 即使装到手机，
  Android Auto 也不会把它列进去；视频类在车机上本来就要求「停车才出画面」，
  而且要单独申请媒体/视频类别。
- 想在大众 ID.3 的车机屏上看视频，现实可行的只有：
  1. 车机自带的应用商店/自带播放器（不开放 sideload 的基本没戏）；
  2. 车上有 Android 盒子（把盒子当独立设备装这个 apk，用 HDMI/车机投屏通道）；
  3. iOS 那套 AirPlay 视频（需要在 iPhone 的视频 App 里能看到车机名字，说明车机支持视频接收）。

这个安卓版的价值在于：**手机上直接能播**，出门/出差/副驾用；车机当蓝牙音箱用。

## 目录

```
app/src/main/java/com/voyagetv/app/
├── MainActivity.kt              入口，通知权限
├── AppViewModel.kt              状态机：订阅、频道、播放、节目单
├── Ui.kt                        Compose 界面（导入页 / 列表页 / 播放页）
├── data/
│   ├── Models.kt                Channel / Playlist / Programme
│   ├── M3UParser.kt             M3U 解析
│   ├── PlaylistRewriter.kt      HLS 清单规范化
│   ├── EpgStore.kt              XMLTV 节目单（流式解析，只留相关频道）
│   ├── Store.kt                 SharedPreferences 持久化
│   ├── Http.kt                  取文本（带 gzip、状态码报错）
│   └── Xtream.kt                Xtream Codes 登录/频道列表
└── player/
    ├── RewritingDataSource.kt   只改写清单那一条请求
    ├── HlsSourceFactory.kt      明确按 HLS 建 MediaSource
    └── PlaybackService.kt       后台播放 + 蓝牙车机媒体控制
```
