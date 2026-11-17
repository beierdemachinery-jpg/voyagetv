# VoyageTV

一个独立实现的 iOS IPTV 播放器：支持 **M3U / M3U8 订阅** 与 **Xtream Codes** 登录，带 **XMLTV 节目单**、分类浏览、搜索、收藏、最近观看、上下切台，并在 CarPlay 车机上以**音频模式**收听 —— 面向大众 ID.3 这类支持 CarPlay 的车机使用。

> 本项目是独立实现，**没有**使用、复制或反编译任何第三方 IPTV 客户端（包括 APTV）的代码、资源或接口。节目内容全部来自你自己添加的订阅。

## 构建状态

| 项 | 状态 |
| --- | --- |
| 云端编译（GitHub Actions · macos-latest） | ✅ 通过（2026-11-17） |
| 单测 21 条（M3U 11 + XMLTV/匹配 10） | ✅ 在 iPhone 模拟器上执行 |
| 归档 + 未签名 IPA | ✅ 产出 `VoyageTV-unsigned-ipa`（约 259 KB） |
| 真机 / CarPlay | ⏳ 待装机验证、待 Apple 审批 CarPlay Audio |

见 `Docs/BUILD-VERIFIED.md`。仓库地址：`https://github.com/beierdemachinery-jpg/voyagetv`

---

## 功能对照（与 APTV 那类客户端的常见能力对齐）

| 能力 | 状态 |
| --- | --- |
| M3U / M3U8 订阅链接导入 | ✅ |
| Xtream Codes（用户名 / 密码）登录导入 | ✅ |
| 粘贴文本 / 本地文件导入 | ✅ |
| 订阅管理：列表、来源标签、更新时间、左滑刷新、删除 | ✅ |
| 分类浏览（`group-title` / `#EXTGRP` / Xtream 分类） | ✅ |
| 搜索（忽略大小写 / 全半角） | ✅ |
| 收藏（跨订阅刷新不丢） | ✅ |
| 最近观看 | ✅ |
| XMLTV 节目单：正在播 / 接下来、12 小时自动更新 | ✅ |
| 上一台 / 下一台（含方向盘媒体键） | ✅ |
| HLS 播放、后台音频、锁屏封面与遥控 | ✅ |
| CarPlay：最近观看 / 收藏 / 搜索 / 分类 / Now Playing | ✅（需 Apple 审批权限） |
| 驾驶中仅音频的安全闸门 | ✅（结构性保证，无可绕过路径） |
| 编译 / 真机 / 真车验证 | ⚠️ 本机是 Windows，无 Xcode，尚未编译验证（见 `Docs/BUILD-AND-RUN.md`） |
| MPEG-TS over HTTP、UDP 组播直连 | ⏳ 未实现（建议走局域网网关，见 `Docs/MVP-PLAN.md`） |
| DRM / 定时录制 / 投屏 | ⏳ 不在本期 |

---

## 目录结构

```
VoyageTV/
├─ project.yml                 # XcodeGen 工程定义（在 Mac 上生成 .xcodeproj）
├─ .github/workflows/ios.yml   # 云端 macOS 编译 + 出未签名 IPA（无 Mac 用这个）
├─ Scripts/build-ipa.sh        # 本机 macOS 一键编译/测试/出 IPA
├─ Config/
│  ├─ Info.plist               # CarPlay 场景清单、后台音频、ATS 例外
│  └─ VoyageTV.entitlements    # com.apple.developer.carplay-audio
├─ VoyageTV/
│  ├─ App/                     # 入口、依赖容器（含切台序列与最近观看钩子）
│  ├─ Models/                  # Channel / Playlist / EPGProgram
│  ├─ Services/                # M3U 解析、Xtream 客户端、XMLTV 解析、
│  │                           # 资料库、收藏、最近观看、节目单、播放引擎
│  ├─ Views/                   # 频道 / 收藏 / 设置 / 播放页 / 添加订阅
│  ├─ CarPlay/                 # CarPlay Scene + 模板协调器
│  └─ Utilities/               # 搜索归一化
├─ VoyageTVTests/              # M3U 11 条 + XMLTV/频道匹配 10 条，共 21 条
└─ Docs/
   ├─ MVP-PLAN.md              # 范围、架构、里程碑、验收标准、风险
   ├─ BUILD-WITHOUT-MAC.md     # 没 Mac 怎么编译、装机、上 CarPlay（先看这个）
   ├─ SIGN-AND-INSTALL.md      # 用全能签 / Sideloadly 自签安装
   ├─ BUILD-AND-RUN.md         # 有 Mac 时的编译、签名、上真车
   └─ CARPLAY-ENTITLEMENT.md   # CarPlay Audio 权限申请要点
```

---

## 三分钟上手

### 有 Mac / Xcode 15+

```bash
brew install xcodegen
cd VoyageTV
xcodegen generate            # 生成 VoyageTV.xcodeproj
open VoyageTV.xcodeproj
```

然后在 Xcode 里：

1. `Signing & Capabilities` 选择你的 Team；
2. 把 `project.yml` 里的 `DEVELOPMENT_TEAM` 改成你的 Team ID（或直接在 Xcode 里改）；
3. 选一台 iOS 16+ 真机运行。

### 只有 Windows（没有 Mac）

工程已内置 GitHub Actions 流水线，**编译在云端完成**：

```bash
# 把 VoyageTV 目录推到 GitHub 仓库（仓库根目录 = 该目录）
# 然后 GitHub → Actions → iOS Build → Run workflow
# 完成后在 Artifacts 下载 VoyageTV-unsigned-ipa
```

下载的未签名 IPA 用 **全能签**（或 Sideloadly）以你的证书 / Apple ID 签名后装进 iPhone。
完整步骤、证书与 CarPlay entitlement 的关系、常见掉签问题 —— 见 `Docs/SIGN-AND-INSTALL.md` 与 `Docs/BUILD-WITHOUT-MAC.md`。


### 首次使用顺序

1. 「频道」页右上角 `+` → 选择 **M3U 链接** 或 **Xtream**，填地址（Xtream 再填用户名密码）→ 导入；
2. 「设置 → 订阅」确认列表，左滑可刷新；
3. 想要节目单：把 XMLTV 地址填进「设置 → 节目单」→ 点启用；
4. 点频道即播；播放页左右键切台。

---

## 安全边界（重要）

本 App 对"行驶中只播音频"的实现方式是**只做减法**，不做任何车辆状态推断，也不尝试绕过系统限制：

- 只要 CarPlay 会话连接，`PlaybackEngine.isVideoAllowed` 恒为 `false`；
- 此时视频承载视图（`VideoLayerView`）**根本不会被创建**，AVPlayer 只输出音频；
- 用户还可以在设置里主动开启"始终仅音频播放"，任何场景都不渲染画面；
- 代码中不存在"根据车速/位置决定是否显示视频"的分支，因此不存在可被绕过的开关。

---

## 已知限制

- 仅支持 AVFoundation 原生可播的流（HLS `.m3u8`、可直接播放的 HTTP 视频）。裸 MPEG-TS over HTTP、UDP/RTP 组播需要额外的解封装或局域网网关，不在本版范围内。
- 无 DRM、无录像；节目单仅解析 XMLTV，不含图片与评分等扩展字段。
- 为兼容大量明文 HTTP 的 IPTV 源，`Info.plist` 打开了 ATS 例外；若要上架 App Store，建议改为按域名白名单并说明理由。
- Xtream 播放地址按 `/live/{user}/{pass}/{id}.m3u8` 生成；若你的服务端只支持 `.ts` 输出，需要在 `XtreamClient.streamURL` 里改成 `.ts`（并确认引擎能播）。

---

## 许可与归属

App 名称、图标、代码均为本项目自建。订阅内容版权归各节目源提供方，使用前请自行确认你拥有相应观看权限。
