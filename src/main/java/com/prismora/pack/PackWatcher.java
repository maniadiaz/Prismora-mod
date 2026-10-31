package com.prismora.pack;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Nonblocking event polling with debounce; polled by the client tick. */
public final class PackWatcher implements AutoCloseable {
 private final WatchService service;
 private final long settleNanos;
 private final List<WatchKey> keys=new ArrayList<>();
 private long changedAt;
 private boolean closed;
 public PackWatcher(long settleMillis)throws IOException{service=FileSystems.getDefault().newWatchService();settleNanos=settleMillis*1_000_000L;}
 public void watch(Path root,String selected)throws IOException{
  if(closed)throw new IOException("Pack watcher is closed");
  for(var key:keys)key.cancel();keys.clear();while(service.poll()!=null){}changedAt=0;
  register(root);
  if(!selected.isEmpty()){
   Path pack=root.resolve(selected);
   if(Files.isDirectory(pack)&&!Files.isSymbolicLink(pack))try(var paths=Files.walk(pack)){
    var iterator=paths.limit(513).iterator();int count=0;
    while(iterator.hasNext()){
     Path path=iterator.next();if(++count>512)throw new IOException("Too many paths to watch");
     if(Files.isSymbolicLink(path)||!path.toRealPath().startsWith(root.toRealPath()))throw new IOException("External pack watch path");
     if(Files.isDirectory(path))register(path);
    }
   }
  }
 }
 private void register(Path path)throws IOException{keys.add(path.register(service,StandardWatchEventKinds.ENTRY_CREATE,StandardWatchEventKinds.ENTRY_MODIFY,StandardWatchEventKinds.ENTRY_DELETE));}
 public boolean changed(){
  if(closed)return false;
  WatchKey key;while((key=service.poll())!=null){if(!key.pollEvents().isEmpty())changedAt=System.nanoTime();key.reset();}
  if(changedAt!=0 && System.nanoTime()-changedAt>=settleNanos){changedAt=0;return true;}return false;
 }
 @Override public void close()throws IOException{if(!closed){closed=true;service.close();}}
}
