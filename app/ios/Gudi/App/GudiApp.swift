import SwiftUI
import GudiData
import GudiDomain

/// The app's entry. For now the skeleton only (stage 13.8): it proves the site's data is packed and read, and that
/// the shared logic runs. The real screens follow the approved designs (round 10 and on), one by one.
@main
struct GudiApp: App {
    init() {
        // The texts, and the language by decision 47 (a choice saved in the app wins, once the picker exists)
        if let url = Bundle.main.url(forResource: "strings", withExtension: "json", subdirectory: "Data"),
           let data = try? Data(contentsOf: url) {
            try? Strings.shared.load(data)
        }
        Strings.shared.lang = Lang.auto(Locale.preferredLanguages)
    }

    var body: some Scene {
        WindowGroup {
            SkeletonView()
                .environment(\.layoutDirection, Strings.shared.lang.rtl ? .rightToLeft : .leftToRight)
        }
    }
}

struct SkeletonView: View {
    private let runs: RunsAndLifts? = {
        guard let url = Bundle.main.url(forResource: "runs-and-lifts", withExtension: "json", subdirectory: "Data"),
              let data = try? Data(contentsOf: url) else { return nil }
        return try? RunsAndLifts.decode(data)
    }()

    var body: some View {
        VStack(spacing: 12) {
            Text("Gudi").font(.largeTitle.bold())
            if let runs {
                Text("\(runs.pistes.count) runs · \(runs.lifts.count) lifts")
                // a plural and the language: the texts are packed and typed
                Text("82 " + S.appPassDays(count: 82))
                HStack(spacing: 6) {
                    ForEach(SlopeColors.hex, id: \.self) { hex in
                        RoundedRectangle(cornerRadius: 0).fill(Color(hex: hex)).frame(width: 44, height: 12)
                    }
                }
            } else {
                Text("no data")
            }
        }
        .padding()
    }
}

extension Color {
    /// #RRGGBB
    init(hex: String) {
        let v = Int(hex.dropFirst(), radix: 16) ?? 0
        self.init(red: Double((v >> 16) & 255) / 255, green: Double((v >> 8) & 255) / 255, blue: Double(v & 255) / 255)
    }
}
