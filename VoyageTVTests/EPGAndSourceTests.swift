import XCTest
@testable import VoyageTV

final class XMLTVParserTests: XCTestCase {

    private let sample = """
    <?xml version="1.0" encoding="UTF-8"?>
    <tv>
      <channel id="cctv1"><display-name>CCTV-1 综合</display-name></channel>
      <channel id="cctv2"><display-name lang="zh">CCTV-2 财经</display-name></channel>
      <programme start="20261116080000 +0000" stop="20261116090000 +0000" channel="cctv1">
        <title>早间新闻</title>
        <desc>国内国际要闻</desc>
      </programme>
      <programme start="20261116090000 +0000" stop="20261116100000 +0000" channel="cctv1">
        <title lang="zh">生活圈</title>
        <sub-title>第 3 期</sub-title>
      </programme>
      <programme start="20261116083000 +0000" stop="20261116100000 +0000" channel="cctv2">
        <title>财经早报</title>
      </programme>
    </tv>
    """

    func testParsesChannelsAndProgrammes() {
        let data = Data(sample.utf8)
        let result = XMLTVParser.parse(data)

        XCTAssertEqual(result.programs.count, 2)
        XCTAssertEqual(result.programs["cctv1"]?.count, 2)
        XCTAssertEqual(result.programs["cctv1"]?.first?.title, "早间新闻")
        XCTAssertEqual(result.programs["cctv1"]?.first?.detail, "国内国际要闻")
        XCTAssertEqual(result.displayNames["cctv1"], "CCTV-1 综合")
        XCTAssertEqual(result.displayNames["cctv2"], "CCTV-2 财经")
    }

    func testSubTitleCaptured() {
        let result = XMLTVParser.parse(Data(sample.utf8))
        XCTAssertEqual(result.programs["cctv1"]?.last?.subtitle, "第 3 期")
    }

    func testInvalidProgrammeWithoutDatesIgnored() {
        let xml = """
        <tv>
          <programme channel="x"><title>没有时间的节目</title></programme>
        </tv>
        """
        let result = XMLTVParser.parse(Data(xml.utf8))
        XCTAssertTrue(result.programs.isEmpty)
    }

    func testProgrammeWithStopBeforeStartIgnored() {
        let xml = """
        <tv>
          <programme start="20261116100000 +0000" stop="20261116090000 +0000" channel="x">
            <title>时间反了</title>
          </programme>
        </tv>
        """
        let result = XMLTVParser.parse(Data(xml.utf8))
        XCTAssertTrue(result.programs.isEmpty)
    }
}

final class XtreamCredentialsTests: XCTestCase {

    func testNormalizesPlayerAPIPath() {
        let credentials = XtreamCredentials(
            baseURL: "http://example.com:8080/player_api.php",
            username: "u",
            password: "p"
        )
        XCTAssertEqual(credentials.normalizedBase, "http://example.com:8080")
    }

    func testTrimsTrailingSlash() {
        let credentials = XtreamCredentials(
            baseURL: "  http://example.com:8080/  ",
            username: "u",
            password: "p"
        )
        XCTAssertEqual(credentials.normalizedBase, "http://example.com:8080")
    }
}

final class ChannelMatchingTests: XCTestCase {

    private func makeChannel(name: String, group: String = "新闻", tvgName: String? = nil) -> Channel {
        Channel(
            id: StableID.channel(URL(string: "https://example.com/x.m3u8")!, name: name),
            name: name,
            streamURL: URL(string: "https://example.com/x.m3u8")!,
            group: group,
            tvgName: tvgName
        )
    }

    func testSearchIgnoresCaseAndWidth() {
        let channel = makeChannel(name: "CCTV-1 综合", group: "央视")
        XCTAssertTrue(channel.matches("cctv"))
        XCTAssertTrue(channel.matches("ＣＣＴＶ"))
        XCTAssertTrue(channel.matches("央视"))
        XCTAssertFalse(channel.matches("凤凰"))
    }

    func testEmptyQueryMatchesEverything() {
        let channel = makeChannel(name: "任意频道")
        XCTAssertTrue(channel.matches("   "))
    }

    func testStableIDIsDeterministic() {
        let first = StableID.channel(URL(string: "https://example.com/a.m3u8")!, name: "A")
        let second = StableID.channel(URL(string: "https://example.com/a.m3u8")!, name: "A")
        XCTAssertEqual(first, second)
        let different = StableID.channel(URL(string: "https://example.com/b.m3u8")!, name: "A")
        XCTAssertNotEqual(first, different)
    }
}
