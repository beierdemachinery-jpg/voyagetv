import CarPlay
import Combine
import UIKit

/// CarPlay 界面：最近观看 → 收藏 → 搜索 → 按分类的频道列表。
/// 选中频道后立即开始音频播放，并推入系统的 Now Playing 模板，
/// 方向盘 / 车机媒体键通过 MPRemoteCommandCenter 生效。
@MainActor
final class CarPlayCoordinator: NSObject, CPSearchTemplateDelegate {
    private let interfaceController: CPInterfaceController
    private let playlists: PlaylistStore
    private let favorites: FavoritesStore
    private let recents: RecentsStore
    private let epg: EPGStore
    private let player: PlaybackEngine

    private let rootTemplate = CPListTemplate(title: "VoyageTV", sections: [])
    private var searchTemplate: CPSearchTemplate?
    private var cancellables = Set<AnyCancellable>()

    /// 频道 id → Channel，供列表项回调使用（CPListItem 的 userInfo 只放 id）。
    private var registeredChannels: [String: Channel] = [:]

    private let maxItemsPerSection = 40
    private let maxSections = 24

    init(
        interfaceController: CPInterfaceController,
        playlists: PlaylistStore,
        favorites: FavoritesStore,
        recents: RecentsStore,
        epg: EPGStore,
        player: PlaybackEngine
    ) {
        self.interfaceController = interfaceController
        self.playlists = playlists
        self.favorites = favorites
        self.recents = recents
        self.epg = epg
        self.player = player
        super.init()
    }

    // MARK: - 生命周期

    func start() {
        rebuildSections()
        interfaceController.setRootTemplate(rootTemplate, animated: true, completion: nil)
        observeLibraryChanges()
    }

    func stop() {
        cancellables.removeAll()
        registeredChannels.removeAll()
    }

    // MARK: - 模板构建

    private func rebuildSections() {
        registeredChannels.removeAll()
        var sections: [CPListSection] = []

        let recentChannels = playlists.channels(ids: recents.channelIDs(limit: 6))
        if !recentChannels.isEmpty {
            sections.append(
                CPListSection(
                    items: makeItems(recentChannels),
                    header: "最近观看",
                    sectionIndexTitle: nil
                )
            )
        }

        let favoriteChannels = playlists.allChannels.filter { favorites.contains($0) }
        if !favoriteChannels.isEmpty {
            sections.append(
                CPListSection(
                    items: makeItems(Array(favoriteChannels.prefix(maxItemsPerSection))),
                    header: "收藏",
                    sectionIndexTitle: nil
                )
            )
        }

        let searchItem = CPListItem(
            text: "搜索频道",
            detailText: "按名称或分类查找",
            image: UIImage(systemName: "magnifyingglass")
        )
        searchItem.handler = { [weak self] _, completion in
            self?.presentSearch()
            completion()
        }
        sections.append(
            CPListSection(items: [searchItem], header: "快捷操作", sectionIndexTitle: nil)
        )

        var sectionCount = 0
        for group in playlists.groups {
            guard sectionCount < maxSections else { break }
            sectionCount += 1
            let channels = playlists.channels(inGroup: group)
            guard !channels.isEmpty else { continue }
            sections.append(
                CPListSection(
                    items: makeItems(Array(channels.prefix(maxItemsPerSection))),
                    header: group,
                    sectionIndexTitle: nil
                )
            )
        }

        if playlists.allChannels.isEmpty {
            let emptyItem = CPListItem(text: "还没有频道", detailText: "请先在 iPhone 上导入播放列表")
            sections = [CPListSection(items: [emptyItem], header: "VoyageTV", sectionIndexTitle: nil)]
        }

        rootTemplate.updateSections(sections)
    }

    private func makeItems(_ channels: [Channel]) -> [CPListItem] {
        channels.map { channel in
            registeredChannels[channel.id] = channel
            // 有节目单就显示"正在播什么"，否则退回分类名
            let detail = epg.now(on: channel)?.title ?? channel.displayGroup
            let item = CPListItem(text: channel.name, detailText: detail)
            item.userInfo = channel.id
            item.handler = { [weak self] _, completion in
                self?.playChannel(id: channel.id)
                completion()
            }
            return item
        }
    }

    // MARK: - 播放

    private func playChannel(id: String) {
        guard let channel = registeredChannels[id] ?? playlists.channel(id: id) else { return }
        // carPlayConnected 已为 true，引擎只会输出音频。
        player.play(channel)

        if interfaceController.topTemplate !== CPNowPlayingTemplate.shared {
            interfaceController.pushTemplate(CPNowPlayingTemplate.shared, animated: true, completion: nil)
        }
    }

    private func presentSearch() {
        if searchTemplate == nil {
            let template = CPSearchTemplate()
            template.delegate = self
            searchTemplate = template
        }
        guard let searchTemplate else { return }
        interfaceController.pushTemplate(searchTemplate, animated: true, completion: nil)
    }

    // MARK: - 数据变更

    private func observeLibraryChanges() {
        playlists.objectWillChange
            .merge(with: favorites.objectWillChange)
            .merge(with: recents.objectWillChange)
            .merge(with: epg.objectWillChange)
            .debounce(for: .milliseconds(80), scheduler: RunLoop.main)
            .sink { [weak self] _ in
                guard let self else { return }
                Task { @MainActor in
                    self.rebuildSections()
                }
            }
            .store(in: &cancellables)
    }

    // MARK: - CPSearchTemplateDelegate

    func searchTemplate(
        _ searchTemplate: CPSearchTemplate,
        updatedSearchText searchText: String,
        completionHandler: @escaping ([CPListItem]) -> Void
    ) {
        let results = playlists.search(searchText, limit: 60)
        completionHandler(makeItems(results))
    }

    func searchTemplate(
        _ searchTemplate: CPSearchTemplate,
        selectedResult item: CPListItem,
        completionHandler: @escaping () -> Void
    ) {
        if let id = item.userInfo as? String {
            playChannel(id: id)
        }
        completionHandler()
    }
}
