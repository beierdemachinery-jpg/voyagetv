# 从零开始：把 VoyageTV 传到 GitHub 并出第一个 IPA

> 你现在：GitHub 已注册登录，账号下没有仓库。
> 目标：让云端 macOS 编译一次，拿到 `VoyageTV-unsigned.ipa`，再用全能签装进 iPhone。

---

## 第 0 步：确认要传的目录

在本机打开这个文件夹（后面所有操作都以它为源）：

```
C:\Users\Vicky\Doubao\chats\2026-11-16\new-chat-3\VoyageTV
```

里面的**顶层**应该正好是这 9 项：

```
.github\           ← 必须有，否则 GitHub 不会出现 Actions 流水线
.gitignore
Config\
Docs\
README.md
Scripts\
VoyageTV\
VoyageTVTests\
project.yml        ← 必须有，且必须在仓库根目录
```

注意：**不要**把外层 `new-chat-3` 传上去，也**不要**在仓库里再套一层 `VoyageTV/` 目录。仓库根目录 = 这个 `VoyageTV` 文件夹本身。

---

## 第 1 步：新建仓库

1. 右上角 `+` → **New repository**
2. 填写：
   - **Repository name**：`voyagetv`（小写，避免 URL 大小写问题）
   - **Description**（可选）：`IPTV player for iOS with CarPlay audio support`
   - **Visibility**：选 **Public**
     （原因：公开仓库的 macOS 编译不计量、不限时长；私有仓库每月只有约 200 分钟 macOS 额度）
   - **不要**勾选 `Add a README file`、`Add .gitignore`、`Choose a license`
     （我们自己的 README 和 .gitignore 已经有了，勾选会造成冲突）
3. 点 **Create repository**

创建完页面会显示 "Quick setup"，说明仓库是空的 —— 这正是我们要的状态。

---

## 第 2 步：上传文件（选一种）

### 方式 A：网页拖拽（最快，不用装东西）

1. 在仓库页点 **uploading an existing file** 链接（或 `Add file` → `Upload files`）。
2. 打开 Windows 资源管理器，进到 `...\new-chat-3\VoyageTV` 目录。
3. **Ctrl+A 全选**该目录下的所有内容（含 `.github`、`.gitignore`）。
4. 直接拖到网页中间的虚线上传区域。
5. 等进度条走完（38 个文件，几秒到十几秒）。
6. 底部 Commit changes 区域随便写一句 `initial commit`，点 **Commit changes**。

> 关键点：拖的是**文件夹里面的内容**，不是文件夹本身。
> 如果拖了文件夹本身，仓库根目录会多出一层 `VoyageTV/`，`project.yml` 就不在根目录了，流水线会报找不到工程。

### 方式 B：GitHub Desktop（更稳，适合以后改代码）

1. 装 GitHub Desktop（Windows），登录同一个账号。
2. `File` → `Add local repository` 如果提示不是仓库，改用 `File` → `New repository`：
   - Name：`voyagetv`
   - Local path：`C:\Users\Vicky\Doubao\chats\2026-11-16\new-chat-3\VoyageTV`
   - 取消勾选 "Initialize with README"
3. 它会把这个目录初始化为 git 仓库并列出全部文件。
4. 左下角写 summary `initial commit` → **Commit to main**。
5. 顶部 **Publish repository** → 取消勾选 "Keep this code private" → Publish。

---

## 第 3 步：验证上传是否完整（30 秒，别跳过）

在仓库首页看文件列表，必须能同时看到：

- [ ] `.github` 目录（点进去应看到 `workflows/ios.yml`）
- [ ] `project.yml`
- [ ] `VoyageTV/` 目录（里面是 `App`、`Models`、`Services`、`Views`、`CarPlay`、`Utilities`）
- [ ] `VoyageTVTests/` 目录
- [ ] `Config/`、`Docs/`、`Scripts/`、`README.md`

如果看不到 `.github`：说明拖拽时漏了隐藏/点开头目录，用 `Add file` → `Create new file`，文件名填 `.github/workflows/ios.yml`，把内容拷进去即可。

---

## 第 4 步：跑第一次云端编译

1. 仓库顶部 **Actions** 标签（第一次进来会提示启用 workflows，点绿色的 `I understand my workflows, go ahead and enable them`）。
2. 左侧列表点 **iOS Build**。
3. 右侧 **Run workflow** → 分支选 `main` → 绿色 **Run workflow** 按钮。
4. 等 15–25 分钟，刷新页面看进度。

流水线会依次执行：

| 步骤 | 作用 | 失败意味着什么 |
| --- | --- | --- |
| Generate Xcode project | 用 XcodeGen 生成 `.xcodeproj` | `project.yml` 写错或路径不对 |
| Run unit tests | 跑 21 条单测 | 测试逻辑问题（不阻断出包） |
| **Build for simulator** | **真正的编译** | **源码类型错误 —— 就看这一步** |
| Archive unsigned | 归档成 `.app` | 编译过了但归档配置问题 |
| Package unsigned IPA | 打成 IPA | 归档产物路径问题 |

**第一次几乎一定在 `Build for simulator` 挂掉**，这是预期内的：代码是在 Windows 上写的，只做过语法层校验，类型检查必须由真编译器裁定。

---

## 第 5 步：把报错抓回来

1. 点进失败的那次运行，点 `Build for simulator (compile check)` 这一步。
2. 往下拉，找到第一行 `error:`，从它开始往下复制 20–40 行（含文件路径和行号）。
3. 贴给我，我改完你再点一次 `Run workflow`。

判断标准：错误通常分三类，处理方式不同——

- `cannot find 'xxx' in scope` / `value of type ... has no member`：函数名或属性名对不上，改几行；
- `type of expression is ambiguous`：类型推断歧义，补显式类型；
- `'xxx' is only available in iOS 16.0 or newer`：API 版本门槛，换等价写法。

一般 1–3 轮收敛。

---

## 第 6 步：下载 IPA

运行成功后，在该次运行页面底部 **Artifacts** 区域 → 点 `VoyageTV-unsigned-ipa` 下载，得到一个 zip，解压后是 `VoyageTV-unsigned.ipa`。

然后走 `SIGN-AND-INSTALL.md`：全能签导入 IPA → 选证书 → 签名 → 安装。

> 提醒：这一步装出来的 App **没有 CarPlay 入口**（描述文件不含 `com.apple.developer.carplay-audio`），iPhone 端功能完整。CarPlay 要等 Apple 审批。

---

## 出问题对照表

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| Actions 标签里没有 `iOS Build` | `.github/workflows/ios.yml` 没传上去 | 补传该文件（见第 3 步） |
| `xcodegen: command not found` | brew 安装步骤失败 | 重跑一次；仍失败则看该步日志 |
| `error: Unable to find a destination` | 模拟器名不存在 | 把 `ios.yml` 里 `name=iPhone 15` 改成 `iPhone 16` |
| `No such file or directory: Config/Info.plist` | `Config/` 没上传 | 补传 |
| Archive 报 signing 相关错误 | 归档参数被改动 | 确认三步都带 `CODE_SIGNING_ALLOWED=NO` |
| 单测失败但 IPA 有产出 | 测试用例与实现不一致 | 先不管，单独修测试 |
