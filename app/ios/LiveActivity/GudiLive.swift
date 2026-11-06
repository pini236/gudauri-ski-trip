import WidgetKit
import SwiftUI
import ActivityKit

/// The Live Activity's extension (1.1, the dayrec build only): the lock screen and the Dynamic Island while a ski day records.
@main
struct GudiLiveBundle: WidgetBundle {
    var body: some Widget { DayLiveWidget() }
}

struct DayLiveWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: DayLiveAttributes.self) { ctx in
            LockScreenView(ctx: ctx)
                .padding(16)
                .activityBackgroundTint(Color(red: 0.05, green: 0.08, blue: 0.13))
                .activitySystemActionForegroundColor(.white)
        } dynamicIsland: { ctx in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) { Big(ctx.state.speedKmh, "km/h") }
                DynamicIslandExpandedRegion(.trailing) { Big(ctx.state.maxKmh, "max") }
                DynamicIslandExpandedRegion(.bottom) {
                    HStack {
                        Text(timerInterval: ctx.attributes.startedAt...Date.distantFuture, countsDown: false).monospacedDigit()
                        Spacer()
                        Text(String(format: "%.1f km", ctx.state.distanceKm)).monospacedDigit()
                    }
                    .font(.system(size: 15, weight: .semibold))
                }
            } compactLeading: {
                Text("\(ctx.state.speedKmh)").monospacedDigit().font(.system(size: 15, weight: .bold))
            } compactTrailing: {
                Image(systemName: "figure.skiing.downhill")
            } minimal: {
                Image(systemName: "figure.skiing.downhill")
            }
        }
    }
}

private struct LockScreenView: View {
    let ctx: ActivityViewContext<DayLiveAttributes>
    var body: some View {
        HStack(alignment: .center, spacing: 16) {
            Image(systemName: "figure.skiing.downhill").font(.system(size: 26)).foregroundStyle(.white)
            Big(ctx.state.speedKmh, "km/h")
            Big(ctx.state.maxKmh, "max")
            Spacer(minLength: 0)
            VStack(alignment: .trailing, spacing: 2) {
                Text(timerInterval: ctx.attributes.startedAt...Date.distantFuture, countsDown: false).monospacedDigit()
                Text(String(format: "%.1f km", ctx.state.distanceKm)).monospacedDigit()
            }
            .font(.system(size: 15, weight: .semibold))
            .foregroundStyle(.white)
        }
    }
}

private struct Big: View {
    let value: Int, unit: String
    init(_ value: Int, _ unit: String) { self.value = value; self.unit = unit }
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("\(value)").font(.system(size: 30, weight: .bold)).monospacedDigit()
            Text(unit).font(.system(size: 12, weight: .semibold)).opacity(0.7)
        }
        .foregroundStyle(.white)
    }
}
