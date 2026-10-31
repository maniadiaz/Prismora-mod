package com.prismora.client.gui;
import com.prismora.client.ShaderController;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.Locale;
public final class ShaderOptionsScreen extends Screen {
 private final Screen parent;private final ShaderController controller=ShaderController.INSTANCE;private int page;private boolean wasBusy;
 public ShaderOptionsScreen(Screen parent){super(Component.translatable("prismora.options"));this.parent=parent;}
 @Override protected void init(){
  var options=controller.options();int size=Math.max(1,(height-110)/24);int pages=Math.max(1,(options.size()+size-1)/size);page=Math.min(page,pages-1);
  for(int i=page*size;i<Math.min(options.size(),(page+1)*size);i++){
   var option=options.get(i);double value=controller.optionValue(option);
   var button=Button.builder(Component.literal(option.label()+": "+String.format(Locale.ROOT,"%.2f",value)),b->{double next=value+option.step();if(next>option.max()+1e-8)next=option.min();controller.setOption(option,next);rebuildWidgets();}).bounds(width/2-150,40+(i-page*size)*24,300,20).build();
   button.active=!controller.busy();addRenderableWidget(button);
  }
  addRenderableWidget(Button.builder(Component.literal("<"),b->{page=Math.max(0,page-1);rebuildWidgets();}).bounds(width/2-150,height-60,40,20).build());
  addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->onClose()).bounds(width/2-100,height-60,200,20).build());
  addRenderableWidget(Button.builder(Component.literal(">"),b->{page=Math.min(pages-1,page+1);rebuildWidgets();}).bounds(width/2+110,height-60,40,20).build());
 }
 @Override public void tick(){if(wasBusy!=controller.busy()){wasBusy=controller.busy();rebuildWidgets();}}
 @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float partial){super.extractRenderState(g,x,y,partial);g.centeredText(font,title,width/2,15,0xFFFFFFFF);if(controller.busy())g.centeredText(font,Component.translatable("prismora.loading"),width/2,height-30,0xFFBBBBBB);}
 @Override public void onClose(){minecraft.gui.setScreen(parent);}
}
