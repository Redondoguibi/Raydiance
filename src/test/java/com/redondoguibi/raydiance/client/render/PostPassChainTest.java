package com.redondoguibi.raydiance.client.render;

import com.redondoguibi.raydiance.client.shaders.PackRepository;
import org.junit.jupiter.api.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("graphics")
class PostPassChainTest {
    private long window;
    @BeforeEach void open(){assertTrue(GLFW.glfwInit());GLFW.glfwDefaultWindowHints();GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,3);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,3);window=GLFW.glfwCreateWindow(32,32,"Pack pipeline validation",0,0);assertNotEquals(0,window);GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();}
    @AfterEach void close(){GL.setCapabilities(null);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    private String source(float gain){return "#version 150\nuniform sampler2D Scene;in vec2 texCoord;out vec4 fragColor;void main(){vec4 c=texture(Scene,texCoord);fragColor=vec4(c.rgb*"+gain+",c.a);}";}
    private PackRepository.Pass pass(float gain,double scale,boolean hdr,String when){return new PackRepository.Pass("test.fsh",source(gain),scale,hdr,when);}
    private void uniforms(GlProgram p,int texture,int iw,int ih,int index,int w,int h){p.texture("Scene",0,texture);}
    @Test void preservesHdrValuesAcrossOrderedScaledPassesAndResize(){
        try(GlState state=new GlState();GlTarget input=new GlTarget(16,16,false);PostPassChain chain=new PostPassChain(List.of(pass(2,0.5,true,""),pass(.5f,0.5,false,"")));MemoryStack stack=MemoryStack.stackPush()){
            input.bind();GL11.glClearColor(.75f,.25f,.5f,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);int vao=GL30.glGenVertexArrays();GL30.glBindVertexArray(vao);GlState.fullscreen();
            var output=chain.render(input.color,16,16,this::uniforms,k->1);assertNotNull(output);assertEquals(8,output.width);
            var pixel=stack.mallocFloat(4);GL11.glReadPixels(4,4,1,1,GL11.GL_RGBA,GL11.GL_FLOAT,pixel);assertEquals(.75,pixel.get(0),.01,"HDR intermediate must not clip to 1 before the second pass");
            output=chain.render(input.color,20,12,this::uniforms,k->1);assertEquals(10,output.width);assertEquals(6,output.height);assertEquals(GL11.GL_NO_ERROR,GL11.glGetError());GL30.glDeleteVertexArrays(vao);
        }
    }
    @Test void skipsDisabledPassesAndRejectsOversizedTargetsBeforeAllocation(){
        try(GlState state=new GlState();GlTarget input=new GlTarget(8,8,false);PostPassChain chain=new PostPassChain(List.of(pass(1,1,true,"ssao")))){
            assertFalse(chain.active(k->0));assertNull(chain.render(input.color,8,8,this::uniforms,k->0));assertTrue(chain.active(k->1));
            assertThrows(IllegalArgumentException.class,()->chain.render(input.color,8192,8192,this::uniforms,k->1));assertEquals(GL11.GL_NO_ERROR,GL11.glGetError());
        }
    }
    @Test void invalidReplacementLeavesWorkingChainUsableAndRejectsUniformMismatch(){
        try(GlState state=new GlState();GlTarget input=new GlTarget(4,4,false);PostPassChain working=new PostPassChain(List.of(pass(1,1,false,"")))){
            var broken=new PackRepository.Pass("broken.fsh","#version 150\ninvalid shader",1,false,"");assertThrows(IllegalArgumentException.class,()->new PostPassChain(List.of(pass(1,1,false,""),broken)));
            assertThrows(IllegalArgumentException.class,()->new GlProgram("#version 150\nuniform vec2 Rain;out vec4 fragColor;void main(){fragColor=vec4(Rain,0,1);}"));
            int vao=GL30.glGenVertexArrays();GL30.glBindVertexArray(vao);GlState.fullscreen();assertNotNull(working.render(input.color,4,4,this::uniforms,k->1));assertEquals(GL11.GL_NO_ERROR,GL11.glGetError());GL30.glDeleteVertexArrays(vao);
        }
    }
}
