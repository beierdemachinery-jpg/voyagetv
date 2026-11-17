import Foundation

/// XMLTV 节目单解析（流式，大文件也不吃内存）。
final class XMLTVParser: NSObject, XMLParserDelegate {
    struct Result {
        /// key 为小写的 XMLTV channel id
        var programs: [String: [EPGProgram]] = [:]
        /// channel id → 显示名，用于 tvg-id 缺失时按名称兜底匹配
        var displayNames: [String: String] = [:]
        var warnings: [String] = []
    }

    static func parse(_ data: Data) -> Result {
        let parser = XMLParser(data: data)
        let delegate = XMLTVParser()
        parser.delegate = delegate
        parser.shouldProcessNamespaces = false
        parser.parse()
        return delegate.result
    }

    private var result = Result()
    private var currentChannelID: String?
    private var currentStart: Date?
    private var currentStop: Date?
    private var currentTitle: String?
    private var currentSubtitle: String?
    private var currentDetail: String?
    private var captureElement: String?
    private var captureBuffer = ""
    // <channel id="x"><display-name>名称</display-name>
    private var channelDisplayID: String?
    private var displayNameBuffer: String?

    private static let dateFormatters: [DateFormatter] = {
        let patterns = ["yyyyMMddHHmmss Z", "yyyyMMddHHmmss", "yyyy-MM-dd HH:mm:ss Z", "yyyy-MM-dd'T'HH:mm:ssZ"]
        return patterns.map { pattern in
            let formatter = DateFormatter()
            formatter.locale = Locale(identifier: "en_US_POSIX")
            formatter.timeZone = TimeZone(identifier: "UTC")
            formatter.dateFormat = pattern
            return formatter
        }
    }()

    // MARK: - XMLParserDelegate

    func parser(
        _ parser: XMLParser,
        didStartElement elementName: String,
        namespaceURI: String?,
        qualifiedName qName: String?,
        attributes attributeDict: [String: String] = [:]
    ) {
        switch elementName.lowercased() {
        case "channel":
            channelDisplayID = attributeDict["id"]
            displayNameBuffer = nil
        case "display-name":
            if channelDisplayID != nil { captureElement = "display-name"; captureBuffer = "" }
        case "programme":
            currentChannelID = attributeDict["channel"]
            currentStart = Self.date(from: attributeDict["start"])
            currentStop = Self.date(from: attributeDict["stop"])
            currentTitle = nil
            currentSubtitle = nil
            currentDetail = nil
        case "title":
            if currentChannelID != nil { captureElement = "title"; captureBuffer = "" }
        case "sub-title", "subtitle":
            if currentChannelID != nil { captureElement = "subtitle"; captureBuffer = "" }
        case "desc":
            if currentChannelID != nil { captureElement = "desc"; captureBuffer = "" }
        default:
            break
        }
    }

    func parser(_ parser: XMLParser, foundCharacters string: String) {
        guard captureElement != nil else { return }
        captureBuffer += string
    }

    func parser(
        _ parser: XMLParser,
        didEndElement elementName: String,
        namespaceURI: String?,
        qualifiedName qName: String?
    ) {
        switch elementName.lowercased() {
        case "title":
            if captureElement == "title" {
                currentTitle = captureBuffer.trimmed
                captureElement = nil
            }
        case "sub-title", "subtitle":
            if captureElement == "subtitle" {
                currentSubtitle = captureBuffer.trimmed
                captureElement = nil
            }
        case "desc":
            if captureElement == "desc" {
                currentDetail = captureBuffer.trimmed
                captureElement = nil
            }
        case "display-name":
            if captureElement == "display-name" {
                displayNameBuffer = captureBuffer.trimmed
                captureElement = nil
            }
        case "channel":
            if let id = channelDisplayID, let name = displayNameBuffer, !name.isEmpty {
                result.displayNames[id.lowercased()] = name
            }
            channelDisplayID = nil
            displayNameBuffer = nil
        case "programme":
            appendCurrentProgramme()
            currentChannelID = nil
            currentStart = nil
            currentStop = nil
        default:
            break
        }
    }

    // MARK: - 组装

    private func appendCurrentProgramme() {
        guard let channelID = currentChannelID, !channelID.isEmpty,
              let start = currentStart,
              let stop = currentStop,
              stop > start else { return }

        let title = (currentTitle?.isEmpty == false) ? currentTitle! : "未命名节目"
        let programme = EPGProgram(
            channelID: channelID,
            title: title,
            subtitle: currentSubtitle?.isEmpty == false ? currentSubtitle : nil,
            detail: currentDetail?.isEmpty == false ? currentDetail : nil,
            start: start,
            stop: stop
        )
        result.programs[channelID.lowercased(), default: []].append(programme)
    }

    private static func date(from raw: String?) -> Date? {
        guard let raw, !raw.trimmed.isEmpty else { return nil }
        let value = raw.trimmed
        for formatter in dateFormatters {
            if let date = formatter.date(from: value) { return date }
        }
        // 有些源给的是 "+0000" 缺秒的形式
        if let spaceIndex = value.firstIndex(of: " ") {
            let trimmedValue = String(value[value.startIndex..<spaceIndex]) + " " + String(value[value.index(after: spaceIndex)...])
            if let date = dateFormatters.first?.date(from: trimmedValue) { return date }
        }
        return nil
    }
}
