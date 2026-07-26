import zipfile
from pathlib import Path
root = Path(r'C:\Android')
zip_path = root / 'cmdline-tools.zip'
out_path = root / 'zip_entries.txt'
with zipfile.ZipFile(zip_path) as z:
    names = z.namelist()
    out_path.write_text('\n'.join(names), encoding='utf-8')
print(f'wrote {len(names)} entries to {out_path}')
