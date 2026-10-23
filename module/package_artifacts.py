# SPDX-License-Identifier: GPL-3.0-only
"""Package an already-built APK, source archive and checksums without publishing."""
from pathlib import Path
import argparse, hashlib, json, os, subprocess, zipfile, re, struct

ROOT = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser()
parser.add_argument('--variant', choices=['release', 'debug'], default='release')
args = parser.parse_args()
variant = args.variant
folder = ROOT / 'module/build/outputs/apk' / variant
metadata = json.loads((folder / 'output-metadata.json').read_text('utf8'))
element = metadata['elements'][0]
version = element['versionName']
assert re.fullmatch(r'\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?', version), 'Invalid version name'
sdk = Path(os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME')
           or (Path(os.environ['LOCALAPPDATA']) / 'Android/Sdk' if os.name == 'nt' else Path.home() / 'Android/Sdk'))
tools = sdk / 'build-tools/36.0.0'
apk = folder / element['outputFile']
def run(cmd):
    return subprocess.run([str(x) for x in cmd], cwd=ROOT, capture_output=True, check=True).stdout.decode('utf8')
signature = run(['java', '-jar', tools / 'lib/apksigner.jar', 'verify', '--verbose', '--print-certs', apk])
certificate = re.search(r'certificate SHA-256 digest: ([0-9a-fA-F]+)', signature).group(1)
suffix = '.exe' if os.name == 'nt' else ''
run([tools / ('zipalign' + suffix), '-c', '4', apk])
manifest = run([tools / ('aapt' + suffix), 'dump', 'xmltree', apk, 'AndroidManifest.xml'])
assert 'com.oldcucumber.oppocastunlock.MainActivity' in manifest or '".MainActivity"' in manifest
assert 'SelectionProvider' in manifest and 'xposedscope' in manifest
with zipfile.ZipFile(apk) as z:
    assert z.testzip() is None
    assert z.read('assets/xposed_init').decode().strip() == 'com.oldcucumber.oppocastunlock.CastUnlockHook'
    definitions = []
    for n in z.namelist():
        if re.fullmatch(r'classes\d*\.dex', n):
            d = z.read(n)
            sc, so = struct.unpack_from('<II', d, 56)
            tc, to = struct.unpack_from('<II', d, 64)
            cc, co = struct.unpack_from('<II', d, 96)
            def text(i):
                at = struct.unpack_from('<I', d, so + i * 4)[0]
                while d[at] & 128: at += 1
                at += 1
                return d[at:d.index(0, at)].decode('utf8', errors='replace')
            for i in range(cc):
                type_id = struct.unpack_from('<I', d, co + i * 32)[0]
                definitions.append(text(struct.unpack_from('<I', d, to + type_id * 4)[0]))
    assert 'Lcom/oldcucumber/oppocastunlock/CastUnlockHook;' in definitions
    assert not any(n.startswith('Lde/robv/android/xposed/') for n in definitions)

dist = ROOT / 'dist'; dist.mkdir(exist_ok=True)
stem = 'OPPO-Cast-Unlock-LSPosed-v' + version + ('-debug' if variant == 'debug' else '')
output = dist / (stem + '.apk'); output.write_bytes(apk.read_bytes())
commit = run(['git', 'rev-parse', 'HEAD']).strip()
proof = {'version': version, 'versionCode': element['versionCode'], 'variant': variant,
         'package': metadata['applicationId'], 'commit': commit, 'has_ui': True,
         'certificate_sha256': certificate, 'signature_verified': True,
         'zipalign_verified': True, 'xposed_stubs_bundled': False,
         'sha256': hashlib.sha256(output.read_bytes()).hexdigest(),
         'real_oppo_cast_tested': False, 'published': False}
proof_file = dist / (stem + '-build.json')
proof_file.write_text(json.dumps(proof, ensure_ascii=False, indent=2), encoding='utf8')
paths = run(['git', 'ls-files', '--cached', '--others', '--exclude-standard']).splitlines()
source = dist / (stem + '-source.zip')
with zipfile.ZipFile(source, 'w', zipfile.ZIP_DEFLATED) as z:
    for name in sorted(set(paths)):
        p = ROOT / name
        if not p.is_file(): continue
        assert not name.startswith(('work/', 'dist/'))
        assert not name.endswith(('.jks', '.keystore', '.apk'))
        z.write(p, name)
    z.writestr('SOURCE_COMMIT', commit + '\n')
with zipfile.ZipFile(source) as z:
    assert 'LICENSE' in z.namelist() and '.github/workflows/build.yml' in z.namelist()
sums = dist / (stem + '-SHA256SUMS.txt')
sums.write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest() + '  ' + p.name + '\n'
                        for p in [output, source, proof_file]), encoding='ascii')
print(json.dumps(proof, indent=2)); print(output)
