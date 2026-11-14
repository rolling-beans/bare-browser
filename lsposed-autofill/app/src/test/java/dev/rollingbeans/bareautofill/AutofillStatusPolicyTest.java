package dev.rollingbeans.bareautofill;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class AutofillStatusPolicyTest {
    @Test
    public void googleProviderEnabledBecomesAvailable() {
        assertEquals(0, AutofillStatusPolicy.remap(6, true));
    }

    @Test
    public void googleProviderDisabledBecomesSwitchableButOff() {
        assertEquals(7, AutofillStatusPolicy.remap(6, false));
    }

    @Test
    public void absentPreferenceDoesNotOverrideGoogleRejection() {
        assertEquals(6, AutofillStatusPolicy.remap(6, null));
    }

    @Test
    public void allOtherStatusesRemainUnchanged() {
        for (int status : new int[] {0, 1, 2, 3, 4, 5, 7, 8}) {
            assertEquals(status, AutofillStatusPolicy.remap(status, true));
            assertEquals(status, AutofillStatusPolicy.remap(status, false));
            assertEquals(status, AutofillStatusPolicy.remap(status, null));
        }
    }
}
