import zipfile
from pathlib import Path
root = Path(r'C:\Android')
zip_path = root / 'cmdline-tools.zip'
with zipfile.ZipFile(zip_path) as z:
    names = z.namelist()
    Path(root / 'zip_names.txt').write_text('\n'.join(names[:200]), encoding='utf-8')
    print('wrote', len(names))
