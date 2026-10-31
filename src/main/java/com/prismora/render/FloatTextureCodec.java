package com.prismora.render;
/** NativeImage uses ARGB; the GPU sampler returns RGBA in low-to-high bit order. */
public final class FloatTextureCodec {
 private FloatTextureCodec() {}
 public static int encode(float value) { int bits=Float.floatToRawIntBits(value);return (bits&0xff000000)|((bits&255)<<16)|(bits&0xff00)|((bits>>>16)&255); }
 public static float decode(int argb) { int bits=(argb&0xff000000)|((argb&255)<<16)|(argb&0xff00)|((argb>>>16)&255);return Float.intBitsToFloat(bits); }
}
