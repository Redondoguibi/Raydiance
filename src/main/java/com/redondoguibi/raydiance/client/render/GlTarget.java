package com.redondoguibi.raydiance.client.render;
import org.lwjgl.opengl.*;
import java.nio.ByteBuffer;
/** Owned framebuffer. Only allocate inside a saved GL state. */
public final class GlTarget implements AutoCloseable {
    public final int fbo,color,depth,width,height;
    private boolean ownsColor=true;
    /** Color-only view of the world target. Depth is intentionally detached while sampled. */
    static GlTarget borrowColor(int texture,int w,int h){return new GlTarget(texture,w,h);}
    private GlTarget(int texture,int w,int h){
        width=w;height=h;color=texture;depth=0;ownsColor=false;fbo=GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,texture,0);
        if(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE){close();throw new IllegalStateException("Incomplete Raydiance output framebuffer");}
    }
    public GlTarget(int w,int h,boolean withDepth){this(w,h,withDepth,GL30.GL_DEPTH_COMPONENT32F);}
    public GlTarget(int w,int h,boolean withDepth,int depthFormat){this(w,h,withDepth,depthFormat,false);}
    public GlTarget(int w,int h,boolean withDepth,int depthFormat,boolean floatingPoint){
        this(w,h,floatingPoint?GL30.GL_RGBA16F:GL11.GL_RGBA8,withDepth?depthFormat:0,false);
    }
    /** Color target with an explicit internal format; {@code mipmaps} allocates the full chain up front. */
    public GlTarget(int w,int h,int colorFormat,int depthFormat,boolean mipmaps){
        width=w;height=h;fbo=GL30.glGenFramebuffers();color=texture(w,h,colorFormat,false,mipmaps);depth=depthFormat!=0?texture(w,h,depthFormat,true,false):0;
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,color,0);
        if(depth!=0) GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,stencil(depthFormat)?GL30.GL_DEPTH_STENCIL_ATTACHMENT:GL30.GL_DEPTH_ATTACHMENT,GL11.GL_TEXTURE_2D,depth,0);
        if(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE){close();throw new IllegalStateException("Incomplete Raydiance framebuffer");}
    }
    private static boolean stencil(int format){return format==GL30.GL_DEPTH24_STENCIL8||format==GL30.GL_DEPTH32F_STENCIL8;}
    private static int texture(int w,int h,int format,boolean depth,boolean mipmaps){
        boolean stencil=depth&&stencil(format);
        int t=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,t);
        // Null uploads only allocate storage, so RGBA/FLOAT is a valid transfer format for every color format used here.
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,format,w,h,0,
                stencil?GL30.GL_DEPTH_STENCIL:depth?GL11.GL_DEPTH_COMPONENT:GL11.GL_RGBA,
                stencil?(format==GL30.GL_DEPTH32F_STENCIL8?GL30.GL_FLOAT_32_UNSIGNED_INT_24_8_REV:GL30.GL_UNSIGNED_INT_24_8):depth||format!=GL11.GL_RGBA8?GL11.GL_FLOAT:GL11.GL_UNSIGNED_BYTE,(ByteBuffer)null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,depth?GL11.GL_NEAREST:mipmaps?GL11.GL_LINEAR_MIPMAP_LINEAR:GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,depth?GL11.GL_NEAREST:GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
        // Allocate every level now: sampling a mipmap filter before the first generation would be incomplete (black).
        if(mipmaps)GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        return t;
    }
    public void bind(){GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);GL11.glViewport(0,0,width,height);}
    public void close(){GL30.glDeleteFramebuffers(fbo);if(ownsColor)GL11.glDeleteTextures(color);if(depth!=0)GL11.glDeleteTextures(depth);}
}
