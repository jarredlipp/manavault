package com.manavault.app;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Persistent, append-only scanner diagnostics. Never retains photographs or collection backups.
 * An Android bridge schedules every append on a dedicated single-thread executor.
 * The 150-line WebView preview is NOT the historical diagnostic source.
 */
final class ScannerDiagnosticArchive {
    static final int MAX_SESSIONS=20;
    static final long MAX_TOTAL_BYTES=32L*1024*1024;
    static final long MAX_SESSION_BYTES=8L*1024*1024;
    private final File directory;
    private File active;
    private boolean truncated;
    private long lines;
    ScannerDiagnosticArchive(File dir){directory=dir;}

    synchronized void beginSession(String label) throws IOException {
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Cannot create diagnostics directory");
        String stamp=new SimpleDateFormat("yyyyMMdd-HHmmss-SSS",Locale.US).format(new Date());
        // Unique even when the scanner is opened repeatedly in one millisecond.
        active=new File(directory,"scan-"+stamp+"-"+UUID.randomUUID().toString().substring(0,8)+".txt");
        truncated=false;lines=0;
        append("=== ManaVault scanner session started " + new Date() + " · " + label + " ===");
        prune();
    }

    synchronized void append(String raw) throws IOException {
        if(active==null)beginSession("Recovery session");
        if(truncated)return;
        String clean=String.valueOf(raw==null?"":raw).replace('\r',' ').replace('\n',' ');
        if(clean.length()>4096)clean=clean.substring(0,4096)+" [line clipped]";
        byte[] payload=(clean+"\n").getBytes(StandardCharsets.UTF_8);
        if(active.length()+payload.length>MAX_SESSION_BYTES){
            truncated=true;
            try(FileOutputStream stream=new FileOutputStream(active,true)){
                stream.write("[Diagnostic session size limit reached; open a new scanner session.]\n".getBytes(StandardCharsets.UTF_8));
            }
            return;
        }
        try(FileOutputStream stream=new FileOutputStream(active,true)){stream.write(payload);}
        lines++;
    }

    synchronized int countSessions(){return sessions().size();}
    synchronized long countCurrentLines(){return lines;}

    /** Streams all retained scanner sessions as separate files, so the Android share
     * sheet/clipboard cannot silently truncate a long text payload. */
    synchronized int writeZip(OutputStream destination) throws IOException {
        List<File> files=sessions();
        try(ZipOutputStream zip=new ZipOutputStream(destination,StandardCharsets.UTF_8)){
            zip.putNextEntry(new ZipEntry("README.txt"));
            zip.write(("ManaVault complete scanner diagnostic archive\n"+
                    "Each scan-*.txt is one native scanner session. All messages are stored in order.\n"+
                    "Historical lines previously evicted by older app versions cannot be reconstructed.\n"+
                    "Card photographs and collection contents are not included.\n"+
                    "Sessions included: "+files.size()+"\n").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            byte[] buffer=new byte[16384];
            for(File file:files){
                zip.putNextEntry(new ZipEntry(file.getName()));
                try(InputStream in=new FileInputStream(file)){
                    int n;while((n=in.read(buffer))!=-1)zip.write(buffer,0,n);
                }
                zip.closeEntry();
            }
        }
        return files.size();
    }
    private List<File> sessions(){
        File[] list=directory.listFiles(f->f.isFile()&&f.getName().matches("scan-[0-9]{8}-[0-9]{6}-[0-9]{3}-[a-f0-9]{8}\\.txt"));
        if(list==null)return new ArrayList<>();
        Arrays.sort(list,Comparator.comparing(File::getName));
        return new ArrayList<>(Arrays.asList(list));
    }
    private void prune(){
        List<File> files=sessions();
        long sum=0;for(File f:files)sum+=f.length();
        for(File file:files){
            if(files.size()<=MAX_SESSIONS && sum<=MAX_TOTAL_BYTES)break;
            if(file.equals(active))continue;
            long size=file.length();if(file.delete()){sum-=size;files.remove(file);}
            // restart after removal so iteration never mutates the iterator
            prune();return;
        }
    }
}
