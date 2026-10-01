"""Builds index.html (one self-contained file) from game.src.html and flights.json."""
import base64, json, pathlib
here = pathlib.Path(__file__).parent; root = here.parents[2]
out = (here/'game.src.html').read_text().replace('__FLIGHTS__', json.dumps(json.loads((here/'flights.json').read_text()), ensure_ascii=False, separators=(',', ':')))
(here/'index.html').write_text(out); print('index.html', len(out))
