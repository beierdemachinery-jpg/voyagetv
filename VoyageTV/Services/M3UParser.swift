import Foundation

/// M3U / M3U8 频道表解析器。
///
/// 支持的写法（真实 IPTV 列表里常见的那几种）：
/// - `#EXTM3U` 头部，含 `http-user-agent` 之类的全局属性
/// - `#EXTINF:-1 tvg-id="..." tvg-name="..." tvg-logo="..." group-title="...",频道名`
/// - `#EXTGRP:分类名`
/// - `#EXTVLCOPT:http-user-agent=...` / `http-referrer=...` / `http-cookie=...`
/// - 相对路径的流地址（按来源播放列表的 URL 解析）
/// - 注释行、空行、CRLF / LF 混排
///
/// 明确不做的事：不解析 `#EXT-X-*` 的 HLS 分片清单为频道列表，
/// 只把整个文件识别为"HLS 单流"，由上层当成一个可播放条目处理。
enum M3UParser {
    struct Output {
        var channels: [Channel] = []
        var warnings: [String] = []
        /// 输入看起来是 HLS 媒体清单（#EXT-X-*），而不是频道表。
        var isHLSMediaPlaylist = false
        /// `#EXTM3U x-tvg-url="..."` / `url-tvg="..."` 里带的节目单地址。
        var epgURL: URL?
    }

    static func parse(_ text: String, baseURL: URL? = nil) -> Output {
        var output = Output()
        let cleaned = text.replacingOccurrences(of: "\u{FEFF}", with: "")
        let lines = cleaned.components(separatedBy: .newlines)

        var defaultHeaders: [String: String] = [:]
        var pendingName: String?
        var pendingAttributes: [String: String] = [:]
        var pendingGroup: String?
        var pendingHeaders: [String: String] = [:]
        var seenIDs = Set<String>()
        var sawHLSMarker = false
        var index = 0

        for rawLine in lines {
            index += 1
            let line = rawLine.trimmed
            guard !line.isEmpty else { continue }

            if line.hasPrefix("#EXTM3U") {
                let attributes = parseAttributes(line)
                if let userAgent = attributes["http-user-agent"], !userAgent.isEmpty {
                    defaultHeaders["User-Agent"] = userAgent
                }
                // 订阅头里可能同时声明多个节目单地址（逗号分隔），取第一个可用的。
                if let rawEPG = attributes["x-tvg-url"] ?? attributes["url-tvg"], !rawEPG.isEmpty {
                    let first = (rawEPG.components(separatedBy: ",").first ?? rawEPG).trimmed
                    if !first.isEmpty { output.epgURL = resolveURL(first, base: baseURL) }
                }
                continue
            }

            if line.hasPrefix("#EXTINF") {
                let body = dropPrefix(line, "#EXTINF:", fallbackPrefix: "#EXTINF")
                let split = splitTitleAndAttributes(body)
                pendingAttributes = parseAttributes(split.attributes)
                let title = split.title.trimmed
                if !title.isEmpty {
                    pendingName = title
                } else if let tvgName = pendingAttributes["tvg-name"], !tvgName.isEmpty {
                    pendingName = tvgName
                } else {
                    pendingName = nil
                }
                pendingGroup = pendingAttributes["group-title"]
                continue
            }

            if line.hasPrefix("#EXTGRP:") {
                pendingGroup = String(line.dropFirst("#EXTGRP:".count)).trimmed
                continue
            }

            if line.hasPrefix("#EXTVLCOPT:") {
                let option = String(line.dropFirst("#EXTVLCOPT:".count))
                applyVLCOption(option, into: &pendingHeaders)
                continue
            }

            if line.hasPrefix("#EXT-X-") {
                sawHLSMarker = true
                continue
            }

            if line.hasPrefix("#") {
                continue
            }

            // 到这里应当是一条流地址
            guard let url = resolveURL(line, base: baseURL) else {
                output.warnings.append("第 \(index) 行地址无法解析：\(line)")
                resetPending(&pendingName, &pendingAttributes, &pendingGroup, &pendingHeaders)
                continue
            }

            let displayName = (pendingName?.isEmpty == false ? pendingName! : fallbackName(for: url))
            var headers = defaultHeaders
            for (key, value) in pendingHeaders { headers[key] = value }

            let id = StableID.channel(url, name: displayName)
            guard seenIDs.insert(id).inserted else {
                resetPending(&pendingName, &pendingAttributes, &pendingGroup, &pendingHeaders)
                continue
            }

            let channel = Channel(
                id: id,
                name: displayName,
                streamURL: url,
                logoURL: resolveURL(pendingAttributes["tvg-logo"] ?? "", base: baseURL),
                group: pendingGroup?.trimmed ?? "未分类",
                tvgID: blankToNil(pendingAttributes["tvg-id"]),
                tvgName: blankToNil(pendingAttributes["tvg-name"]),
                number: blankToNil(pendingAttributes["tvg-chno"] ?? pendingAttributes["channel-number"]),
                httpHeaders: headers
            )
            output.channels.append(channel)
            resetPending(&pendingName, &pendingAttributes, &pendingGroup, &pendingHeaders)
        }

        output.isHLSMediaPlaylist = output.channels.isEmpty && sawHLSMarker
        return output
    }

    // MARK: - 行内属性

    /// 把 `-1 tvg-id="x" group-title="A,B"` 这类片段解析成键值对。
    /// 逗号可能出现在引号内部（例如 group-title="News, World"），因此不能用简单 split。
    static func parseAttributes(_ text: String) -> [String: String] {
        var result: [String: String] = [:]
        var index = text.startIndex

        while index < text.endIndex {
            while index < text.endIndex, text[index] == " " || text[index] == "\t" {
                index = text.index(after: index)
            }
            guard index < text.endIndex else { break }

            var key = ""
            while index < text.endIndex, text[index] != "=", text[index] != " ", text[index] != "\t" {
                key.append(text[index])
                index = text.index(after: index)
            }

            guard index < text.endIndex, text[index] == "=" else {
                continue
            }
            index = text.index(after: index)

            var value = ""
            if index < text.endIndex, text[index] == "\"" {
                index = text.index(after: index)
                while index < text.endIndex, text[index] != "\"" {
                    value.append(text[index])
                    index = text.index(after: index)
                }
                if index < text.endIndex { index = text.index(after: index) }
            } else {
                while index < text.endIndex, text[index] != " ", text[index] != "\t" {
                    value.append(text[index])
                    index = text.index(after: index)
                }
            }

            if !key.isEmpty {
                result[key.lowercased()] = value
            }
        }
        return result
    }

    /// 在引号外找到第一个逗号，左边是属性区（含时长），右边是频道名。
    static func splitTitleAndAttributes(_ body: String) -> (attributes: String, title: String) {
        var inQuotes = false
        var cursor = body.startIndex
        while cursor < body.endIndex {
            let character = body[cursor]
            if character == "\"" {
                inQuotes.toggle()
            } else if character == ",", !inQuotes {
                let attributes = String(body[body.startIndex..<cursor])
                let title = String(body[body.index(after: cursor)...])
                return (attributes, title)
            }
            cursor = body.index(after: cursor)
        }
        return (body, "")
    }

    // MARK: - 辅助

    private static func applyVLCOption(_ option: String, into headers: inout [String: String]) {
        let parts = option.split(separator: "=", maxSplits: 1, omittingEmptySubsequences: false)
        guard parts.count == 2 else { return }
        let key = String(parts[0]).trimmed.lowercased()
        let value = String(parts[1]).trimmed
        guard !value.isEmpty else { return }
        switch key {
        case "http-user-agent":
            headers["User-Agent"] = value
        case "http-referrer", "http-referer":
            headers["Referer"] = value
        case "http-cookie":
            headers["Cookie"] = value
        case "http-origin":
            headers["Origin"] = value
        default:
            break
        }
    }

    private static func resolveURL(_ raw: String, base: URL?) -> URL? {
        let trimmed = raw.trimmed
        guard !trimmed.isEmpty else { return nil }
        let candidate = trimmed.addingPercentEncoding(withAllowedCharacters: .urlFragmentAllowed) ?? trimmed
        if let url = URL(string: candidate), url.scheme != nil {
            return url
        }
        guard let base else { return nil }
        return URL(string: candidate, relativeTo: base)?.absoluteURL
    }

    private static func dropPrefix(_ line: String, _ prefixWithColon: String, fallbackPrefix: String) -> String {
        if line.hasPrefix(prefixWithColon) {
            return String(line.dropFirst(prefixWithColon.count))
        }
        return String(line.dropFirst(fallbackPrefix.count))
    }

    private static func fallbackName(for url: URL) -> String {
        let last = url.lastPathComponent
        if last.isEmpty { return url.host ?? url.absoluteString }
        return last
    }

    private static func blankToNil(_ value: String?) -> String? {
        guard let value else { return nil }
        let trimmed = value.trimmed
        return trimmed.isEmpty ? nil : trimmed
    }

    private static func resetPending(
        _ name: inout String?,
        _ attributes: inout [String: String],
        _ group: inout String?,
        _ headers: inout [String: String]
    ) {
        name = nil
        attributes = [:]
        group = nil
        headers = [:]
    }
}
