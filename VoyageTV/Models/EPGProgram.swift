import Foundation

/// 节目单条目（来自 XMLTV）。
struct EPGProgram: Identifiable, Codable, Hashable {
    let channelID: String
    let title: String
    let subtitle: String?
    let detail: String?
    let start: Date
    let stop: Date

    var id: String { "\(channelID)|\(start.timeIntervalSince1970)" }

    var timeRangeText: String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "zh_CN")
        formatter.dateFormat = "HH:mm"
        return "\(formatter.string(from: start))–\(formatter.string(from: stop))"
    }
}
