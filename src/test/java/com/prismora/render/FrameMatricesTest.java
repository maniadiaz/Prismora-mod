package com.prismora.render;
import org.junit.jupiter.api.Test;
import org.joml.*;
import static org.junit.jupiter.api.Assertions.*;
class FrameMatricesTest {
 private Matrix4f matrix(FrameMatrices data,int offset){float[] values=new float[16];for(int i=0;i<16;i++)values[i]=FloatTextureCodec.decode(FloatTextureCodec.encode(data.value(offset+i)));return new Matrix4f().set(values);}
 @Test void reversedProjectionBecomesLegacyOnBothClipConventions(){for(boolean zeroToOne:new boolean[]{false,true}){var data=new FrameMatrices();data.capture(new Matrix4f().setPerspective(1.1f,1.7f,1000f,0.05f,zeroToOne),new Matrix4f(),zeroToOne);var p=matrix(data,0);assertEquals(-1f,p.transformProject(new Vector3f(0,0,-0.05f)).z,0.0001f);assertEquals(1f,p.transformProject(new Vector3f(0,0,-1000f)).z,0.0001f);}}
 @Test void viewAndProjectionInversesSurviveFloatTextureTransport(){var data=new FrameMatrices();var view=new Matrix4f().rotateY(0.8f).rotateX(-0.4f).translate(2,3,4);data.capture(new Matrix4f().setPerspective(1.2f,1.6f,800f,0.1f,true),view,true);assertTrue(matrix(data,0).mul(matrix(data,16)).equals(new Matrix4f(),0.0001f));assertTrue(matrix(data,32).equals(view,0f));assertTrue(matrix(data,32).mul(matrix(data,48)).equals(new Matrix4f(),0.0001f));}
}
