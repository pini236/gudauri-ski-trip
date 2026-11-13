import SwiftUI
import GudiDomain

#if DAYREC
/// "הסיפור של היום" (round 29, the insights): a head for the kind of day over its numbers, up to three insights
/// against the skier's own days, and one idea for tomorrow. Android's `dayrec/ui/Story.kt`.
/// The words come from the language file; no position, no name of anyone else, and no speed as a goal.
struct StoryCard: View {
    let story: DayRec.Moments
    let summary: DayRec.DayModel.Summary
    var sample = false
    var name: (String) -> String = { $0 }

    @Environment(\.ski) private var ski

    /// The story's moments, in its order; a fall and an off-piste stay with the skier and are not part of the card.
    private var cards: [DayRec.Moment] {
        story.story.compactMap { story.items.indices.contains($0) ? story.items[$0] : nil }
            .filter { $0.key != "fall" && $0.key != "offpiste" }
    }

    private var head: MomentText.Plan {
        MomentText.head(theme: story.theme, descents: summary.descents, dropM: summary.dropM, distM: summary.distM)
    }

    var body: some View {
        let c = ski.colors
        let headWords = MomentText.render(head, seed: DayRec.seed("story.head", summary.start))
        VStack(alignment: .leading, spacing: 12) {
            if let title = headWords.title { Text(title).font(ski.type.display(32)).foregroundStyle(c.ink) }
            ForEach(Array(headWords.lines.enumerated()), id: \.offset) { _, line in
                Text(line).font(ski.type.text(13)).foregroundStyle(c.muted)
            }
            ForEach(Array(cards.enumerated()), id: \.offset) { _, m in insight(m) }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(c.paper)
        .overlay(Rectangle().strokeBorder(c.rule, lineWidth: 1))
        .onAppear {
            guard !sample else { return }
            let n = min(3, cards.filter { $0.key != "tomorrow" && $0.key != "trip" }.count)
            Telemetry.shared.event("story_view", ["moments": n, "surface": "end_of_day"])
        }
    }

    @ViewBuilder private func insight(_ m: DayRec.Moment) -> some View {
        let c = ski.colors
        if let plan = MomentText.plan(m, zone: tbilisi, name: name) {
            let words = MomentText.render(plan, seed: m.seed)
            if words.title != nil || !words.lines.isEmpty {
                VStack(alignment: .leading, spacing: 2) {
                    if let t = words.title {
                        // tomorrow's idea is quieter than the day itself
                        Text(t).font(ski.type.text(16, .bold)).foregroundStyle(m.key == "tomorrow" ? c.muted : c.ink)
                    }
                    ForEach(Array(words.lines.enumerated()), id: \.offset) { _, line in
                        Text(line).font(ski.type.text(16)).foregroundStyle(c.ink)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.top, 4)
            }
        }
    }
}
#endif
