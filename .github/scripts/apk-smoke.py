# SPDX-License-Identifier: GPL-3.0-only
"""Launch the actual packaged APK after instrumentation to check R8 and startup."""
from pathlib import Path
import subprocess
import re
import time
import xml.etree.ElementTree as ET

PACKAGE = 'com.oldcucumber.oppocastunlock'
out = Path('work/ui-screenshots')
apk, = Path('work/built-apk').glob('*.apk')

def adb(*args):
    return subprocess.check_output(['adb', *args], timeout=30)

subprocess.run(['adb', 'uninstall', PACKAGE], capture_output=True, timeout=30)
adb('install', str(apk))
adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.MainActivity')
for _ in range(12):
    time.sleep(1)
    adb('shell', 'uiautomator', 'dump', '/sdcard/selection.xml')
    xml = adb('shell', 'cat', '/sdcard/selection.xml')
    nodes = list(ET.fromstring(xml).iter('node'))
    texts = {node.get('text') for node in nodes}
    (out / 'installed-page.xml').write_bytes(xml)
    # Compose can merge a row's switch role and texts into one accessibility node.
    loaded = any(PACKAGE in (text or '') for text in texts)
    if loaded and 'UnlockOPPOshare' in texts and '搜索应用' in texts and '个应用已显示' in texts:
        assert '应用加载失败' not in texts
        break
else:
    (out / 'installed-page.png').write_bytes(adb('exec-out', 'screencap', '-p'))
    raise AssertionError('Packaged APK did not load applications: ' + repr(sorted(texts)))
assert adb('shell', 'pidof', PACKAGE).strip(), 'Packaged app exited'
time.sleep(1)
(out / 'installed-page.png').write_bytes(adb('exec-out', 'screencap', '-p'))

def app_rows(tree):
    rows = []
    for node in tree.iter('node'):
        bounds = [int(n) for n in re.findall(r'\d+', node.get('bounds', ''))]
        if node.get('checkable') == 'true' and len(bounds) == 4 and bounds[2] - bounds[0] > 500:
            rows.append((node, bounds))
    return rows

row, bounds = app_rows(ET.fromstring(xml))[0]
before = row.get('checked')
adb('shell', 'input', 'tap', str((bounds[0] + bounds[2]) // 2), str((bounds[1] + bounds[3]) // 2))
time.sleep(1)
adb('shell', 'uiautomator', 'dump', '/sdcard/selection.xml')
toggled = adb('shell', 'cat', '/sdcard/selection.xml')
assert app_rows(ET.fromstring(toggled))[0][0].get('checked') != before, 'Packaged app did not toggle the selection'
(out / 'installed-toggled.png').write_bytes(adb('exec-out', 'screencap', '-p'))
print('Packaged APK launched, loaded applications and toggled a selection')
