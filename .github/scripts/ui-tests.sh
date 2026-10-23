#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
set +e
./gradlew --no-daemon -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true connectedDebugAndroidTest
result=$?
mkdir -p work/ui-screenshots
adb pull /sdcard/Android/data/com.oldcucumber.oppocastunlock/files/test-screenshots/. work/ui-screenshots || true
if [[ "$result" == 0 && ! -s work/ui-screenshots/selection-page.png ]]; then
  echo 'UI screenshot was not collected'
  exit 1
fi
exit "$result"
