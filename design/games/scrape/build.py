"""Builds index.html (one self-contained file) from game.src.html and three views from the village."""
import base64, pathlib
here = pathlib.Path(__file__).parent; root = here.parents[2]
img = lambda n: 'data:image/webp;base64,'+base64.b64encode((root/f'site/img/pano/pano-{n}.webp').read_bytes()).decode()
out = (here/'game.src.html').read_text().replace('__DAWN__', img('dawn')).replace('__MORNING__', img('morning')).replace('__NOON__', img('noon'))
(here/'index.html').write_text(out); print('index.html', len(out))
