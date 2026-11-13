# HuaStat

Personal Android app that shows live signal, speed and data usage from a Huawei B-series router (tested target: B628-350).

## Build with GitHub Actions (no PC needed)

1. Create a new GitHub repository (public = unlimited free build minutes).
2. Upload all files from this folder, keeping the structure (including the hidden `.github` folder).
3. Open the **Actions** tab, then **Build APK**, and wait for the green check (about 5 minutes).
4. Open the finished run, download **HuaStat-debug-apk**, unzip it, and install `app-debug.apk` on your phone.

## Use

Connect your phone to the router's WiFi, open the app, tap **Settings** and enter your router address (default 192.168.8.1), username and password.

The password is stored on your phone only, in plain app storage. That is fine for personal use.
