import Foundation

/// 一次订阅 / 导入得到的播放列表。
struct Playlist: Identifiable, Codable, Hashable {
    enum Kind: String, Codable {
        case m3uURL
        case xtream
        case pasted
        case file

        var label: String {
            switch self {
            case .m3uURL: return "M3U 链接"
            case .xtream: return "Xtream Codes"
            case .pasted: return "粘贴文本"
            case .file: return "本地文件"
            }
        }

        /// 能否用保存下来的配置重新拉取频道。
        var isRefreshable: Bool {
            switch self {
            case .m3uURL, .xtream: return true
            case .pasted, .file: return false
            }
        }
    }

    let id: UUID
    var name: String
    /// 来源标识：远端 URL、file://文件名，或 "pasted"。
    var source: String
    var importedAt: Date
    var kind: Kind
    /// 仅 Xtream 源需要，刷新时用它重新登录。
    var credentials: XtreamCredentials?
    var channels: [Channel]

    init(
        id: UUID = UUID(),
        name: String,
        source: String,
        importedAt: Date = Date(),
        kind: Kind = .m3uURL,
        credentials: XtreamCredentials? = nil,
        channels: [Channel]
    ) {
        self.id = id
        self.name = name
        self.source = source
        self.importedAt = importedAt
        self.kind = kind
        self.credentials = credentials
        self.channels = channels
    }

    var channelCount: Int { channels.count }

    /// 有序去重的分类名，"未分类" 排最后。
    var groups: [String] {
        var ordered: [String] = []
        var seen = Set<String>()
        for channel in channels {
            let group = channel.displayGroup
            if seen.insert(group).inserted { ordered.append(group) }
        }
        let normal = ordered.filter { $0 != "未分类" }.sorted { $0.localizedStandardCompare($1) == .orderedAscending }
        return ordered.contains("未分类") ? normal + ["未分类"] : normal
    }

    // MARK: - Codable（对旧数据宽容：缺字段时给默认值）

    private enum CodingKeys: String, CodingKey {
        case id, name, source, importedAt, kind, credentials, channels
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decodeIfPresent(UUID.self, forKey: .id) ?? UUID()
        name = try container.decodeIfPresent(String.self, forKey: .name) ?? "播放列表"
        source = try container.decodeIfPresent(String.self, forKey: .source) ?? ""
        importedAt = try container.decodeIfPresent(Date.self, forKey: .importedAt) ?? Date()
        kind = try container.decodeIfPresent(Kind.self, forKey: .kind) ?? .m3uURL
        credentials = try container.decodeIfPresent(XtreamCredentials.self, forKey: .credentials)
        channels = try container.decodeIfPresent([Channel].self, forKey: .channels) ?? []
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(id, forKey: .id)
        try container.encode(name, forKey: .name)
        try container.encode(source, forKey: .source)
        try container.encode(importedAt, forKey: .importedAt)
        try container.encode(kind, forKey: .kind)
        try container.encodeIfPresent(credentials, forKey: .credentials)
        try container.encode(channels, forKey: .channels)
    }
}
