package com.redondoguibi.raydiance.client.pipeline;

/**
 * Pure backend decision, independent of Minecraft and graphics. Every fallback carries a visible reason:
 * a pack never switches renderer silently.
 */
public final class BackendSelector {
    public enum Choice { NONE, BUILTIN, OPENGL, DIRECT }
    public record Decision(Choice choice, String reason) {}
    /**
     * @param directWorldPost Direct is installed, API-compatible, replacing terrain and offers world-post-v1
     * @param directRejected  Direct rejected this pack's HLSL since the last reload
     */
    public record Inputs(boolean effects, boolean packSelected, boolean packLoaded, PackBackend preference,
                         boolean integration, boolean directWorldPost, boolean canRunDirect, boolean canRunOpenGl,
                         boolean directRejected) {}

    public static Decision decide(Inputs in) {
        if (!in.effects()) return new Decision(Choice.NONE, "Effects are disabled");
        if (!in.packSelected()) return new Decision(Choice.BUILTIN, "");
        if (!in.packLoaded()) return new Decision(Choice.NONE, "The shaderpack could not be loaded");
        String direct = directBlocker(in);
        return switch (in.preference()) {
            case DIRECT -> direct == null ? new Decision(Choice.DIRECT, "")
                    : new Decision(Choice.NONE, "Paused (backend: Direct only): " + direct);
            case OPENGL -> in.canRunOpenGl() ? new Decision(Choice.OPENGL, "Backend set to OpenGL")
                    : new Decision(Choice.NONE, "The pack has no GLSL programs; it needs Direct DX12");
            case AUTO -> direct == null ? new Decision(Choice.DIRECT, "")
                    : in.canRunOpenGl() ? new Decision(Choice.OPENGL, "OpenGL fallback: " + direct)
                    : new Decision(Choice.NONE, "Paused: " + direct + "; the pack has no GLSL programs");
        };
    }
    private static String directBlocker(Inputs in) {
        if (!in.integration()) return "Direct integration is disabled";
        if (!in.directWorldPost()) return "Direct DX12 world post-processing is not active";
        if (!in.canRunDirect()) return "the pack has no direct.hlsl";
        if (in.directRejected()) return "Direct rejected the pack's HLSL";
        return null;
    }
    private BackendSelector() {}
}
