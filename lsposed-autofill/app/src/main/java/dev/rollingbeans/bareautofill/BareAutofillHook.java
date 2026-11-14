package dev.rollingbeans.bareautofill;

import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Narrow, fail-closed bridge for the Android Autofill provider already selected by the user.
 * No passwords, form values, account data, or URLs are read or logged.
 */
public final class BareAutofillHook implements IXposedHookLoadPackage {
    private static final String TAG = "[BareAutofillBridge] ";
    private static final String TARGET_PACKAGE = "org.barebrowser";
    private static final String PROVIDER_UTILS =
            "org.chromium.chrome.browser.autofill.AutofillClientProviderUtils";
    private static final String PREF_SERVICE = "org.chromium.components.prefs.PrefService";
    private static final String PLATFORM_PREF = "autofill.using_virtual_view_structure";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam packageParam) {
        // Keep all secondary/sandboxed processes and every other app unmodified.
        if (!TARGET_PACKAGE.equals(packageParam.packageName)
                || !TARGET_PACKAGE.equals(packageParam.processName)) {
            return;
        }

        try {
            Class<?> utilsClass = XposedHelpers.findClassIfExists(
                    PROVIDER_UTILS, packageParam.classLoader);
            Class<?> prefClass = XposedHelpers.findClassIfExists(
                    PREF_SERVICE, packageParam.classLoader);
            if (utilsClass == null || prefClass == null) {
                XposedBridge.log(TAG + "Hook unavailable: Chromium classes not found");
                return;
            }

            AtomicBoolean mappedOnce = new AtomicBoolean(false);
            AtomicBoolean failedOnce = new AtomicBoolean(false);

            XposedHelpers.findAndHookMethod(
                    utilsClass,
                    "getAndroidAutofillFrameworkAvailability",
                    prefClass,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (param.hasThrowable() || !(param.getResult() instanceof Integer)) {
                                return;
                            }
                            int originalStatus = (Integer) param.getResult();
                            if (originalStatus != AutofillStatusPolicy.GOOGLE_SERVICE_REJECTED) {
                                return;
                            }
                            if (param.args.length != 1 || param.args[0] == null) {
                                return;
                            }

                            try {
                                Object preference = XposedHelpers.callMethod(
                                        param.args[0], "getBoolean", PLATFORM_PREF);
                                if (!(preference instanceof Boolean)) {
                                    return;
                                }

                                int mapped = AutofillStatusPolicy.remap(
                                        originalStatus, (Boolean) preference);
                                param.setResult(mapped);
                                if (mappedOnce.compareAndSet(false, true)) {
                                    XposedBridge.log(TAG
                                            + "Mapped Google service availability to " + mapped
                                            + " (0=available, 7=off)");
                                }
                            } catch (Throwable error) {
                                // A Chromium update or changed PrefService must not break Bare.
                                if (failedOnce.compareAndSet(false, true)) {
                                    XposedBridge.log(TAG + "Preference read failed ("
                                            + error.getClass().getSimpleName()
                                            + "); preserving original result");
                                }
                            }
                        }
                    });
            XposedBridge.log(TAG + "Hook installed in " + TARGET_PACKAGE);
        } catch (Throwable error) {
            XposedBridge.log(TAG + "Hook installation failed ("
                    + error.getClass().getSimpleName() + ")");
        }
    }
}
