#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
set +e
./gradlew --no-daemon connectedDebugAndroidTest
result=$?
mkdir -p work/ui-screenshots
adb pull /sdcard/Android/data/com.oldcucumber.oppocastunlock/files/test-screenshots/. work/ui-screenshots || true
exit "$result"
