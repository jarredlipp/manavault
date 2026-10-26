package com.manavault.app;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;

public final class ScannerDiagnosticArchiveTest {
  private static void ok(boolean value,String message){if(!value)throw new AssertionError(message);}
  public static void main(String[] args)throws Exception{
    File tmp=Files.createTempDirectory("mv514-diagnostics-").toFile();
    try{
      ScannerDiagnosticArchive store=new ScannerDiagnosticArchive(tmp);
      store.beginSession("session one");
      for(int i=0;i<186;i++){
        store.append("2026-10-26T00:00:01Z [info] Printing marks for Card "+i);
        store.append("2026-10-26T00:00:02Z [info] Saved DURING SCAN: Card "+i);
      }
      ok(store.countCurrentLines()==373,"no 150-message truncation");
      store.beginSession("session two");
      for(int i=0;i<25;i++)store.append("More card "+i);
      ByteArrayOutputStream dest=new ByteArrayOutputStream();
      ok(store.writeZip(dest)==2,"export both independent sessions");
      Set<String> lines=new HashSet<>();int entries=0;
      try(ZipInputStream zis=new ZipInputStream(new ByteArrayInputStream(dest.toByteArray()),StandardCharsets.UTF_8)){
        ZipEntry item;while((item=zis.getNextEntry())!=null){entries++;
          ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;
          while((n=zis.read(buf))>0)bytes.write(buf,0,n);
          String payload=bytes.toString("UTF-8");
          if(item.getName().startsWith("scan-"))for(String line:payload.split("\\n"))lines.add(line);
        }
      }
      ok(entries==3,"zip must contain README plus two log groups");
      ok(lines.contains("2026-10-26T00:00:02Z [info] Saved DURING SCAN: Card 0"),"first scan retained");
      ok(lines.contains("2026-10-26T00:00:02Z [info] Saved DURING SCAN: Card 185"),"186th scan retained");
      ok(lines.contains("More card 24"),"second session retained");
      System.out.println("v5.13.4 persistent complete diagnostic sessions PASS");
    }finally{for(File f:Objects.requireNonNull(tmp.listFiles()))f.delete();tmp.delete();}
  }
}
