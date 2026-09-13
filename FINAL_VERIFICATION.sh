#!/bin/bash

echo "🔍 FINAL DISABLED SOURCES ENABLEMENT VERIFICATION"
echo "=================================================="
echo ""

# Color codes
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

passed=0
failed=0

# Test function
test_condition() {
    local name=$1
    local condition=$2
    
    if eval "$condition"; then
        echo -e "${GREEN}✅${NC} $name"
        ((passed++))
    else
        echo -e "${RED}❌${NC} $name"
        ((failed++))
    fi
}

echo "PHASE 0: BLOCKING ISSUES"
echo "------------------------"
test_condition "Filters use correct package" "grep -q 'package com.calcvault.ui.filters' app/src/main/java/com/calcvault/filters/FilterControlPanel.kt"
test_condition "WatchTogether uses correct package" "grep -q 'package com.calcvault.watchtogether' app/src/main/java/com/calcvault/watchtogether/WatchTogetherActivity.kt"
test_condition "House duplicates removed" "[ ! -d app/src/main/java/com/calcvault/emotional/house ]"
test_condition "Mood consolidated" "[ ! -d app/src/main/java/com/calcvault/emotional/mood ] || [ ! -f app/src/main/java/com/calcvault/emotional/mood/MoodActivity.kt ]"

echo ""
echo "PHASE 1: FILE INTEGRITY"
echo "-----------------------"
test_condition "Browser (1 file)" "[ $(find app/src/main/java/com/calcvault/browser -name '*.kt' | wc -l) -eq 1 ]"
test_condition "Chat (4 files)" "[ $(find app/src/main/java/com/calcvault/chat -name '*.kt'| wc -l) -ge 4 ]"
test_condition "Filters (4 files)" "[ $(find app/src/main/java/com/calcvault/filters -name '*.kt' | wc -l) -eq 4 ]"
test_condition "House (7 files)" "[ $(find app/src/main/java/com/calcvault/house -name '*.kt' | wc -l) -ge 7 ]"
test_condition "Keyboard (2 files)" "[ $(find app/src/main/java/com/calcvault/keyboard -name '*.kt' | wc -l) -eq 2 ]"
test_condition "Mood (3 files)" "[ $(find app/src/main/java/com/calcvault/mood -name '*.kt' | wc -l) -ge 3 ]"
test_condition "Notifications (2+ files)" "[ $(find app/src/main/java/com/calcvault/notifications -name '*.kt' | wc -l) -ge 2 ]"
test_condition "Settings (6 files)" "[ $(find app/src/main/java/com/calcvault/ui/settings -name '*.kt' | wc -l) -ge 6 ]"
test_condition "Sync (11 files)" "[ $(find app/src/main/java/com/calcvault/sync -name '*.kt' | wc -l) -ge 11 ]"
test_condition "WatchTogether (2 files)" "[ $(find app/src/main/java/com/calcvault/watchtogether -name '*.kt' | wc -l) -eq 2 ]"

echo ""
echo "PHASE 2: MANIFEST ENTRIES"
echo "------------------------"
test_condition "PrivateBrowserActivity in manifest" "grep -q 'PrivateBrowserActivity' app/src/main/AndroidManifest.xml"
test_condition "MoodActivity in manifest" "grep -q 'MoodActivity' app/src/main/AndroidManifest.xml"
test_condition "SettingsActivity in manifest" "grep -q '.ui.settings.SettingsActivity' app/src/main/AndroidManifest.xml"
test_condition "CoupleThemeSettingsActivity in manifest" "grep -q 'CoupleThemeSettingsActivity' app/src/main/AndroidManifest.xml"
test_condition "IncomingScreenShareLauncher in manifest" "grep -q 'IncomingScreenShareLauncher' app/src/main/AndroidManifest.xml"
test_condition "WatchTogetherActivity in manifest" "grep -q 'WatchTogetherActivity' app/src/main/AndroidManifest.xml"
test_condition "FilterSettingsActivity in manifest" "grep -q 'FilterSettingsActivity' app/src/main/AndroidManifest.xml"
test_condition "CharacterChatActivityTemplate in manifest" "grep -q 'CharacterChatActivityTemplate' app/src/main/AndroidManifest.xml"
test_condition "MultiDeviceSyncService in manifest" "grep -q 'MultiDeviceSyncService' app/src/main/AndroidManifest.xml"

echo ""
echo "PHASE 3: GRADLE DEPENDENCIES"
echo "----------------------------"
test_condition "androidx.webkit:webkit" "grep -q 'androidx.webkit:webkit' app/build.gradle"
test_condition "OpenCV dependency removed (not required)" "! grep -q 'org.opencv:opencv-android' app/build.gradle"
test_condition "androidx.work:work-runtime-ktx" "grep -q 'androidx.work:work-runtime-ktx' app/build.gradle"
test_condition "androidx.mediarouter:mediarouter" "grep -q 'androidx.mediarouter:mediarouter' app/build.gradle"
test_condition "androidx.core:core" "grep -q 'androidx.core:core' app/build.gradle"

echo ""
echo "=================================================="
echo "SUMMARY"
echo "=================================================="
echo -e "Total Passed: ${GREEN}$passed${NC}"
echo -e "Total Failed: ${RED}$failed${NC}"

if [ $failed -eq 0 ]; then
    echo ""
    echo -e "${GREEN}✅ ALL VERIFICATIONS PASSED${NC}"
    echo ""
    echo "Next steps:"
    echo "  1. Run: ./gradlew clean compileDebugKotlin"
    echo "  2. If compilation succeeds, run: ./gradlew assembleDebug"
    echo "  3. Deploy and test: ./gradlew installDebug"
    exit 0
else
    echo ""
    echo -e "${RED}⚠️  SOME VERIFICATIONS FAILED${NC}"
    echo "Please review the failed checks above"
    exit 1
fi
