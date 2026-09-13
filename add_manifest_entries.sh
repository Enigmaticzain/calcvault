#!/bin/bash

MANIFEST="app/src/main/AndroidManifest.xml"

# Check if entries already exist
echo "Adding missing manifest entries..."
echo ""

# Function to add activity if not present
add_activity_if_missing() {
    local activity_name=$1
    local activity_line=$2
    
    if ! grep -q "$activity_name" "$MANIFEST"; then
        echo "Adding: $activity_name"
        # Find the line number of </application>
        local line_num=$(grep -n "</application>" "$MANIFEST" | head -1 | cut -d: -f1)
        # Insert before </application>
        sed -i "${line_num}i\\
$activity_line" "$MANIFEST"
    else
        echo "✅ Already present: $activity_name"
    fi
}

add_activity_if_missing "CoupleThemeSettingsActivity" "        <activity android:name=\".ui.settings.CoupleThemeSettingsActivity\" android:exported=\"false\" android:screenOrientation=\"portrait\" android:windowSoftInputMode=\"adjustResize\" />"

add_activity_if_missing "IncomingScreenShareLauncher" "        <activity android:name=\".ui.settings.IncomingScreenShareLauncher\" android:exported=\"false\" android:screenOrientation=\"portrait\" />"

add_activity_if_missing "WatchTogetherActivity" "        <activity android:name=\".watchtogether.WatchTogetherActivity\" android:exported=\"false\" android:screenOrientation=\"portrait\" />"

add_activity_if_missing "FilterSettingsActivity" "        <activity android:name=\".ui.filters.FilterSettingsActivity\" android:exported=\"false\" android:screenOrientation=\"portrait\" />"

add_activity_if_missing "MultiDeviceSyncService" "        <service android:name=\".sync.MultiDeviceSyncService\" android:exported=\"false\" />"

echo ""
echo "✅ Manifest entries addition complete"
