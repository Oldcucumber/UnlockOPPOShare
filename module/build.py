# SPDX-License-Identifier: GPL-3.0-only
# Copyright (C) 2026 Old_cucumber
from pathlib import Path
import subprocess, os, zipfile, urllib.request, hashlib, json, shutil, re
import xml.etree.ElementTree as ET

PROJECT = Path(__file__).resolve().parent
BASE = PROJECT.parent
WORK = BASE / 'work/build'
DIST = BASE / 'dist'
DIST.mkdir(exist_ok=True)
if os.name != 'nt':
    raise SystemExit('This build script requires Windows and Android SDK build-tools 36.0.0.')
SDK = Path(os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME')
           or str(Path(os.environ['LOCALAPPDATA']) / 'Android/Sdk'))
TOOLS = SDK / 'build-tools/36.0.0'
ANDROID = SDK / 'platforms/android-36/android.jar'
API = WORK / 'deps/xposed-api-82.jar'
manifest_xml = ET.parse(PROJECT / 'AndroidManifest.xml').getroot()
VERSION = manifest_xml.attrib['{http://schemas.android.com/apk/res/android}versionName']
VERSION_CODE = int(manifest_xml.attrib['{http://schemas.android.com/apk/res/android}versionCode'])
if not re.fullmatch(r'[0-9]+(?:\.[0-9]+)*', VERSION):
    raise SystemExit('Unexpected version name.')
STEM = 'OPPO-Cast-Unlock-LSPosed-v' + VERSION
OUTPUT = DIST / (STEM + '.apk')

def run(args):
    p = subprocess.run([str(a) for a in args], capture_output=True, timeout=90)
    output = (p.stdout + p.stderr).decode('utf8', errors='replace')
    if p.returncode:
        raise RuntimeError(output)
    return output

for d in ['deps', 'classes', 'dex']:
    target = WORK / d
    if d != 'deps' and target.exists():
        # Only remove these two generated directories inside this project's build folder.
        assert WORK.resolve().is_relative_to(BASE.resolve())
        assert target.resolve().parent == WORK.resolve(), 'Unexpected build directory target'
        shutil.rmtree(target)
    target.mkdir(parents=True, exist_ok=True)
if not API.exists():
    with urllib.request.urlopen('https://api.xposed.info/de/robv/android/xposed/api/82/api-82.jar', timeout=30) as response:
        API.write_bytes(response.read())
assert hashlib.sha256(API.read_bytes()).hexdigest() == 'f48c635f1c7469fdec0e00ad2ea0b7a6b2f5b55065784a35b7ca3a84615e8e25'

sources = sorted((PROJECT / 'src').rglob('*.java'))
print('Compiling Java...', flush=True)
run(['javac', '-encoding', 'UTF-8', '-source', '8', '-target', '8', '-g:none',
     '-Xlint:-options', '-bootclasspath', ANDROID, '-classpath', API,
     '-d', WORK / 'classes', *sources])
with zipfile.ZipFile(WORK / 'classes.jar', 'w') as z:
    for f in (WORK / 'classes').rglob('*.class'):
        z.write(f, f.relative_to(WORK / 'classes').as_posix())
print('Building DEX and resources...', flush=True)
run(['java', '-cp', TOOLS / 'lib/d8.jar', 'com.android.tools.r8.D8',
     '--release', '--min-api', '28', '--lib', ANDROID, '--classpath', API,
     '--output', WORK / 'dex', WORK / 'classes.jar'])
run([TOOLS / 'aapt2.exe', 'compile', '--dir', PROJECT / 'res', '-o', WORK / 'resources.zip'])
run([TOOLS / 'aapt2.exe', 'link', '-o', WORK / 'unsigned.apk', '-I', ANDROID,
     '--manifest', PROJECT / 'AndroidManifest.xml', '-A', PROJECT / 'assets',
     WORK / 'resources.zip', '--min-sdk-version', '28', '--target-sdk-version', '36'])
with zipfile.ZipFile(WORK / 'unsigned.apk', 'a', zipfile.ZIP_DEFLATED) as z:
    z.write(WORK / 'dex/classes.dex', 'classes.dex')
run([TOOLS / 'zipalign.exe', '-f', '-p', '4', WORK / 'unsigned.apk', WORK / 'aligned.apk'])
key = WORK / 'Old_cucumber.jks'
if not key.exists():
    run(['keytool', '-genkeypair', '-keystore', key, '-storepass', 'old_cucumber',
         '-keypass', 'old_cucumber', '-alias', 'old_cucumber', '-keyalg', 'RSA',
         '-keysize', '3072', '-validity', '10000', '-dname', 'CN=Old_cucumber'])
print('Signing APK...', flush=True)
signer = ['java', '-jar', TOOLS / 'lib/apksigner.jar']
run([*signer, 'sign', '--ks', key, '--ks-key-alias', 'old_cucumber',
     '--ks-pass', 'pass:old_cucumber', '--key-pass', 'pass:old_cucumber',
     '--v1-signing-enabled', 'false', '--out', OUTPUT, WORK / 'aligned.apk'])
verify = run([*signer, 'verify', '--verbose', '--print-certs', OUTPUT])
run([TOOLS / 'zipalign.exe', '-c', '4', OUTPUT])
manifest = run([TOOLS / 'aapt.exe', 'dump', 'xmltree', OUTPUT, 'AndroidManifest.xml'])
assert 'E: activity ' not in manifest and 'E: activity-alias ' not in manifest
assert 'com.oldcucumber.oppocastunlock' in manifest and 'xposedmodule' in manifest
assert 'xposedscope' in manifest and 'xposedminversion' in manifest
with zipfile.ZipFile(OUTPUT) as z:
    assert z.testzip() is None
    assert z.read('assets/xposed_init').decode().strip() == 'com.oldcucumber.oppocastunlock.CastUnlockHook'
    assert 'classes.dex' in z.namelist()
    # Compile-only Xposed stubs must not become module classes.
    assert all('de/robv' not in f.relative_to(WORK / 'classes').as_posix()
               for f in (WORK / 'classes').rglob('*.class'))
(WORK / 'apk-verification.txt').write_text(verify + '\n' + manifest, encoding='utf8')
proof = {'package': 'com.oldcucumber.oppocastunlock', 'author': 'Old_cucumber',
         'version': VERSION, 'versionCode': VERSION_CODE,
         'name': 'OPPO互联全应用投屏', 'no_activity': True, 'scope': ['com.oplus.linker'],
         'signature_verified': True, 'zipalign_verified': True,
         'apk_bytes': OUTPUT.stat().st_size,
         'sha256': hashlib.sha256(OUTPUT.read_bytes()).hexdigest(),
         'certificate_sha256': re.search(r'certificate SHA-256 digest: ([0-9a-fA-F]+)', verify).group(1),
         'runtime_hook_tested': False, 'cast_tested': False}
proof_path = DIST / (STEM + '-build.json')
proof_path.write_text(json.dumps(proof, ensure_ascii=False, indent=2), encoding='utf8')
source_zip = DIST / (STEM + '-source.zip')
with zipfile.ZipFile(source_zip, 'w', zipfile.ZIP_DEFLATED) as z:
    for f in sorted(PROJECT.rglob('*')):
        if f.is_file() and '__pycache__' not in f.parts:
            z.write(f, 'module/' + f.relative_to(PROJECT).as_posix())
    for name in ['LICENSE', 'README.md', 'THIRD_PARTY.md', '.gitignore', '.gitattributes']:
        z.write(BASE / name, name)
with zipfile.ZipFile(source_zip) as z:
    assert z.testzip() is None
    assert 'LICENSE' in z.namelist() and 'module/build.py' in z.namelist()
    assert not any(n.endswith(('.jks', '.keystore', '.class', '.apk')) for n in z.namelist())
artifacts = [OUTPUT, source_zip, proof_path]
(DIST / (STEM + '-SHA256SUMS.txt')).write_text(''.join(
    hashlib.sha256(p.read_bytes()).hexdigest() + '  ' + p.name + '\n' for p in artifacts), encoding='ascii')
print(OUTPUT)
print(json.dumps(proof, indent=2))
