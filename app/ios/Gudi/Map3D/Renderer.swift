@preconcurrency import Metal
@preconcurrency import MetalKit
import simd
import GudiData
import GudiDomain

/// One frame's values for the shaders (`Frame` in Map3D.metal): only float4 and float4x4, so the layouts match.
struct FrameU {
    var mvp = matrix_identity_float4x4
    var inv = matrix_identity_float4x4
    var eye = SIMD4<Float>(), sun = SIMD4<Float>(), sunCol = SIMD4<Float>(), ambTop = SIMD4<Float>(), ambGround = SIMD4<Float>()
    var fog = SIMD4<Float>(), fogCol = SIMD4<Float>(), skyTop = SIMD4<Float>(), skyBottom = SIMD4<Float>()
    var sunDir = SIMD4<Float>(), moonDir = SIMD4<Float>(), glowCol = SIMD4<Float>(), misc = SIMD4<Float>()
    var groundBox = SIMD4<Float>(), drapeBox = SIMD4<Float>(), res = SIMD4<Float>()
}

/// Per draw of a ribbon (`LineU` in Map3D.metal).
struct LineU {
    var tint = SIMD4<Float>(0, 0, 0, 0)
    var width: Float = 1, bias: Float = 0, reveal: Float = 1e9, alpha: Float = 1, dash: Float = 0, on: Float = 1, pad0: Float = 0, pad1: Float = 0
}

/// Per draw of points (`PointU` in Map3D.metal).
struct PointU {
    var color = SIMD4<Float>(0.957, 0.725, 0.259, 1)
    var size: Float = 9, kind: Float = 0, pulse: Float = 0.5, pad: Float = 0
}

/// A ribbon on the GPU.
struct GpuRibbon {
    let vb: MTLBuffer, ib: MTLBuffer, count: Int

    init?(_ d: MTLDevice, _ r: Ribbon) {
        guard !r.indices.isEmpty,
              let vb = d.makeBuffer(bytes: r.vertices, length: r.vertices.count * 4),
              let ib = d.makeBuffer(bytes: r.indices, length: r.indices.count * 4) else { return nil }
        self.vb = vb; self.ib = ib; count = r.indices.count
    }

    func draw(_ e: MTLRenderCommandEncoder) {
        e.setVertexBuffer(vb, offset: 0, index: 0)
        e.drawIndexedPrimitives(type: .triangle, indexCount: count, indexType: .uint32, indexBuffer: ib, indexBufferOffset: 0)
    }
}

/// What the lift status changes on the mountain (S1, as the site draws it): closed runs and lifts grey and dashed,
/// "only what's open for me" fades the closed runs, and three chairs ride each open lift.
struct StatusPaint: Equatable {
    var closedRuns: Set<String> = []
    var closedLifts: Set<String> = []
    var forMe = false
    var openLifts: Set<String> = []
}

/// Draws the mountain with Metal (Android's MapRenderer): the sky, the terrain with its light, shadows, the drape
/// and the chosen run's ground layer, the runs and lifts as ribbons, the stations, the chairs, the chosen run painted
/// from the top in slope colours, and the skier's dot. It only draws; `Map3DEngine` decides what and when.
@MainActor
final class Renderer {
    let device: MTLDevice
    let queue: MTLCommandQueue
    private let terrainPipe: MTLRenderPipelineState
    private let skyPipe: MTLRenderPipelineState
    private let ribbonPipe: MTLRenderPipelineState
    private let pointPipe: MTLRenderPipelineState
    private let depthOn: MTLDepthStencilState
    private let depthRead: MTLDepthStencilState
    private let depthOff: MTLDepthStencilState
    private let sampler: MTLSamplerState
    private var blank: MTLTexture!
    private var dotB: MTLBuffer?

    // the mountain, once the scene is ready
    private var terrainVB: MTLBuffer?, terrainIB: MTLBuffer?, shadowB: MTLBuffer?
    private var terrainCount = 0
    private var runs: [(key: String, casing: GpuRibbon, core: GpuRibbon, w: Float)] = []
    private var lifts: [(name: String, r: GpuRibbon, idle: Bool, index: Int)] = []
    private var stations: MTLBuffer?, stationCount = 0
    private var chairs: MTLBuffer?
    /// Each cable's points and running length (3D metres), and a lap in len/60 seconds (at least 8), as the site's.
    private var cablePts: [[P3]] = [], cableCum: [[Double]] = [], cableDur: [Double] = []
    private var liftNames: [String] = []

    // a chosen run: the white casing and the slope paint, and the ground around it
    private var selCasing: GpuRibbon?, selPaint: GpuRibbon?
    private var groundTex: MTLTexture?
    private var groundBox = SIMD4<Float>()
    private var drapeTex: MTLTexture?
    private var drapeBox = SIMD4<Float>()

    static let sampleCount = 4

    init?() {
        guard let d = MTLCreateSystemDefaultDevice(), let q = d.makeCommandQueue(), let lib = d.makeDefaultLibrary() else { return nil }
        device = d; queue = q
        let samples = d.supportsTextureSampleCount(Self.sampleCount) ? Self.sampleCount : 1
        func pipe(_ vs: String, _ fs: String, _ vd: MTLVertexDescriptor?, blend: Bool) -> MTLRenderPipelineState? {
            let p = MTLRenderPipelineDescriptor()
            p.vertexFunction = lib.makeFunction(name: vs); p.fragmentFunction = lib.makeFunction(name: fs)
            p.vertexDescriptor = vd
            p.rasterSampleCount = samples
            p.colorAttachments[0].pixelFormat = .bgra8Unorm
            p.depthAttachmentPixelFormat = .depth32Float
            if blend {
                // premultiplied alpha
                let c = p.colorAttachments[0]!
                c.isBlendingEnabled = true
                c.sourceRGBBlendFactor = .one; c.destinationRGBBlendFactor = .oneMinusSourceAlpha
                c.sourceAlphaBlendFactor = .one; c.destinationAlphaBlendFactor = .oneMinusSourceAlpha
            }
            return try? d.makeRenderPipelineState(descriptor: p)
        }
        let tv = MTLVertexDescriptor()
        for (i, (fmt, off)) in [(MTLVertexFormat.float3, 0), (.float3, 12), (.float3, 24), (.float, 36)].enumerated() {
            tv.attributes[i].format = fmt; tv.attributes[i].offset = off; tv.attributes[i].bufferIndex = 0
        }
        tv.attributes[4].format = .float; tv.attributes[4].offset = 0; tv.attributes[4].bufferIndex = 1
        tv.layouts[0].stride = TerrainMesh.stride * 4; tv.layouts[1].stride = 4
        let rv = MTLVertexDescriptor()
        for (i, (fmt, off)) in [(MTLVertexFormat.float3, 0), (.float3, 12), (.float3, 24), (.float, 36), (.float4, 40), (.float, 56)].enumerated() {
            rv.attributes[i].format = fmt; rv.attributes[i].offset = off; rv.attributes[i].bufferIndex = 0
        }
        rv.layouts[0].stride = Ribbon.stride * 4
        guard let t = pipe("terrain_vs", "terrain_fs", tv, blend: false), let s = pipe("sky_vs", "sky_fs", nil, blend: false),
              let r = pipe("ribbon_vs", "ribbon_fs", rv, blend: true), let pt = pipe("point_vs", "point_fs", nil, blend: true) else { return nil }
        terrainPipe = t; skyPipe = s; ribbonPipe = r; pointPipe = pt
        func depth(_ cmp: MTLCompareFunction, write: Bool) -> MTLDepthStencilState? {
            let ds = MTLDepthStencilDescriptor(); ds.depthCompareFunction = cmp; ds.isDepthWriteEnabled = write
            return d.makeDepthStencilState(descriptor: ds)
        }
        guard let a = depth(.less, write: true), let b = depth(.lessEqual, write: false), let c = depth(.always, write: false) else { return nil }
        depthOn = a; depthRead = b; depthOff = c
        let sd = MTLSamplerDescriptor()
        sd.minFilter = .linear; sd.magFilter = .linear; sd.mipFilter = .linear
        sd.sAddressMode = .clampToEdge; sd.tAddressMode = .clampToEdge
        sd.maxAnisotropy = 8
        guard let smp = d.makeSamplerState(descriptor: sd) else { return nil }
        sampler = smp
        guard let bl = texture([0, 0, 0, 0], w: 1, h: 1, mipmapped: false) else { return nil }
        blank = bl
        dotB = d.makeBuffer(length: 16, options: .storageModeShared)
    }

    var samples: Int { device.supportsTextureSampleCount(Self.sampleCount) ? Self.sampleCount : 1 }

    // MARK: uploads

    func upload(_ s: Scene3D, mesh: TerrainMesh, shadow: [Float]) {
        terrainVB = device.makeBuffer(bytes: mesh.vertices, length: mesh.vertices.count * 4)
        terrainIB = device.makeBuffer(bytes: mesh.indices, length: mesh.indices.count * 4)
        terrainCount = mesh.indices.count
        setShadow(shadow)
        runs = []
        for p in s.runs.pistes {
            guard let lines = s.draped[p.key], !lines.isEmpty else { continue }
            let c = Scene3D.runRgb(p.color)
            let m = lines.map(Scene3D.metres)
            guard let casing = GpuRibbon(device, Ribbon.build(lines, colour: { _, _ in SIMD4(1, 1, 1, 1) })),
                  let core = GpuRibbon(device, Ribbon.build(lines, colour: { _, _ in c }, progress: { li, i in m[li][i] })) else { continue }
            runs.append((p.key, casing, core, Float(Scene3D.width(p))))
        }
        lifts = []
        liftNames = s.runs.lifts.map(\.name)
        for (i, l) in s.runs.lifts.enumerated() {
            let m = Scene3D.metres(s.cables[i])
            guard let r = GpuRibbon(device, Ribbon.build([s.cables[i]], colour: { _, _ in SIMD4(0.227, 0.271, 0.337, 1) }, progress: { _, k in m[k] })) else { continue }
            lifts.append((l.name, r, l.status == "inactive", i))
        }
        // both ends of every cable
        var ends: [Float] = []
        for c in s.cables where c.count >= 2 { for p in [c[0], c[c.count - 1]] { ends += [Float(p.x), Float(p.y), Float(p.z)] } }
        stations = ends.isEmpty ? nil : device.makeBuffer(bytes: ends, length: ends.count * 4)
        stationCount = ends.count / 3
        cablePts = s.cables
        cableCum = s.cables.map(Scene3D.metres)
        cableDur = s.runs.lifts.map { max(8, ($0.len > 0 ? $0.len : 1000) / 60) }
        chairs = device.makeBuffer(length: max(s.cables.count * 3 * 12, 12), options: .storageModeShared)
    }

    func setShadow(_ shadow: [Float]) {
        guard !shadow.isEmpty else { return }
        if let b = shadowB, b.length == shadow.count * 4 {
            b.contents().copyMemory(from: shadow, byteCount: shadow.count * 4)
        } else {
            shadowB = device.makeBuffer(bytes: shadow, length: shadow.count * 4, options: .storageModeShared)
        }
    }

    /// A texture in private memory, filled through a staging buffer and a blit (the simulator's Metal wants this).
    private func texture(_ rgba: [UInt8], w: Int, h: Int, mipmapped: Bool) -> MTLTexture? {
        let td = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: .rgba8Unorm, width: w, height: h, mipmapped: mipmapped)
        td.storageMode = .private; td.usage = .shaderRead
        guard let t = device.makeTexture(descriptor: td),
              let stage = device.makeBuffer(bytes: rgba, length: rgba.count, options: .storageModeShared),
              let cb = queue.makeCommandBuffer(), let bl = cb.makeBlitCommandEncoder() else { return nil }
        bl.copy(from: stage, sourceOffset: 0, sourceBytesPerRow: w * 4, sourceBytesPerImage: w * h * 4,
                sourceSize: MTLSizeMake(w, h, 1), to: t, destinationSlice: 0, destinationLevel: 0, destinationOrigin: MTLOriginMake(0, 0, 0))
        if mipmapped { bl.generateMipmaps(for: t) }
        bl.endEncoding()
        cb.commit()
        return t
    }

    func setDrape(_ rgba: [UInt8], w: Int, h: Int, box: SIMD4<Float>) {
        drapeTex = texture(rgba, w: w, h: h, mipmapped: true)
        drapeBox = box
    }

    func select(casing: Ribbon?, paint: Ribbon?, ground: SlopeLayer?) {
        selCasing = casing.flatMap { GpuRibbon(device, $0) }
        selPaint = paint.flatMap { GpuRibbon(device, $0) }
        if let g = ground {
            groundTex = texture(g.rgba, w: g.w, h: g.h, mipmapped: false)
            groundBox = SIMD4(Float(g.x0), Float(g.z0), Float(1 / g.width), Float(1 / g.depth))
        } else { groundTex = nil }
    }

    var hasScene: Bool { terrainVB != nil }

    // MARK: drawing

    struct Inputs {
        var frame = FrameU()
        var hidden: Set<String> = []
        var hideLifts = false
        var status = StatusPaint()
        /// 0...1: how much the rest of the mountain is dimmed, and how far the chosen run is painted.
        var dim: Float = 0, reveal: Float = 1
        /// Ground metres a pixel at the target, for dashes as long on the screen as the site's.
        var metresPerPoint: Float = 1
        var dot: P3?
        var pulse: Float = 0.5
        var chairsAt: Double?
    }

    func draw(_ view: MTKView, _ inp: Inputs) {
        guard let pass = view.currentRenderPassDescriptor, let drawable = view.currentDrawable,
              let cb = queue.makeCommandBuffer() else { return }
        let sky = inp.frame.skyBottom
        pass.colorAttachments[0].clearColor = MTLClearColorMake(Double(sky.x), Double(sky.y), Double(sky.z), 1)
        guard let e = cb.makeRenderCommandEncoder(descriptor: pass) else { return }
        var f = inp.frame
        // the sky first, behind everything
        e.setRenderPipelineState(skyPipe)
        e.setDepthStencilState(depthOff)
        e.setFragmentBytes(&f, length: MemoryLayout<FrameU>.stride, index: 0)
        e.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
        if let vb = terrainVB, let ib = terrainIB, let sb = shadowB {
            e.setRenderPipelineState(terrainPipe)
            e.setDepthStencilState(depthOn)
            e.setCullMode(.none)
            e.setVertexBuffer(vb, offset: 0, index: 0)
            e.setVertexBuffer(sb, offset: 0, index: 1)
            e.setVertexBytes(&f, length: MemoryLayout<FrameU>.stride, index: 2)
            f.groundBox = groundBox; f.drapeBox = drapeBox
            f.misc.z = groundTex == nil ? 0 : min(1, inp.dim * 1.6)
            f.misc.w = drapeTex == nil ? 0 : 1
            e.setFragmentBytes(&f, length: MemoryLayout<FrameU>.stride, index: 0)
            e.setFragmentTexture(groundTex ?? blank, index: 0)
            e.setFragmentTexture(drapeTex ?? blank, index: 1)
            e.setFragmentSamplerState(sampler, index: 0)
            e.drawIndexedPrimitives(type: .triangle, indexCount: terrainCount, indexType: .uint32, indexBuffer: ib, indexBufferOffset: 0)
            drawLines(e, &f, inp)
        }
        e.endEncoding()
        cb.present(drawable)
        cb.commit()
    }

    private func drawLines(_ e: MTLRenderCommandEncoder, _ f: inout FrameU, _ inp: Inputs) {
        e.setRenderPipelineState(ribbonPipe)
        e.setDepthStencilState(depthRead)
        e.setVertexBytes(&f, length: MemoryLayout<FrameU>.stride, index: 2)
        let others = 1 - 0.65 * inp.dim
        let paint = inp.status
        let px = inp.metresPerPoint
        func fade(_ key: String) -> Float { paint.forMe && paint.closedRuns.contains(key) ? 0.15 : 1 }
        func go(_ u: inout LineU, _ r: GpuRibbon) {
            e.setVertexBytes(&u, length: MemoryLayout<LineU>.stride, index: 3)
            e.setFragmentBytes(&u, length: MemoryLayout<LineU>.stride, index: 3)
            r.draw(e)
        }
        // the white casing of every run, then its colour (a closed one grey and dashed, 6 on 5 off as the site's)
        for r in runs where !inp.hidden.contains(r.key) {
            var u = LineU(width: 2.6 * r.w + 3, bias: 0.0006, alpha: others * fade(r.key))
            go(&u, r.casing)
        }
        for r in runs where !inp.hidden.contains(r.key) {
            let shut = paint.closedRuns.contains(r.key)
            var u = LineU(width: 2.6 * r.w, bias: 0.0008, alpha: others * fade(r.key))
            if shut { u.tint = SIMD4(0.557, 0.612, 0.678, 1); u.dash = 11 * px; u.on = 6 / 11 }
            go(&u, r.core)
        }
        if !inp.hideLifts {
            for l in lifts {
                var u = LineU(width: 1.8, bias: 0.0010, alpha: others)
                if !l.name.isEmpty && paint.closedLifts.contains(l.name) { u.tint = SIMD4(0.604, 0.647, 0.702, 1); u.dash = 8 * px; u.on = 0.5 }
                else if l.idle { u.dash = 5 * px; u.on = 0.4 } // the site's 2 on, 3 off
                go(&u, l.r)
            }
            if let st = stations, stationCount > 0 {
                e.setRenderPipelineState(pointPipe)
                var pu = PointU(size: 9, kind: 0)
                e.setVertexBuffer(st, offset: 0, index: 0)
                e.setVertexBytes(&pu, length: MemoryLayout<PointU>.stride, index: 3)
                e.setFragmentBytes(&pu, length: MemoryLayout<PointU>.stride, index: 3)
                e.drawPrimitives(type: .point, vertexStart: 0, vertexCount: stationCount)
                if let t = inp.chairsAt, let n = placeChairs(t, paint.openLifts), n > 0, let cb = chairs {
                    var cu = PointU(size: 6, kind: 1)
                    e.setVertexBuffer(cb, offset: 0, index: 0)
                    e.setVertexBytes(&cu, length: MemoryLayout<PointU>.stride, index: 3)
                    e.setFragmentBytes(&cu, length: MemoryLayout<PointU>.stride, index: 3)
                    e.drawPrimitives(type: .point, vertexStart: 0, vertexCount: n)
                }
                e.setRenderPipelineState(ribbonPipe)
            }
        }
        if let c = selCasing, let p = selPaint {
            var u = LineU(width: 9, bias: 0.0012, reveal: inp.reveal, alpha: 1)
            go(&u, c)
            u.width = 5; u.bias = 0.0014
            go(&u, p)
        }
        if let d = inp.dot {
            // the dot where the skier is (flying down, or under the finger on the profile), over everything
            e.setRenderPipelineState(pointPipe)
            e.setDepthStencilState(depthOff)
            guard let db = dotB else { return }
            let pt = db.contents().bindMemory(to: Float.self, capacity: 4)
            pt[0] = Float(d.x); pt[1] = Float(d.y + 6); pt[2] = Float(d.z)
            var pu = PointU(size: 34, kind: 2, pulse: inp.pulse)
            e.setVertexBuffer(db, offset: 0, index: 0)
            e.setVertexBytes(&pu, length: MemoryLayout<PointU>.stride, index: 3)
            e.setFragmentBytes(&pu, length: MemoryLayout<PointU>.stride, index: 3)
            e.drawPrimitives(type: .point, vertexStart: 0, vertexCount: 1)
        }
    }

    /// This moment's chairs on the open lifts, into their buffer; how many.
    private func placeChairs(_ now: Double, _ open: Set<String>) -> Int? {
        guard !open.isEmpty, let buf = chairs else { return nil }
        let p = buf.contents().bindMemory(to: Float.self, capacity: buf.length / 4)
        var n = 0
        for (i, name) in liftNames.enumerated() where !name.isEmpty && open.contains(name) && i < cableCum.count {
            guard let total = cableCum[i].last, total > 0, (n + 3) * 3 <= buf.length / 4 else { continue }
            for k in 0..<3 {
                let f = (now / cableDur[i] + Double(k) / 3).truncatingRemainder(dividingBy: 1)
                let q = Scene3D.along(cablePts[i], cableCum[i], f * total)
                p[n * 3] = Float(q.x); p[n * 3 + 1] = Float(q.y); p[n * 3 + 2] = Float(q.z)
                n += 1
            }
        }
        return n
    }
}
