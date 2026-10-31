package com.prismora.client.gui;
import com.prismora.compat.*;
import com.prismora.client.ShaderController;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.Blaze3D;
import java.util.*;
import java.nio.file.Path;

/** The shaderpack supplies categories, labels, value ranges and profiles. */
public final class LegacyOptionsScreen extends Screen {
 private final Screen parent;private final LegacyMenuSession session;private final String menu;private final int depth;
 private final ShaderController controller=ShaderController.INSTANCE;private int page;private String status="";
 public LegacyOptionsScreen(Screen parent,LegacyPack pack,Map<String,String> values){this(parent,newSession(pack,values),"",0);}
 private static LegacyMenuSession newSession(LegacyPack pack,Map<String,String> values){try{return new LegacyMenuSession(pack,values);}catch(Exception e){ShaderController.INSTANCE.fail(e);try{return new LegacyMenuSession(pack,Map.of());}catch(Exception impossible){throw new IllegalArgumentException(impossible);}}}
 private LegacyOptionsScreen(Screen parent,LegacyMenuSession session,String menu,int depth){super(Component.literal(session.pack().name()));this.parent=parent;this.session=session;this.menu=menu;this.depth=depth;}
 private String language(){return minecraft.options.languageCode;}
 private String text(String key,String fallback){return session.pack().text(language(),key,fallback);}
 private Path presetDirectory(){return minecraft.gameDirectory.toPath().resolve("config/prismora-presets");}
 @Override protected void init(){
  int left=16,area=width-32;var metadata=session.pack().screens().get(menu);if(metadata==null)return;
  int columns=metadata.columns(),buttonWidth=(area-6*(columns-1))/columns,rows=Math.max(1,(height-130)/24),size=rows*columns;
  var entries=session.entries(menu);int pages=Math.max(1,(entries.size()+size-1)/size);page=Math.min(page,pages-1);
  addRenderableWidget(Button.builder(Component.translatable("prismora.back"),b->onClose()).bounds(12,30,70,20).build());
  addRenderableWidget(Button.builder(Component.translatable("prismora.reset"),b->{session.reset();rebuildWidgets();}).bounds(width-82,30,70,20).build());
  for(int i=page*size;i<Math.min(entries.size(),(page+1)*size);i++){
   String entry=entries.get(i);if(entry.equals("<empty>"))continue;int slot=i-page*size,x=left+(slot%columns)*(buttonWidth+6),y=62+(slot/columns)*24;
   if(entry.equals("<profile>")){
    String selected=session.matchedProfile();String label=text("profile."+selected,selected.isEmpty()?Component.translatable("prismora.profile.custom").getString():selected);
    var button=Button.builder(Component.translatable("prismora.pack_profile").append(": "+label),b->{var names=new ArrayList<>(session.pack().profiles().keySet());if(names.isEmpty())return;String next=names.get((names.indexOf(session.matchedProfile())+1)%names.size());try{session.profile(next);}catch(Exception e){status=e.getMessage();}rebuildWidgets();}).bounds(x,y,buttonWidth,20).build();button.active=!session.pack().profiles().isEmpty();addRenderableWidget(button);continue;
   }
   if(entry.startsWith("[")&&entry.endsWith("]")){
    String name=entry.substring(1,entry.length()-1);var button=Button.builder(Component.literal(text("screen."+name,name)+" >"),b->{if(depth<32)minecraft.gui.setScreen(new LegacyOptionsScreen(this,session,name,depth+1));}).bounds(x,y,buttonWidth,20).build();button.active=depth<32&&session.pack().screens().containsKey(name);addRenderableWidget(button);continue;
   }
   var option=session.pack().options().get(entry);String label=text("option."+entry,entry);
   if(option==null){var info=Button.builder(Component.literal(label),b->{}).bounds(x,y,buttonWidth,20).build();String comment=text("option."+entry+".comment","");if(!comment.isEmpty())info.setTooltip(Tooltip.create(Component.literal(comment)));addRenderableWidget(info);continue;}
   int index=option.values().indexOf(session.value(entry));
   if(option.slider()){
    var slider=new AbstractSliderButton(x,y,buttonWidth,20,Component.empty(),Math.max(0,index)/(double)Math.max(1,option.values().size()-1)){
     {updateMessage();}
     protected void updateMessage(){setMessage(Component.literal(label+": "+text("value."+entry+"."+session.value(entry),session.value(entry))));}
     protected void applyValue(){int selected=(int)Math.round(value*(option.values().size()-1));try{session.set(entry,option.values().get(selected));}catch(Exception e){status=e.getMessage();}}
    };String comment=text("option."+entry+".comment","");if(!comment.isEmpty())slider.setTooltip(Tooltip.create(Component.literal(comment)));addRenderableWidget(slider);
   }else{
    String current=session.value(entry);String value=option.toggle()?Component.translatable(current.equals("true")?"options.on":"options.off").getString():text("value."+entry+"."+current,current);
    var button=Button.builder(Component.literal(label+": "+value),b->{try{session.set(entry,option.values().get((option.values().indexOf(session.value(entry))+1)%option.values().size()));}catch(Exception e){status=e.getMessage();}rebuildWidgets();}).bounds(x,y,buttonWidth,20).build();String comment=text("option."+entry+".comment","");if(!comment.isEmpty())button.setTooltip(Tooltip.create(Component.literal(comment)));addRenderableWidget(button);
   }
  }
  int bottom=height-58;
  addRenderableWidget(Button.builder(Component.literal("<"),b->{page=Math.max(0,page-1);rebuildWidgets();}).bounds(16,bottom,30,20).build());
  var apply=Button.builder(Component.translatable("prismora.apply"),b->{controller.applyLegacyOptions(session.pack().id(),session.values());status=Component.translatable("prismora.applied").getString();}).bounds(50,bottom,Math.max(60,(width-164)/3),20).build();apply.active=!controller.busy();addRenderableWidget(apply);
  int w=Math.max(60,(width-164)/3);
  addRenderableWidget(Button.builder(Component.translatable("prismora.export"),b->{try{var path=session.exportPreset(presetDirectory());status=path.getFileName().toString();Blaze3D.openPath(presetDirectory());}catch(Exception e){status=e.getMessage();}}).bounds(54+w,bottom,w,20).build());
  addRenderableWidget(Button.builder(Component.translatable("prismora.import"),b->{try{session.importPreset(presetDirectory());status=Component.translatable("prismora.imported").getString();rebuildWidgets();}catch(Exception e){status=e.getMessage();}}).bounds(58+2*w,bottom,w,20).build());
  addRenderableWidget(Button.builder(Component.literal(">"),b->{page=Math.min(pages-1,page+1);rebuildWidgets();}).bounds(width-46,bottom,30,20).build());
 }
 @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float partial){
  super.extractRenderState(g,x,y,partial);g.centeredText(font,Component.literal(menu.isEmpty()?session.pack().name():text("screen."+menu,menu)),width/2,12,0xFFFFFFFF);
  String message=controller.busy()?Component.translatable("prismora.loading").getString():status;
  if(!controller.error().isEmpty())message=controller.error();g.text(font,font.plainSubstrByWidth(message,Math.max(100,width-24)),12,height-26,0xFFBBBBBB);
 }
 @Override public void onClose(){minecraft.gui.setScreen(parent);}
}
