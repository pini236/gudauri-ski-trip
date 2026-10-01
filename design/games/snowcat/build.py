"""Builds index.html (one self-contained file) from game.src.html and levels.json."""
import json, pathlib
here = pathlib.Path(__file__).parent
out = (here/'game.src.html').read_text().replace('__LEVELS__', json.dumps(json.loads((here/'levels.json').read_text()), ensure_ascii=False, separators=(',', ':')))
(here/'index.html').write_text(out); print('index.html', len(out))
