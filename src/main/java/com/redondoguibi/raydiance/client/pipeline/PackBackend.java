package com.redondoguibi.raydiance.client.pipeline;

import java.util.Locale;

/** Which renderer executes a selected shaderpack. */
public enum PackBackend {
    /** Direct DX12 when it owns the world output and the pack has HLSL; otherwise the pack's GLSL on OpenGL. */
    AUTO,
    /** Only Direct DX12; the pack pauses when DX12 world post-processing is unavailable. */
    DIRECT,
    /** Only OpenGL, even when Direct is installed. */
    OPENGL;

    public String id() { return name().toLowerCase(Locale.ROOT); }
    public PackBackend next() { return values()[(ordinal() + 1) % values().length]; }
    public static PackBackend parse(String value) {
        try { return valueOf(value.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException ex) { return AUTO; }
    }
}
