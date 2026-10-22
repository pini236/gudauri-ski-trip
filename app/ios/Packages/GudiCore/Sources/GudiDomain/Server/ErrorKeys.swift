/// The words for every refusal a person can meet (server/CONTRACT.md, "שגיאות", ד5): the key in i18n/strings.json of
/// each code's own message, the site's (group.err_*) where it fits, as Android's errorRes. A code the app does not know
/// reads as the general message. The test reads the contract and fails on a code without a message here.
public enum ErrorKeys {
    public static let general = "app.err_general"

    public static let byCode: [String: String] = [
        "not_admin": "group.err_not_admin",
        "last_admin": "group.err_last_admin",
        "admin_must_register": "group.err_admin_must_register",
        "must_register": "app.err_not_registered",
        "not_registered": "app.err_not_registered",
        "not_a_guest": "app.err_not_a_guest",
        "not_signed_in": "app.err_not_signed_in",
        "no_session": "app.err_not_signed_in",
        "unknown_action": "app.err_update_app",
        "too_many_groups": "app.err_too_many_groups",
        "group_full": "app.g_full",
        "already_member": "app.err_already_member",
        "not_member": "app.g_gone",
        "sign_in_instead": "app.g_sign_in_instead",
        "group_has_admin": "app.err_group_has_admin",
        "use_leave_group": "app.err_use_leave_group",
        "trip_not_in_group": "app.err_trip_not_in_group",
        "invalid_ticket": "app.err_invalid_ticket",
        "member_owns_trip": "group.err_member_owns_trip",
        "request_closed": "group.err_request_closed",
        "member_gone": "group.err_member_gone",
        "too_many_trips": "group.err_too_many_trips",
        "too_many_meetups": "group.err_too_many_meetups",
        "trip_not_yours": "group.err_trip_not_yours",
        "invalid_name": "group.err_invalid_name",
        "invalid_input": "group.err_invalid_input",
        "not_allowed": "group.err_not_allowed",
        "not_found": "app.err_not_found",
        "conflict": "app.err_conflict",
        "server_error": "group.err_server_error",
        "rate_limited": "join.st_rate_limited",
        "invalid_code": "join.st_invalid_code",
        "no_such_member": "join.st_no_such_member",
        "google_not_ready": "app.err_google_not_ready",
        "cancelled": "app.err_cancelled",
        "offline": "app.err_offline",
        "network": "app.err_offline",
    ]

    public static func key(_ code: String) -> String { byCode[code] ?? general }
}
