# VoyageTV MVP 计划

## 1. 产品定位

在大众 ID.3 上替代"手机上翻频道、车机上没法用"的现状：
**手机端负责导入与管理，车机端负责选台与音频收听。**

不追求做全能播放器，M0–M2 只解决一条路径：
「我有一份 M3U 列表 → 导入 → 开车时在车机上点一下就听」。

## 2. 已确认的产品边界

### 做
- M3U / M3U8 频道表导入（远端链接、粘贴文本、本地文件）
- 分类（`group-title` / `#EXTGRP`）、搜索、收藏
- HLS 播放、锁屏控制、方向盘媒体键
- CarPlay：收藏优先、搜索、按分类浏览、Now Playing
- 连接 CarPlay 后强制音频模式

### 不做（本期）
- 绕过车辆行驶限制、驾驶中显示视频 —— **永不做**
- 裸 MPEG-TS over HTTP 直连、UDP/RTP 组播
- DRM、EPG 节目单、定时录制、投屏
- 任何第三方客户端代码/资源的复用

## 3. 技术架构

```
SwiftUI App (@main)
├─ AppContainer（进程级单例）
│  ├─ PlaylistStore   ── Application Support/playlists.json
│  ├─ FavoritesStore  ── UserDefaults（只存 channel id）
│  ├─ RecentsStore    ── UserDefaults（最近 20 个频道）
│  ├─ EPGStore        ── XMLTV + Application Support/epg.json
│  ├─ PlaybackEngine  ── AVPlayer + AVAudioSession + MPRemoteCommandCenter
│  └─ CarPlaySession  ── 连接状态
│
├─ 导入链路：PlaylistImporter → M3UParser / XtreamClient → PlaylistStore.upsert
├─ 节目单链路：XMLTVParser（后台线程）→ EPGStore.now/next
├─ iPhone UI：ChannelListView / FavoritesView / PlayerView / SettingsView / AddPlaylistView
└─ CarPlay：CarPlaySceneDelegate → CarPlayCoordinator
             CPListTemplate（最近观看 / 收藏 / 快捷操作 / 分类）
             CPSearchTemplate
             CPNowPlayingTemplate
```

关键设计取舍：

1. **频道 id 由 URL + 名称哈希派生**：重复导入、刷新列表后收藏与最近观看不丢。
2. **收藏 / 最近观看只存 id**：不随列表体积膨胀，刷新后自动重新关联。
3. **画面闸门放在引擎层而不是视图层**：`isVideoAllowed == false` 时视频视图不实例化，避免出现"忘记隐藏"的路径。
4. **CarPlay 与 iPhone 共享同一个 PlaybackEngine**：车机上选台，手机端同步显示正在播放，不会出现两套状态。
5. **节目单只在后台线程解析、只保留未来 1 小时之后的数据**：10MB 级 XMLTV 也不会把内存顶爆。
6. **订阅凭据只存在本机**：Xtream 用户名/密码写在 playlists.json（App 沙盒内），不上传、不写日志。

## 4. 里程碑与验收标准

### M0 工程可编译（在 Mac 上）
- [ ] `xcodegen generate` 成功，Scheme 可直接 Run
- [ ] iPhone 16 模拟器能启动，三个 Tab 正常显示
- [ ] `Cmd+U` 跑通全部单测（M3U 11 条 + XMLTV/匹配 10 条）

### M1 订阅与浏览
- [ ] 贴一份 300+ 频道的 M3U 链接，10 秒内导入完成并落盘
- [ ] Xtream 服务器登录成功，分类与频道数与服务端一致
- [ ] 杀掉进程重进，订阅、收藏、最近观看都在
- [ ] 分类筛选 + 搜索（中英混排、大小写、全半角）均能命中
- [ ] 同一订阅再次刷新是"更新"而不是出现两份
- [ ] 左滑刷新后，收藏的频道仍然被标记为收藏


### M2 播放
- [ ] HLS 频道点开 2 秒内出画面/出声音
- [ ] 锁屏能看到频道名与分类，遥控键可暂停/继续
- [ ] 弱网时有缓冲态与超时错误提示，不是白屏
- [ ] 带 `#EXTVLCOPT` 防盗链头的源能正常播放

### M3 CarPlay（需权限）
- [ ] iPhone 连上 ID.3 后，车机出现 VoyageTV 图标
- [ ] 收藏频道排在第一位，列表可滚动、可搜索
- [ ] 选台后车机进入 Now Playing，方向盘可暂停/继续
- [ ] **连接期间手机端播放页只显示音频页，无任何视频画面**
- [ ] 拔线后 iPhone 端恢复画面播放能力

### M4 稳定性
- [ ] 断网重连后单台可自动恢复（重试 3 次）
- [ ] 1000 频道列表滚动不掉帧
- [ ] 连续切台 30 次无内存增长异常

## 5. 风险与对策

| 风险 | 影响 | 对策 |
| --- | --- | --- |
| CarPlay Audio 权限未获批 | 车机不显示入口 | 见 `CARPLAY-ENTITLEMENT.md`；iPhone 端功能不受影响，可先用蓝牙音频 + 手机选台过渡 |
| 节目源是裸 TS / 组播 | 部分频道播不了 | 明确报错并提示"该源需要网关"；M5 增加 TS 解封装或 XTeVe 类网关对接 |
| ATS 例外被审核质疑 | 上架受阻 | 改为域名白名单 + 审核说明；企业内部分发可保留 |
| 明文 HTTP 源不稳定 | 播放失败率高 | 引擎侧 3 次重试 + 明确错误文案 |
| 无 Xcode 环境验证 | 首次编译可能有签名/工程配置问题 | `BUILD-AND-RUN.md` 已给出逐步排查点 |

## 6. 后续（M5+，未排期）

1. **流适配层**：`StreamAdapter` 协议，为 MPEG-TS / UDP 组播接入预留接口（组播走局域网网关，不在手机上直接抓包）。
2. ~~EPG：解析 XMLTV，车机端显示"正在播 / 下一档"~~ → **已完成**（`XMLTVParser` + `EPGStore`，iPhone 与 CarPlay 同时生效）。
3. 播放列表定时刷新（每天一次，失败静默重试）。当前是手动刷新 + 节目单 12 小时自动刷新。
4. 多车记忆：记录 ID.3 上最近播放，下次上车直接续播。当前已有"最近观看"，还不能按车区分。
5. 台标本地缓存、离线导入 zip。

## 7. 与 APTV 的能力对照（截至当前版本）

已对齐：多订阅管理、Xtream Codes、分类、搜索、收藏、最近观看、XMLTV 节目单、上一台/下一台、锁屏与车机遥控。

尚未对齐（需评估是否值得做）：iCloud 多端同步、播放器手势（快进/亮度/音量）、自定义台标缓存策略、少儿/隐藏分类、TS 直连、直播回看（依赖源站提供 archive 能力）。

