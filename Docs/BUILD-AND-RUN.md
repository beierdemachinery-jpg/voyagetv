# 在 Mac 上编译、签名、上真车

> 当前工程是在 Windows 上生成的源码，**尚未编译验证**。以下步骤按顺序做，遇到问题按每节的排查点处理。

## 1. 前置条件

- macOS + **Xcode 15 或更新**（工程使用 Swift 5.9、iOS 16 部署目标、`CarPlay.framework`）
- 一台 iOS 16+ 的 iPhone
- Apple Developer 账号（免费账号可以真机调试，但**没有** CarPlay 权限）
- XcodeGen：`brew install xcodegen`

## 2. 生成工程

```bash
cd VoyageTV
xcodegen generate
open VoyageTV.xcodeproj
```

生成后会自动带上：

- `Config/Info.plist`（后台音频 + CarPlay 场景清单）
- `Config/VoyageTV.entitlements`
- `CarPlay.framework` / `AVFoundation.framework` / `MediaPlayer.framework` 链接
- `VoyageTVTests` 测试 Target

## 3. 签名配置

1. Xcode → 选中 `VoyageTV` Target → **Signing & Capabilities**
2. `Team` 选你的账号，`Bundle Identifier` 若冲突就改成 `com.<你的域名>.voyagetv`
3. 也可以在 `project.yml` 里填死：`DEVELOPMENT_TEAM: ABCDE12345`，然后重新 `xcodegen generate`

**没有 CarPlay 权限时请这样做**（否则签名会失败）：把 `Config/VoyageTV.entitlements` 里的
`com.apple.developer.carplay-audio` 暂时设为 `<false/>` 或整段删除，等权限批下来再加回。
`Info.plist` 里的 CarPlay 场景清单可以保留，不影响编译。

## 4. 真机运行

```bash
# 也可以直接用 Xcode Run
xcodebuild -scheme VoyageTV -destination 'platform=iOS,name=<你的 iPhone>' build
```

首次安装后到 iPhone「设置 → 通用 → VPN与设备管理」里信任开发者证书。

## 5. 跑测试

```bash
xcodebuild test -scheme VoyageTV -destination 'platform=iOS Simulator,name=iPhone 15'
```

应看到 `M3UParserTests` 11 条全部通过。若某条失败，先看断言信息里的频道名/URL 是否符合预期 —— 大概率是解析器边界情况，而不是工程配置问题。

## 6. CarPlay 上车（大众 ID.3）

### 6.1 连接方式
- **有线**：ID.3 前排 USB-C 数据口（扶手箱/中控下方），用支持数据传输的线，不要用纯充电线。
- **无线**：ID.3 多数配置支持无线 App-Connect，需 iPhone 蓝牙 + Wi-Fi 同时开启，车机首次配对时选 CarPlay。

### 6.2 首次连接会发生什么
- 权限已批：车机主屏出现 VoyageTV 图标 → 点进去是「收藏 / 快捷操作 / 分类」列表。
- 权限未批：**车机不出现图标**，这是系统行为，不是 bug。iPhone 端一切正常。

### 6.3 验证清单
1. 车机点一个频道 → 有声、车机进入 Now Playing；
2. 方向盘媒体键暂停/继续有效；
3. 手机端播放页此时只显示音频页（有「音频模式 · 不显示画面」标识）；
4. 拔线 → 手机端回到视频画面；
5. 车机上搜一个中文频道名，能命中。

### 6.4 模拟器
Xcode 的 Simulator 对 CarPlay 的支持不稳定（不同 Xcode 版本菜单不同，且不一定加载外部 App 的 CarPlay 场景）。
**结论：CarPlay 部分必须在真车或支持 CarPlay 的车机模拟台上验证**，模拟器只用来跑 iPhone 端 UI。

## 7. 常见报错

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| `Provisioning profile doesn't include com.apple.developer.carplay-audio` | 权限没批 / App ID 没勾 | 见 `CARPLAY-ENTITLEMENT.md`，或先关掉该 entitlement |
| 车机不显示图标，但 App 已装 | 无 CarPlay 权限 | 正常现象，等审批 |
| 导入链接报"下载失败 403" | 源站挡 UA 或需要 referer | 播放列表里带 `#EXTVLCOPT` 头，或换源 |
| 播放一直转圈 | 源是裸 TS / 组播 | 本期不支持，见 `MVP-PLAN.md` 风险表 |
| 中文频道名乱码 | 列表是 GBK 编码 | 已内置 GB18030 回退，仍乱码说明源本身编码异常 |
| `xcodegen` 报 YAML 错误 | 缩进被编辑器改了 | 用空格缩进，不要 Tab |
