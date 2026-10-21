import XCTest
@testable import GudiDomain

/// The texts read the way tools/build-ios-strings.py packs them, from the real i18n/strings.json.
final class StringsTests: XCTestCase {
    private func shared() throws -> Strings {
        let raw = try JSONSerialization.jsonObject(with: Data(contentsOf: Repo.root.appendingPathComponent("i18n/strings.json"))) as! [String: Any]
        var table: [String: [String: Any]] = [:]
        for (k, v) in raw["strings"] as! [String: [String: Any]] { table[k] = v.filter { Lang(rawValue: $0.key) != nil } }
        let s = Strings()
        try s.load(JSONSerialization.data(withJSONObject: table))
        return s
    }

    func testDecision47() {
        XCTAssertEqual(Lang.auto(["he-IL", "en-US"]), .he)
        XCTAssertEqual(Lang.auto(["iw"]), .he)
        XCTAssertEqual(Lang.auto(["ru-RU"]), .en)
        XCTAssertEqual(Lang.auto(["ka-GE", "he"]), .en)
        XCTAssertEqual(Lang.auto([]), .en)
    }

    func testPluralFormsAreTheOnesInTheFile() throws {
        let meta = (try JSONSerialization.jsonObject(with: Data(contentsOf: Repo.root.appendingPathComponent("i18n/strings.json"))) as! [String: Any])["meta"] as! [String: Any]
        let forms = meta["plural_forms"] as! [String: [String]]
        for lang in Lang.allCases {
            let used = Set((0...130).map(lang.plural))
            XCTAssertTrue(used.isSubset(of: Set(forms[lang.rawValue]!)), "\(lang)")
        }
        XCTAssertEqual([1, 2, 5, 11, 21, 22, 25, 112].map(Lang.ru.plural), ["one", "few", "many", "many", "one", "few", "many", "many"])
        XCTAssertEqual([1, 2, 3].map(Lang.he.plural), ["one", "two", "other"])
    }

    func testTextsPlaceholdersAndPlurals() throws {
        let s = try shared()
        s.lang = .en
        XCTAssertEqual(s.text("app.pass_days", count: 1), "day to go")
        XCTAssertEqual(s.text("app.pass_days", count: 5), "days to go")
        let yt = s.text("run.videos_search_youtube", ["name": "Tatra 2", "resort": "Gudauri"])
        XCTAssertTrue(yt.contains("Tatra 2") && yt.contains("Gudauri") && !yt.contains("{"), yt)
        s.lang = .ru
        XCTAssertEqual(s.text("app.pass_days", count: 3), "дня до вылета")
        XCTAssertEqual(s.text("no.such.key"), "no.such.key")
    }

    func testEveryPlaceholderIsTheSameInEveryLanguage() throws {
        let raw = (try JSONSerialization.jsonObject(with: Data(contentsOf: Repo.root.appendingPathComponent("i18n/strings.json"))) as! [String: Any])["strings"] as! [String: [String: Any]]
        let ph = try NSRegularExpression(pattern: "\\{([a-zA-Z_][a-zA-Z0-9_]*)\\}")
        func names(_ v: Any?) -> Set<String> {
            let texts = (v as? [String: String]).map { Array($0.values) } ?? [(v as? String) ?? ""]
            var out = Set<String>()
            for t in texts { for m in ph.matches(in: t, range: NSRange(t.startIndex..., in: t)) { out.insert(String(t[Range(m.range(at: 1), in: t)!])) } }
            return out
        }
        for (key, e) in raw {
            let he = names(e["he"])
            for lang in ["en", "ru", "ka"] { XCTAssertEqual(names(e[lang]).subtracting(["n"]), he.subtracting(["n"]), "\(key) \(lang)") }
        }
    }
}
