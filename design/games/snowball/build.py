"""Builds index.html (one self-contained file) from game.src.html and the noon view from the village."""
import base64, pathlib
here = pathlib.Path(__file__).parent; root = here.parents[2]
pano = base64.b64encode((root/'site/img/pano/pano-noon.webp').read_bytes()).decode()
out = (here/'game.src.html').read_text().replace('__PANO__', 'data:image/webp;base64,'+pano)
(here/'index.html').write_text(out); print('index.html', len(out))
