#!/usr/bin/env python3
"""Draws a touch circle on the screen recording at every tap the script made, and checks that each one shows.

Why (13.11.2026): the emulator's "show touches" does not reach the screen recording, so the taps were invisible. The
script logs every tap (seconds since the recording started, x, y in device pixels); this draws a soft white ring on the
video at that place and time (about a second), like Android's own touch marker. It only marks where the script touched;
the app is untouched. The original is kept next to it.

usage: touches.py <video.mp4> <taps.txt> <device_w> <device_h> <out.mp4> <report.txt>
taps.txt: one tap per line, "<t seconds> <x> <y>".  Exit 1 if a tap's circle cannot be found in the result."""
import struct, subprocess, sys, zlib

SIZE = 96          # the circle's side in video pixels
LEAD, TAIL = 0.15, 1.0  # seconds before the press and after it that the circle stays

def png(path):
    c = (SIZE - 1) / 2.0
    rows = []
    for y in range(SIZE):
        row = bytearray([0])
        for x in range(SIZE):
            d = ((x - c) ** 2 + (y - c) ** 2) ** 0.5
            if d <= c - 1:
                a = 235 if d >= c - 7 else 120          # a solid ring and a soft fill
            else:
                a = 0
            row += bytes((255, 255, 255, a))
        rows.append(bytes(row))
    raw = b"".join(rows)
    def chunk(t, data):
        return struct.pack(">I", len(data)) + t + data + struct.pack(">I", zlib.crc32(t + data) & 0xFFFFFFFF)
    open(path, "wb").write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0))
                           + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))

def probe(video):
    out = subprocess.run(["ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries", "stream=width,height",
                          "-of", "csv=p=0:s=x", video], capture_output=True, text=True).stdout.strip()
    w, h = out.split("x")
    return int(w), int(h)

def crop_gray(video, t, x, y):
    """the SIZE x SIZE pixels around (x, y) at time t, as gray bytes"""
    r = subprocess.run(["ffmpeg", "-loglevel", "error", "-ss", f"{max(t, 0):.2f}", "-i", video, "-frames:v", "1",
                        "-vf", f"crop={SIZE}:{SIZE}:{x - SIZE // 2}:{y - SIZE // 2},format=gray", "-f", "rawvideo", "-"],
                       capture_output=True)
    return r.stdout

def mad(a, b):
    if not a or not b or len(a) != len(b):
        return -1.0
    return sum(abs(p - q) for p, q in zip(a, b)) / len(a)

def main():
    video, taps_file, dw, dh, out, report = sys.argv[1:7]
    dw, dh = int(dw), int(dh)
    vw, vh = probe(video)
    sx, sy = vw / dw, vh / dh
    taps = []
    for line in open(taps_file):
        p = line.split()
        if len(p) == 3:
            taps.append((float(p[0]), int(float(p[1]) * sx), int(float(p[2]) * sy)))
    if not taps:
        print("no taps"); return 0
    png("/tmp/touch-circle.png")
    chain, last = [], "[0:v]fps=30[b0]"
    parts = [last]
    for i, (t, x, y) in enumerate(taps):
        x = min(max(x, SIZE // 2), vw - SIZE // 2); y = min(max(y, SIZE // 2), vh - SIZE // 2)
        taps[i] = (t, x, y)
        parts.append(f"[b{i}][1:v]overlay={x - SIZE // 2}:{y - SIZE // 2}:enable='between(t,{t - LEAD:.2f},{t + TAIL:.2f})'[b{i + 1}]")
    cmd = ["ffmpeg", "-loglevel", "error", "-y", "-i", video, "-i", "/tmp/touch-circle.png", "-filter_complex", ";".join(parts),
           "-map", f"[b{len(taps)}]", "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "23", out]
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0:
        print(r.stderr[-800:]); return 1
    bad, lines = 0, []
    for i, (t, x, y) in enumerate(taps):
        before = crop_gray(video, t - 0.5, x, y)                 # the original, before the touch
        during_orig = crop_gray(video, t + 0.15, x, y)           # the original while touching
        during_new = crop_gray(out, t + 0.15, x, y)              # the result while touching
        d_new = mad(during_new, during_orig)                     # how much the circle changed the frame there
        d_native = mad(during_orig, before)                      # whether the emulator itself drew anything
        ok = d_new > 8
        bad += 0 if ok else 1
        lines.append(f"tap {i + 1} at +{t:.1f}s ({x},{y}) circle changes the frame by {d_new:.1f} (needs > 8): {'SEEN' if ok else 'NOT SEEN'}; "
                     f"the original changed there by {d_native:.1f}")
    open(report, "w").write("\n".join(lines) + "\n")
    print("\n".join(lines))
    return 1 if bad else 0

sys.exit(main())
