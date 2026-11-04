package com.redondoguibi.raydiance.client.render;
/** Shared pass gating, independent of Minecraft and the graphics context. */
public final class RenderPlan {
    public static boolean post(boolean enabled,boolean neutral,boolean custom,double bloom,double atmosphere,double shadows,boolean aa){
        return enabled&&(!neutral||custom||bloom>0||atmosphere>0||shadows>0||aa);
    }
    public static boolean post(boolean enabled,boolean neutral,boolean custom,double bloom,double atmosphere,double shadows,boolean aa,double ssao,double rays,double sharpen,double tone){
        return post(enabled,neutral,custom,bloom,atmosphere,shadows,aa)||enabled&&(ssao>0||rays>0||sharpen>0||tone>0);
    }
    public static boolean opaque(boolean enabled,boolean terrainCompatible,double water,double reflections){
        return enabled&&terrainCompatible&&(water>0||reflections>0);
    }
    private RenderPlan(){}
}
