# StealthText v1.0.0 - Build notes

Package: com.cartoniq.stealthtext | minSdk 29 | target/compile 35 | Kotlin 1.9.24 | AGP 8.5.2 | Compose BOM 2024.06.00

## Build in Termux
```
unzip StealthText.zip && cd StealthText
~/gradle-8.9/bin/gradle assembleDebug
```
APK: app/build/outputs/apk/debug/app-debug.apk (use this to test first).

## Release (signed with YOUR keystore)
```
keytool -genkeypair -v -keystore ~/stealthtext.jks -alias stealth -keyalg RSA -keysize 2048 -validity 10000
mkdir -p /storage/emulated/0/Download/My_Keystores && cp ~/stealthtext.jks /storage/emulated/0/Download/My_Keystores/
export KS_FILE=$HOME/stealthtext.jks KS_PASS='yourpass' KS_ALIAS=stealth
~/gradle-8.9/bin/gradle assembleRelease    # APK
~/gradle-8.9/bin/gradle bundleRelease      # AAB for Play (refuses without keystore)
```
Use the same password for store and key. Without the env vars, assembleRelease gives an UNSIGNED apk (cannot install).

## How the hiding works
- Photo pixels have R,G,B bytes. The lowest 1-3 bits of a byte change the colour almost invisibly.
- Your text is encrypted first: PBKDF2-SHA256 (200,000 rounds) -> AES-256-GCM, random salt and IV each time.
- Encrypted bytes are written into the low bits in a scattered order that depends on your password.
- A 21-byte header (salt + masked bit depth and length) has no readable signature.
- Output is always PNG (lossless) with EXIF removed. JPEG or app compression destroys the data.

## Features in this version (v1.0.0)
- Hide and reveal: text, PDF/TXT files, voice notes (MediaRecorder, raw bytes, compressed), watermark tag.
- 1-3 bit slider, capacity meter, laser scan line, PNG only, EXIF stripped.
- Decoy password (second fake message in the same photo), Split Secret (XOR, two images).
- App PIN, fake crash after 3 wrong PINs, self-destruct after N wrong PINs (3-10, default 5).
- Biometric private vault (BiometricPrompt), shake to lock, on-screen LOCK button, calculator disguise icon.
- FLAG_SECURE on the whole app, clipboard clears after 30 s, no INTERNET permission.
- NEW: Invisible Text (message hidden in normal text with invisible characters), Smart hiding (writes only in busy image areas, photos up to 8 MP), Key file + password (all hide/reveal screens), Shamir split (any K of 3-5 images).
- NEW: Languages English, Hindi, Urdu (RTL), Arabic (RTL), Bengali. Picker in Settings > Language. Translations are machine-quality; please have a native speaker review them.
- Permissions: RECORD_AUDIO (voice notes) and USE_BIOMETRIC (vault). Photos use the system photo picker, no storage permission.

## Not included
Burn after reading, Scan Image, dark mode, R8 shrinking (off for first build).
Untranslated (stay English): the long Tip and About paragraphs, status lines with numbers, the Voice timer, and legal text.

## Known limits
- Never test by sending through WhatsApp as a photo. Send as Document.
- Photos over 25 megapixels are refused (memory).
- Light theme only.
- Smart hiding capacity depends on the photo; the meter shows an estimate and encoding tells you if it does not fit. Smart images must not be edited or resized.
- Key file: lose the file and the secret is gone. Use the exact same file bytes when revealing.
- Invisible Text: some apps strip invisible characters. Test with the same app first.
- Shake and the LOCK button work only while the app is open. Without a PIN they close the app.
- Disguise needs a PIN. Some launchers take a few seconds to change the icon.
- Self-destruct wipes app data (vault, PIN, settings). PNGs already in the gallery are not deleted.
- Settings has About, Privacy Policy, Contact, Delete My Data. Terms, Licenses, Rate, Share, Update and Bug Report from your old list are not added.
- R8 shrinking is off for the first build; turn on isMinifyEnabled after it works.

## Icon and Settings (added)
- Play Store icon: store/play_icon_512.png (512 px). Launcher: adaptive vector icon with a monochrome layer, plus PNG icons for mdpi to xxxhdpi.
- Settings has the 11 items: About (version from build.gradle), Privacy Policy, Terms and Conditions, Licenses and Copyright (Apache 2.0 notice for open-source parts), Data collection information, Delete My Data, Contact Support, Rate, Share, Check for Updates, Report a Bug (email with device info).
- No AdMob, no UMP, no INTERNET permission.
- Terms text is a draft: have it reviewed before publishing.

## Pre-flight check done
Sources were run through kotlinc 1.9.24 for syntax and structure only (Android and Compose libraries were not available, so full type checking was not possible). One real bug was found and fixed (PasswordField parameter order). The real build in Termux is the final test.

## Round 3 additions
- Duress PIN (Settings, needs an app PIN): entering it on the lock screen or in the calculator silently wipes the vault and temporary files, then opens the app empty. PIN and settings stay.
- Burn after reading (switch on hide screens): the receiving app opens it once, then refuses it. A vault copy is deleted after the first open. It works only inside this app on that phone; copies on other phones or after a reinstall can still open. Not a guarantee.
- Scan Image (Tools): finds StealthText images without a password and runs a statistical LSB check. Estimate only.
- Detectability (after hiding): Low / Medium / High estimate with a tip when High.
- Generate a cover photo: makes a film-grain style cover sized to fit your secret, good for Smart hiding.

## Languages (15)
English, Hindi, Urdu (RTL), Arabic (RTL), Bengali (full UI), plus Spanish, French, Indonesian, Turkish, Portuguese, Russian, Persian (RTL), Tamil, Telugu, Marathi (core UI: navigation, buttons, onboarding, Settings items, common messages). Anything not translated falls back to English. All translations are machine-quality: please have native speakers review them before release. Play Store listing text is not translated yet.
