package com.redondoguibi.raydiance.client.render;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;
import org.joml.Matrix4f;
import java.nio.*;
import java.nio.charset.StandardCharsets;

@Tag("graphics")
class GraphicsPipelineTest {
    private long window;
    @BeforeEach void open(){assertTrue(GLFW.glfwInit());GLFW.glfwDefaultWindowHints();GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,3);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,3);window=GLFW.glfwCreateWindow(32,32,"Raydiance validation",0,0);assertNotEquals(0,window);GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();}
    @AfterEach void close(){GL.setCapabilities(null);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    @Test void identityCompositeAndEffectsProduceFinitePixels() throws Exception{
        try(GlState state=new GlState();GlTarget input=new GlTarget(16,16,true);GlTarget output=new GlTarget(16,16,false);GlProgram shader=new GlProgram(GlProgram.resource("composite.fsh"));GlProgram bloom=new GlProgram(GlProgram.resource("bloom.fsh"));MemoryStack stack=MemoryStack.stackPush()){
            int vao=GL30.glGenVertexArrays();GL30.glBindVertexArray(vao);input.bind();GL11.glClearColor(.25f,.5f,.75f,1);GL11.glClearDepth(1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);GlState.fullscreen();
            output.bind();shader.use();shader.texture("Scene",0,input.color);shader.texture("Depth",1,input.depth);shader.texture("BloomTex",2,input.color);shader.vec2("Texel",1f/16,1f/16);
            for(String name:new String[]{"Saturation","Contrast","Brightness","Gamma","Strength"})shader.scalar(name,1);
            shader.matrix("Projection",new Matrix4f().perspective(1,1,.1f,100));shader.matrix("InvProjection",new Matrix4f().perspective(1,1,.1f,100).invert());shader.vec3("Sun",0,1,0);shader.scalar("Daylight",1);shader.integer("Steps",12);
            shader.draw();FloatBuffer pixels=stack.mallocFloat(16*16*4);GL11.glReadPixels(0,0,16,16,GL11.GL_RGBA,GL11.GL_FLOAT,pixels);
            for(int i=0;i<16*16;i++){assertEquals(.25,pixels.get(i*4),.006);assertEquals(.5,pixels.get(i*4+1),.006);assertEquals(.75,pixels.get(i*4+2),.006);}
            shader.scalar("Bloom",.3f);shader.scalar("Atmosphere",.5f);shader.scalar("Shadows",1);shader.integer("Fxaa",1);
            shader.scalar("Ssao",1);shader.scalar("GodRays",1);shader.scalar("Sharpen",1);shader.scalar("ToneMapping",1);shader.vec3("Sun",0,.3f,-1);
            input.bind();GL11.glDepthMask(true);GL11.glClearDepth(.5);GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);output.bind();GlState.fullscreen();shader.draw();GL11.glReadPixels(0,0,16,16,GL11.GL_RGBA,GL11.GL_FLOAT,pixels);
            for(int i=0;i<16*16*4;i++)assertTrue(Float.isFinite(pixels.get(i)));
            bloom.use();bloom.texture("Scene",0,input.color);bloom.vec2("Direction",1f/16,0);bloom.scalar("Threshold",.3f);bloom.integer("Extract",1);bloom.draw();assertEquals(GL11.GL_NO_ERROR,GL11.glGetError());GL30.glDeleteVertexArrays(vao);
        }
    }
    @Test void rejectsWrongTypeForShaderSettingsBeforeBinding(){
        try(GlProgram program=new GlProgram("#version 150\nuniform vec3 PackSky;out vec4 color;void main(){color=vec4(PackSky,1);}")){
            assertThrows(IllegalArgumentException.class,()->program.validateScalars(java.util.List.of("PackSky")));
        }
        try(GlProgram program=new GlProgram("#version 150\nuniform float PackSky;out vec4 color;void main(){color=vec4(PackSky);}")){
            assertDoesNotThrow(()->program.validateScalars(java.util.List.of("PackSky")));
        }
        assertEquals(GL11.GL_NO_ERROR,GL11.glGetError());
    }
    @Test void restoresStateAndRejectsBrokenShaderWithoutLosingWorkingProgram() throws Exception{
        GL11.glEnable(GL11.GL_BLEND);GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glViewport(1,2,20,21);GL13.glActiveTexture(GL13.GL_TEXTURE3);
        try(GlProgram working=new GlProgram(GlProgram.resource("composite.fsh"))){
            try(GlState state=new GlState();GlTarget target=new GlTarget(7,9,false)){target.bind();GlState.fullscreen();working.use();assertThrows(IllegalArgumentException.class,()->new GlProgram("#version 150\ninvalid GLSL"));}
            assertTrue(GL11.glIsEnabled(GL11.GL_BLEND));assertTrue(GL11.glIsEnabled(GL11.GL_DEPTH_TEST));assertEquals(GL13.GL_TEXTURE3,GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE));int[] v=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,v);assertArrayEquals(new int[]{1,2,20,21},v);
        }
        assertEquals(GL11.GL_NO_ERROR,GL11.glGetError());
    }
    @Test void copiesVanillaAndStencilDepthFormatsExactly(){
        try(GlState state=new GlState()){
            for(int format:new int[]{GL14.GL_DEPTH_COMPONENT24,GL30.GL_DEPTH_COMPONENT32F,GL30.GL_DEPTH24_STENCIL8,GL30.GL_DEPTH32F_STENCIL8}){
                try(GlTarget source=new GlTarget(8,8,true,format);GlTarget copy=new GlTarget(8,8,true,format);MemoryStack stack=MemoryStack.stackPush()){
                    source.bind();GL11.glDepthMask(true);GL11.glClearDepth(.35);GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
                    GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,source.fbo);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,copy.fbo);
                    GL30.glBlitFramebuffer(0,0,8,8,0,0,8,8,GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,GL11.GL_NEAREST);
                    assertEquals(GL11.GL_NO_ERROR,GL11.glGetError(),"depth format "+format);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D,copy.depth);var data=stack.mallocFloat(64);GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,data);assertEquals(.35,data.get(0),.0001);
                }
            }
        }
    }
    @Test void compilesProjectedShadowMapShader() throws Exception{
        int v=GL20.glCreateShader(GL20.GL_VERTEX_SHADER),f=GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER),p=GL20.glCreateProgram();
        try{GL20.glShaderSource(v,GlProgram.resource("core/shadow.vsh"));GL20.glCompileShader(v);assertEquals(1,GL20.glGetShaderi(v,GL20.GL_COMPILE_STATUS),GL20.glGetShaderInfoLog(v));GL20.glShaderSource(f,GlProgram.resource("core/shadow.fsh"));GL20.glCompileShader(f);assertEquals(1,GL20.glGetShaderi(f,GL20.GL_COMPILE_STATUS),GL20.glGetShaderInfoLog(f));GL20.glAttachShader(p,v);GL20.glAttachShader(p,f);GL20.glLinkProgram(p);assertEquals(1,GL20.glGetProgrami(p,GL20.GL_LINK_STATUS),GL20.glGetProgramInfoLog(p));}
        finally{GL20.glDeleteProgram(p);GL20.glDeleteShader(v);GL20.glDeleteShader(f);}
    }
    @Test void actualShadowProgramWritesDepthAndRestoresMainFramebuffer() throws Exception{
        try(GlState state=new GlState();GlTarget atlas=new GlTarget(1,1,false);GlTarget map=new GlTarget(16,16,true);
            GlProgram shader=new GlProgram(GlProgram.resource("core/shadow.vsh"),GlProgram.resource("core/shadow.fsh"),true);MemoryStack stack=MemoryStack.stackPush()){
            atlas.bind();GL11.glClearColor(1,1,1,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            int vao=GL30.glGenVertexArrays(),vbo=GL15.glGenBuffers();GL30.glBindVertexArray(vao);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,vbo);
            FloatBuffer vertices=stack.mallocFloat(30);for(float[] p:new float[][]{{-1,-1},{1,-1},{1,1},{-1,-1},{1,1},{-1,1}})vertices.put(p[0]).put(p[1]).put(0).put(.5f).put(.5f);vertices.flip();GL15.glBufferData(GL15.GL_ARRAY_BUFFER,vertices,GL15.GL_STATIC_DRAW);
            GL20.glEnableVertexAttribArray(0);GL20.glVertexAttribPointer(0,3,GL11.GL_FLOAT,false,20,0);GL20.glEnableVertexAttribArray(2);GL20.glVertexAttribPointer(2,2,GL11.GL_FLOAT,false,20,12);
            map.bind();GL11.glDisable(GL11.GL_BLEND);GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthFunc(GL11.GL_LESS);GL11.glDepthMask(true);GL11.glClearDepth(1);GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            shader.use();shader.texture("Sampler0",0,atlas.color);shader.matrix("ModelViewMat",new Matrix4f());shader.matrix("ProjMat",new Matrix4f());shader.scalar("AlphaCutoff",.1f);shader.draw();
            var depths=stack.mallocFloat(256);GL11.glReadPixels(0,0,16,16,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depths);assertEquals(.5,depths.get(8*16+8),.0001);
            assertEquals(GL11.GL_NO_ERROR,GL11.glGetError());GL15.glDeleteBuffers(vbo);GL30.glDeleteVertexArrays(vao);
        }
    }
    private String imports(String s) throws Exception{
        for(String name:new String[]{"light","fog"}){
            try(var in=getClass().getResourceAsStream("/assets/minecraft/shaders/include/"+name+".glsl")){assertNotNull(in);s=s.replace("#moj_import <"+name+".glsl>",new String(in.readAllBytes(),StandardCharsets.UTF_8).replaceAll("(?m)^#version[^\\n]*", ""));}
        }return s;
    }
    @Test void compilesActualTerrainShadersWithVanillaIncludes() throws Exception{
        int v=GL20.glCreateShader(GL20.GL_VERTEX_SHADER),f=GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER),p=GL20.glCreateProgram();
        try{GL20.glShaderSource(v,imports(GlProgram.resource("core/terrain.vsh")));GL20.glCompileShader(v);assertEquals(1,GL20.glGetShaderi(v,GL20.GL_COMPILE_STATUS),GL20.glGetShaderInfoLog(v));GL20.glShaderSource(f,imports(GlProgram.resource("core/terrain.fsh")));GL20.glCompileShader(f);assertEquals(1,GL20.glGetShaderi(f,GL20.GL_COMPILE_STATUS),GL20.glGetShaderInfoLog(f));GL20.glAttachShader(p,v);GL20.glAttachShader(p,f);GL20.glLinkProgram(p);assertEquals(1,GL20.glGetProgrami(p,GL20.GL_LINK_STATUS),GL20.glGetProgramInfoLog(p));}
        finally{GL20.glDeleteProgram(p);GL20.glDeleteShader(v);GL20.glDeleteShader(f);}
    }
}
