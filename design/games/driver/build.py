"""Builds index.html (one self-contained file) from game.src.html, road.json and the village areas of the site map."""
import json, pathlib
here = pathlib.Path(__file__).parent; root = here.parents[2]
src = (here/'game.src.html').read_text()
road = json.loads((here/'road.json').read_text())
road = {'step': road['step'], 'pts': [[round(p[0]), round(p[1]), round(p[2])] for p in road['pts']]}
T = json.loads((root/'site/data/terrain.json').read_text())
out = src.replace('__ROAD__', json.dumps(road, separators=(',', ':'))).replace('__VILLAGE__', json.dumps(T['env']['village'], separators=(',', ':')))
(here/'index.html').write_text(out); print('index.html', len(out))
