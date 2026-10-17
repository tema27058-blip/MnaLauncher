from pathlib import Path
import shutil
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
res=root/'app/src/main/res'
foreground=root/'mna_foreground.png'
legacy=root/'ic_launcher.png'
if not foreground.is_file() or not legacy.is_file():
    raise SystemExit('Jupiter PNG files missing at repository root')
for sub in ['drawable-nodpi','mipmap-nodpi','mipmap-anydpi-v26','values']:
    (res/sub).mkdir(parents=True,exist_ok=True)
shutil.copyfile(foreground,res/'drawable-nodpi/mna_foreground.png')
shutil.copyfile(legacy,res/'mipmap-nodpi/ic_launcher.png')
shutil.copyfile(legacy,res/'mipmap-nodpi/ic_launcher_round.png')
xml='<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android"><background android:drawable="@color/mna_background"/><foreground android:drawable="@drawable/mna_foreground"/></adaptive-icon>'
for name in ['ic_launcher','ic_launcher_round']:
    (res/'mipmap-anydpi-v26'/f'{name}.xml').write_text(xml,encoding='utf-8')
(res/'values/mna_colors.xml').write_text('<resources><color name="mna_background">#100F13</color></resources>',encoding='utf-8')
assets=root/'app/src/main/assets'
assets.mkdir(parents=True,exist_ok=True)
if (root/'wallpaper.jpg').is_file():
    shutil.copyfile(root/'wallpaper.jpg',assets/'wallpaper.jpg')
manifest=ET.parse(root/'app/src/main/AndroidManifest.xml')
app=manifest.getroot().find('application')
ns='{http://schemas.android.com/apk/res/android}'
assert app.get(ns+'label')=='MnaLauncher'
assert app.get(ns+'icon')=='@mipmap/ic_launcher'
print('MnaLauncher resources prepared from existing Jupiter PNGs')
