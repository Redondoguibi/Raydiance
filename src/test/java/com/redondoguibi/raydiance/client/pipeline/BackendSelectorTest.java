package com.redondoguibi.raydiance.client.pipeline;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class BackendSelectorTest {
    @Test void disablingStandaloneDoesNotDisableSelectedShaderpack() {
        assertTrue(BackendSelector.shadersRequested(true, true));
        assertFalse(BackendSelector.standaloneActive(true, false));
        assertFalse(BackendSelector.standaloneActive(true, true));
    }

    @Test void disablingShaderpacksRestoresTheIndependentStandaloneChoice() {
        assertFalse(BackendSelector.shadersRequested(true, false));
        assertFalse(BackendSelector.shadersRequested(false, true));
        assertTrue(BackendSelector.standaloneActive(false, true));
        assertFalse(BackendSelector.standaloneActive(false, false));
    }
}
