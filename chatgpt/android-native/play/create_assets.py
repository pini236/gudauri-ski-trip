#!/usr/bin/env python3
"""Render store artwork from the app's own vector shapes, fonts and palette."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "play" / "assets"
OUT.mkdir(exist_ok=True)
FONT = ROOT / "app/src/main/res/font"

def icon():
    factor = 4
    im = Image.new("RGB", (512*factor,512*factor), "#EEF2F5")
    draw = ImageDraw.Draw(im)
    def polygon(points,fill):
        draw.polygon([(x/108*512*factor,y/108*512*factor) for x,y in points], fill=fill)
    polygon([(4,74),(34,27),(48,48),(65,17),(104,74)],"#C4D3E0")
    polygon([(25,42),(34,27),(43,42),(34,37)],"white")
    polygon([(53,35),(65,17),(78,35),(64,29)],"white")
    polygon([(76,77),(81,77),(81,99),(76,99)],"#13233A")
    polygon([(19,55),(89,55),(89,77),(19,77),(9,66)],"#1F5FC4")
    im.resize((512,512), Image.Resampling.LANCZOS).save(OUT/"icon-512.png")

def feature():
    scale=2
    im=Image.new("RGB",(1024*scale,500*scale),"#EEF2F5")
    draw=ImageDraw.Draw(im)
    def polygon(points,fill):
        draw.polygon([(x*scale,y*scale) for x,y in points],fill=fill)
    polygon([(0,345),(190,100),(275,240),(420,55),(620,320),(755,175),(1024,380),(1024,500),(0,500)],"#C4D3E0")
    polygon([(125,185),(190,100),(250,190),(192,162)],"white")
    polygon([(345,160),(420,55),(505,165),(420,129)],"white")
    polygon([(0,393),(190,268),(300,345),(492,225),(725,415),(870,305),(1024,389),(1024,500),(0,500)],"#DDE6EE")
    polygon([(536,244),(960,244),(960,351),(536,351),(505,297)],"#1F5FC4")
    title=ImageFont.truetype(str(FONT/"karantina_bold.ttf"),72*scale)
    body=ImageFont.truetype(str(FONT/"plex_hebrew_regular.ttf"),23*scale)
    draw.text((921*scale,263*scale),"גודאורי",font=title,fill="white",anchor="rt",direction="rtl")
    draw.text((944*scale,379*scale),"ההר, המסלולים והדרך למעלה",font=body,fill="#13233A",anchor="rt",direction="rtl")
    im.resize((1024,500),Image.Resampling.LANCZOS).save(OUT/"feature-1024x500.png")

if __name__ == "__main__":
    icon()
    feature()
