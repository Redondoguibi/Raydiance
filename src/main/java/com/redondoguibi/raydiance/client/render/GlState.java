package com.redondoguibi.raydiance.client.render;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;
/** Restore the GL state exactly; do not invalidate Minecraft's RenderSystem caches. */
public final class GlState implements AutoCloseable {
    private final int read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
        program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),vao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
    private final int[] viewport = new int[4];
    private final int[] textures;
    /** Texture units Raydiance programs may bind (FrameUniforms caps samplers per program at this count). */
    public static final int UNITS=16;
    private final boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),blend=GL11.glIsEnabled(GL11.GL_BLEND),
        cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),srgb=GL11.glIsEnabled(GL30.GL_FRAMEBUFFER_SRGB),
        depthMask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
    private final int depthFunc=GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
    private final double clearDepth=GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE);
    private final byte[] colorMask=new byte[4];
    private final float[] clearColor=new float[4];
    public GlState() {
        this(UNITS);
    }
    /** Saves the consecutive texture units a pass may write, beginning at unit zero. */
    GlState(int textureUnits) {
        textures = new int[textureUnits];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE,clearColor);
        try(MemoryStack stack=MemoryStack.stackPush()){var mask=stack.malloc(4);GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK,mask);mask.get(colorMask);}
        for(int i=0;i<textures.length;i++){GL13.glActiveTexture(GL13.GL_TEXTURE0+i);textures[i]=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);}
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
    }
    private static void enable(int cap,boolean v){if(v)GL11.glEnable(cap);else GL11.glDisable(cap);}
    public static void fullscreen(){GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_CULL_FACE);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);GL11.glDepthMask(false);GL11.glColorMask(true,true,true,true);}
    public void close(){
        for(int i=0;i<textures.length;i++){GL13.glActiveTexture(GL13.GL_TEXTURE0+i);GL11.glBindTexture(GL11.GL_TEXTURE_2D,textures[i]);}
        GL13.glActiveTexture(active);GL20.glUseProgram(program);GL30.glBindVertexArray(vao);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);
        GL11.glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);
        enable(GL11.GL_DEPTH_TEST,depth);enable(GL11.GL_BLEND,blend);enable(GL11.GL_CULL_FACE,cull);enable(GL11.GL_SCISSOR_TEST,scissor);enable(GL30.GL_FRAMEBUFFER_SRGB,srgb);
        GL11.glDepthFunc(depthFunc);GL11.glClearDepth(clearDepth);GL11.glClearColor(clearColor[0],clearColor[1],clearColor[2],clearColor[3]);GL11.glDepthMask(depthMask);GL11.glColorMask(colorMask[0]!=0,colorMask[1]!=0,colorMask[2]!=0,colorMask[3]!=0);
    }
}
