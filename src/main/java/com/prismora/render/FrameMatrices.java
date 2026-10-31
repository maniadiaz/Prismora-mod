package com.prismora.render;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/** Converts the actual world projection into the legacy OpenGL clip-space convention. */
public final class FrameMatrices {
 public static final int WIDTH=80;
 private final float[] values=new float[64];
 public FrameMatrices(){capture(new Matrix4f(),new Matrix4f(),false);}
 public void capture(Matrix4fc projection,Matrix4fc view,boolean zeroToOne){
  Matrix4f conversion=new Matrix4f().m22(zeroToOne?-2f:-1f).m32(zeroToOne?1f:0f);
  Matrix4f legacy=conversion.mul(projection);
  write(legacy,0);write(new Matrix4f(legacy).invert(),16);
  write(view,32);write(new Matrix4f(view).invert(),48);
 }
 private void write(Matrix4fc matrix,int offset){matrix.get(values,offset);}
 public float value(int index){return values[index];}
}
