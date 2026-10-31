package com.prismora.client.gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
public final class ShaderErrorScreen extends Screen {
 private final Screen parent;private final String error;private int page;
 public ShaderErrorScreen(Screen parent,String error){super(Component.translatable("prismora.errors"));this.parent=parent;this.error=error;}
 @Override protected void init(){
  addRenderableWidget(Button.builder(Component.literal("<"),b->{page=Math.max(0,page-1);}).bounds(width/2-120,height-30,60,20).build());
  addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->onClose()).bounds(width/2-50,height-30,100,20).build());
  addRenderableWidget(Button.builder(Component.literal(">"),b->{int count=font.split(Component.literal(error),width-32).size();page=Math.min(Math.max(0,(count-1)/Math.max(1,(height-75)/11)),page+1);}).bounds(width/2+60,height-30,60,20).build());
 }
 @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float partial){
  super.extractRenderState(g,x,y,partial);g.centeredText(font,title,width/2,12,0xFFFFFFFF);
  var lines=font.split(Component.literal(error),Math.max(40,width-32));int size=Math.max(1,(height-75)/11);
  for(int i=page*size;i<Math.min(lines.size(),(page+1)*size);i++)g.text(font,lines.get(i),16,35+(i-page*size)*11,0xFFFFAAAA);
 }
 @Override public void onClose(){minecraft.gui.setScreen(parent);}
}
