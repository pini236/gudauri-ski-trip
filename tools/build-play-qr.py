#!/usr/bin/env python3
"""The QR code on the home page card (round 26, decision 72): the Google Play link with utm_medium=home_qr,
so installs from a scan are told apart from taps on the sign. Writes site/img/play-qr.svg.
Needs `pip install qrcode`. Run again only if the link changes."""
import pathlib, qrcode
URL = 'https://play.google.com/store/apps/details?id=io.github.pini236.skiapp&referrer=utm_source%3Dsite%26utm_medium%3Dhome_qr'
q = qrcode.QRCode(error_correction=qrcode.constants.ERROR_CORRECT_M, border=0)
q.add_data(URL); q.make(fit=True)
m = q.get_matrix(); n = len(m)
d = ''.join(f'M{x} {y}h1v1h-1z' for y, row in enumerate(m) for x, v in enumerate(row) if v)
out = pathlib.Path(__file__).resolve().parent.parent / 'site/img/play-qr.svg'
out.write_text(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="-2 -2 {n+4} {n+4}" shape-rendering="crispEdges">'
               f'<rect x="-2" y="-2" width="{n+4}" height="{n+4}" fill="#fff"/><path fill="#13233A" d="{d}"/></svg>\n')
print(out, n)
