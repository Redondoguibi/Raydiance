package com.redondoguibi.raydiance.client.render;

import static org.junit.jupiter.api.Assertions.*;
import com.redondoguibi.raydiance.client.shaders.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;
import java.nio.file.*;
import java.util.*;

/** Format-3 programs on a real GL context: bundled example, ping-pong, MRT and direct final output. */
@Tag("graphics")
class CompositeGraphTest {
    private long window;
    @TempDir Path root;
    @BeforeEach void open(){assertTrue(GLFW.glfwInit());GLFW.glfwDefaultWindowHints();GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,3);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,3);GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_CORE_PROFILE);GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT,GLFW.GLFW_TRUE);window=GLFW.glfwCreateWindow(32,32,"Raydiance format 3",0,0);assertNotEquals(0,window);GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();}
    @AfterEach void close(){GL.setCapabilities(null);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}

    private void write(String file,String text) throws Exception{Path p=root.resolve("pack/"+file);Files.createDirectories(p.getParent());Files.writeString(p,text);}
    private static List<String> finish(PackRepository.Pack pack,Map<String,String> defines){
        var macros=ShaderMacros.standard("opengl",1,"minecraft:overworld",3);
        return pack.programs().stream().map(p->ShaderMacros.inject(DefineOptions.apply(p.source(),defines),ShaderSourceLoader.Dialect.GLSL,macros,p.file())).toList();
    }
    private static float[] pixel(int fbo,int x,int y){
        try(MemoryStack stack=MemoryStack.stackPush()){var b=stack.mallocFloat(4);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,fbo);GL11.glReadPixels(x,y,1,1,GL11.GL_RGBA,GL11.GL_FLOAT,b);return new float[]{b.get(0),b.get(1),b.get(2),b.get(3)};}
    }
    private static void fill(GlTarget t,float r,float g,float b){t.bind();GL11.glClearColor(r,g,b,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);}

    @Test void bundledExampleCompilesAndRendersFiniteImages() throws Exception{
        for(String file:List.of("raydiance.json","composite.fsh","composite1.fsh","composite2.fsh","final.fsh","lib/common.glsl","world-1/final.fsh","lang/en_us.lang","lang/pt_br.lang"))
            try(var in=getClass().getResourceAsStream("/assets/raydiance/shaders/examples/iris_style_demo/"+file)){write(file,new String(in.readAllBytes()));}
        for(String dimension:List.of("minecraft:overworld","minecraft:the_nether")){
            var pack=PackRepository.read(root,"pack",new PackRepository.LoadContext(dimension,"en_us"));
            var store=new PackOptionStore(root.resolve("options"));store.prepare(pack);
            for(var haze:List.of("false","true")){
                var defines=new TreeMap<>(store.defineValues(pack));if(defines.containsKey("HAZE"))defines.put("HAZE",haze);
                try(GlState state=new GlState();GlTarget world=new GlTarget(16,16,false);CompositeGraph graph=new CompositeGraph(pack,finish(pack,defines),pack)){
                    int vao=GL30.glGenVertexArrays();GL30.glBindVertexArray(vao);fill(world,.9f,.9f,.9f);GlState.fullscreen();
                    var output=graph.render(null,world.color,16,16,k->1,d->"true".equals(defines.get(d)),(p,pass)->{
                        // Only colortex samplers are bound here; the test context has no Minecraft world.
                        for(int b=0;b<4;b++)p.texture("colortex"+b,b,pass.colortex()[b]==0?world.color:pass.colortex()[b]);
                        p.texture("depthtex0",4,world.color);
                    });
                    assertNotNull(output,"final samples colortex0 = world, so it cannot draw into the world in place");
                    float[] c=pixel(output.fbo,8,8);
                    for(float v:c)assertTrue(Float.isFinite(v));
                    assertTrue(c[0]>.3f&&c[0]<1.01f,"bright input survives tone mapping: "+c[0]);
                    assertEquals(GL11.GL_NO_ERROR,GL11.glGetError());GL30.glDeleteVertexArrays(vao);
                }
            }
        }
    }

    @Test void pingPongMrtAndDirectFinalOutput() throws Exception{
        write("raydiance.json","{\"format\":3,\"buffers\":{\"colortex2\":{\"format\":\"RGBA16F\"}}}");
        // Reads colortex0 and writes colortex1 + colortex2 through MRT.
        write("composite.fsh","#version 330\n/* RENDERTARGETS: 1,2 */\nuniform sampler2D colortex0;in vec2 texCoord;layout(location=0) out vec4 a;layout(location=1) out vec4 b;\nvoid main(){vec4 c=texture(colortex0,texCoord);a=c*0.5;b=vec4(c.rgb*4.0,1.0);}");
        // Reads and writes colortex1 in the same program: must see the previous side.
        write("composite1.fsh","#version 150\n/* RENDERTARGETS: 1 */\nuniform sampler2D colortex1;in vec2 texCoord;out vec4 o;\nvoid main(){o=texture(colortex1,texCoord)+vec4(0.25,0.25,0.25,0.0);}");
        // Does not read colortex0, so it may render straight into the world target.
        write("final.fsh","#version 150\nuniform sampler2D colortex1,colortex2;in vec2 texCoord;out vec4 o;\nvoid main(){o=vec4(texture(colortex1,texCoord).r,texture(colortex2,texCoord).r/8.0,0.0,1.0);}");
        var pack=PackRepository.read(root,"pack");
        assertEquals(Set.of(0,1,2),pack.buffers().keySet());
        try(GlState state=new GlState();GlTarget world=new GlTarget(8,8,false);CompositeGraph graph=new CompositeGraph(pack,finish(pack,Map.of()),pack)){
            int vao=GL30.glGenVertexArrays();GL30.glBindVertexArray(vao);fill(world,.5f,.5f,.5f);GlState.fullscreen();
            var output=graph.render(null,world.color,8,8,k->1,d->false,(p,pass)->{for(int b=0;b<3;b++)p.texture("colortex"+b,b,pass.colortex()[b]);});
            assertNull(output,"final wrote the world target directly");
            float[] c=pixel(world.fbo,4,4);
            assertEquals(.5f,c[0],.02f,"colortex1 = 0.5*0.5 + 0.25 after ping-pong");
            assertEquals(.25f,c[1],.02f,"colortex2 kept its HDR value 2.0 (divided by 8)");
            // A second frame must give the same result: cleared buffers do not accumulate.
            fill(world,.5f,.5f,.5f);GlState.fullscreen();
            graph.render(null,world.color,8,8,k->1,d->false,(p,pass)->{for(int b=0;b<3;b++)p.texture("colortex"+b,b,pass.colortex()[b]);});
            assertEquals(.5f,pixel(world.fbo,4,4)[0],.02f);
            assertEquals(GL11.GL_NO_ERROR,GL11.glGetError());GL30.glDeleteVertexArrays(vao);
        }
    }
}
