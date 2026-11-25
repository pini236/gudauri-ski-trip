// The reference of the live model (see live_ref.html). Mirrors the Android engine where it matters: the world
// (x east, y up, z = -north), OrbitCamera (target, dist, yaw, pitch, fov 40), and Sun.shadows (per vertex, soft).
(async () => {
  const q = new URLSearchParams(location.search), num = (k, d) => q.has(k) ? +q.get(k) : d;
  const H = '/design/handoff/2026-11-20-redesign-live/';
  const [T, L, D] = await Promise.all(['/site/data/terrain.json', H + 'peak-live.json', '/site/data/runs-and-lifts.json'].map(u => fetch(u).then(r => r.json())));
  const dm = T.dem, nx = dm.nx, ny = dm.ny, bin = atob(dm.b64), Z = new Float32Array(nx * ny);
  for (let i = 0; i < nx * ny; i++) { let v = bin.charCodeAt(2 * i) | (bin.charCodeAt(2 * i + 1) << 8); Z[i] = v > 32767 ? v - 65536 : v; }
  const x0 = dm.x0, x1 = dm.x1, z0 = dm.y0, z1 = dm.y1, sx = (x1 - x0) / (nx - 1), sz = (z1 - z0) / (ny - 1);
  const at = (c, r) => Z[r * nx + c];
  const elev = (x, z) => { let c = Math.min(Math.max((x - x0) / sx, 0), nx - 1.001), r = Math.min(Math.max((z - z0) / sz, 0), ny - 1.001), c0 = c | 0, r0 = r | 0, fc = c - c0, fr = r - r0;
    return (at(c0, r0) * (1 - fc) + at(c0 + 1, r0) * fc) * (1 - fr) + (at(c0, r0 + 1) * (1 - fc) + at(c0 + 1, r0 + 1) * fc) * fr; };

  // ---- the light: one of the seven keys (?light=), or an hour of 12.1.2027 (?h=) blended between the keys
  const rad = Math.PI / 180;
  function sun(h) { const n = 12, dec = -23.44 * Math.cos(2 * Math.PI / 365 * (n + 10)) * rad, b = 2 * Math.PI / 364 * (n - 81), eot = 9.87 * Math.sin(2 * b) - 7.53 * Math.cos(b) - 1.5 * Math.sin(b);
    const ha = 15 * (h + (44.495 - 60) / 15 + eot / 60 - 12) * rad, phi = 42.51 * rad;
    const alt = Math.asin(Math.sin(phi) * Math.sin(dec) + Math.cos(phi) * Math.cos(dec) * Math.cos(ha)), az = Math.atan2(Math.sin(ha), Math.cos(ha) * Math.sin(phi) - Math.tan(dec) * Math.cos(phi));
    return [alt / rad, (az / rad + 180) % 360]; }
  const keys = L.lights, mix = (a, b, t) => a.map((v, i) => v + (b[i] - v) * t);
  let lt;
  if (q.has('h')) { const h = +q.get('h'); let i = 0; while (i < keys.length - 2 && keys[i + 1].hour < h) i++;
    const a = keys[i], b = keys[i + 1], t = Math.min(Math.max((h - a.hour) / (b.hour - a.hour), 0), 1), [alt, az] = h < 20 ? sun(h) : [a.alt + (b.alt - a.alt) * t, a.az + (b.az - a.az) * t];
    lt = { alt, az, direct: a.direct + (b.direct - a.direct) * t, stars: a.stars + (b.stars - a.stars) * t, bandOn: (a.band ? 1 - t : 0) + (b.band ? t : 0), band: a.band || b.band || [1, 1, 1] };
    for (const k of ['lit', 'shade', 'rock', 'rockS', 'zen', 'hor']) lt[k] = mix(a[k], b[k], t);
  } else { const a = keys.find(k => k.name === (q.get('light') || 'morning')); lt = Object.assign({}, a, { bandOn: a.band ? 1 : 0, band: a.band || [1, 1, 1] }); }
  const altL = Math.max(lt.alt, 1.2) * rad, azL = lt.az * rad;
  const sunDir = new THREE.Vector3(Math.sin(azL) * Math.cos(altL), Math.sin(altL), -Math.cos(azL) * Math.cos(altL));
  const glow = lt.direct * (1 - Math.min(Math.max((lt.alt - 4) / 3, 0), 1));

  // ---- Sun.shadows, as the engine computes it
  const shadow = new Float32Array(nx * ny);
  { const hz = Math.max(Math.hypot(sunDir.x, sunDir.z), 1e-4), ux = sunDir.x / hz, uz = sunDir.z / hz, rise = sunDir.y / hz;
    for (let r = 0; r < ny; r++) for (let c = 0; c < nx; c++) { const x = x0 + c * sx, z = z0 + r * sz, h0 = at(c, r) + 3; let s = 0, dd = 35;
      while (dd < 9000) { const px = x + ux * dd, pz = z + uz * dd; if (px < x0 || px > x1 || pz < z0 || pz > z1) break;
        const over = elev(px, pz) - (h0 + rise * dd); if (over > 0) { s = Math.max(s, Math.min(over / 30, 1)); if (s >= 1) break; }
        if (h0 + rise * dd > 3400) break; dd += 35 * (1 + dd / 3000); }
      shadow[r * nx + c] = s; } }
  if (num('soft', 1)) { const b = new Float32Array(shadow); for (let r = 1; r < ny - 1; r++) for (let c = 1; c < nx - 1; c++) { let a = 0; for (let j = -1; j <= 1; j++) for (let i = -1; i <= 1; i++) a += b[(r + j) * nx + c + i] * (i && j ? 1 : i || j ? 2 : 4); shadow[r * nx + c] = a / 16; } }

  // ---- the mesh
  const pos = new Float32Array(nx * ny * 3), idx = new Uint32Array((nx - 1) * (ny - 1) * 6);
  for (let r = 0; r < ny; r++) for (let c = 0; c < nx; c++) { const i = (r * nx + c) * 3; pos[i] = x0 + c * sx; pos[i + 1] = at(c, r); pos[i + 2] = z0 + r * sz; }
  let k = 0; for (let r = 0; r < ny - 1; r++) for (let c = 0; c < nx - 1; c++) { const a = r * nx + c; idx[k++] = a; idx[k++] = a + nx; idx[k++] = a + 1; idx[k++] = a + 1; idx[k++] = a + nx; idx[k++] = a + nx + 1; }
  const geo = new THREE.BufferGeometry(); geo.setAttribute('position', new THREE.BufferAttribute(pos, 3)); geo.setAttribute('shadow', new THREE.BufferAttribute(shadow, 1)); geo.setIndex(new THREE.BufferAttribute(idx, 1));

  const W = num('w', 390), Hh = num('hh', 844), dpr = num('dpr', 2);
  const renderer = new THREE.WebGLRenderer({ antialias: true, preserveDrawingBuffer: true }); renderer.setPixelRatio(dpr); renderer.setSize(W, Hh); document.body.appendChild(renderer.domElement);
  const tex = (name, rep) => new Promise(res => new THREE.TextureLoader().load(H + 'tex/' + name + (q.get('v') ? '?' + q.get('v') : ''), t => { t.flipY = false; t.generateMipmaps = true; t.minFilter = THREE.LinearMipmapLinearFilter; t.magFilter = THREE.LinearFilter; t.anisotropy = 8;
    t.wrapS = rep ? THREE.RepeatWrapping : THREE.ClampToEdgeWrapping; t.wrapT = rep === 2 ? THREE.RepeatWrapping : THREE.ClampToEdgeWrapping; res(t); }));
  const [relief, cover, tile, horizon, cirrus] = await Promise.all([tex(q.get('relief') || 'relief.webp'), tex(q.get('cover') || 'cover.webp'), tex(q.get('tile') || 'tile.webp', 2), (q.get('hzpath') ? new Promise(res => new THREE.TextureLoader().load(q.get('hzpath'), t => { t.flipY = false; t.generateMipmaps = true; t.minFilter = THREE.LinearMipmapLinearFilter; t.wrapS = THREE.RepeatWrapping; t.wrapT = THREE.ClampToEdgeWrapping; res(t); })) : tex(q.get('horizon') || 'horizon.webp', 1)), tex(q.get('cirrus') || 'cirrus.webp', 1)]);

  // ---- the camera (OrbitCamera)
  const kx = 111320 * Math.cos(T.proj.lat0 * rad);
  const C = L.camera, st = { tx: num('tx', C.tx), ty: num('ty', C.ty), tz: num('tz', C.tz), dist: num('dist', C.dist), yaw: num('yaw', C.yaw_deg) * rad, pitch: num('pitch', C.pitch_deg) * rad }, fov = num('fov', C.fov_deg);
  const eye = new THREE.Vector3(st.tx + Math.sin(st.yaw) * Math.cos(st.pitch) * st.dist, st.ty + Math.sin(st.pitch) * st.dist, st.tz + Math.cos(st.yaw) * Math.cos(st.pitch) * st.dist);
  eye.y = Math.max(eye.y, elev(eye.x, eye.z) + 40);
  const cam = new THREE.PerspectiveCamera(fov, W / Hh, Math.min(Math.max(st.dist * 0.02, 5), 150), st.dist * 5 + 30000);
  cam.position.copy(eye); cam.lookAt(st.tx, st.ty, st.tz); cam.updateMatrixWorld(); cam.updateProjectionMatrix();
  const mvp = new THREE.Matrix4().multiplyMatrices(cam.projectionMatrix, cam.matrixWorldInverse), inv = new THREE.Matrix4().copy(mvp).invert();

  const V3 = a => new THREE.Vector3(a[0], a[1], a[2]);
  const hazeK = [num('hz0', 1.0), num('hz1', 0.43)];
  const common = { uSun: { value: sunDir }, uLit: { value: V3(lt.lit) }, uShade: { value: V3(lt.shade) }, uHor: { value: V3(lt.hor) }, uDirect: { value: lt.direct }, uGlow: { value: glow } };
  const terrain = new THREE.Mesh(geo, new THREE.RawShaderMaterial({ glslVersion: THREE.GLSL3, vertexShader: document.getElementById('tvs').textContent, fragmentShader: document.getElementById('tfs').textContent,
    uniforms: Object.assign({ uMvp: { value: mvp }, uRelief: { value: relief }, uCover: { value: cover }, uTile: { value: tile }, uBox: { value: new THREE.Vector4(x0, z0, 1 / (x1 - x0), 1 / (z1 - z0)) },
      uRock: { value: V3(lt.rock) }, uRockS: { value: V3(lt.rockS) }, uCam: { value: eye }, uHaze: { value: new THREE.Vector3(st.dist * hazeK[0], st.dist * hazeK[1], num('hzmax', 0.72)) }, uValley: { value: num('valley', 0.26) }, uDetail: { value: num('detail', 1) }, uEdge: { value: num('edge', 0) }, uRipple: { value: num('ripple', L.k.ripple) } }, common) }));
  terrain.frustumCulled = false;
  const sgeo = new THREE.BufferGeometry(); sgeo.setAttribute('position', new THREE.BufferAttribute(new Float32Array([-1, -1, 0, 3, -1, 0, -1, 3, 0]), 3));
  const hz = L.tex.horizon;
  const sky = new THREE.Mesh(sgeo, new THREE.RawShaderMaterial({ glslVersion: THREE.GLSL3, depthWrite: false, depthTest: false, vertexShader: document.getElementById('svs').textContent, fragmentShader: document.getElementById('sfs').textContent,
    uniforms: Object.assign({ uInv: { value: inv }, uEye: { value: eye }, uHorizon: { value: horizon }, uCirrus: { value: cirrus }, uZen: { value: V3(lt.zen) }, uBand: { value: V3(lt.band) }, uBandOn: { value: lt.bandOn },
      uStars: { value: lt.stars }, uSunUp: { value: lt.alt > 0 && lt.direct > 0 && lt.stars < 1 ? 1 : 0 }, uStrip: { value: new THREE.Vector4(hz.lo_deg, hz.hi_deg, hz.far_m, 0) }, uFar: { value: num('far', 1) }, uSkyTop: { value: num('skytop', 11) }, uAir: { value: new THREE.Vector2(num('air0', 0.55), num('air1', 0.30)) } }, common) }));
  sky.frustumCulled = false; sky.renderOrder = -1;
  const scene = new THREE.Scene(); scene.add(sky); scene.add(terrain);

  // ---- the edge board (25.11.2026). ?ring=1: alternative A, the real terrain around the model (design/redesign/assets/edge/ring.json)
  //      ?cloud=<metres>: alternative B, a sea of cloud at that height (paint), with ?edge=<metres> fading the last metres of the model
  if (num('ring', 0)) { const R = await fetch('/design/redesign/assets/edge/ring.json').then(r => r.json()), rn = R.n, rc = R.cell, rb = atob(R.b64), RZ = new Float32Array(rn * rn);
    for (let i = 0; i < rn * rn; i++) { let v = rb.charCodeAt(2 * i) | (rb.charCodeAt(2 * i + 1) << 8); RZ[i] = v > 32767 ? v - 65536 : v; }
    const rat = (c, r) => RZ[Math.min(Math.max(r, 0), rn - 1) * rn + Math.min(Math.max(c, 0), rn - 1)];
    const rel = (x, z) => { const c = Math.min(Math.max((x - R.x0) / rc, 0), rn - 1.001), r = Math.min(Math.max((z - R.z0) / rc, 0), rn - 1.001), c0 = c | 0, r0 = r | 0, fc = c - c0, fr = r - r0;
      return (rat(c0, r0) * (1 - fc) + rat(c0 + 1, r0) * fc) * (1 - fr) + (rat(c0, r0 + 1) * (1 - fc) + rat(c0 + 1, r0 + 1) * fc) * fr; };
    const H2 = new Float32Array(rn * rn), out = (x, z) => Math.max(x0 - x, x - x1, z0 - z, z - z1);     // > 0 outside the model
    for (let r = 0; r < rn; r++) for (let c = 0; c < rn; c++) { const x = R.x0 + c * rc, z = R.z0 + r * rc, o = out(x, z);
      H2[r * rn + c] = o < -rc ? elev(x, z) - 40 : o <= 0 ? elev(x, z) : elev(x, z) + (rat(c, r) - elev(x, z)) * Math.min(o / 900, 1) ** 2 * (3 - 2 * Math.min(o / 900, 1)); }
    const rp = new Float32Array(rn * rn * 3), rnm = new Float32Array(rn * rn * 3), rsh = new Float32Array(rn * rn), H2at = (c, r) => H2[Math.min(Math.max(r, 0), rn - 1) * rn + Math.min(Math.max(c, 0), rn - 1)];
    const hz = Math.max(Math.hypot(sunDir.x, sunDir.z), 1e-4), ux = sunDir.x / hz, uz = sunDir.z / hz, rise = sunDir.y / hz;
    for (let r = 0; r < rn; r++) for (let c = 0; c < rn; c++) { const i = r * rn + c, x = R.x0 + c * rc, z = R.z0 + r * rc, h = H2[i];
      rp.set([x, h, z], 3 * i); const gx = (H2at(c + 1, r) - H2at(c - 1, r)) / (2 * rc), gz = (H2at(c, r + 1) - H2at(c, r - 1)) / (2 * rc), l = Math.hypot(gx, 1, gz); rnm.set([-gx / l, 1 / l, -gz / l], 3 * i);
      let s = 0, dd = rc; while (dd < 20000) { const px = x + ux * dd, pz = z + uz * dd; if (px < R.x0 || px > R.x1 || pz < R.z0 || pz > R.z1) break;
        const ov = rel(px, pz) - (h + 3 + rise * dd); if (ov > 0) { s = Math.max(s, Math.min(ov / 60, 1)); if (s >= 1) break; } if (h + rise * dd > 5100) break; dd += rc * (1 + dd / 6000); } rsh[i] = s; }
    const ri = []; for (let r = 0; r < rn - 1; r++) for (let c = 0; c < rn - 1; c++) { const a = r * rn + c; ri.push(a, a + rn, a + 1, a + 1, a + rn, a + rn + 1); }
    const rg = new THREE.BufferGeometry(); rg.setAttribute('position', new THREE.BufferAttribute(rp, 3)); rg.setAttribute('shadow', new THREE.BufferAttribute(rsh, 1)); rg.setAttribute('nrm', new THREE.BufferAttribute(rnm, 3)); rg.setIndex(ri);
    const rm = new THREE.Mesh(rg, new THREE.RawShaderMaterial({ glslVersion: THREE.GLSL3, vertexShader: document.getElementById('rvs').textContent, fragmentShader: document.getElementById('rfs').textContent, polygonOffset: true, polygonOffsetFactor: 2, polygonOffsetUnits: 4,
      uniforms: Object.assign({ uMvp: { value: mvp }, uTile: { value: tile }, uRock: { value: V3(lt.rock) }, uRockS: { value: V3(lt.rockS) }, uCam: { value: eye }, uHaze: terrain.material.uniforms.uHaze, uValley: { value: num('valley', 0.26) }, uRingR: { value: R.x1 } }, common) }));
    rm.frustumCulled = false; scene.add(rm); }
  if (num('cloud', 0)) { const ct = await new Promise(res => new THREE.TextureLoader().load('/design/redesign/assets/edge/tex/cloud.webp', t => { t.wrapS = t.wrapT = THREE.RepeatWrapping; t.generateMipmaps = true; t.minFilter = THREE.LinearMipmapLinearFilter; res(t); }));
    const cy = num('cloud', 0), S = 70000, cg = new THREE.BufferGeometry();
    cg.setAttribute('position', new THREE.BufferAttribute(new Float32Array([-S, cy, -S, S, cy, -S, -S, cy, S, S, cy, S]), 3)); cg.setAttribute('shadow', new THREE.BufferAttribute(new Float32Array(4), 1)); cg.setAttribute('nrm', new THREE.BufferAttribute(new Float32Array([0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0]), 3)); cg.setIndex([0, 2, 1, 1, 2, 3]);
    const cm = new THREE.Mesh(cg, new THREE.RawShaderMaterial({ glslVersion: THREE.GLSL3, side: THREE.DoubleSide, vertexShader: document.getElementById('rvs').textContent, fragmentShader: document.getElementById('cfs').textContent,
      uniforms: Object.assign({ uMvp: { value: mvp }, uCloudT: { value: ct }, uCam: { value: eye }, uHaze: terrain.material.uniforms.uHaze }, common) }));
    cm.frustumCulled = false; scene.add(cm); }

  // ---- the runs, as ribbons on the snow (only to judge the mountain with its live layer; the engine has its own)
  if (num('runs', 1)) { const col = { green: 0x178A4A, blue: 0x1B6FDB, red: 0xD8362C, black: 0x14171B }, night = lt.stars >= 0.25;
    const ribbon = (pts, w, color, lift, op, order) => { const p = [], ix = []; for (let i = 0; i < pts.length; i++) { const a = pts[Math.max(i - 1, 0)], b = pts[Math.min(i + 1, pts.length - 1)]; let dx = b[0] - a[0], dz = b[1] - a[1]; const l = Math.hypot(dx, dz) || 1; dx /= l; dz /= l;
        const y = elev(pts[i][0], pts[i][1]) + lift; p.push(pts[i][0] - dz * w, y, pts[i][1] + dx * w, pts[i][0] + dz * w, y, pts[i][1] - dx * w); if (i) { const o = (i - 1) * 2; ix.push(o, o + 1, o + 2, o + 1, o + 3, o + 2); } }
      const g = new THREE.BufferGeometry(); g.setAttribute('position', new THREE.BufferAttribute(new Float32Array(p), 3)); g.setIndex(ix);
      const m = new THREE.Mesh(g, new THREE.MeshBasicMaterial({ color, side: THREE.DoubleSide, transparent: op < 1, opacity: op, depthWrite: false })); m.frustumCulled = false; m.renderOrder = order; scene.add(m); };
    const mpp = 2 * st.dist * Math.tan(fov / 2 * rad) / Hh;                                 // metres per point at the target
    const sel = q.get('sel'), chosen = [];
    for (const p of D.pistes) if (p.named && (p.kind || 'run') === 'run') for (const s of p.segs) if (!s.area) { const pts = []; const g = s.g.map(v => [(v[1] - T.proj.lon0) * kx, -(v[0] - T.proj.lat0) * 111320]);
        for (let i = 0; i < g.length - 1; i++) { const n = Math.max(1, Math.ceil(Math.hypot(g[i + 1][0] - g[i][0], g[i + 1][1] - g[i][1]) / 18)); for (let j = 0; j < n; j++) pts.push([g[i][0] + (g[i + 1][0] - g[i][0]) * j / n, g[i][1] + (g[i + 1][1] - g[i][1]) * j / n]); } pts.push(g[g.length - 1]);
        if (sel && p.key === sel) { chosen.push(pts); continue; }
        const op = sel ? 0.5 : 1; ribbon(pts, 1.5 * mpp, night ? 0x2A3A5C : 0xffffff, 6, op, 1); ribbon(pts, 0.9 * mpp, night && p.color === 'black' ? 0xE8EEF8 : col[p.color], 6, op, 2); }
    // ?sel=<run key>: the chosen run as on K11 (a line of light in the slope colours, on a casing), over a dimmed screen (K09); 0.999 so that it sorts after the dimming
    if (sel) { const dim = new THREE.Mesh(sgeo, new THREE.ShaderMaterial({ depthTest: false, depthWrite: false, transparent: true, vertexShader: 'void main(){gl_Position=vec4(position.xy,0.,1.);}', fragmentShader: 'void main(){gl_FragColor=vec4(0.03,0.06,0.11,' + num('dim', 0.42).toFixed(2) + ');}' }));
      dim.frustumCulled = false; dim.renderOrder = 2.5; scene.add(dim);
      const sc = [[15, 0x3FA85F], [25, 0xF2C13D], [30, 0xF08A3C], [91, 0xDC3B33]], w = num('selw', 1);
      for (const pts of chosen) { ribbon(pts, 3.0 * mpp * w, night ? 0x081020 : 0xffffff, 8, 0.999, 3);
        const cls = pts.map((q, i) => { const a = pts[Math.max(i - 1, 0)], b = pts[Math.min(i + 1, pts.length - 1)], d = Math.hypot(b[0] - a[0], b[1] - a[1]) || 1, deg = Math.atan(Math.abs(elev(a[0], a[1]) - elev(b[0], b[1])) / d) / rad; return sc.findIndex(x => deg < x[0]); });
        let a = 0; for (let i = 1; i <= pts.length; i++) if (i === pts.length || cls[i] !== cls[a]) { ribbon(pts.slice(Math.max(a - 1, 0), i), 1.8 * mpp * w, sc[cls[a]][1], 8, 0.999, 4); a = i; } } } }

  // ---- the village after dark: a warm light for every real building
  if (lt.stars >= 0.25) { const g = new THREE.BufferGeometry(); g.setAttribute('position', new THREE.BufferAttribute(new Float32Array(L.village.flat()), 3));
    const m = new THREE.Points(g, new THREE.PointsMaterial({ color: 0xFFC56E, size: 2.2 * dpr, sizeAttenuation: false, transparent: true, opacity: 0.5 + 0.5 * Math.min(lt.stars / 0.6, 1), depthWrite: false })); m.frustumCulled = false; m.renderOrder = 3; scene.add(m); }
  renderer.render(scene, cam); window.done = true;
})();
