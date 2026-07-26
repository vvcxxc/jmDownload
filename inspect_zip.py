import zipfile
z = zipfile.ZipFile(r'C:\Android\cmdline-tools.zip')
for name in z.namelist()[:80]:
    print(name)
