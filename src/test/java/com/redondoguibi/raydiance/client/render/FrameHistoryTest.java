package com.redondoguibi.raydiance.client.render;
import org.junit.jupiter.api.Test;
import org.joml.*;
import static org.junit.jupiter.api.Assertions.*;
class FrameHistoryTest {
    @Test void tracksPreviousMatricesAndCameraDelta(){
        var h=new FrameHistory();Object world=new Object();Matrix4f first=new Matrix4f().translate(1,2,3);
        h.update(world,1000000000,first,new Matrix4f(),new Vector3d(10,20,30),0);assertFalse(h.valid);
        h.update(world,1100000000,new Matrix4f().scale(2),new Matrix4f(),new Vector3d(11,20,30),0);
        assertTrue(h.valid);assertEquals(.1,h.delta,.00001);assertEquals(1,h.cameraDelta.x);assertEquals(first,h.previousProjection);assertEquals(1,h.counter);
    }
    @Test void invalidatesWorldSwitchTeleportAndReset(){
        var h=new FrameHistory();var w=new Object();h.update(w,0,new Matrix4f(),new Matrix4f(),new Vector3d(),0);
        h.update(w,100,new Matrix4f(),new Matrix4f(),new Vector3d(3000,0,0),0);assertFalse(h.valid);assertEquals(0,h.cameraDelta.length());
        h.update(new Object(),200,new Matrix4f(),new Matrix4f(),new Vector3d(3000,0,0),0);assertFalse(h.valid);h.reset();assertFalse(h.valid);assertEquals(0,h.counter);
    }
    @Test void boundsFrameDeltaAndSmoothsWetness(){
        var h=new FrameHistory();var w=new Object();h.update(w,0,new Matrix4f(),new Matrix4f(),new Vector3d(),0);
        h.update(w,10000000000L,new Matrix4f(),new Matrix4f(),new Vector3d(),1);assertEquals(.25,h.delta);assertTrue(h.wetness>0&&h.wetness<1);
    }
}
