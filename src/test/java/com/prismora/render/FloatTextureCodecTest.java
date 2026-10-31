package com.prismora.render;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FloatTextureCodecTest {
 @Test void preservesFloatBitsAcrossRgbaChannels(){
  for(float x:new float[]{0f,-0f,1f,-1234.25f,Float.MIN_VALUE,Float.MAX_VALUE,24000f,3.1415927f})assertEquals(Float.floatToRawIntBits(x),Float.floatToRawIntBits(FloatTextureCodec.decode(FloatTextureCodec.encode(x))));
 }
}
