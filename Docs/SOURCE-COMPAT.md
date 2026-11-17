# 源站兼容性实测记录

本文件记录真实源站的抓取结果与 App 侧的适配策略，避免以后重复踩坑。

## 1. 实测对象

| 项目 | 值 |
| --- | --- |
| 订阅地址 | `http://nas.vipgoodmedical.com:1122/ysp.m3u` |
| 域名解析 | IPv4 `112.87.199.116` / IPv6 `240e:3a5:5403:d671::db9` |
| 抓取时间 | 2026-11-17 |
| 响应 | HTTP 200，11248 字节，130 行，`Content-Type: application/octet-stream` |
| 频道数 | 64 条 `#EXTINF` |
| 分类 | 只有 1 类：`未分组`（`group-title="未分组"`） |
| 台标 | `https://logo.catvod.com/CCTV1.png` 这类 catvod 图床 |
| 节目单 | 头部 `x-tvg-url="https://epg.catvod.com/epg.xml"` |
| 流地址形态 | `http://nas.vipgoodmedical.com:1122/ysp.php?id=cctv1`（**无 .m3u8 / .ts 后缀**） |

样例：

```
#EXTM3U x-tvg-url="https://epg.catvod.com/epg.xml"
#EXTINF:-1 tvg-name="CCTV1" tvg-logo="https://logo.catvod.com/CCTV1.png" group-title="未分组",CCTV1
http://nas.vipgoodmedical.com:1122/ysp.php?id=cctv1
```

## 2. 播放接口的真实形态（关键）

`ysp.php?id=cctv1` 返回：

- HTTP 200
- `Content-Type: text/html; charset=UTF-8`（**不是** `application/vnd.apple.mpegurl`）
- 没有 `Content-Length`
- 正文 1815 字节，实际是一份 HLS 媒体清单：

```
#EXTM3U
#EXT-X-REFRESH:21
#EXT-X-VERSION:3
#EXT-X-TARGETDURATION:7
#EXT-X-PROGRAM-DATE-TIME:2026-11-17T12:46:12+08:00
#EXTINF:5.000,
http://hlsliveali-cdn.ysp.cctv.cn/<长串 hash>/2024078202-1786664719.ts
```

结论：**是真 HLS，但 URL 无后缀 + MIME 错误**。AVPlayer 仅凭 URL 后缀和 Content-Type 判断类型，
这两种情况都会直接判定"不可播放"，与网络无关。

## 3. App 侧适配

### 3.1 HLS 识别纠正（`Services/HLSResourceLoader.swift`）

触发条件：`HLSResourceLoader.needsLoader(for:)` 为真 —— http(s) 且路径后缀不是 `.m3u8`。

做法：

1. 给 asset 套自定义 scheme `voyagetv-hls://`，让 AVFoundation 把所有请求交给
   `AVAssetResourceLoaderDelegate`；
2. 我们自己用 `URLSession`（带 `User-Agent` 与频道级自定义头）去下载；
3. 判断正文是否 `#EXTM3U` / `#EXT-X-`：
   - 是 → `contentInformationRequest.contentType = "application/vnd.apple.mpegurl"`，回数据；
   - 否（分片/其它）→ `video/mp2t`，回数据；
4. 清单里的相对地址按真实地址补成绝对地址，后续分片由播放器直接访问原站。

注意：`AVAssetResourceLoader` 对 delegate 是弱引用，**loader 必须由调用方强持有**
（`PlaybackEngine.hlsLoader`），换台 / `stop()` 时调用 `loader.stop()` 释放。

### 3.2 节目单自动接入

`M3UParser` 解析 `#EXTM3U` 行的 `x-tvg-url` / `url-tvg`（多个逗号分隔时取第一个），
存到 `Playlist.epgURL`；`EPGStore.adoptSourceFromPlaylists(_:)` 在**用户尚未手动设置节目单**时
自动把它设为节目单源并拉取。导入订阅后即刻生效（`AddPlaylistView`），冷启动也会补一次。

### 3.3 错误信息

`PlaylistImporter.describe(_:)` 把 `URLError` 翻成带错误码的中文提示：

| 错误码 | 含义 | 用户该做什么 |
| --- | --- | --- |
| -1009 | 设备没联网 | 检查 Wi-Fi / 蜂窝 |
| -1004 | 对方拒绝连接 | 端口未开放 / 没做端口映射 / 服务没监听 0.0.0.0 |
| -1001 | 超时 | 服务器无响应或被拦截 |
| -9806/-1202 等证书类 | 证书校验失败 | 改 http 或换正规证书 |
| -1022 | 系统拦明文 HTTP | 配置问题，反馈开发者 |

## 4. 端口可达性是间歇的（排查注意）

同一台机器、同一天多次探测 `112.87.199.116`：

| 时刻 | 1122 | 5000 (DSM) | 8096 (Emby) | 80/443/8080/8000/9000/32400 |
| --- | --- | --- | --- | --- |
| 早期 | refused | - | - | - |
| 复测 | OPEN | OPEN | OPEN | 全部 closed |

说明 NAS / 端口映射本身会掉。App 报"无法连接服务器"时，先看错误码：
- 若带 **-1004**：几乎必然是服务端/端口问题，浏览器打开同地址也会失败；
- 若带 **-1001**：网络或运营商侧拦截。

## 5. 明确不支持的源类型

- 裸 MPEG-TS 广播流（`http://.../x.ts` 直接返回 TS，不是 HLS 清单）：AVPlayer 不支持，需要 VLCKit/FFmpeg；
- 需要 DRM / 播放密钥的源；
- 需要 cookie 登录态且清单里未声明 `#EXTVLCOPT:http-cookie` 的源（可在 App 内手工补 header 的功能尚未实现）。

## 6. `ysp.php?id=cctvN` 这类源的三道坎（已解决，2026-11-17 实测打通）

这类 PHP 代理不返回 `.m3u8` 地址，Content-Type 是 `text/html`，清单里还混着私有标签。实测逐个踩到三个不同的错误码，按顺序修完才播得出来：

| 现象 | 错误码 | 根因 | 处理 |
| --- | --- | --- | --- |
| 直连清单 | `CoreMediaErrorDomain -12642` | 清单里 `#EXT-X-REFRESH` / `#EXT-QQHLS-*` / `#EXT-SID` / `#EXT-X-ALLOW-CACHE` / `#EXT-X-PROGRAM-DATE-TIME` 等非标准标签 | 自定义资源加载器重写清单，只留标准标签 |
| 加载器 + 原始清单 | 仍 `-12642` | 源站把 `#EXT-X-VERSION:3` 写成 `-X-VERSION:3`（少一个 `#`），被当相对地址拼进清单 | 补回 `#EXT`，并丢弃带冒号的非 URL 坏行 |
| 加载器（修好标签后） | `AVFoundationErrorDomain -11828`「媒体格式不支持」 | 代理地址没有 `.m3u8` 后缀，AVPlayer 先用 `Range: bytes=0-1` 嗅探类型，嗅探路径下判死 | 代理地址统一成 `voyagetv-hls://host/playlist.m3u8?src=<原始地址>` |

验证方式：`Tools/AVProbe` 在 macOS runner 上用真实 AVFoundation 跑同一份代码，判据是 `AVPlayerItem.status == .readyToPlay` 且连续播放超过 3 秒。最新一次（AVProbe 报告 #9）用户源那一组 `asset.isPlayable=true`、`played` 到 3.0009 秒，`RESULT: PLAYING`。

调试要诀：`HLSResourceLoader.onDebug` 打开后，请求日志里的 `range=` 很关键——`range=bytes=0-1` 出现就说明 AVPlayer 在嗅探类型，此时必然是清单地址形态不对，而不是源站内容有问题。
