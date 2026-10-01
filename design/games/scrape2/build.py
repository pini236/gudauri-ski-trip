"""Builds index.html (one self-contained file) from game.src.html, the recordings in audio/ and a view for the reflection."""
import base64, json, pathlib
here = pathlib.Path(__file__).parent; root = here.parents[2]
snd = {p.stem: base64.b64encode(p.read_bytes()).decode() for p in sorted((here/'audio').glob('*.wav'))}
refl = 'data:image/webp;base64,' + base64.b64encode((root/'site/img/pano/pano-morning.webp').read_bytes()).decode()
out = (here/'game.src.html').read_text().replace('__SND__', json.dumps(snd)).replace('__REFLECT__', refl)
(here/'index.html').write_text(out); print('index.html', len(out))
