import SwiftUI
import UniformTypeIdentifiers

/// 添加订阅：M3U 链接 / Xtream Codes / 粘贴文本 / 本地文件。
struct AddPlaylistView: View {
    @EnvironmentObject private var playlists: PlaylistStore
    @Environment(\.dismiss) private var dismiss

    enum Mode: String, CaseIterable, Identifiable {
        case m3u
        case xtream
        case paste
        case file

        var id: String { rawValue }

        var label: String {
            switch self {
            case .m3u: return "M3U 链接"
            case .xtream: return "Xtream"
            case .paste: return "粘贴"
            case .file: return "文件"
            }
        }
    }

    @State private var mode: Mode = .m3u

    @State private var urlText = ""
    @State private var m3uName = ""

    @State private var serverText = ""
    @State private var usernameText = ""
    @State private var passwordText = ""
    @State private var xtreamName = ""

    @State private var pastedText = ""

    @State private var isWorking = false
    @State private var errorMessage: String?
    @State private var showFileImporter = false

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Picker("方式", selection: $mode) {
                        ForEach(Mode.allCases) { item in
                            Text(item.label).tag(item)
                        }
                    }
                    .pickerStyle(.segmented)
                    .disabled(isWorking)
                }

                switch mode {
                case .m3u: m3uSection
                case .xtream: xtreamSection
                case .paste: pasteSection
                case .file: fileSection
                }

                if let errorMessage {
                    Section {
                        Text(errorMessage)
                            .font(.footnote)
                            .foregroundStyle(.red)
                    }
                }

                if isWorking {
                    Section {
                        HStack(spacing: 10) {
                            ProgressView().controlSize(.small)
                            Text(mode == .xtream ? "正在登录并拉取频道…" : "正在导入…")
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }
            .navigationTitle("添加订阅")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("取消") { dismiss() }
                        .disabled(isWorking)
                }
            }
            .fileImporter(
                isPresented: $showFileImporter,
                allowedContentTypes: importableTypes,
                allowsMultipleSelection: false
            ) { result in
                handleFileResult(result)
            }
            .interactiveDismissDisabled(isWorking)
        }
    }

    // MARK: - 各模式

    private var m3uSection: some View {
        Section {
            TextField("https://example.com/list.m3u", text: $urlText)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .keyboardType(.URL)
                .disabled(isWorking)

            TextField("订阅名称（可留空）", text: $m3uName)
                .disabled(isWorking)

            Button {
                importFromURL()
            } label: {
                Text("导入")
            }
            .disabled(isWorking || urlText.trimmed.isEmpty)
        } header: {
            Text("M3U / M3U8 链接")
        } footer: {
            Text("导入后可随时在「设置 → 订阅」里手动刷新。直接填一个 .m3u8 直播地址会作为单流频道加入。")
        }
    }

    private var xtreamSection: some View {
        Section {
            TextField("http://host:8080", text: $serverText)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .keyboardType(.URL)
                .disabled(isWorking)

            TextField("用户名", text: $usernameText)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .disabled(isWorking)

            SecureField("密码", text: $passwordText)
                .disabled(isWorking)

            TextField("订阅名称（可留空）", text: $xtreamName)
                .disabled(isWorking)

            Button {
                importXtream()
            } label: {
                Text("登录并导入")
            }
            .disabled(isWorking || serverText.trimmed.isEmpty || usernameText.trimmed.isEmpty)
        } header: {
            Text("Xtream Codes")
        } footer: {
            Text("填服务器根地址即可（会自动去掉结尾的 player_api.php）。频道按服务端分类归组。")
        }
    }

    private var pasteSection: some View {
        Section {
            TextEditor(text: $pastedText)
                .frame(minHeight: 150)
                .font(.system(.footnote, design: .monospaced))
                .disabled(isWorking)

            Button {
                importFromPaste()
            } label: {
                Text("导入粘贴的内容")
            }
            .disabled(isWorking || pastedText.trimmed.isEmpty)
        } header: {
            Text("粘贴 M3U 文本")
        } footer: {
            Text("以 #EXTM3U 开头的内容会被识别为频道表。粘贴导入的内容无法在线刷新。")
        }
    }

    private var fileSection: some View {
        Section {
            Button {
                showFileImporter = true
            } label: {
                Label("选择本地文件", systemImage: "folder")
            }
            .disabled(isWorking)
        } header: {
            Text("本地文件")
        } footer: {
            Text("支持 .m3u / .m3u8 / .txt。")
        }
    }

    private var importableTypes: [UTType] {
        var types: [UTType] = [.plainText]
        for ext in ["m3u", "m3u8", "txt"] {
            if let type = UTType(filenameExtension: ext) {
                types.append(type)
            }
        }
        return types
    }

    // MARK: - 动作

    private func importFromURL() {
        errorMessage = nil
        isWorking = true
        let url = urlText
        let name = m3uName
        Task {
            defer { isWorking = false }
            do {
                try await PlaylistImporter.importFromURLString(
                    url,
                    name: name.trimmed.isEmpty ? nil : name.trimmed,
                    into: playlists
                )
                dismiss()
            } catch {
                errorMessage = error.localizedDescription
            }
        }
    }

    private func importXtream() {
        errorMessage = nil
        isWorking = true
        let server = serverText
        let user = usernameText
        let pass = passwordText
        let name = xtreamName
        Task {
            defer { isWorking = false }
            do {
                try await PlaylistImporter.importXtream(
                    server: server,
                    username: user,
                    password: pass,
                    name: name,
                    into: playlists
                )
                dismiss()
            } catch {
                errorMessage = error.localizedDescription
            }
        }
    }

    private func importFromPaste() {
        errorMessage = nil
        isWorking = true
        defer { isWorking = false }
        do {
            try PlaylistImporter.importFromText(pastedText, into: playlists)
            dismiss()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func handleFileResult(_ result: Result<[URL], Error>) {
        errorMessage = nil
        switch result {
        case .success(let urls):
            guard let url = urls.first else { return }
            do {
                try PlaylistImporter.importFromFile(url, into: playlists)
                dismiss()
            } catch {
                errorMessage = error.localizedDescription
            }
        case .failure(let error):
            errorMessage = error.localizedDescription
        }
    }
}
