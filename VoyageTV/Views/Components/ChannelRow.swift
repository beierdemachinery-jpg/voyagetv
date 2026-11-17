import SwiftUI

struct ChannelRow: View {
    let channel: Channel
    let isFavorite: Bool
    let isPlaying: Bool
    var nowPlayingTitle: String? = nil
    let onTap: () -> Void
    let onToggleFavorite: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Button(action: onTap) {
                HStack(spacing: 12) {
                    logo
                    VStack(alignment: .leading, spacing: 3) {
                        Text(channel.name)
                            .font(.subheadline.weight(isPlaying ? .semibold : .regular))
                            .lineLimit(2)
                        HStack(spacing: 6) {
                            Text(channel.displayGroup)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            if let number = channel.number {
                                Text("#\(number)")
                                    .font(.caption.monospacedDigit())
                                    .foregroundStyle(.tertiary)
                            }
                            if isPlaying {
                                Text("播放中")
                                    .font(.caption2.weight(.medium))
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 2)
                                    .background(Color.accentColor.opacity(0.15), in: Capsule())
                                    .foregroundStyle(Color.accentColor)
                            }
                        }
                        if let nowPlayingTitle {
                            HStack(spacing: 4) {
                                Image(systemName: "clock")
                                    .font(.caption2)
                                Text(nowPlayingTitle)
                                    .font(.caption2)
                                    .lineLimit(1)
                            }
                            .foregroundStyle(.secondary)
                        }
                    }
                    Spacer(minLength: 0)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            Button(action: onToggleFavorite) {
                Image(systemName: isFavorite ? "star.fill" : "star")
                    .foregroundStyle(isFavorite ? Color.yellow : Color.secondary)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(isFavorite ? "取消收藏" : "收藏")
        }
    }

    private var logo: some View {
        Group {
            if let url = channel.logoURL {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let image):
                        image.resizable().scaledToFit()
                    default:
                        placeholder
                    }
                }
            } else {
                placeholder
            }
        }
        .frame(width: 44, height: 44)
        .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
    }

    private var placeholder: some View {
        ZStack {
            Color.secondary.opacity(0.12)
            Image(systemName: "tv")
                .foregroundStyle(.secondary)
        }
    }
}
