package com.prismora.pack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class PackWatcherTest {
 @TempDir Path root;
 @Test void pollingAfterShutdownIsSafe()throws Exception{
  var watcher=new PackWatcher(0);watcher.watch(root,"");watcher.close();assertFalse(watcher.changed());assertDoesNotThrow(watcher::close);
 }
 @Test void observesNestedShaderEditAndDebounces()throws Exception{
  Path shaders=Files.createDirectories(root.resolve("pack/shaders"));Path file=Files.writeString(shaders.resolve("post.fsh"),"before");
  try(var watcher=new PackWatcher(30)){
   watcher.watch(root,"pack");Files.writeString(file,"after");boolean changed=false;long deadline=System.nanoTime()+3_000_000_000L;
   while(System.nanoTime()<deadline){if(watcher.changed()){changed=true;break;}Thread.sleep(15);}
   assertTrue(changed,"Expected nested edit event");assertFalse(watcher.changed());
   watcher.watch(root,"");Files.writeString(file,"unwatched");Thread.sleep(60);assertFalse(watcher.changed());
  }
 }
}
