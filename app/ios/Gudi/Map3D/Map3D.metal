// The 3D trail map's shaders: Android's map/MapRenderer.kt (GLSL ES 3) in Metal. The same light, sky, fog, ribbons
// and points; the only difference is Metal's clip depth (0...1, so the line bias is half of GL's).
#include <metal_stdlib>
using namespace metal;

/// One frame's values; the same layout as `FrameU` in Renderer.swift (only float4 and float4x4, so no padding).
struct Frame {
    float4x4 mvp;
    float4x4 inv;
    float4 eye;
    float4 sun;       // towards the light that casts the shadows
    float4 sunCol;
    float4 ambTop;
    float4 ambGround;
    float4 fog;       // x: where the fog starts, y: where it is whole
    float4 fogCol;
    float4 skyTop;
    float4 skyBottom;
    float4 sunDir;    // w: the sun is up
    float4 moonDir;   // w: the moon is up
    float4 glowCol;   // w: the glow
    float4 misc;      // x: dark, y: dim (a run is chosen), z: the ground layer's opacity, w: the drape is there
    float4 groundBox; // x0, z0, 1/width, 1/depth of the ground layer
    float4 drapeBox;  // the same for the drape
    float4 res;       // xy: the drawable in pixels, z: pixels a point
};

// ---- the terrain ----

struct TerrainIn {
    float3 pos [[attribute(0)]];
    float3 normal [[attribute(1)]];
    float3 color [[attribute(2)]];
    float slope [[attribute(3)]];
    float shadow [[attribute(4)]];
};

struct TerrainOut {
    float4 position [[position]];
    float3 base;
    float3 light;
    float2 xz;
    float fog;
};

vertex TerrainOut terrain_vs(TerrainIn in [[stage_in]], constant Frame &f [[buffer(2)]]) {
    TerrainOut o;
    o.position = f.mvp * float4(in.pos, 1.0);
    float3 n = normalize(in.normal);
    float sun = max(dot(n, f.sun.xyz), 0.0) * (1.0 - 0.8 * in.shadow);
    // sky light from above and bounce from below, and the sun (or the moon), as the site's hemisphere and sun
    o.base = in.color;
    o.light = mix(f.ambGround.rgb, f.ambTop.rgb, 0.5 + 0.5 * n.y) * 1.25 + f.sunCol.rgb * sun;
    o.xz = in.pos.xz;
    o.fog = clamp((length(in.pos - f.eye.xyz) - f.fog.x) / (f.fog.y - f.fog.x), 0.0, 1.0);
    return o;
}

// the ground around a chosen run in slope colours (X-3), laid on the terrain by its x and z, which also keeps that
// ground out of the dimming of the rest of the mountain; and what lies on the snow (the drape), premultiplied
fragment float4 terrain_fs(TerrainOut in [[stage_in]], constant Frame &f [[buffer(0)]],
                           texture2d<float> ground [[texture(0)]], texture2d<float> drape [[texture(1)]],
                           sampler lin [[sampler(0)]]) {
    float2 uv = (in.xz - f.groundBox.xy) * f.groundBox.zw;
    float4 g = (f.misc.z > 0.0 && uv.x >= 0.0 && uv.y >= 0.0 && uv.x <= 1.0 && uv.y <= 1.0) ? ground.sample(lin, uv) : float4(0.0);
    float a = g.a * f.misc.z;
    float4 e = f.misc.w > 0.0 ? drape.sample(lin, (in.xz - f.drapeBox.xy) * f.drapeBox.zw) : float4(0.0);
    float3 base = in.base * (1.0 - e.a) + e.rgb;
    float3 lit = mix(base, g.rgb, a * 0.85) * in.light;
    lit = mix(lit, f.skyBottom.rgb, 0.55 * f.misc.y * (1.0 - a));
    return float4(mix(lit, f.fogCol.rgb, in.fog), 1.0);
}

// ---- the sky behind the mountain: the site's colours from the horizon up, and the sun and the moon where they are ----

struct SkyOut {
    float4 position [[position]];
    float2 ndc;
};

vertex SkyOut sky_vs(uint vid [[vertex_id]]) {
    float2 p = float2(vid == 1 ? 3.0 : -1.0, vid == 2 ? 3.0 : -1.0);
    SkyOut o;
    o.position = float4(p, 0.9999, 1.0);
    o.ndc = p;
    return o;
}

static float hash3(float3 p) { return fract(sin(dot(p, float3(12.9898, 78.233, 37.719))) * 43758.5453); }

fragment float4 sky_fs(SkyOut in [[stage_in]], constant Frame &f [[buffer(0)]]) {
    float4 w = f.inv * float4(in.ndc, 1.0, 1.0);
    float3 d = normalize(w.xyz / w.w - f.eye.xyz);
    float3 c = mix(f.skyBottom.rgb, f.skyTop.rgb, smoothstep(-0.02, 0.6, d.y));
    // stars on a dark night, fixed to the sky
    if (f.misc.x > 0.5 && d.y > 0.03) {
        float h = hash3(floor(d * 420.0));
        if (h > 0.9965) c += float3(0.85) * (h - 0.9965) / 0.0035 * smoothstep(0.03, 0.2, d.y);
    }
    // the sun: a bright disc with a glow around it, warmer when low
    float s = dot(d, f.sunDir.xyz);
    if (f.sunDir.w > 0.5) {
        c += f.glowCol.rgb * (0.10 * pow(max(s, 0.0), 8.0) + 0.45 * pow(max(s, 0.0), 400.0)) * (0.4 + f.glowCol.w);
        c = mix(c, float3(1.0, 0.97, 0.90), smoothstep(0.99962, 0.99976, s));
    }
    // the moon, lit from the sun's side: its phase and the tilt of its crescent are real
    float m = dot(d, f.moonDir.xyz);
    if (f.moonDir.w > 0.5 && m > 0.9997) {
        float sinR = 0.0245; // about 1.4°: larger than life, to be seen on a phone
        float3 u = (d - f.moonDir.xyz * m) / sinR;
        float r2 = dot(u, u);
        if (r2 < 1.0) {
            float3 n = u - f.moonDir.xyz * sqrt(1.0 - r2);
            float lit = smoothstep(-0.05, 0.12, dot(n, f.sunDir.xyz));
            float3 moon = mix(float3(0.16, 0.19, 0.27), float3(0.93, 0.95, 1.0), lit);
            c = mix(c, moon, smoothstep(1.0, 0.85, r2) * (f.sunDir.w > 0.5 ? 0.55 : 1.0));
        }
    }
    if (f.moonDir.w > 0.5 && f.sunDir.w < 0.5) c += float3(0.6, 0.65, 0.8) * 0.12 * pow(max(m, 0.0), 300.0);
    return float4(c, 1.0);
}

// ---- ribbons: lines widened on the screen, in points ----

struct RibbonIn {
    float3 pos [[attribute(0)]];
    float3 prev [[attribute(1)]];
    float3 next [[attribute(2)]];
    float side [[attribute(3)]];
    float4 color [[attribute(4)]];
    float prog [[attribute(5)]];
};

/// Per draw: the width (points), the depth bias, the reveal, the opacity, a tint, and dashes.
struct LineU {
    float4 tint;   // rgb, a: how much
    float width;
    float bias;
    float reveal;  // 0...1 down the chosen run; anything past it is not drawn
    float alpha;
    float dash;    // the dash's period, in the units of prog (metres); 0: solid
    float on;      // the share of the period drawn
    float pad0, pad1;
};

struct RibbonOut {
    float4 position [[position]];
    float4 color;
    float prog;
};

vertex RibbonOut ribbon_vs(RibbonIn in [[stage_in]], constant Frame &f [[buffer(2)]], constant LineU &u [[buffer(3)]]) {
    float4 c = f.mvp * float4(in.pos, 1.0), p = f.mvp * float4(in.prev, 1.0), n = f.mvp * float4(in.next, 1.0);
    float2 res = f.res.xy;
    float2 a = c.xy / c.w * res * 0.5, b = p.xy / p.w * res * 0.5, e = n.xy / n.w * res * 0.5;
    float2 d1 = a - b, d2 = e - a;
    if (length(d1) < 1e-4) d1 = d2;
    if (length(d2) < 1e-4) d2 = d1;
    d1 = normalize(d1); d2 = normalize(d2);
    float2 t = d1 + d2;
    t = length(t) < 1e-3 ? d1 : normalize(t);
    float2 nn = float2(-t.y, t.x);
    float ml = 1.0 / max(dot(nn, float2(-d1.y, d1.x)), 0.6);
    c.xy += nn * in.side * u.width * f.res.z * ml / res * c.w;
    c.z -= u.bias * 0.5 * c.w;
    RibbonOut o;
    o.position = c;
    o.color = in.color;
    o.prog = in.prog;
    return o;
}

fragment float4 ribbon_fs(RibbonOut in [[stage_in]], constant LineU &u [[buffer(3)]]) {
    if (in.prog > u.reveal) discard_fragment();
    if (u.dash > 0.0 && fract(in.prog / u.dash) > u.on) discard_fragment();
    float4 c = mix(in.color, float4(u.tint.rgb, in.color.a), u.tint.a);
    return float4(c.rgb * c.a * u.alpha, c.a * u.alpha);
}

// ---- points: the lifts' stations, the chairs, and the skier's dot ----

struct PointU {
    float4 color;  // the chair's colour
    float size;    // points
    float kind;    // 0 station, 1 chair, 2 the skier's dot
    float pulse;
    float pad;
};

struct PointOut {
    float4 position [[position]];
    float size [[point_size]];
};

vertex PointOut point_vs(const device packed_float3 *pts [[buffer(0)]], uint vid [[vertex_id]],
                         constant Frame &f [[buffer(2)]], constant PointU &u [[buffer(3)]]) {
    PointOut o;
    o.position = f.mvp * float4(float3(pts[vid]), 1.0);
    o.size = u.size * f.res.z;
    return o;
}

fragment float4 point_fs(PointOut in [[stage_in]], float2 pc [[point_coord]], constant PointU &u [[buffer(3)]]) {
    float r = length(pc * 2.0 - 1.0);
    if (r > 1.0) discard_fragment();
    float4 c;
    if (u.kind < 0.5) c = r < 0.58 ? float4(0.165, 0.184, 0.220, 1.0) : float4(1.0);
    else if (u.kind < 1.5) c = r > 0.8 ? float4(0.075, 0.137, 0.227, 1.0) : u.color;
    else {
        if (r < 0.30) c = float4(0.075, 0.137, 0.227, 1.0);
        else if (r < 0.42) c = float4(1.0);
        else c = float4(1.0, 1.0, 1.0, (0.55 + 0.2 * u.pulse) * (1.0 - (r - 0.42) / 0.58));
    }
    return float4(c.rgb * c.a, c.a);
}
