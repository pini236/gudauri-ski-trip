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
  const [relief, cover, tile, horizon, cirrus] = await Promise.all([tex(q.get('relief') || 'relief.webp'), tex(q.get('cover') || 'cover.webp'), tex('tile.png', 2), tex('horizon.png', 1), tex('cirrus.png', 1)]);

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
      uRock: { value: V3(lt.rock) }, uRockS: { value: V3(lt.rockS) }, uCam: { value: eye }, uHaze: { value: new THREE.Vector3(st.dist * hazeK[0], st.dist * hazeK[1], num('hzmax', 0.72)) }, uValley: { value: num('valley', 0.26) }, uDetail: { value: num('detail', 1) }, uRipple: { value: num('ripple', L.k.ripple) } }, common) }));
  terrain.frustumCulled = false;
  const sgeo = new THREE.BufferGeometry(); sgeo.setAttribute('position', new THREE.BufferAttribute(new Float32Array([-1, -1, 0, 3, -1, 0, -1, 3, 0]), 3));
  const hz = L.tex.horizon;
  const sky = new THREE.Mesh(sgeo, new THREE.RawShaderMaterial({ glslVersion: THREE.GLSL3, depthWrite: false, depthTest: false, vertexShader: document.getElementById('svs').textContent, fragmentShader: document.getElementById('sfs').textContent,
    uniforms: Object.assign({ uInv: { value: inv }, uEye: { value: eye }, uHorizon: { value: horizon }, uCirrus: { value: cirrus }, uZen: { value: V3(lt.zen) }, uBand: { value: V3(lt.band) }, uBandOn: { value: lt.bandOn },
      uStars: { value: lt.stars }, uSunUp: { value: lt.alt > 0 && lt.direct > 0 && lt.stars < 1 ? 1 : 0 }, uStrip: { value: new THREE.Vector4(hz.lo_deg, hz.hi_deg, hz.far_m, 0) }, uFar: { value: num('far', 1) }, uSkyTop: { value: num('skytop', 11) }, uAir: { value: new THREE.Vector2(num('air0', 0.55), num('air1', 0.30)) } }, common) }));
  sky.frustumCulled = false; sky.renderOrder = -1;
  const scene = new THREE.Scene(); scene.add(sky); scene.add(terrain);

  // ---- the runs, as ribbons on the snow (only to judge the mountain with its live layer; the engine has its own)
  if (num('runs', 1)) { const col = { green: 0x178A4A, blue: 0x1B6FDB, red: 0xD8362C, black: 0x14171B }, night = lt.stars >= 0.25;
    const ribbon = (pts, w, color, lift, op, order) => { const p = [], ix = []; for (let i = 0; i < pts.length; i++) { const a = pts[Math.max(i - 1, 0)], b = pts[Math.min(i + 1, pts.length - 1)]; let dx = b[0] - a[0], dz = b[1] - a[1]; const l = Math.hypot(dx, dz) || 1; dx /= l; dz /= l;
        const y = elev(pts[i][0], pts[i][1]) + lift; p.push(pts[i][0] - dz * w, y, pts[i][1] + dx * w, pts[i][0] + dz * w, y, pts[i][1] - dx * w); if (i) { const o = (i - 1) * 2; ix.push(o, o + 1, o + 2, o + 1, o + 3, o + 2); } }
      const g = new THREE.BufferGeometry(); g.setAttribute('position', new THREE.BufferAttribute(new Float32Array(p), 3)); g.setIndex(ix);
      const m = new THREE.Mesh(g, new THREE.MeshBasicMaterial({ color, side: THREE.DoubleSide, transparent: op < 1, opacity: op, depthWrite: false })); m.frustumCulled = false; m.renderOrder = order; scene.add(m); };
    const mpp = 2 * st.dist * Math.tan(fov / 2 * rad) / Hh;                                 // metres per point at the target
    for (const p of D.pistes) if (p.named && (p.kind || 'run') === 'run') for (const s of p.segs) if (!s.area) { const pts = []; const g = s.g.map(v => [(v[1] - T.proj.lon0) * kx, -(v[0] - T.proj.lat0) * 111320]);
        for (let i = 0; i < g.length - 1; i++) { const n = Math.max(1, Math.ceil(Math.hypot(g[i + 1][0] - g[i][0], g[i + 1][1] - g[i][1]) / 18)); for (let j = 0; j < n; j++) pts.push([g[i][0] + (g[i + 1][0] - g[i][0]) * j / n, g[i][1] + (g[i + 1][1] - g[i][1]) * j / n]); } pts.push(g[g.length - 1]);
        ribbon(pts, 1.5 * mpp, night ? 0x2A3A5C : 0xffffff, 6, 1, 1); ribbon(pts, 0.9 * mpp, night && p.color === 'black' ? 0xE8EEF8 : col[p.color], 6, 1, 2); } }

  // ---- the village after dark: a warm light for every real building
  if (lt.stars >= 0.25) { const g = new THREE.BufferGeometry(); g.setAttribute('position', new THREE.BufferAttribute(new Float32Array(L.village.flat()), 3));
    const m = new THREE.Points(g, new THREE.PointsMaterial({ color: 0xFFC56E, size: 2.2 * dpr, sizeAttenuation: false, transparent: true, opacity: 0.5 + 0.5 * Math.min(lt.stars / 0.6, 1), depthWrite: false })); m.frustumCulled = false; m.renderOrder = 3; scene.add(m); }
  renderer.render(scene, cam); window.done = true;
})();
