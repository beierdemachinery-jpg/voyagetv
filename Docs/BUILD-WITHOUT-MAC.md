# 没有 Mac 怎么编译、装机、上 CarPlay

> 现状：你这边只有 Windows。Swift/SwiftUI 必须在 **macOS + Xcode** 上编译，这是 Apple 的硬限制，绕不过去。
> 但"没有 Mac"不等于"做不了"，有三条现实路线，按推荐顺序排。

---

## 路线 A（推荐）：用 GitHub 的 macOS 运行器编译，Windows 上签名装机

工程里已经配好了 `.github/workflows/ios.yml`，全程不需要你的电脑是 Mac。

### A1. 把代码推到 GitHub

1. 注册 / 登录 GitHub，新建一个仓库（私有即可，Actions 私有仓库每月有免费额度；公开仓库完全免费）。
2. 用 GitHub Desktop（Windows 版）把 `VoyageTV` 这个目录作为仓库上传。
   注意：仓库根目录应当就是 `VoyageTV` 目录本身，这样 `.github/workflows/ios.yml` 才会被识别。

### A2. 触发编译

- 进仓库 → **Actions** 标签 → 左侧 `iOS Build` → 右侧 **Run workflow**。
- 约 10–25 分钟，流水线会依次做：
  1. 装 XcodeGen → 生成 `VoyageTV.xcodeproj`
  2. 跑 21 条单测（失败不阻断）
  3. 模拟器编译（**这一步就是你的"编译器"，报错看这里**）
  4. 归档并打包 `VoyageTV-unsigned.ipa`

### A3. 下载产物

- 构建完成后，在该次运行页面底部 **Artifacts** → `VoyageTV-unsigned-ipa` 下载。

### A4. 在 Windows 上签名并装到 iPhone

两条路，任选其一：

- **全能签（推荐，你已在用）**：导入 `VoyageTV-unsigned.ipa` → 选证书 → 签名 → 安装。细则见 `SIGN-AND-INSTALL.md`。
- **Sideloadly**：选 IPA + 填 Apple ID，等它推到手机上，再到「设置 → 通用 → VPN与设备管理」信任证书。

**免费 Apple ID 的限制（重要）**：

| 项 | 免费账号 | 付费账号（$99/年） |
| --- | --- | --- |
| 有效期 | **7 天**，到期需重新签 | 1 年 |
| 同时安装 App 数 | 3 个 | 不限 |
| CarPlay 车机入口 | ❌ 不可用 | 仍需 Apple 单独审批 CarPlay Audio |
| 适合 | 自用验证功能 | 长期使用 / 上车 |

> 也就是说：**没有 Mac 也能让你在 iPhone 上用起来**；但"车机上出现图标"这件事由 CarPlay 权限决定，跟有没有 Mac 无关。

---

## 路线 B：按小时租一台云端 Mac

- MacinCloud / MacStadium 之类，按小时计费，Windows 上装个远程桌面客户端就能用。
- 优点：能直接用 Xcode 调试、连真机；缺点：要花钱、首次配置约 30 分钟。
- 适合：想过一遍真机调试、或者以后要长期改代码。

## 路线 C：借一台 Mac，30 分钟

把 `VoyageTV` 目录拷过去，执行：

```bash
brew install xcodegen
cd VoyageTV
./Scripts/build-ipa.sh          # 编译 + 测试 + 出 IPA
open VoyageTV.xcodeproj         # 想在 Xcode 里点 Run 就跑这个
```

只需要对方机器装 Xcode 15+，登录你自己的 Apple ID 即可签名。

---

## 第一次编译大概率会报错，这是正常的

代码是在 Windows 上写的，已经做了两层静态校验：

- **Swift 语法解析**（tree-sitter，31 个文件）：0 语法错误；
- **工程 YAML 结构校验**：targets / scheme / sources 结构正确。

但类型检查、SwiftUI API 版本差异、XcodeGen 生成细节只有真编译器能裁定。做法很简单：

1. 把 Actions 页面里 `Build for simulator` 那一步的红色日志，从第一个 `error:` 开始整段复制给我；
2. 我按报错逐条改，你再 Run 一次；
3. 通常 1–3 轮就能过。

---

## 上 CarPlay 的真实门槛（与 Mac 无关）

1. iPhone 端：能装、能用（路线 A 即可）。
2. 车机端：需要 **Apple Developer 付费账号 + CarPlay Audio 审批**，见 `CARPLAY-ENTITLEMENT.md`。
3. 审批通过后，仍需**带该权限的描述文件**签名 —— 这一步建议届时用云端 Mac 或借 Mac 做一次（Sideloadly 无法注入未获批的 CarPlay entitlement）。

---

## 如果你其实不用 iPhone

那 iOS 这条路整体不成立（CarPlay 是 Apple 独占）。替代方案是 **Android Auto**：可以在 Windows 上用 Android Studio 直接编译，ID.3 同样支持。需要的话告诉我，我按同样的功能范围另起一套。
