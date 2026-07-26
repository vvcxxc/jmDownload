import os, zipfile, shutil
from pathlib import Path

root = Path(r'C:\Android')
zip_path = root / 'cmdline-tools.zip'
extract_root = root / 'cmdline-tools-extract'
sdk_root = root / 'sdk'
latest_root = sdk_root / 'cmdline-tools' / 'latest'
bin_dir = latest_root / 'bin'

if extract_root.exists():
    shutil.rmtree(extract_root)
extract_root.mkdir(parents=True, exist_ok=True)

with zipfile.ZipFile(zip_path) as z:
    z.extractall(extract_root)

source_root = None
for candidate in [extract_root / 'cmdline-tools', extract_root / 'tools']:
    if candidate.exists():
        source_root = candidate
        break

if source_root is None:
    raise SystemExit('source root not found')

latest_root.mkdir(parents=True, exist_ok=True)
bin_dir.mkdir(parents=True, exist_ok=True)

for child in source_root.iterdir():
    dst = latest_root / child.name
    if child.is_dir():
        if dst.exists():
            shutil.rmtree(dst)
        shutil.copytree(child, dst)
    else:
        if dst.exists():
            dst.unlink()
        shutil.copy2(child, dst)

(root / 'sdk_layout_ready.txt').write_text('sdk layout ready', encoding='utf-8')
print('done')
