package com.redondoguibi.raydiance.client.render;

import org.joml.Matrix4f;
import org.joml.Vector3d;

/** Per-world frame metadata, independent of GL. Teleports and world switches invalidate history. */
public final class FrameHistory {
    private Object world;
    private long previousNanos;
    private boolean initialized;
    public int counter;
    public float seconds,delta,wetness;
    public boolean valid;
    public final Matrix4f previousProjection=new Matrix4f(),previousView=new Matrix4f();
    private final Matrix4f projection=new Matrix4f(),view=new Matrix4f();
    public final Vector3d camera=new Vector3d(),cameraDelta=new Vector3d();
    public void update(Object world,long nanos,Matrix4f newProjection,Matrix4f newView,Vector3d position,float rain) {
        valid=initialized&&this.world==world&&camera.distanceSquared(position)<1024*1024;
        delta=valid?(float)Math.clamp((nanos-previousNanos)/1e9,0,0.25):0;
        previousProjection.set(valid?projection:newProjection);previousView.set(valid?view:newView);
        cameraDelta.set(valid?new Vector3d(position).sub(camera):new Vector3d());
        seconds=valid?(seconds+delta)%3600:0;counter=valid&&counter<Integer.MAX_VALUE?counter+1:0;
        float target=Math.clamp(rain,0,1);
        wetness=valid?wetness+(target-wetness)*(float)-Math.expm1(-delta/(target>wetness?5:15)):target;
        camera.set(position);projection.set(newProjection);view.set(newView);this.world=world;previousNanos=nanos;initialized=true;
    }
    public void reset(){world=null;initialized=false;valid=false;counter=0;seconds=delta=wetness=0;}
}
