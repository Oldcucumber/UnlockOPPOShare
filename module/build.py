# SPDX-License-Identifier: GPL-3.0-only
"""Local checked build; CI invokes the same Gradle tasks and packager."""
from pathlib import Path
import os, subprocess, sys

ROOT = Path(__file__).resolve().parent.parent
env = os.environ.copy()
# Stray quote characters in Windows PATH otherwise break the Gradle test worker.
if os.name == 'nt': env['PATH'] = env.get('PATH', '').replace('"', '')
wrapper = ROOT / ('gradlew.bat' if os.name == 'nt' else 'gradlew')
args = [str(wrapper), '--no-daemon', 'testDebugUnitTest', 'lintDebug', 'assembleRelease']
if os.name == 'nt': args = ['cmd', '/c'] + args
subprocess.run(args, cwd=ROOT, env=env, check=True)
subprocess.run([sys.executable, str(ROOT / 'module/package_artifacts.py')], cwd=ROOT, check=True)
