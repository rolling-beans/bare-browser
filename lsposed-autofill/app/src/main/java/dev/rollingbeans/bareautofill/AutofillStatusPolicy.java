package dev.rollingbeans.bareautofill;

/**
 * Maps only Chromium's "Android Autofill service is Google" status.
 *
 * <p>All policy, unsupported-service and unknown-service statuses are left intact. Unknown
 * preferences fail closed to the original status. Numeric values correspond to Chromium's
 * AndroidAutofillAvailabilityStatus enum in the Bare-pinned Chromium 157 revision.
 */
final class AutofillStatusPolicy {
    static final int AVAILABLE = 0;
    static final int GOOGLE_SERVICE_REJECTED = 6;
    static final int SETTING_TURNED_OFF = 7;

    private AutofillStatusPolicy() {}

    static int remap(int originalStatus, Boolean userEnabledPlatformAutofill) {
        if (originalStatus != GOOGLE_SERVICE_REJECTED || userEnabledPlatformAutofill == null) {
            return originalStatus;
        }
        return userEnabledPlatformAutofill ? AVAILABLE : SETTING_TURNED_OFF;
    }
}
