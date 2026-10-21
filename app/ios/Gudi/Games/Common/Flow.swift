import SwiftUI

/// Rows that wrap (Compose's FlowRow): children left to right in the reading direction, each row centred.
struct Flow: Layout {
    var spacing: CGFloat = 4
    var lineSpacing: CGFloat = 4

    private func rows(_ sizes: [CGSize], _ width: CGFloat) -> [[Int]] {
        var rows: [[Int]] = [[]]
        var x: CGFloat = 0
        for (i, s) in sizes.enumerated() {
            if !rows[rows.count - 1].isEmpty && x + spacing + s.width > width { rows.append([]); x = 0 }
            x += (rows[rows.count - 1].isEmpty ? 0 : spacing) + s.width
            rows[rows.count - 1].append(i)
        }
        return rows
    }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let w = proposal.width ?? 400
        let sizes = subviews.map { $0.sizeThatFits(.unspecified) }
        let r = rows(sizes, w)
        let h = r.reduce(CGFloat(0)) { $0 + ($1.map { sizes[$0].height }.max() ?? 0) } + CGFloat(max(0, r.count - 1)) * lineSpacing
        return CGSize(width: w, height: h)
    }

    func placeSubviews(in b: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let sizes = subviews.map { $0.sizeThatFits(.unspecified) }
        var y = b.minY
        for row in rows(sizes, b.width) {
            let rw = row.reduce(CGFloat(0)) { $0 + sizes[$1].width } + CGFloat(max(0, row.count - 1)) * spacing
            let rh = row.map { sizes[$0].height }.max() ?? 0
            var x = b.minX + (b.width - rw) / 2
            for i in row { subviews[i].place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(sizes[i])); x += sizes[i].width + spacing }
            y += rh + lineSpacing
        }
    }
}
