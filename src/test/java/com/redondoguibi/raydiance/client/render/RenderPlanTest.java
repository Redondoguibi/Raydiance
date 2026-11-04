package com.redondoguibi.raydiance.client.render;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RenderPlanTest {
    @Test void disabledMasterSkipsAllWork(){assertFalse(RenderPlan.post(false,false,true,1,1,1,true));assertFalse(RenderPlan.opaque(false,true,1,1));}
    @Test void neutralFrameSkipsPostAndGeometryOnlyEffectsDoNotRequirePost(){assertFalse(RenderPlan.post(true,true,false,0,0,0,false));assertTrue(RenderPlan.opaque(true,true,1,0));}
    @Test void schedulesOnlyRequestedWork(){assertTrue(RenderPlan.post(true,true,false,.1,0,0,false));assertTrue(RenderPlan.post(true,true,true,0,0,0,false));assertTrue(RenderPlan.post(true,true,false,0,0,0,true));assertFalse(RenderPlan.opaque(true,false,1,1));assertFalse(RenderPlan.opaque(true,true,0,0));}
    @Test void gatesNewLightingEffects(){assertTrue(RenderPlan.post(true,true,false,0,0,0,false,1,0,0,0));assertFalse(RenderPlan.post(false,true,false,0,0,0,false,1,1,1,1));}
}
