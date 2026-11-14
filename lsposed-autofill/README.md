# Bare Autofill Bridge (LSPosed proof of concept)

A minimal **legacy Xposed API 82** module that allows **Bare Browser** to use
Android's already-selected **Autofill with Google** provider. This **does not**
enable Chrome's built-in Google Password Manager, Chrome sign-in, or Google
account synchronization.

This is a **runtime experiment**, not an established fix for password filling.
The first thing to verify is whether Bare's Autofill option becomes enabled and
stays selected after relaunch; the second is whether the OS provider actually
returns password suggestions.

## Source behavior

Bare's pinned Chromium 157 source returns status `6` from
`AutofillClientProviderUtils.getAndroidAutofillFrameworkAvailability(PrefService)`
when Android Autofill is provided by `com.google.android.gms`. The C++ browser
startup path calls that Java method via JNI, and can reset the platform-autofill
preference when the status isn't `AVAILABLE`.

This hook uses `XC_MethodHook.afterHookedMethod` to intercept a **result of 6
only**, reading `PrefService.getBoolean("autofill.using_virtual_view_structure")`
in the *same browser process*. Bare 2.0.3's R8 output renames this method to
`PrefService.b(String)` (verified by scanning DEX method definitions and both call
sites in the release APK). The hook resolves `getBoolean` first and falls back to
the verified runtime method `b`; an unknown signature fails closed:

| Original status | Saved pref | New status |
| --- | --- | --- |
| 6 (system service is Google) | true | 0 (available) |
| 6 (system service is Google) | false | 7 (setting off, selectable) |
| 6 (system service is Google) | cannot read | unchanged (fail closed) |
| Any other status | any | unchanged |

The hook does **not** modify preferences itself, hook `system_server` or
Google Play Services, read passwords or form contents, or redirect network calls.

It intentionally does not override Chromium's method that skips persisting
Google as a preferred third-party provider. This does not block the primary
toggle test, but may affect some provider-switching scenarios. Native JNI
calls are expected to pass through the hooked Java method on supported ART /
LSPosed builds, but this is **not yet verified** on Bare.

### Requirements and limitations

- Bare package: **`org.barebrowser`**, main process only (secondary processes ignored).
- Requires a compatible LSPosed/Vector framework with legacy API hook support.
- Depends on Chromium's **runtime Java class and method names**. R8 or future
  Chromium changes can invalidate the hook. The module logs a diagnostic and
  leaves the original result untouched if installation or the preference read
  fails.
- The bridge is limited to status codes verified for the Bare-pinned Chromium
  `800628ad1ca1fd0c51e3b43b5b2bca081e81c3f6`.
- Passkeys and Google account/browser-level integration are **not** guaranteed
  by enabling Android Autofill.

## GitHub Actions build

The workflow `.github/workflows/bare-autofill-lsposed.yml` builds on ordinary
`ubuntu-24.04` runners. The build uses AGP 8.7.3, Gradle 8.9, JDK 17 and
Android API 35. Xposed API 82 is a **compileOnly** dependency, not embedded
into the APK.

On this branch, push a change under `lsposed-autofill/` to start a build. Open
**Actions → Bare Autofill LSPosed APK** to find the uploaded
`app-debug.apk` artifact after a successful run. It uses an ephemeral Android
debug signing key and is intended **only for testing**. Rebuilds from separate
runners may use different signing keys and therefore may not install as updates.
**To move from test 0.1.0 to 0.1.1:** disable and uninstall only the LSPosed
module APK (`dev.rollingbeans.bareautofill`), then install the new APK and
re-enable the Bare scope. Do not uninstall Bare or clear browser data.

To build locally with Android SDK and Gradle 8.9 installed:

```bash
cd lsposed-autofill
gradle :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

## Device test

1. Install the *test* APK and enable **Bare Autofill Bridge** in LSPosed/Vector.
   Check `[BareAutofillBridge] Hook installed ... PrefService reader=b` for
   official Bare 2.0.3. An absent reader means this APK is incompatible.
2. Select only `org.barebrowser` in module scope. Never enable system processes.
3. Force-stop Bare and launch it again. In Android Settings, verify that
   **Autofill with Google** is already selected.
4. Open **Bare → Settings → Autofill options**. The previously disabled
   **Autofill using another service** radio option should now be selectable.
   Select it, force-stop Bare, launch again, and check if the choice persists.
5. Test a normal website with a known saved password using the *real* Android
   Autofill provider. The toggle working does not prove password filling works.
6. Inspect LSPosed logs for `[BareAutofillBridge]` only if something fails.
   No passwords, URLs or field values are written by this module.

**Rollback:** disable the module in LSPosed and force-stop/restart Bare.
If it fails to start, disable the module via the LSPosed manager before
trying any destructive data clearing.

This module is separate from the Chromium source patch:
[rolling-beans/bare-browser PR #1](https://github.com/rolling-beans/bare-browser/pull/1).
