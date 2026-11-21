package com.redondoguibi.raydiance.client.pipeline;

import com.redondoguibi.raydiance.client.shaders.PackRepository;

/**
 * One compiled rendering configuration (compare Iris' WorldRenderingPipeline). Built and replaced as a unit by
 * {@link PipelineManager}; construction either fully succeeds or throws, leaving the previous pipeline intact.
 * All methods run on the render thread.
 */
public interface ShaderPipeline extends AutoCloseable {
    enum Kind { OPENGL, DIRECT }
    Kind kind();
    PackRepository.Pack pack();
    /** Called once per frame after the level, before hand and GUI. */
    void render(float partialTicks);
    /** True when programs sample depth captured before translucent geometry (depthtex1). */
    default boolean wantsOpaqueDepth() { return false; }
    /** Frees size-dependent targets while keeping compiled programs. */
    default void releaseTargets() {}
    /** True after the backend permanently rejected this pipeline's sources. */
    default boolean rejected() { return false; }
    String status();
    @Override void close();
}
