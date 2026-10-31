package com.prismora.pack;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Bounded immutable-content snapshot, shared by both native and legacy pack readers. */
public final class PackFiles {
    public static final int MAX_FILE=1024*1024, MAX_TOTAL=64*MAX_FILE, MAX_ENTRIES=8192;
    private PackFiles() {}
    public static Map<String,byte[]> read(Path source)throws IOException {
        var files=new TreeMap<String,byte[]>();long[] total={0};
        if(Files.isSymbolicLink(source))throw new IOException("Pack links are not supported");
        if(Files.isDirectory(source)) {
            Path real=source.toRealPath();
            try(var stream=Files.walk(source)) {
                var iterator=stream.iterator();int visited=0;
                while(iterator.hasNext()) {
                    Path path=iterator.next();if(++visited>MAX_ENTRIES*2)throw new IOException("Too many pack entries");
                    if(Files.isSymbolicLink(path)||!path.toRealPath().startsWith(real))throw new IOException("External pack link");
                    if(Files.isRegularFile(path))try(var input=Files.newInputStream(path)){add(files,source.relativize(path).toString().replace('\\','/'),input,total);}
                }
            }
        }else try(var zip=new ZipFile(source.toFile())) {
            var entries=zip.entries();int count=0;
            while(entries.hasMoreElements()) {
                var entry=entries.nextElement();if(++count>MAX_ENTRIES)throw new IOException("Too many ZIP entries");
                PackLoader.safePath(entry.isDirectory()?entry.getName().replaceAll("/$",""):entry.getName());
                if(!entry.isDirectory())try(var input=zip.getInputStream(entry)){add(files,entry.getName(),input,total);}
            }
        }
        // A common release layout wraps shaders/ in a single directory. Never guess among several roots.
        if(!files.containsKey("pack.json") && files.keySet().stream().noneMatch(n->n.startsWith("shaders/"))) {
            var roots=new TreeSet<String>();
            for(String name:files.keySet()){int index=name.indexOf("/shaders/");if(index>0)roots.add(name.substring(0,index+1));}
            if(roots.size()==1){String prefix=roots.first();var unwrapped=new TreeMap<String,byte[]>();files.forEach((name,bytes)->{if(name.startsWith(prefix))unwrapped.put(name.substring(prefix.length()),bytes);});return Collections.unmodifiableMap(unwrapped);}
            if(roots.size()>1)throw new IOException("Ambiguous shaderpack roots");
        }
        return Collections.unmodifiableMap(files);
    }
    private static void add(Map<String,byte[]> files,String name,InputStream input,long[] total)throws IOException {
        PackLoader.safePath(name);
        if(files.size()>=MAX_ENTRIES||files.containsKey(name)||files.keySet().stream().anyMatch(n->n.equalsIgnoreCase(name)))throw new IOException("Duplicate or excessive pack entries");
        byte[] bytes=input.readNBytes(MAX_FILE+1);total[0]+=bytes.length;
        if(bytes.length>MAX_FILE||total[0]>MAX_TOTAL)throw new IOException("Pack size limit exceeded");files.put(name,bytes);
    }
}
