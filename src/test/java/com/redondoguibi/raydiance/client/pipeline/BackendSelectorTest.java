package com.redondoguibi.raydiance.client.pipeline;

import static org.junit.jupiter.api.Assertions.*;
import com.redondoguibi.raydiance.client.pipeline.BackendSelector.Choice;
import org.junit.jupiter.api.Test;

class BackendSelectorTest {
    private static BackendSelector.Decision decide(PackBackend preference, boolean integration, boolean worldPost,
                                                   boolean hlsl, boolean glsl, boolean rejected) {
        return BackendSelector.decide(new BackendSelector.Inputs(true, true, true, preference, integration, worldPost, hlsl, glsl, rejected));
    }

    @Test void effectsOffOrNoPackNeverRunsAPack() {
        assertEquals(Choice.NONE, BackendSelector.decide(new BackendSelector.Inputs(false, true, true, PackBackend.AUTO, true, true, true, true, false)).choice());
        assertEquals(Choice.BUILTIN, BackendSelector.decide(new BackendSelector.Inputs(true, false, false, PackBackend.AUTO, true, true, false, false, false)).choice());
        assertEquals(Choice.NONE, BackendSelector.decide(new BackendSelector.Inputs(true, true, false, PackBackend.AUTO, true, true, true, true, false)).choice(),
                "a pack that failed to load must not fall back to the built-in visuals silently");
    }

    @Test void autoPrefersDirectAndFallsBackToOpenGlWithAReason() {
        assertEquals(Choice.DIRECT, decide(PackBackend.AUTO, true, true, true, true, false).choice());
        var fallback = decide(PackBackend.AUTO, true, false, true, true, false);
        assertEquals(Choice.OPENGL, fallback.choice());
        assertFalse(fallback.reason().isBlank(), "fallbacks are never silent");
        assertEquals(Choice.OPENGL, decide(PackBackend.AUTO, false, true, true, true, false).choice(), "integration off means OpenGL");
        assertEquals(Choice.OPENGL, decide(PackBackend.AUTO, true, true, true, true, true).choice(), "rejected HLSL falls back");
        assertEquals(Choice.OPENGL, decide(PackBackend.AUTO, true, true, false, true, false).choice(), "GLSL-only packs use OpenGL");
        assertEquals(Choice.NONE, decide(PackBackend.AUTO, true, false, true, false, false).choice(), "HLSL-only packs pause without DX12");
    }

    @Test void explicitBackendsNeverSwitch() {
        assertEquals(Choice.NONE, decide(PackBackend.DIRECT, true, false, true, true, false).choice());
        assertEquals(Choice.DIRECT, decide(PackBackend.DIRECT, true, true, true, true, false).choice());
        assertEquals(Choice.OPENGL, decide(PackBackend.OPENGL, true, true, true, true, false).choice());
        assertEquals(Choice.NONE, decide(PackBackend.OPENGL, true, true, true, false, false).choice());
    }

    @Test void parsesAndCyclesBackendIds() {
        assertEquals(PackBackend.OPENGL, PackBackend.parse("opengl"));
        assertEquals(PackBackend.AUTO, PackBackend.parse("garbage"));
        assertEquals(PackBackend.AUTO, PackBackend.OPENGL.next());
    }
}
