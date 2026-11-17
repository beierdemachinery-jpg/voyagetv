import SwiftUI

/// 频道列表：最近观看 + 搜索 + 分类筛选 + 收藏 + 点按播放。
struct ChannelListView: View {
    @EnvironmentObject private var playlists: PlaylistStore
    @EnvironmentObject private var favorites: FavoritesStore
    @EnvironmentObject private var recents: RecentsStore
    @EnvironmentObject private var epg: EPGStore
    @EnvironmentObject private var player: PlaybackEngine

    @State private var query = ""
    @State private var selectedGroup: String?
    @State private var showImporter = false
    @State private var showPlayer = false

    var body: some View {
        NavigationStack {
            Group {
                if playlists.playlists.isEmpty {
                    emptyLibrary
                } else if filteredChannels.isEmpty {
                    emptyResult
                } else {
                    channelList
                }
            }
            .navigationTitle("频道")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        showImporter = true
                    } label: {
                        Image(systemName: "plus")
                    }
                    .accessibilityLabel("导入播放列表")
                }
                ToolbarItem(placement: .navigationBarLeading) {
                    if player.current != nil {
                        Button {
                            showPlayer = true
                        } label: {
                            Label("正在播放", systemImage: "waveform")
                        }
                    }
                }
            }
            .sheet(isPresented: $showImporter) {
                AddPlaylistView()
            }
            .fullScreenCover(isPresented: $showPlayer) {
                PlayerView()
            }
        }
    }

    // MARK: - 列表

    private var channelList: some View {
        List {
            if !playlists.groups.isEmpty {
                Section {
                    CategoryBar(groups: playlists.groups, selected: $selectedGroup)
                        .listRowInsets(EdgeInsets(top: 8, leading: 0, bottom: 8, trailing: 0))
                        .listRowSeparator(.hidden)
                        .listRowBackground(Color.clear)
                }
            }

            if showsRecents {
                Section {
                    ForEach(recentChannels) { channel in
                        row(for: channel)
                    }
                } header: {
                    Text("最近观看")
                }
            }

            Section {
                ForEach(filteredChannels) { channel in
                    row(for: channel)
                }
            } header: {
                Text(filteredChannels.count > 0 ? "共 \(filteredChannels.count) 个频道" : "")
            }
        }
        .listStyle(.plain)
        .searchable(text: $query, prompt: "搜索频道或分类")
    }

    private func row(for channel: Channel) -> some View {
        ChannelRow(
            channel: channel,
            isFavorite: favorites.contains(channel),
            isPlaying: player.current?.id == channel.id,
            nowPlayingTitle: epg.now(on: channel)?.title,
            onTap: { play(channel) },
            onToggleFavorite: { favorites.toggle(channel) }
        )
    }

    private var emptyLibrary: some View {
        VStack(spacing: 16) {
            Image(systemName: "antenna.radiowaves.left.and.right")
                .font(.system(size: 48, weight: .light))
                .foregroundStyle(.secondary)
            Text("还没有播放列表")
                .font(.title3.weight(.semibold))
            Text("支持 M3U / M3U8 链接、Xtream Codes 登录、粘贴文本与本地文件。")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
            Button("添加订阅") { showImporter = true }
                .buttonStyle(.borderedProminent)
        }
        .padding()
    }

    private var emptyResult: some View {
        VStack(spacing: 12) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 40, weight: .light))
                .foregroundStyle(.secondary)
            Text("没有匹配的频道")
                .font(.headline)
            if let selectedGroup {
                Button("清除分类筛选（\(selectedGroup)）") { self.selectedGroup = nil }
                    .font(.subheadline)
            }
        }
        .padding()
    }

    private var filteredChannels: [Channel] {
        playlists.search(query, group: selectedGroup)
    }

    private var showsRecents: Bool {
        selectedGroup == nil && query.trimmed.isEmpty && !recentChannels.isEmpty
    }

    private var recentChannels: [Channel] {
        playlists.channels(ids: recents.channelIDs(limit: 6))
    }

    private func play(_ channel: Channel) {
        player.play(channel)
        showPlayer = true
    }
}
