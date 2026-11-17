import Foundation

/// Xtream Codes 登录凭据。
struct XtreamCredentials: Codable, Hashable {
    /// 服务器根地址，例如 http://example.com:8080（也可粘 player_api.php 的完整地址）
    var baseURL: String
    var username: String
    var password: String

    /// 规范化后的服务器根（无尾斜杠、无 player_api.php）。
    var normalizedBase: String {
        var raw = baseURL.trimmed
        while raw.hasSuffix("/") { raw.removeLast() }
        if let range = raw.range(of: "player_api.php", options: .backwards) {
            raw = String(raw[raw.startIndex..<range.lowerBound])
        }
        while raw.hasSuffix("/") { raw.removeLast() }
        return raw
    }
}

/// Xtream Codes（player_api.php）客户端：登录 → 拉分类 → 拉直播频道 → 拼 HLS 播放地址。
enum XtreamClient {
    enum ClientError: LocalizedError {
        case invalidServer
        case authFailed
        case http(Int)
        case malformed(String)
        case empty

        var errorDescription: String? {
            switch self {
            case .invalidServer:
                return "服务器地址不正确，请填写类似 http://host:8080 的地址。"
            case .authFailed:
                return "账号或密码不对，服务器拒绝了登录。"
            case .http(let code):
                return "服务器返回状态码 \(code)。"
            case .malformed(let message):
                return "返回内容无法解析：\(message)"
            case .empty:
                return "服务器没有返回任何直播频道。"
            }
        }
    }

    struct Payload {
        var channels: [Channel]
        var warnings: [String]
    }

    static func fetch(_ credentials: XtreamCredentials, fallbackName: String) async throws -> Payload {
        let categories = try await fetchCategories(credentials)
        let streams = try await fetchStreams(credentials)

        var channels: [Channel] = []
        var warnings: [String] = []
        var seen = Set<String>()

        for stream in streams {
            guard let streamID = string(stream["stream_id"]).flatMap({ $0.isEmpty ? nil : $0 }) else { continue }
            let name = string(stream["name"]) ?? "频道 \(streamID)"
            guard let url = streamURL(credentials: credentials, streamID: streamID) else {
                warnings.append("频道「\(name)」的播放地址无法生成")
                continue
            }
            let id = StableID.channel(url, name: name)
            guard seen.insert(id).inserted else { continue }

            let categoryID = string(stream["category_id"])
            let group = categoryID.flatMap { categories[$0] } ?? "未分类"
            let logo = string(stream["stream_icon"]).flatMap { URL(string: $0) }

            channels.append(
                Channel(
                    id: id,
                    name: name,
                    streamURL: url,
                    logoURL: logo,
                    group: group,
                    tvgID: string(stream["epg_channel_id"]),
                    tvgName: name,
                    number: string(stream["num"]),
                    httpHeaders: [:]
                )
            )
        }

        guard !channels.isEmpty else { throw ClientError.empty }
        return Payload(channels: channels, warnings: warnings)
    }

    // MARK: - 请求

    private static func fetchCategories(_ credentials: XtreamCredentials) async throws -> [String: String] {
        let json = try await get(credentials, action: "get_live_categories")
        guard let array = json as? [[String: Any]] else { return [:] }
        var mapping: [String: String] = [:]
        for entry in array {
            guard let id = string(entry["category_id"]) else { continue }
            mapping[id] = string(entry["category_name"]) ?? "未分类"
        }
        return mapping
    }

    private static func fetchStreams(_ credentials: XtreamCredentials) async throws -> [[String: Any]] {
        let json = try await get(credentials, action: "get_live_streams")
        guard let array = json as? [[String: Any]] else {
            throw ClientError.malformed("get_live_streams 不是数组")
        }
        return array
    }

    private static func get(_ credentials: XtreamCredentials, action: String?) async throws -> Any {
        guard var components = URLComponents(string: credentials.normalizedBase + "/player_api.php") else {
            throw ClientError.invalidServer
        }
        var items = [
            URLQueryItem(name: "username", value: credentials.username),
            URLQueryItem(name: "password", value: credentials.password)
        ]
        if let action { items.append(URLQueryItem(name: "action", value: action)) }
        components.queryItems = items
        guard let url = components.url else { throw ClientError.invalidServer }

        var request = URLRequest(url: url)
        request.timeoutInterval = 25
        request.setValue("VoyageTV/1.0 (iOS)", forHTTPHeaderField: "User-Agent")

        let (data, response) = try await URLSession.shared.data(for: request)
        if let http = response as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
            throw ClientError.http(http.statusCode)
        }
        guard data.count > 0 else { throw ClientError.malformed("空响应") }

        let json = try JSONSerialization.jsonObject(with: data, options: [.fragmentsAllowed])

        // 登录校验：user_info.auth == 0 表示凭据被拒
        if let dict = json as? [String: Any],
           let userInfo = dict["user_info"] as? [String: Any],
           let auth = int(userInfo["auth"]), auth == 0 {
            throw ClientError.authFailed
        }
        return json
    }

    private static func streamURL(credentials: XtreamCredentials, streamID: String) -> URL? {
        let base = credentials.normalizedBase
        let raw = "\(base)/live/\(credentials.username)/\(credentials.password)/\(streamID).m3u8"
        if let url = URL(string: raw) { return url }
        let encoded = raw.addingPercentEncoding(withAllowedCharacters: .urlFragmentAllowed) ?? raw
        return URL(string: encoded)
    }

    // MARK: - 宽容取值（Xtream 返回里数字/字符串混用很常见）

    private static func string(_ value: Any?) -> String? {
        switch value {
        case let text as String:
            return text.trimmed.isEmpty ? nil : text
        case let number as NSNumber:
            return number.stringValue
        case let number as Int:
            return String(number)
        default:
            return nil
        }
    }

    private static func int(_ value: Any?) -> Int? {
        switch value {
        case let number as Int: return number
        case let number as NSNumber: return number.intValue
        case let text as String: return Int(text.trimmed)
        default: return nil
        }
    }
}
