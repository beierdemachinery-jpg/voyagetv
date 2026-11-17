import SwiftUI

/// 横向分类筛选条。"全部" 对应 selected == nil。
struct CategoryBar: View {
    let groups: [String]
    @Binding var selected: String?

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                chip(title: "全部", isSelected: selected == nil) {
                    selected = nil
                }
                ForEach(groups, id: \.self) { group in
                    chip(title: group, isSelected: selected == group) {
                        selected = (selected == group) ? nil : group
                    }
                }
            }
            .padding(.horizontal, 16)
        }
    }

    private func chip(title: String, isSelected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.subheadline.weight(isSelected ? .semibold : .regular))
                .lineLimit(1)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(
                    Capsule().fill(isSelected ? Color.accentColor.opacity(0.18) : Color.secondary.opacity(0.12))
                )
                .foregroundStyle(isSelected ? Color.accentColor : Color.primary)
        }
        .buttonStyle(.plain)
    }
}
