# 云端构建记录

## 2026-11-17 · 第一次成功编译

| 项 | 结果 |
| --- | --- |
| 运行器 | `macos-latest`（Apple silicon，Xcode 26 系列） |
| 工程生成 | `xcodegen generate` 成功 |
| 编译 | **成功**（iphonesimulator / iphoneos 均通过） |
| 单测 | 21 条在 iPhone 模拟器上执行 |
| 归档 | `CODE_SIGNING_ALLOWED=NO` 归档成功 |
| 产物 | `VoyageTV-unsigned-ipa`，约 **259 KB** |

### 过程中修掉的两个真实问题

1. **`runs-on: macos-14` 已失效**
   GitHub 于 2026-11-02 下线 macOS 14 镜像，指向它的任务会在 1 秒内被取消（日志里只有
   "Process completed with exit code 65"，连 runner 都没分配）。已改成 `macos-latest`。

2. **`Substring` 没有 `trimmed` 扩展**
   `M3UParser.swift` 里对 `split(separator:)` 的结果直接调 `.trimmed`，
   而 `trimmed` 是 `String` 的扩展，`Substring` 上不存在。
   修法：`String(parts[0]).trimmed`。

### 这次构建暴露的验证边界

- 之前的 tree-sitter 语法扫描只能证明"括号和语法没写错"，**证明不了类型正确**；
- 类型层唯一的错误就是上面第 2 条，说明整体代码质量尚可；
- 但编译通过 ≠ 功能正确：Xtream 登录、EPG 解析、CarPlay 模板都还需要在真机上跑一遍。

### 后续每次构建怎么看结果

流水线已加了两件事：

1. 失败时把 `error:` 行自动发布成 **GitHub Issue**（公开仓库可直接读，不用下载日志包）；
2. 无论成败都上传 `build-logs` 制品（`build.log` / `test.log` / `archive.log` 三个纯文本）。

### 产物有效期

GitHub Actions 制品默认保留 **30 天**，过期后重新跑一次工作流即可再生成。
