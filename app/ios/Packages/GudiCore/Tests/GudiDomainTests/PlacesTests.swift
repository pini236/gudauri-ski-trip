import XCTest
@testable import GudiDomain

final class PlacesTests: XCTestCase {
    private func site() throws -> Places {
        let data = try Data(contentsOf: Repo.root.appendingPathComponent("site/data/places.json"))
        return try XCTUnwrap(Places.parse(data))
    }

    func testReadsTheSitesFile() throws {
        let p = try site()
        XCTAssertEqual(p.stations.count, 21)
        XCTAssertEqual(p.places.count, 32)
        XCTAssertEqual(p.withPlaces().count, 12) // the others say "none": no information
    }

    func testAStationHasItsPlacesNearestFirst() throws {
        let p = try site()
        let bottom = try XCTUnwrap(p.stationOf(liftId: "158744075", bottom: true)) // Goodaura, lower end
        let near = p.near(bottom)
        XCTAssertEqual(near.count, 13)
        XCTAssertEqual(near.map(\.d), near.map(\.d).sorted())
        // the top end of the same lift is another station
        XCTAssertNotEqual(p.stationOf(liftId: "158744075", bottom: false)?.id, bottom.id)
    }

    func testNoInformationIsNotNothingHere() throws {
        let p = try site()
        let none = try XCTUnwrap(p.stations.first { $0.info == "none" })
        XCTAssertTrue(p.near(none).isEmpty)
        XCTAssertFalse(p.withPlaces().contains { $0.station.id == none.id })
    }

    func testTheKindsComeInTheOrderOfTheDots() throws {
        let p = try site()
        for (_, kinds) in p.withPlaces() { XCTAssertEqual(kinds, Places.kinds.filter(kinds.contains)) }
    }

    func testTwoCafesOnTheSlopeBelongToNoStation() throws {
        XCTAssertEqual(try site().onSlope().map(\.name), ["2300", "Soliko"])
    }

    func testAnotherSchemaIsNotUsed() {
        XCTAssertNil(Places.parse(Data(#"{"schema":2,"places":[],"stations":[]}"#.utf8)))
        XCTAssertNil(Places.parse(Data("not json".utf8)))
    }

    func testAKindThisAppDoesNotKnowIsIgnored() throws {
        let json = #"""
        {"schema":1,"places":[{"id":"a","kind":"spa","name":"X","ll":[42.4,44.4]},{"id":"b","kind":"wc","ll":[42.4,44.4],"name":""}],
         "stations":[{"id":"s","ll":[42.4,44.4],"ends":[{"liftId":7,"end":"top"}],"info":"some","places":[{"p":"a","d":10},{"p":"b","d":20}]}]}
        """#
        let p = try XCTUnwrap(Places.parse(Data(json.utf8)))
        let near = p.near(p.stations[0])
        XCTAssertEqual(near.map(\.place.id), ["b"])
        XCTAssertNil(near[0].place.name) // a blank name is no name: the kind is the label
        XCTAssertNotNil(p.stationOf(liftId: "7", bottom: false))
    }
}
