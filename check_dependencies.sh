#!/bin/bash

GRADLE="app/build.gradle"

echo "GRADLE DEPENDENCY CHECKER"
echo "========================="
echo ""

# Dependencies needed for disabled features
declare -a deps=(
    "androidx.webkit:webkit:"
    "org.opencv:opencv-android:"
    "androidx.work:work-runtime-ktx:"
    "androidx.mediarouter:mediarouter:"
    "androidx.core:core:"
)

echo "Required Dependencies:"
for dep in "${deps[@]}"; do
    if grep -q "$dep" "$GRADLE"; then
        version=$(grep "$dep" "$GRADLE" | head -1 | sed "s/.*$dep//")
        echo "✅ $dep found: $version"
    else
        echo "❌ $dep NOT FOUND"
    fi
done
