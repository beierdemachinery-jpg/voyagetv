import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var playlists: PlaylistStore
    @EnvironmentObject private var favorites: FavoritesStore
    @EnvironmentObject private var recents: RecentsStore
    @EnvironmentObject private var epg: EPGStore
    @EnvironmentObject private var player: PlaybackEngine
    @EnvironmentObject private var carPlaySession: CarPlaySession

    @State private var showImporter = false
    @State private var confirmReset = false
    @State private var epgURL = ""
    @State private var epgBusy = false
    @State private var epgMessage: String?
    @State private var refreshingID: UUID?

    var body: some View {
        NavigationStack {
            Form {
                playbackSection
                librarySection
                epgSection
                carPlaySection
                resetSection
                aboutSection
            }
            .navigationTitle("设置")
            .sheet(isPresented: $showImporter) {
                AddPlaylistView()
            }
            .alert("清除全部资料？", isPresented: $confirmReset) {
                Button("取消", role: .cancel) {}
                Button("清除", role: .destructive) {
                    player.stop()
                    playlists.clearAll()
                    favorites.clearAll()
                    recents.clearAll()
                    epg.clear()
                }
            } message: {
                Text("所有订阅、收藏、最近观看与节目单都会被删除，无法恢复。")
            }
            .onAppear {
                if epgURL.isEmpty { epgURL = epg.sourceURL }
            }
        }
    }

    // MARK: - 播放模式

    private var playbackSection: some View {
        Section {
            Toggle(isOn: Binding(
                get: { player.forceAudioOnlyEnabled },
                set: { player.setForceAudioOnly($0) }
            )) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("始终仅音频播放")
                    Text("开启后任何场景都不渲染视频画面。")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
        } header: {
            Text("播放模式")
        } footer: {
            Text("连接 CarPlay 时会自动切换为仅音频，且不会为驾驶场景显示任何视频画面。")
        }
    }

    // MARK: - 订阅

    private var librarySection: some View {
        Section {
            if playlists.playlists.isEmpty {
                Text("还没有订阅")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(playlists.playlists) { playlist in
                    VStack(alignment: .leading, spacing: 4) {
                        HStack(spacing: 8) {
                            Text(playlist.name)
                                .font(.subheadline)
                            Text(playlist.kind.label)
                                .font(.caption2.weight(.medium))
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(Color.secondary.opacity(0.15), in: Capsule())
                                .foregroundStyle(.secondary)
                        }
                        Text("\(playlist.channelCount) 个频道 · \(playlist.groups.count) 个分类")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Text("更新于 \(playlist.importedAt.formatted(date: .numeric, time: .shortened))")
                            .font(.caption2)
                            .foregroundStyle(.tertiary)
                    }
                    .swipeActions(edge: .leading) {
                        if playlist.kind.isRefreshable {
                            Button {
                                refresh(playlist)
                            } label: {
                                Label("刷新", systemImage: "arrow.clockwise")
                            }
                            .tint(.blue)
                        }
                    }
                    .overlay(alignment: .trailing) {
                        if refreshingID == playlist.id {
                            ProgressView().controlSize(.small)
                        }
                    }
                }
                .onDelete(perform: playlists.remove)
            }

            Button {
                showImporter = true
            } label: {
                Label("添加订阅", systemImage: "plus")
            }
        } header: {
            Text("订阅")
        } footer: {
            Text("左滑可刷新 M3U 链接与 Xtream 源；粘贴与本地文件需要重新导入。")
        }
    }

    // MARK: - 节目单

    private var epgSection: some View {
        Section {
            TextField("https://example.com/epg.xml", text: $epgURL)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .keyboardType(.URL)
                .disabled(epgBusy)

            HStack(spacing: 12) {
                Button {
                    updateEPG()
                } label: {
                    Text(epg.hasSource ? "更新节目单" : "启用节目单")
                }
                .disabled(epgBusy || epgURL.trimmed.isEmpty)

                if epgBusy {
                    ProgressView().controlSize(.small)
                }

                Spacer()

                if epg.hasSource {
                    Button(role: .destructive) {
                        epg.clear()
                        epgURL = ""
                        epgMessage = "已清除节目单"
                    } label: {
                        Text("清除")
                    }
                    .disabled(epgBusy)
                }
            }

            if let message = epgMessage {
                Text(message)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            } else if let updated = epg.lastUpdated {
                Text("节目单更新于 \(updated.formatted(date: .numeric, time: .shortened))")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        } header: {
            Text("节目单（XMLTV）")
        } footer: {
            Text("填入 XMLTV 地址后，频道列表与播放页会显示「正在播 / 接下来」，车机端也会显示当前节目。每 12 小时自动更新一次。")
        }
    }

    // MARK: - 车机

    private var carPlaySection: some View {
        Section {
            HStack {
                Text("CarPlay")
                Spacer()
                Text(carPlaySession.isConnected ? "已连接" : "未连接")
                    .foregroundStyle(carPlaySession.isConnected ? Color.green : Color.secondary)
            }
            if let date = carPlaySession.lastConnectedAt {
                HStack {
                    Text("最近连接")
                    Spacer()
                    Text(date.formatted(date: .abbreviated, time: .shortened))
                        .foregroundStyle(.secondary)
                }
            }
        } header: {
            Text("车机")
        } footer: {
            Text("CarPlay 界面需要 Apple 审批的 CarPlay Audio 权限。未获授权时车机不会显示本 App 入口，iPhone 端功能不受影响。")
        }
    }

    // MARK: - 重置 / 关于

    private var resetSection: some View {
        Section {
            Button(role: .destructive) {
                confirmReset = true
            } label: {
                Text("清除全部资料、收藏与节目单")
            }
        } footer: {
            Text("此操作不可撤销。")
        }
    }

    private var aboutSection: some View {
        Section {
            HStack {
                Text("版本")
                Spacer()
                Text(appVersion)
                    .foregroundStyle(.secondary)
            }
            Text("VoyageTV 为独立实现，未使用任何第三方 IPTV 客户端的代码或素材。所有节目内容来自你自行添加的订阅。")
                .font(.caption)
                .foregroundStyle(.secondary)
        } header: {
            Text("关于")
        }
    }

    // MARK: - 动作

    private func updateEPG() {
        epgBusy = true
        epgMessage = nil
        Task {
            defer { epgBusy = false }
            do {
                try await epg.setSource(epgURL)
                epgMessage = "节目单已更新，共 \(epg.programs.count) 个频道有节目数据。"
            } catch {
                epgMessage = error.localizedDescription
            }
        }
    }

    private func refresh(_ playlist: Playlist) {
        refreshingID = playlist.id
        Task {
            defer { refreshingID = nil }
            do {
                try await PlaylistImporter.refresh(playlist, into: playlists)
            } catch {
                epgMessage = error.localizedDescription
            }
        }
    }

    private var appVersion: String {
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "0.1.0"
        let build = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "1"
        return "\(version) (\(build))"
    }
}
