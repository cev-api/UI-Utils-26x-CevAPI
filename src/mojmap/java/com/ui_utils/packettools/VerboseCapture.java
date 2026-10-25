package com.ui_utils.packettools;
import java.util.*;
import java.util.concurrent.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import com.google.gson.*;
import com.ui_utils.uiutils.UiUtils;
/** One bounded asynchronous output owner for an existing verbose activation. */
final class VerboseCapture {
 final PacketDumper dumper;
 final boolean disk;
 final String id=UUID.randomUUID().toString();
 final PacketTimingTracker timing=new PacketTimingTracker(id);
 final Path file,human;
 final ArrayDeque<Runnable> queue=new ArrayDeque<>();
 final Thread worker;
 final long started=System.nanoTime();
 boolean closing;
 long dropped,firstLoss,lastLoss;
 long totalDropped,errors;
 BufferedWriter output,text;
 VerboseCapture(Path directory,PacketDecodeCoverage coverage,PacketFilter filter,boolean disk) {
 this.disk=disk;
 dumper=new PacketDumper(coverage,filter,new EntityLifecycleTracker());
  file=directory.resolve("verbose-"+id+".jsonl");human=directory.resolve("verbose-"+id+".log");
  worker=new Thread(this::run,"UI-Utils-packet-log-"+id);worker.setDaemon(true);worker.start();
 }
 synchronized void submit(Runnable job) {
  if(closing)return;
  if(queue.size()>=256){if(dropped++==0)firstLoss=System.nanoTime();lastLoss=System.nanoTime();totalDropped++;return;}
  queue.addLast(job);notifyAll();
 }
 synchronized void close(){closing=true;notifyAll();}
 void await() {try{worker.join(10000);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
 void line(String line,boolean readable,boolean disk) {
  try {
   if(!disk){UiUtils.LOGGER.info("{}",line);net.minecraft.client.Minecraft.getInstance().execute(()->UiUtils.postSystemMessage(net.minecraft.network.chat.Component.literal(line)));return;}
   if(output==null){Files.createDirectories(file.getParent());output=Files.newBufferedWriter(file,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
   output.write(line);output.newLine();
   if(readable){if(text==null)text=Files.newBufferedWriter(human,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);text.write(AdvancedPacketTool.toHumanReadable(line));text.newLine();}
  }catch(IOException e){if(errors++==0)UiUtils.LOGGER.error("Packet capture {} I/O failed; capture incomplete",id,e);}
 }
 void event(Map<String,Object> fields){line(new Gson().toJson(fields),false,disk);}
 void flush() throws IOException {if(output!=null)output.flush();if(text!=null)text.flush();}
 void loss(){long count,first,last;synchronized(this){count=dropped;first=firstLoss;last=lastLoss;dropped=0;}if(count>0)event(Map.of("recordType","CAPTURE_LOSS","sessionId",id,"droppedRecordCount",count,"firstDroppedNs",first,"lastDroppedNs",last,"captureComplete",false));}
 void run(){
  try{
   event(Map.of("recordType","SESSION_START","sessionId",id,"monotonicNs",started));
   long lastFlush=System.nanoTime();
   while(true){Runnable job;synchronized(this){if(queue.isEmpty()&&!closing)wait(200);job=queue.pollFirst();if(job==null&&closing)break;}
    if(job!=null)try{job.run();}catch(RuntimeException e){errors++;UiUtils.LOGGER.warn("Packet diagnostic failed",e);}
    if(System.nanoTime()-lastFlush>1000000000L){loss();flush();lastFlush=System.nanoTime();}
   }
   loss();event(Map.of("recordType","SESSION_END","sessionId",id,"elapsedNs",System.nanoTime()-started,"droppedRecords",totalDropped,"ioErrors",errors,"captureComplete",totalDropped==0&&errors==0,"packetTimings",timing.summary()));flush();
  }catch(Exception e){UiUtils.LOGGER.error("Packet capture {} failed",id,e);}finally{try{if(output!=null)output.close();if(text!=null)text.close();}catch(IOException e){UiUtils.LOGGER.error("Packet log close failed",e);}}
 }
}
