package com.prismora.render.runtime;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import java.util.Optional;

/** RGB formats use padded RGBA storage with alpha fixed at one and writes masked to RGB. */
public enum TargetFormat {
 R11F_G11F_B10F(GpuFormat.RG11B10_FLOAT,ColorTargetState.WRITE_COLOR,true),
 R8(GpuFormat.R8_UNORM,ColorTargetState.WRITE_RED,false),
 RGB16F(GpuFormat.RGBA16_FLOAT,ColorTargetState.WRITE_COLOR,true),
 RGB8(GpuFormat.RGBA8_UNORM,ColorTargetState.WRITE_COLOR,true),
 RGB8_SNORM(GpuFormat.RGBA8_SNORM,ColorTargetState.WRITE_COLOR,true),
 RGBA16F(GpuFormat.RGBA16_FLOAT,ColorTargetState.WRITE_ALL,false),
 RGBA8(GpuFormat.RGBA8_UNORM,ColorTargetState.WRITE_ALL,false),
 RGBA8_SNORM(GpuFormat.RGBA8_SNORM,ColorTargetState.WRITE_ALL,false);
 private final GpuFormat physical;private final int mask;private final boolean alphaOne;
 TargetFormat(GpuFormat physical,int mask,boolean alphaOne){this.physical=physical;this.mask=mask;this.alphaOne=alphaOne;}
 public GpuFormat physical(){return physical;}
 public boolean alphaOne(){return alphaOne;}
 public ColorTargetState state(){return new ColorTargetState(Optional.empty(),physical,mask);}
}
