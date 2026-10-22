import Foundation
import GudiDomain

/// The descent's words, from the app's texts (i18n/strings.json).
enum DescentText {
    static func word(_ w: DescentWord) -> String {
        switch w {
        case .popPerfect: S.gameDescentPopPerfect
        case .popLeftBehind: S.gameDescentPopLeftBehind
        case .popOuch: S.gameDescentPopOuch
        case .popGullySwallowed: S.gameDescentPopGullySwallowed
        case .popFellGully: S.gameDescentPopFellGully
        case .popCleanLanding: S.gameDescentPopCleanLanding
        case .popOverSnowcat: S.gameDescentPopOverSnowcat
        case .popJoined: S.gameDescentPopJoined
        case .popFlips(let n, let mult): S.gameDescentPopFlips(count: n) + "!" + (mult > 1 ? " ×\(mult)" : "")
        case .crashRock: S.gameDescentCrashRock
        case .crashSnowcat: S.gameDescentCrashSnowcat
        case .coachHoldNow: S.gameDescentCoachHoldNow
        case .coachReleaseLine: S.gameDescentCoachReleaseLine
        case .coachAirFlip: S.gameDescentCoachAirFlip
        case .coachWind: S.gameDescentCoachWind
        case .bannerGully: S.gameDescentBannerGully
        case .bannerGullyText: S.gameDescentBannerGullyText
        case .bannerWind: S.gameDescentBannerWind
        case .bannerWindText: S.gameDescentBannerWindText
        case .bannerSnowcat: S.gameDescentBannerSnowcat
        case .bannerSnowcatText: S.gameDescentBannerSnowcatText
        case .bannerPowderText: S.gameDescentBannerPowderText
        case .bannerIceText: S.gameDescentBannerIceText
        case .bannerDog: S.gameDescentBannerDog
        case .powder: S.gameDescentPowder
        case .ice: S.gameDescentIce
        case .liftName(let l): S.gameDescentLiftName(lift: l)
        case .finishPop: S.gameDescentFinishPop
        case .signGully: S.gameDescentSignGully
        case .signSnowcat: S.gameDescentSignSnowcat
        case .mission(let k): mission(k) ?? ""
        }
    }

    /// The third star's words for a run (the site's MISSION).
    static func mission(_ key: String) -> String? {
        switch key {
        case "Pirveli": S.gameDescentMissionPirveli
        case "Kikilo 1": S.gameDescentMissionKikilo1
        case "Tatra 2": S.gameDescentMissionTatra2
        case "Sadzele 3": S.gameDescentMissionSadzele3
        case "Sadzele 1": S.gameDescentMissionSadzele1
        default: nil
        }
    }

    /// A time as the site's fmt(): m:ss.s.
    static func time(_ s: Float) -> String {
        let m = Int((s / 60).rounded(.down)); let r = s - Float(m) * 60
        return "\(m):" + (r < 10 ? "0" : "") + String(format: "%.1f", locale: Locale(identifier: "en_US"), r)
    }

    /// A whole number with the site's grouping (1,140).
    static func n(_ v: Int) -> String { v.formatted(.number.grouping(.automatic).locale(Locale(identifier: "en_US"))) }
}
