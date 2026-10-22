import Foundation

/// Supabase Realtime (server/CONTRACT.md, "זמן אמת"), as Android's server/Realtime.kt: one WebSocket for the app, one
/// channel per thing the screens watch, over the Phoenix protocol. A change only says "something in this table
/// changed": the store then reads again through the row rules, so nothing reaches the phone that the person may not
/// read. While there is no signal it retries with growing pauses (up to a minute) and, when back, tells every channel to
/// read again (changes made meanwhile were missed).
public actor Realtime {
    /// What one channel listens to: a table, and optionally a filter ("group_id=eq.<id>").
    public struct Watch: Sendable, Equatable {
        public let table: String, filter: String?, event: String
        public init(_ table: String, filter: String? = nil, event: String = "*") { self.table = table; self.filter = filter; self.event = event }
    }

    public final class Channel: @unchecked Sendable {
        public let topic: String
        let watches: [Watch]
        let onChange: @Sendable (String) -> Void
        let onReconnect: @Sendable () -> Void
        var joinRef: String?
        init(_ topic: String, _ watches: [Watch], _ onChange: @escaping @Sendable (String) -> Void, _ onReconnect: @escaping @Sendable () -> Void) {
            self.topic = topic; self.watches = watches; self.onChange = onChange; self.onReconnect = onReconnect
        }
    }

    private let server: ServerClient
    private let sockets: SocketFactory
    private var channels: [String: Channel] = [:]
    private var order: [String] = []
    private var socket: Socket?
    private var open = false
    private var ref = 0
    private var attempts = 0
    private var hadGap = false
    private var lastToken: String?
    // Which socket is the current one: a late "closed" or message from one we gave up must not reset the new one.
    private var generation = 0
    private var heartbeat: Task<Void, Never>?
    private var retry: Task<Void, Never>?

    public init(server: ServerClient, sockets: SocketFactory) {
        self.server = server; self.sockets = sockets
    }

    public var connected: Bool { open }

    /// Listen until [leave]. Topics are names of our choosing ("group:<id>"); one channel per topic.
    @discardableResult
    public func join(_ topic: String, _ watches: [Watch], onChange: @escaping @Sendable (String) -> Void,
                     onReconnect: @escaping @Sendable () -> Void = {}) async -> Channel {
        if let old = channels[topic] { await leave(old) }
        let c = Channel(topic, watches, onChange, onReconnect)
        channels[topic] = c
        order.append(topic)
        if open { await sendJoin(c) } else { connect() }
        return c
    }

    public func leave(_ c: Channel) async {
        guard channels[c.topic] === c else { return }
        channels[c.topic] = nil
        order.removeAll { $0 == c.topic }
        if open, c.joinRef != nil { send(c.topic, "phx_leave", .object([:])) }
        if channels.isEmpty { disconnect() }
    }

    /// A renewed session token must reach the channels, or the server stops sending after the old one ends.
    public func tokenChanged() async {
        guard let token = try? await server.current()?.accessToken, open, token != lastToken else { return }
        lastToken = token
        for t in order { if let c = channels[t], c.joinRef != nil { send(t, "access_token", .obj(["access_token": .string(token)])) } }
    }

    private func connect() {
        guard socket == nil, !channels.isEmpty else { return }
        let base = server.url.replacingOccurrences(of: "https://", with: "wss://").replacingOccurrences(of: "http://", with: "ws://")
        let url = "\(base)/realtime/v1/websocket?apikey=\(ServerClient.enc(server.key))&vsn=1.0.0"
        generation += 1
        let mine = generation
        socket = sockets.open(url, SocketListener(
            onOpen: { [weak self] in Task { await self?.opened(mine) } },
            onMessage: { [weak self] text in Task { await self?.received(mine, text) } },
            onClosed: { [weak self] in Task { await self?.closed(mine) } }))
    }

    private func opened(_ gen: Int) async {
        guard gen == generation else { return }
        open = true
        attempts = 0
        heartbeat?.cancel()
        heartbeat = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 25_000_000_000)
                guard !Task.isCancelled else { return }
                await self?.beat()
            }
        }
        for t in order { if let c = channels[t] { await sendJoin(c) } }
        if hadGap {
            hadGap = false
            for t in order { channels[t]?.onReconnect() }
        }
    }

    private func beat() async {
        if open { send("phoenix", "heartbeat", .object([:])) }
        await tokenChanged()
    }

    private func received(_ gen: Int, _ text: String) {
        guard gen == generation, let m = JSON.parse(text), m.string("event") == "postgres_changes" else { return }
        let topic = m.string("topic")
        let name = topic.hasPrefix("realtime:") ? String(topic.dropFirst("realtime:".count)) : topic
        guard let c = channels[name], let table = m["payload"]?["data"]?.str("table") else { return }
        c.onChange(table)
    }

    private func closed(_ gen: Int) {
        guard gen == generation else { return }
        socket = nil
        open = false
        heartbeat?.cancel()
        for c in channels.values { c.joinRef = nil }
        guard !channels.isEmpty else { return }
        hadGap = true
        let wait = min(60, 1 << min(attempts, 6))
        attempts += 1
        retry?.cancel()
        retry = Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(wait) * 1_000_000_000)
            guard !Task.isCancelled else { return }
            await self?.reconnect()
        }
    }

    private func reconnect() { connect() }

    private func disconnect() {
        retry?.cancel(); heartbeat?.cancel()
        let s = socket
        generation += 1 // whatever this socket still says is old news
        socket = nil
        open = false
        hadGap = false
        s?.close()
    }

    // The session may be nil for a moment (renewing); the server then treats the channel as public, and the row rules
    // let nothing through. The next token change fixes it.
    private func sendJoin(_ c: Channel) async {
        let token = try? await server.current()?.accessToken
        lastToken = token
        let changes: [JSON] = c.watches.map { w in
            var o: [String: JSON] = ["event": .string(w.event), "schema": "public", "table": .string(w.table)]
            if let f = w.filter { o["filter"] = .string(f) }
            return .object(o)
        }
        let config = JSON.obj(["broadcast": .obj(["self": false]), "presence": .obj(["key": ""]), "postgres_changes": .array(changes), "private": false])
        var payload: [String: JSON] = ["config": config]
        if let token { payload["access_token"] = .string(token) }
        c.joinRef = send(c.topic, "phx_join", .object(payload), join: true)
    }

    @discardableResult
    private func send(_ topic: String, _ event: String, _ payload: JSON, join: Bool = false) -> String {
        ref += 1
        let r = String(ref)
        var msg: [String: JSON] = ["topic": .string(topic == "phoenix" ? topic : "realtime:\(topic)"), "event": .string(event), "payload": payload, "ref": .string(r)]
        if join { msg["join_ref"] = .string(r) }
        socket?.send(JSON.object(msg).text)
        return r
    }
}

public struct SocketListener: Sendable {
    public let onOpen: @Sendable () -> Void
    public let onMessage: @Sendable (String) -> Void
    public let onClosed: @Sendable () -> Void
    public init(onOpen: @escaping @Sendable () -> Void, onMessage: @escaping @Sendable (String) -> Void, onClosed: @escaping @Sendable () -> Void) {
        self.onOpen = onOpen; self.onMessage = onMessage; self.onClosed = onClosed
    }
}

public protocol SocketFactory: Sendable {
    func open(_ url: String, _ listener: SocketListener) -> Socket
}

public protocol Socket: Sendable {
    func send(_ text: String)
    func close()
}
