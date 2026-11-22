package com.redondoguibi.raydiance.client.pipeline;

/** Standalone and shaderpack switches are independent. Selected, enabled packs own the frame. */
final class BackendSelector {
    private BackendSelector() {}

    static boolean shadersRequested(boolean selected, boolean shadersEnabled) {
        return selected && shadersEnabled;
    }

    static boolean standaloneActive(boolean shadersRequested, boolean standaloneEnabled) {
        return !shadersRequested && standaloneEnabled;
    }
}
