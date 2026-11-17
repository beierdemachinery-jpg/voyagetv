import Foundation

extension String {
    /// 搜索用的归一化键：忽略大小写、变音符号与全半角差异，让"新闻"和"新聞"式的输入尽量命中。
    var searchKey: String {
        folding(options: [.caseInsensitive, .diacriticInsensitive, .widthInsensitive], locale: .current)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var trimmed: String {
        trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
