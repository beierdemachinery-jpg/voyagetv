import Foundation

/// 一个可播放的频道条目。
/// 完全由本 App 自行解析 M3U 生成，与任何第三方 App 的数据结构无关。
struct Channel: Identifiable, Codable, Hashable {
    /// 稳定 ID：由 streamURL + 名称派生，导入同一播放列表多次不会产生重复收藏项。
    let id: String
    var name: String
    var streamURL: URL
    var logoURL: URL?
    var group: String
    var tvgID: String?
    var tvgName: String?
    /// 频道号（tvg-chno / 键值对里的 number），用于排序，可能为空。
    var number: String?
    /// 播放时附加的 HTTP 头（来自 #EXTVLCOPT 或 #EXTM3U 全局属性）。
    var httpHeaders: [String: String]

    init(
        id: String,
        name: String,
        streamURL: URL,
        logoURL: URL? = nil,
        group: String = "未分类",
        tvgID: String? = nil,
        tvgName: String? = nil,
        number: String? = nil,
        httpHeaders: [String: String] = [:]
    ) {
        self.id = id
        self.name = name
        self.streamURL = streamURL
        self.logoURL = logoURL
        self.group = group
        self.tvgID = tvgID
        self.tvgName = tvgName
        self.number = number
        self.httpHeaders = httpHeaders
    }

    var displayGroup: String {
        let trimmed = group.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? "未分类" : trimmed
    }

    /// 是否是 AVFoundation 原生能直接播的地址。
    var isNativelyPlayable: Bool {
        guard let scheme = streamURL.scheme?.lowercased() else { return false }
        return ["http", "https", "file"].contains(scheme)
    }

    func matches(_ query: String) -> Bool {
        let key = query.searchKey
        guard !key.isEmpty else { return true }
        if name.searchKey.contains(key) { return true }
        if displayGroup.searchKey.contains(key) { return true }
        if let tvgName, tvgName.searchKey.contains(key) { return true }
        return false
    }
}
