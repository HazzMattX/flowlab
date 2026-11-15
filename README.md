# Flow

By [HazzMattX](https://github.com/HazzMattX)

Flow is an offline, on-device automation app for Android — an Apple Shortcuts-style builder for GrapheneOS and other de-Googled setups. Create **Macros** (tap to run) and **Automations** (run on a trigger) from the same step library, editor, and run engine.

- Package: `com.hazzmattx.flowlab`
- No account, no cloud sync. Flows are stored on-device.
- Install the signed APK from [Releases](../../releases).

## Install

1. Download `Flow-v0.1.apk` from the latest release.
2. This is the first official build (version 0.1, versionCode 1). It does not install over earlier test builds — export your flows first (top ⋮ menu → Export flows), uninstall the old version, then install this one and import your flows back. If the old version was set as Device Owner, remove that first with ADB (see Device Owner below).
3. Allow install from unknown sources for your browser / file manager when prompted, then install.
4. Grant permissions as Flow asks for them — only the ones for the steps and triggers you actually use.

## What it does

**Macros** run when you tap them (or from a home-screen widget or Quick Settings tile). **Automations** run when a trigger fires. Both use the same steps.

Steps include: show message, notification (including one with a button that runs a flow), vibrate, wait, open URL / app, set variable, get time / location, HTTP GET and POST, stop-if, If / Else If / Else / End If, While / End While, Repeat / End Repeat, Run another flow, set toggles and panels, set brightness / volume / screen timeout, copy to clipboard, share text, variable operations (add, subtract, multiply, divide, uppercase, lowercase, append, prepend, length), toggle flashlight / blink flashlight, Ask for input, Speak text (needs a separate TTS engine on GrapheneOS, e.g. RHVoice), choose from a list, get the clipboard into a variable, random number, comment, send text / make a call, get current value, set alarm or timer, and save / read a text file.

Triggers include: time (including weekday-only), charging starts / stops, battery low / below % / full, location enter / exit, Wi-Fi connects / disconnects / specific network, Bluetooth device connects, screen on / off, app opened, sunrise / sunset, headphones connect / disconnect, device unlocked, NFC tag tapped, and every X minutes / hours.

Variables: built-ins for time, date, hour, minute, battery, charging, Wi-Fi, Bluetooth, ringer, DND, NFC, location, airplane mode, and vibrate-when-ringing, plus your own variables. Use `${name}` in free-text fields. If/While conditions support `==`, `!=`, `contains`, `>`, `<`, `<=`, `>=`, including `HH:mm` time comparisons.

Also included: a separate Run Log screen (top ⋮ menu, capped at 200 entries), search on the main grid and in every step / trigger / app / settings picker, import / export of all flows as JSON, per-card duplicate, duplicate / disable a single step, a home-screen icon per flow, and a home-screen widget and Quick Settings tile that run a flow you pick.

## Permissions — why each one is there

Flow only uses a permission when the step or trigger you chose needs it:

- Location (fine, coarse, background) + foreground service: location enter/exit triggers and sunrise/sunset.
- Exact alarms: time triggers.
- Usage stats: app-opened trigger (can lag ~20 seconds).
- Notification policy access: Do Not Disturb steps.
- Modify system settings (`WRITE_SETTINGS`): brightness and screen-timeout steps — Flow opens that settings page on first use.
- Bluetooth / Wi-Fi state and change, NFC, camera (flashlight), vibrate, notifications, boot completed, internet (only for the HTTP GET/POST steps you add).

## Device Owner (optional, advanced)

Most of Flow works as a regular app. Only the direct Set Wi-Fi and Set Bluetooth steps need Device Owner. Without it they log that Device Owner is required instead of silently failing; the settings-panel steps work instead.

If you want it, Flow's own in-app instructions (top ⋮ menu → Device Owner setup) are:

1. Install Flow, then remove all accounts from Settings → Accounts (the `dpm` command fails while accounts exist), or factory reset if it still fails.
2. From a computer with ADB:
   `adb shell dpm set-device-owner com.hazzmattx.flowlab/.FlowDeviceAdmin`
3. Re-add your accounts, reopen Flow, and the status should say Device Owner: Yes.

Device Owner persists across over-the-top updates with the same package and signing key. It only needs redoing after a full uninstall, a factory reset, or manual removal as an active admin. To remove it later:
`adb shell dpm remove-active-admin com.hazzmattx.flowlab/.FlowDeviceAdmin`
then uninstall Flow normally.

Warning: a Device Owner app is hard to uninstall normally — remove it with ADB first.

## Known limits in v0.1

- Screen and Wi-Fi triggers depend on Flow's foreground service staying alive.
- App-opened triggers need usage access and can lag ~20 seconds.
- Speak text needs a TTS engine with an English voice installed; Flow fails fast with a message if none is available.
- Choose from list and Get clipboard only work when Flow is in the foreground; background runs skip them.
- Every X minutes / hours timing is not exact; Android battery optimization can delay it.
- NFC tag triggers may show an app chooser if another app also handles NFC tags.

## Build from source

Requires JDK 17, the Android SDK (platform android-34, build-tools 34.0.0), and `kotlinc`. See the usage notes at the top of `build-portable.sh`, then from the repo root:

```sh
./build-portable.sh
```

That compiles the Kotlin sources in `src/main/java/com/hazzmattx/flowlab/`, links the manifest (min SDK 26, target SDK 34), and signs the APK. The signing keystore is not included in this package; build with your own key, or with the original key to install over an existing install.

**Never commit or publish `flow.keystore`.** It is the signing key that lets updates install over earlier versions. If it is lost or replaced, users must uninstall and reinstall, losing their flows.

## Contributing / reporting issues

Open an issue with the Flow version, your phone / OS (e.g. Pixel 10 Pro, GrapheneOS, Android 16), the exact steps to reproduce, and what the Run Log shows.

## License

No license has been chosen yet. Pick one before publishing the source publicly (MIT and Apache-2.0 are the usual permissive choices for an app like this).
