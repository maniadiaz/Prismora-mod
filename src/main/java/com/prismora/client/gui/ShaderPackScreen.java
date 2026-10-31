package com.prismora.client.gui;
import com.prismora.client.ShaderController;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.Blaze3D;

public final class ShaderPackScreen extends Screen {
 private final Screen parent;
 private final ShaderController controller=ShaderController.INSTANCE;
 private int page;
 public ShaderPackScreen(Screen parent) { super(Component.translatable("prismora.title"));this.parent=parent; }
 @Override protected void init() {
  int left=width/2-150;
  int pageSize=Math.max(1,(height-160)/24);
  int pages=Math.max(1,(controller.entries().size()+pageSize-1)/pageSize);
  page=Math.min(page,pages-1);
  for(int i=page*pageSize;i<Math.min(controller.entries().size(),(page+1)*pageSize);i++) {
   var entry=controller.entries().get(i);
   String label=(entry.id().equals(controller.selected())?"* ":"")+entry.name();
   var button=Button.builder(Component.literal(label),b->{controller.select(entry);rebuildWidgets();}).bounds(left,40+(i-page*pageSize)*24,300,20).build();
   if(!entry.valid()) button.setTooltip(Tooltip.create(Component.literal(entry.error())));
   addRenderableWidget(button);
  }
  addRenderableWidget(Button.builder(Component.literal("<"),b->{page=Math.max(0,page-1);rebuildWidgets();}).bounds(left,height-110,40,20).build());
  addRenderableWidget(Button.builder(Component.literal(">"),b->{page=Math.min(pages-1,page+1);rebuildWidgets();}).bounds(left+260,height-110,40,20).build());
  addRenderableWidget(Button.builder(Component.translatable("prismora.disable"),b->{controller.select(null);rebuildWidgets();}).bounds(left,height-84,146,20).build());
  addRenderableWidget(Button.builder(Component.translatable("prismora.reload"),b->{controller.refresh();rebuildWidgets();}).bounds(left+154,height-84,146,20).build());
  addRenderableWidget(Button.builder(Component.translatable("prismora.folder"),b->Blaze3D.openPath(controller.manager().directory())).bounds(left,height-58,146,20).build());
  addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->onClose()).bounds(left+154,height-58,146,20).build());
 }
 @Override public void extractRenderState(GuiGraphicsExtractor graphics,int x,int y,float partial) {
  super.extractRenderState(graphics,x,y,partial);
  graphics.centeredText(font,title,width/2,15,0xFFFFFFFF);
  String status=controller.error().isEmpty()?Component.translatable("prismora.selection_only").getString():controller.error();
  graphics.text(font,font.plainSubstrByWidth(status,Math.max(50,width-24)),12,height-26,controller.error().isEmpty()?0xFFBBBBBB:0xFFFF7777);
 }
 @Override public void onClose() { minecraft.gui.setScreen(parent); }
}

