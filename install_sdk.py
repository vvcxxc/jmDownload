import os, zipfile, shutil, subprocess, sys
from pathlib import Path

root = Path(r'C:\Android')
zip_path = root / 'cmdline-tools.zip'
extract_root = root / 'cmdline-tools-extract'
sdk_root = root / 'sdk'
latest_root = sdk_root / 'cmdline-tools' / 'latest'
bin_dir = latest_root / 'bin'
java_root = root / 'jdk-17.0.12+7'
java_exe = java_root / 'bin' / 'java.exe'
log_path = root / 'install_sdk.log'

with log_path.open('w', encoding='utf-8') as log:
    log.write('start\n')
    print('start', file=sys.stderr)
    if not zip_path.exists():
        log.write(f'missing zip: {zip_path}\n')
        raise SystemExit(1)
    if extract_root.exists():
        shutil.rmtree(extract_root)
    extract_root.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(zip_path) as z:
        z.extractall(extract_root)
        log.write('extracted\n')
    candidates = [extract_root / 'cmdline-tools', extract_root / 'tools']
    source_root = None
    for c in candidates:
        if c.exists():
            source_root = c
            break
    if source_root is None:
        log.write('no source root found\n')
        raise SystemExit(2)
    log.write(f'source root: {source_root}\n')
    sdk_root.mkdir(parents=True, exist_ok=True)
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
    log.write('copied contents\n')
    sdkmanager = bin_dir / 'sdkmanager.bat'
    log.write(f'sdkmanager exists: {sdkmanager.exists()}\n')
    if not sdkmanager.exists():
        raise SystemExit(3)
    env = os.environ.copy()
    env['JAVA_HOME'] = str(java_root)
    env['ANDROID_HOME'] = str(sdk_root)
    env['ANDROID_SDK_ROOT'] = str(sdk_root)
    env['PATH'] = str(java_root / 'bin') + ';' + env.get('PATH', '')
    cmd = [str(sdkmanager), '--sdk_root=' + str(sdk_root), 'platforms;android-35', 'build-tools;35.0.0', 'platform-tools']
    log.write('running: ' + ' '.join(cmd) + '\n')
    completed = subprocess.run(cmd, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    log.write(completed.stdout)
    log.write(f'\nreturncode={completed.returncode}\n')
    if completed.returncode != 0:
        raise SystemExit(completed.returncode)
