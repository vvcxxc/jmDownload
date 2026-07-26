import os, zipfile, shutil
from pathlib import Path

root = Path(r'C:\Android')
zip_path = root / 'cmdline-tools.zip'
extract_root = root / 'cmdline-tools-extract'
sdk_root = root / 'sdk'
latest_root = sdk_root / 'cmdline-tools' / 'latest'

if extract_root.exists():
    shutil.rmtree(extract_root)
extract_root.mkdir(parents=True, exist_ok=True)

with zipfile.ZipFile(zip_path) as z:
    z.extractall(extract_root)

source_root = None
for p in [extract_root / 'cmdline-tools', extract_root / 'tools']:
    if p.exists():
        source_root = p
        break
if source_root is None:
    raise SystemExit('source root not found')

latest_root.mkdir(parents=True, exist_ok=True)
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

Path(root / 'copy_sdk_files.done').write_text('done', encoding='utf-8')
print('done')
