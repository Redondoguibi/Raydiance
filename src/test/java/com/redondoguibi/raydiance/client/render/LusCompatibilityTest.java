package com.redondoguibi.raydiance.client.render;

import static org.junit.jupiter.api.Assertions.*;
import com.redondoguibi.raydiance.client.shaders.*;
import org.junit.jupiter.api.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import java.nio.file.*;
import java.util.Map;

/** The sibling Lus pack (Direct Group/Lus/lus) must keep loading and compiling unchanged. Skipped when absent. */
@Tag("graphics")
class LusCompatibilityTest {
    private static final Path PACKS = Path.of("..", "Lus");
    @Test void lusLoadsForBothBackendsAndCompilesWithMacros() throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(PACKS.resolve("lus/raydiance.json")), "Lus pack not present");
        var pack = PackRepository.read(PACKS, "lus");
        assertTrue(pack.canRunOpenGl());
        assertTrue(pack.canRunDirect(), "direct.hlsl is present");
        assertTrue(pack.shaderOptions().stream().noneMatch(ShaderOption::define), "value defines without [..] lists are not options");
        assertFalse(pack.uniformOptions().isEmpty());
        assertTrue(GLFW.glfwInit());
        GLFW.glfwDefaultWindowHints();GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,3);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,3);
        long window=GLFW.glfwCreateWindow(16,16,"Lus",0,0);
        try{
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
            String source=ShaderMacros.inject(pack.composite(),ShaderSourceLoader.Dialect.GLSL,ShaderMacros.standard("opengl",1,"minecraft:overworld",1),"composite.fsh");
            try(GlState state=new GlState();GlProgram program=new GlProgram(source)){
                program.use();
                assertDoesNotThrow(()->FrameUniforms.prepare(program,pack),"Lus uniforms match the reserved types");
                assertTrue(pack.uniformOptions().stream().anyMatch(o->program.has(o.uniform())),"pack controls stay live uniforms");
            }
            assertEquals(GL11.GL_NO_ERROR,GL11.glGetError());
        }finally{GL.setCapabilities(null);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
        assertTrue(ShaderMacros.inject(pack.hlsl(),ShaderSourceLoader.Dialect.HLSL,Map.of("RAYDIANCE","1"),"direct.hlsl").startsWith("#define RAYDIANCE 1"));
    }
}
