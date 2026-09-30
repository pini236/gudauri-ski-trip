"""Builds index.html (one self-contained file) from game.src.html, profiles.json and the noon panorama."""
import base64, json, pathlib
here = pathlib.Path(__file__).parent
src = (here/'game.src.html').read_text()
prof = json.dumps(json.loads((here/'profiles.json').read_text()), separators=(',',':'))
pano = base64.b64encode((here/'../../../site/img/pano/pano-noon.webp').read_bytes()).decode()
out = src.replace('__PROFILES__', prof).replace('__PANO__', 'data:image/webp;base64,'+pano)
(here/'index.html').write_text(out)
print('index.html', len(out))
