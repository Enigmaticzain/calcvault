# AndroidManifest.xml - Couple Theme Activity Registration

## Add This Activity Declaration

Add the following to your `AndroidManifest.xml` file in the `<application>` section:

```xml
<!-- Couple Theme Settings Activity -->
<activity
    android:name="com.calcvault.ui.settings.CoupleThemeSettingsActivity"
    android:exported="false"
    android:screenOrientation="portrait"
    android:theme="@style/Theme.AppCompat.NoActionBar" />
```

## Complete Example

Here's how it should look in context:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.calcvault">

    <!-- Permissions -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />

    <application
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/Theme.CalcVault">

        <!-- Main Activities -->
        <activity
            android:name="com.calcvault.ui.calculator.CalculatorActivity"
            android:exported="true"
            android:screenOrientation="portrait">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <activity
            android:name="com.calcvault.ui.main.MainVaultActivity"
            android:exported="false"
            android:screenOrientation="portrait" />

        <activity
            android:name="com.calcvault.ui.chat.ChatActivity"
            android:exported="false"
            android:screenOrientation="portrait" />

        <!-- Settings Activities -->
        <activity
            android:name="com.calcvault.ui.settings.SettingsActivity"
            android:exported="false"
            android:screenOrientation="portrait" />

        <!-- ADD THIS: Couple Theme Settings Activity -->
        <activity
            android:name="com.calcvault.ui.settings.CoupleThemeSettingsActivity"
            android:exported="false"
            android:screenOrientation="portrait"
            android:theme="@style/Theme.AppCompat.NoActionBar" />

        <!-- Other Activities -->
        <activity
            android:name="com.calcvault.ui.call.CallActivity"
            android:exported="false"
            android:screenOrientation="portrait" />

        <activity
            android:name="com.calcvault.ui.listen.ListenTogetherActivity"
            android:exported="false"
            android:screenOrientation="portrait" />

        <!-- ... rest of your activities ... -->

    </application>

</manifest>
```

## Attributes Explained

| Attribute | Value | Purpose |
|-----------|-------|---------|
| `android:name` | `com.calcvault.ui.settings.CoupleThemeSettingsActivity` | Full class path |
| `android:exported` | `false` | Not accessible from other apps (security) |
| `android:screenOrientation` | `portrait` | Lock to portrait orientation |
| `android:theme` | `@style/Theme.AppCompat.NoActionBar` | Use app theme without action bar |

## Verification

After adding the activity, verify:

1. ✅ Activity is in correct package: `com.calcvault.ui.settings`
2. ✅ Class name matches: `CoupleThemeSettingsActivity`
3. ✅ `android:exported="false"` for security
4. ✅ `android:screenOrientation="portrait"` for consistency
5. ✅ Activity is inside `<application>` tags
6. ✅ No duplicate activity declarations

## Build & Test

After updating the manifest:

```bash
# Clean build
./gradlew clean

# Build the project
./gradlew build

# Run on device
./gradlew installDebug
```

## Troubleshooting

### Activity Not Found Error
```
android.content.ActivityNotFoundException: Unable to find explicit activity class
```

**Solution:**
- Verify package name matches: `com.calcvault.ui.settings`
- Verify class name matches: `CoupleThemeSettingsActivity`
- Rebuild the project: `./gradlew clean build`

### Manifest Merge Error
```
Manifest merger failed
```

**Solution:**
- Check for duplicate activity declarations
- Verify XML syntax is correct
- Check for conflicting attributes

### Activity Crashes on Launch
```
java.lang.ClassNotFoundException: com.calcvault.ui.settings.CoupleThemeSettingsActivity
```

**Solution:**
- Verify the class file exists at the correct path
- Check that the class is properly compiled
- Verify no typos in the package or class name

## Alternative: Using Intent Filters

If you want to make the activity accessible via intent:

```xml
<activity
    android:name="com.calcvault.ui.settings.CoupleThemeSettingsActivity"
    android:exported="false"
    android:screenOrientation="portrait">
    <intent-filter>
        <action android:name="com.calcvault.COUPLE_THEME_SETTINGS" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</activity>
```

Then launch it with:

```kotlin
val intent = Intent("com.calcvault.COUPLE_THEME_SETTINGS")
startActivity(intent)
```

## Complete Manifest Template

Here's a complete minimal manifest with the couple theme activity:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.calcvault">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />

    <application
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name">

        <activity
            android:name="com.calcvault.ui.calculator.CalculatorActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <activity
            android:name="com.calcvault.ui.main.MainVaultActivity"
            android:exported="false" />

        <activity
            android:name="com.calcvault.ui.chat.ChatActivity"
            android:exported="false" />

        <activity
            android:name="com.calcvault.ui.settings.SettingsActivity"
            android:exported="false" />

        <activity
            android:name="com.calcvault.ui.settings.CoupleThemeSettingsActivity"
            android:exported="false"
            android:screenOrientation="portrait" />

    </application>

</manifest>
```

## Validation Checklist

- [ ] Activity declaration added to manifest
- [ ] Package name is correct: `com.calcvault.ui.settings`
- [ ] Class name is correct: `CoupleThemeSettingsActivity`
- [ ] Activity is inside `<application>` tags
- [ ] `android:exported="false"` is set
- [ ] No XML syntax errors
- [ ] Project builds successfully
- [ ] Activity launches without crashes
- [ ] Settings UI displays correctly
- [ ] Settings persist after app restart

## Next Steps

1. Update AndroidManifest.xml with the activity declaration
2. Build the project: `./gradlew build`
3. Run on device: `./gradlew installDebug`
4. Test launching the settings activity from MainVaultActivity
5. Verify all settings work correctly

---

**The couple theme settings activity is now registered and ready to use!**
