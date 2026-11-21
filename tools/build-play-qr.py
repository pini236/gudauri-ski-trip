#!/usr/bin/env python3
"""The QR codes on the home page card (round 26, decision 72; round 37): the Google Play link with utm_medium=home_qr,
and the App Store link with ct=home_qr, so installs from a scan are told apart from taps on the signs.
Writes site/img/play-qr.svg and site/img/ios-qr.svg. Needs `pip install qrcode`. Run again only if a link changes."""
import pathlib, qrcode
LINKS = {
    'play-qr.svg': 'https://play.google.com/store/apps/details?id=io.github.pini236.skiapp&referrer=utm_source%3Dsite%26utm_medium%3Dhome_qr',
    'ios-qr.svg': 'https://apps.apple.com/app/gudi-gudauri-ski-map/id6825506333?ct=home_qr',
}
for name, url in LINKS.items():
    q = qrcode.QRCode(error_correction=qrcode.constants.ERROR_CORRECT_M, border=0)
    q.add_data(url); q.make(fit=True)
    m = q.get_matrix(); n = len(m)
    d = ''.join(f'M{x} {y}h1v1h-1z' for y, row in enumerate(m) for x, v in enumerate(row) if v)
    out = pathlib.Path(__file__).resolve().parent.parent / 'site/img' / name
    out.write_text(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="-2 -2 {n+4} {n+4}" shape-rendering="crispEdges">'
                   f'<rect x="-2" y="-2" width="{n+4}" height="{n+4}" fill="#fff"/><path fill="#13233A" d="{d}"/></svg>\n')
    print(out, n)
