import SwiftUI
import GudiDomain

/*
 * Filling the trip without typing (Pini, 2.10.2026; Android's trip/Pickers.kt): dates and times from the iPhone's own
 * pickers in sheets, the airports from a short list with search. Only the flight number is typed, in capitals.
 */

// MARK: - Days and dates

extension Day {
    /// The phone's own calendar, Gregorian, in its time zone: the trip counts in the phone's days.
    static var calendar: Calendar {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = .current
        return c
    }

    /// Noon of this day, so a time-zone change never moves it to another day.
    var date: Date { Day.calendar.date(from: DateComponents(year: year, month: month, day: day, hour: 12))! }

    init(date: Date) {
        let c = Day.calendar.dateComponents([.year, .month, .day], from: date)
        self = Day(c.year!, c.month!, c.day!)!
    }

    static var today: Day { Day(date: Date()) }
}

extension ClockTime {
    /// Today at this time, for the wheel.
    var date: Date { Day.calendar.date(from: DateComponents(year: 2027, month: 1, day: 10, hour: hour, minute: minute))! }
    init(date: Date) {
        let c = Day.calendar.dateComponents([.hour, .minute], from: date)
        self = ClockTime(c.hour!, c.minute!)!
    }
}

extension Lang {
    /// The locale the pickers and dates speak, with the phone's region (its first day of the week).
    var locale: Locale {
        let region = Locale.current.region?.identifier
        return Locale(identifier: region.map { "\(rawValue)_\($0)" } ?? rawValue)
    }

    /// A locale with a 24-hour clock in every language (English in British form), as Android's is24Hour.
    var clock24: Locale { Locale(identifier: self == .en ? "en_GB" : rawValue) }
}

/// "Sun · 10.1.2027", in the app's language (Android's "EEE · d.M.yyyy").
func tripDayText(_ d: Day?, _ lang: Lang) -> String? {
    guard let d else { return nil }
    let f = DateFormatter()
    f.calendar = Day.calendar
    f.locale = lang.locale
    f.dateFormat = "EEE · d.M.yyyy"
    return f.string(from: d.date)
}

// MARK: - The airports

/// The name of an airport's city in the app's language, from the list's keys.
func airportCity(_ code: String) -> String {
    Airports.list.first { $0.code == code }.map { Strings.shared.text($0.city) } ?? ""
}

/// "TBS · Tbilisi", as the form saves a place from the list.
func airportPlace(_ code: String) -> String { "\(code) · \(airportCity(code))" }

// MARK: - The fields

/// A part of the form: its name in the sign voice, a rule, and a short note at the end.
struct TripSection: View {
    let title: String
    let note: String
    @Environment(\.ski) private var ski
    var body: some View {
        let c = ski.colors
        HStack(spacing: 10) {
            Text(title).font(ski.type.display(30)).foregroundStyle(c.ink).accessibilityAddTraits(.isHeader)
                .fixedSize().layoutPriority(2)
            Rectangle().fill(c.rule).frame(minWidth: 12, maxHeight: 1)
            Text(note).font(ski.type.text(12)).foregroundStyle(c.muted).lineLimit(2)
                .multilineTextAlignment(.trailing).fixedSize(horizontal: false, vertical: true).layoutPriority(1)
        }
        .padding(.top, 6)
    }
}

/// A field that opens a picker: the same look as a typed field (label, box, error), with the picker's icon, and never
/// the keyboard. With [onClear], a set value gets an X.
struct TripPickField: View {
    let label: String
    let value: String?
    let hint: String
    let icon: String
    var error: String? = nil
    var onClear: (() -> Void)? = nil
    let action: () -> Void
    @Environment(\.ski) private var ski

    var body: some View {
        let c = ski.colors
        VStack(alignment: .leading, spacing: 5) {
            Text(label).font(ski.type.text(13, .semibold)).foregroundStyle(c.muted).lineLimit(1)
            HStack(spacing: 8) {
                Button(action: action) {
                    HStack(spacing: 8) {
                        Text(value ?? hint).font(ski.type.text(16)).foregroundStyle(value == nil ? c.muted.opacity(0.75) : c.ink)
                            .lineLimit(1).minimumScaleFactor(0.7)
                        Spacer(minLength: 0)
                        if value == nil || onClear == nil {
                            Image(systemName: icon).font(.system(size: 16)).foregroundStyle(value == nil ? c.muted : c.glacier)
                        }
                    }
                    .frame(maxHeight: .infinity).contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(label + ", " + (value ?? hint))
                if value != nil, let onClear {
                    Button(action: onClear) {
                        Image(systemName: "xmark").font(.system(size: 13, weight: .semibold)).foregroundStyle(c.muted)
                            .frame(width: 40, height: 44).contentShape(Rectangle().inset(by: -2)) // a 44-wide hit area, drawn as before
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(S.appTripClear + ", " + label)
                }
            }
            .padding(.leading, 12).padding(.trailing, value != nil && onClear != nil ? 2 : 12)
            .frame(height: 48)
            .background(c.paper)
            .overlay(Rectangle().inset(by: 0.75).stroke(error != nil ? c.red : c.rule, lineWidth: 1.5))
            if let error { Text(error).font(ski.type.text(12.5)).foregroundStyle(c.red).fixedSize(horizontal: false, vertical: true) }
        }
    }
}

/// The flight number: the one thing typed, in capitals, Latin, left to right (12 characters at most).
struct TripFlightField: View {
    let label: String
    @Binding var text: String
    var showLabel = true
    var focus: FocusState<Bool>.Binding? = nil
    @Environment(\.ski) private var ski

    var body: some View {
        let c = ski.colors
        VStack(alignment: .leading, spacing: 5) {
            if showLabel { Text(label).font(ski.type.text(13, .semibold)).foregroundStyle(c.muted).lineLimit(1) }
            field
                .font(.custom("IBM Plex Sans", fixedSize: 16).weight(.semibold))
                .foregroundStyle(c.ink)
                .textInputAutocapitalization(.characters)
                .autocorrectionDisabled()
                .keyboardType(.asciiCapable)
                .submitLabel(.done)
                .onChange(of: text) { _, v in
                    let u = String(v.uppercased().prefix(12))
                    if u != v { text = u }
                }
                .padding(.horizontal, 12)
                .frame(height: 48)
                .background(c.paper)
                .overlay(Rectangle().inset(by: 0.75).stroke(c.rule, lineWidth: 1.5))
                .environment(\.layoutDirection, .leftToRight)
                .accessibilityLabel(label)
        }
    }

    @ViewBuilder private var field: some View {
        let prompt = Text(S.appTripFlightHint).foregroundColor(ski.colors.muted.opacity(0.75))
        if let focus { TextField(label, text: $text, prompt: prompt).focused(focus) } else { TextField(label, text: $text, prompt: prompt) }
    }
}

/// The page's main button (the site's .btn-primary): accent, square, 52 high.
struct TripButton: View {
    let title: String
    var icon: String? = nil
    var enabled = true
    let action: () -> Void
    @Environment(\.ski) private var ski
    var body: some View {
        let c = ski.colors
        Button(action: action) {
            HStack(spacing: 8) {
                if let icon { Image(systemName: icon).font(.system(size: 16, weight: .bold)).accessibilityHidden(true) }
                Text(title).font(ski.type.text(16, .bold))
            }
            .foregroundStyle(Color(rgb: c.p.onAccent))
            .frame(maxWidth: .infinity, minHeight: 52)
            .background(c.accent)
            .opacity(enabled ? 1 : 0.5)
        }
        .buttonStyle(.plain)
    }
}

/// A quiet button under it (delete), red once armed.
struct TripQuietButton: View {
    let title: String
    var danger = false
    let action: () -> Void
    @Environment(\.ski) private var ski
    var body: some View {
        let c = ski.colors
        Button(action: action) {
            Text(title).font(ski.type.text(15, .bold)).foregroundStyle(danger ? c.red : c.ink)
                .frame(maxWidth: .infinity, minHeight: 48)
                .overlay(Rectangle().inset(by: 0.75).stroke(danger ? c.red : c.rule, lineWidth: 1.5))
        }
        .buttonStyle(.plain)
    }
}

// MARK: - The sheets

/// A sheet's head: what it fills, in the sign voice, and a close button.
struct TripSheetHead: View {
    let title: String
    var sub: String? = nil
    @Environment(\.ski) private var ski
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        let c = ski.colors
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 2) {
                if let sub { Text(sub).font(ski.type.text(13, .semibold)).foregroundStyle(c.muted) }
                Text(title).font(ski.type.display(32)).foregroundStyle(c.ink).lineLimit(1).minimumScaleFactor(0.6)
                    .accessibilityAddTraits(.isHeader)
            }
            Spacer()
            Button { dismiss() } label: {
                Image(systemName: "xmark").font(.system(size: 17, weight: .semibold)).foregroundStyle(c.ink).frame(width: 44, height: 44)
            }
            .accessibilityLabel(S.commonClose)
        }
    }
}

/// A day from the iPhone's calendar, between [min] and [max]; [title] says which day this is ("Outbound · Date"), and
/// the chosen day shows big above the month.
struct TripDateSheet: View {
    let title: String
    let initial: Day?
    let open: Day?
    let min: Day?
    let max: Day?
    let onPick: (Day) -> Void
    @Environment(\.ski) private var ski
    @Environment(\.dismiss) private var dismiss
    @State private var chosen = Date()

    var body: some View {
        let c = ski.colors
        let lang = ski.type.lang
        let lo = min?.date ?? Date.distantPast
        let hi = Swift.max(max?.date ?? Date.distantFuture, lo)
        VStack(alignment: .leading, spacing: 10) {
            TripSheetHead(title: tripDayText(Day(date: chosen), lang) ?? S.appTripPickDate, sub: title)
            DatePicker("", selection: $chosen, in: lo...hi, displayedComponents: .date)
                .datePickerStyle(.graphical)
                .labelsHidden()
                .tint(c.accent)
                .environment(\.locale, lang.locale)
                .environment(\.calendar, Day.calendar)
            TripButton(title: S.appPickOk) { onPick(Day(date: chosen)); dismiss() }
        }
        .padding(16)
        .background(c.snow)
        .onAppear { chosen = (initial ?? open ?? min ?? Day.today).date }
    }
}

/// A time from the iPhone's wheel, in 24 hours; [title] says which time this is ("Outbound · Departs").
struct TripTimeSheet: View {
    let title: String
    let initial: ClockTime?
    let onPick: (ClockTime) -> Void
    @Environment(\.ski) private var ski
    @Environment(\.dismiss) private var dismiss
    @State private var at = ClockTime.noon.date

    var body: some View {
        let c = ski.colors
        VStack(alignment: .leading, spacing: 8) {
            TripSheetHead(title: ClockTime(date: at).text, sub: title)
            DatePicker("", selection: $at, displayedComponents: .hourAndMinute)
                .datePickerStyle(.wheel)
                .labelsHidden()
                .frame(maxWidth: .infinity)
                .environment(\.locale, ski.type.lang.clock24)
                .environment(\.calendar, Day.calendar)
                .environment(\.layoutDirection, .leftToRight)
            TripButton(title: S.appPickOk) { onPick(ClockTime(date: at)); dismiss() }
        }
        .padding(16)
        .background(c.snow)
        .onAppear { at = (initial ?? .noon).date }
    }
}

/// The airport, from the short list (with search), or another three-letter code typed in the search: the same rule as
/// the site (K-5, decision 62; its trip.bad_code). Writes "TBS · Tbilisi", or the code alone.
struct TripPlaceSheet: View {
    let title: String
    let current: String
    let first: [String]
    let onPick: (String) -> Void
    @Environment(\.ski) private var ski
    @Environment(\.dismiss) private var dismiss
    @State private var q = ""
    @FocusState private var searching: Bool

    var body: some View {
        let c = ski.colors
        let shown = Array(Airports.shown(first: first, query: q, city: airportCity).prefix(8))
        VStack(alignment: .leading, spacing: 10) {
            TripSheetHead(title: title)
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass").foregroundStyle(c.muted).accessibilityHidden(true)
                TextField(S.appTripPlaceSearch, text: $q, prompt: Text(S.appTripPlaceSearch).foregroundColor(c.muted.opacity(0.75)))
                    .font(ski.type.text(16)).foregroundStyle(c.ink)
                    .autocorrectionDisabled().focused($searching)
                    .onChange(of: q) { _, v in if v.count > 40 { q = String(v.prefix(40)) } }
            }
            .padding(.horizontal, 12).frame(height: 48)
            .background(c.paper)
            .overlay(Rectangle().inset(by: 0.75).stroke(c.rule, lineWidth: 1.5))
            ScrollView {
                VStack(spacing: 0) {
                    ForEach(shown, id: \.self) { code in
                        let on = current.hasPrefix(code)
                        Button { pick(airportPlace(code)) } label: {
                            HStack(spacing: 14) {
                                TripPlaceCode(code: code, color: c.ink)
                                Text(airportCity(code)).font(ski.type.text(16)).foregroundStyle(c.ink)
                                Spacer()
                                if on { Image(systemName: "checkmark").font(.system(size: 16, weight: .bold)).foregroundStyle(c.glacier) }
                            }
                            .padding(.horizontal, 12).frame(minHeight: 52)
                            .background(on ? Color(rgb: c.p.grid) : c.paper)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityAddTraits(on ? .isSelected : [])
                        Rectangle().fill(c.rule).frame(height: 1)
                    }
                    // another airport by its code (the site's app.trip_place_other, shared)
                    if let code = Airports.code(q), !shown.contains(code) {
                        Button { pick(code) } label: {
                            HStack(spacing: 14) {
                                TripPlaceCode(code: code, color: c.glacier)
                                Text(S.appTripPlaceOther(text: code)).font(ski.type.text(15, .bold)).foregroundStyle(c.glacier)
                                Spacer()
                                Image(systemName: "plus").font(.system(size: 16, weight: .bold)).foregroundStyle(c.glacier).accessibilityHidden(true)
                            }
                            .padding(.horizontal, 12).frame(minHeight: 52).contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
        .padding(16)
        .background(c.snow)
        // as the site's sheet: the cursor in the search once the sheet is up
        .onAppear { searching = UserDefaults.standard.string(forKey: "qaRoute") == nil }
    }

    private func pick(_ place: String) {
        Fx.shared.tick(0.4)
        onPick(place)
        dismiss()
    }
}

/// An airport code in the display face, Latin, left to right.
struct TripPlaceCode: View {
    let code: String
    let color: Color
    var body: some View {
        Text(code).font(.custom("Karantina-Bold", fixedSize: 28)).foregroundStyle(color)
            .frame(width: 56, alignment: .leading)
            .environment(\.layoutDirection, .leftToRight)
    }
}

/// The flight number, its own small sheet (round 20): typed in capitals, saved as it is (empty clears it).
struct TripFlightSheet: View {
    let current: String
    let onSave: (String) -> Void
    @Environment(\.ski) private var ski
    @Environment(\.dismiss) private var dismiss
    @State private var f = ""
    @FocusState private var focus: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            TripSheetHead(title: S.appTripFlightNo)
            TripFlightField(label: S.appTripFlightNo, text: $f, showLabel: false, focus: $focus)
            TripButton(title: S.appSave) { onSave(f.trimmingCharacters(in: .whitespaces)); dismiss() }
            Spacer(minLength: 0)
        }
        .padding(16)
        .background(ski.colors.snow)
        .onAppear { f = current; focus = true }
    }
}
