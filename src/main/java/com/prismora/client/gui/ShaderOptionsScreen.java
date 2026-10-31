package com.prismora.client.gui;
import com.prismora.client.ShaderController;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
public final class ShaderOptionsScreen extends Screen {
 private final Screen parent;private final ShaderController controller=ShaderController.INSTANCE;private int page;private boolean wasBusy;
 private final Map<String,Double> draft=new HashMap<>();
 public ShaderOptionsScreen(Screen parent){super(Component.translatable("prismora.options"));this.parent=parent;for(var option:controller.options())draft.put(option.id(),controller.optionValue(option));}
 @Override protected void init(){
  var options=controller.options();int size=Math.max(1,(height-110)/24);int pages=Math.max(1,(options.size()+size-1)/size);page=Math.min(page,pages-1);
  for(int i=page*size;i<Math.min(options.size(),(page+1)*size);i++){
   var option=options.get(i);double initial=draft.getOrDefault(option.id(),option.defaultValue());
   if(option.widget()!=com.prismora.pack.PackOptions.Widget.SLIDER){
    double current=option.normalize(initial);String label;
    if(option.widget()==com.prismora.pack.PackOptions.Widget.TOGGLE)label=Component.translatable(current>=0.5?"options.on":"options.off").getString();
    else{int index=option.values().indexOf(current);label=option.labels().isEmpty()?String.valueOf(current):option.labels().get(index);}
    var button=Button.builder(Component.literal(option.label()+": "+label),b->{double next=option.widget()==com.prismora.pack.PackOptions.Widget.TOGGLE?(current>=0.5?0:1):option.values().get((option.values().indexOf(current)+1)%option.values().size());draft.put(option.id(),next);rebuildWidgets();}).bounds(width/2-150,40+(i-page*size)*24,300,20).build();button.active=!controller.busy();addRenderableWidget(button);continue;
   }
   var slider=new AbstractSliderButton(width/2-150,40+(i-page*size)*24,300,20,Component.empty(),(initial-option.min())/(option.max()-option.min())){
    {updateMessage();}
    protected void updateMessage(){setMessage(Component.literal(option.label()+": "+String.format(Locale.ROOT,"%.2f",draft.getOrDefault(option.id(),option.defaultValue()))));}
    protected void applyValue(){double raw=option.min()+value*(option.max()-option.min());draft.put(option.id(),Math.clamp(option.min()+Math.round((raw-option.min())/option.step())*option.step(),option.min(),option.max()));}
   };
   slider.active=!controller.busy();addRenderableWidget(slider);
  }
  addRenderableWidget(Button.builder(Component.literal("<"),b->{page=Math.max(0,page-1);rebuildWidgets();}).bounds(width/2-150,height-60,40,20).build());
  var apply=Button.builder(Component.translatable("prismora.apply"),b->controller.applyOptions(draft)).bounds(width/2-100,height-60,96,20).build();apply.active=!controller.busy();addRenderableWidget(apply);
  addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->onClose()).bounds(width/2+4,height-60,96,20).build());
  addRenderableWidget(Button.builder(Component.literal(">"),b->{page=Math.min(pages-1,page+1);rebuildWidgets();}).bounds(width/2+110,height-60,40,20).build());
 }
 @Override public void tick(){if(wasBusy!=controller.busy()){wasBusy=controller.busy();rebuildWidgets();}}
 @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float partial){super.extractRenderState(g,x,y,partial);g.centeredText(font,title,width/2,15,0xFFFFFFFF);if(controller.busy())g.centeredText(font,Component.translatable("prismora.loading"),width/2,height-30,0xFFBBBBBB);}
 @Override public void onClose(){minecraft.gui.setScreen(parent);}
}
