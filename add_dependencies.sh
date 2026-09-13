#!/bin/bash

GRADLE="app/build.gradle"

echo "Adding missing gradle dependencies..."
echo ""

# Find the dependencies block
DEPS_LINE=$(grep -n "dependencies {" "$GRADLE" | head -1 | cut -d: -f1)

if [ -z "$DEPS_LINE" ]; then
    echo "❌ Could not find dependencies block"
    exit 1
fi

echo "Found dependencies block at line: $DEPS_LINE"

# Add dependencies if not present
add_dependency() {
    local dep=$1
    if ! grep -q "$dep" "$GRADLE"; then
        echo "Adding: $dep"
        # Add after the first { of dependencies
        local insert_line=$((DEPS_LINE + 1))
        sed -i "${insert_line}i\\
    implementation '$dep'" "$GRADLE"
    else
        echo "✅ Already present: $dep"
    fi
}

# Add required dependencies
add_dependency "androidx.webkit:webkit:1.8.0"
add_dependency "org.opencv:opencv-android:4.8.0"
add_dependency "androidx.work:work-runtime-ktx:2.9.1"
add_dependency "androidx.mediarouter:mediarouter:1.7.0"
add_dependency "androidx.core:core:1.13.1"

echo ""
echo "✅ Dependencies added"
