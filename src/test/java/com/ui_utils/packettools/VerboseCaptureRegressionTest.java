package com.ui_utils.packettools;
import java.util.*;
import java.nio.file.*;
import java.util.concurrent.*;
import com.google.gson.JsonParser;
public final class VerboseCaptureRegressionTest {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception {
  Path dir=Files.createTempDirectory("ui-utils-capture-");
  var a=new VerboseCapture(dir,new PacketDecodeCoverage(),new PacketFilter(),true);
  var b=new VerboseCapture(dir,new PacketDecodeCoverage(),new PacketFilter(),true);
  CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
  a.submit(()->{entered.countDown();try{release.await();}catch(Exception e){throw new RuntimeException(e);}});
  check(entered.await(5,TimeUnit.SECONDS),"Async owner starts");
  for(int i=0;i<400;i++)a.submit(()->a.event(Map.of("owner","A","sessionId",a.id)));
  b.submit(()->b.event(Map.of("owner","B","sessionId",b.id)));b.close();release.countDown();a.close();a.await();b.await();
  check(!a.file.equals(b.file),"Rapid activations have unique files");
  check(a.totalDropped==144,"Bounded queue reports exact dropped jobs");
  long losses=0;for(Path file:List.of(a.file,b.file))for(String line:Files.readAllLines(file)) {
   var record=JsonParser.parseString(line).getAsJsonObject();
   if(record.has("sessionId"))check(file.toString().contains(record.get("sessionId").getAsString()),"Late old-session writes stay isolated");
   if(record.has("recordType")&&record.get("recordType").getAsString().equals("CAPTURE_LOSS"))losses++;
  }
  check(losses==1,"Overload emits one aggregate loss report");
  var t=new PacketTimingTracker("test");Object packet=new Object();
  var start=t.observe(packet,"S2C","DECODED",10000,1,Map.of());
  t.observe(packet,"S2C","HANDLER_START",10500,1,Map.of());
  var end=t.observe(packet,"S2C","HANDLER_END",10550,1,Map.of());
  check(end.get("handlerDurationNs").equals(50L),"Nanosecond duration preserved");
  check(start.get("logicalPacketId").equals(end.get("logicalPacketId")),"Same packet same identity across hooks");
  check(!t.observe(new Object(),"S2C","DECODED",11000,1,Map.of()).get("logicalPacketId").equals(start.get("logicalPacketId")),"Distinct packets not payload-deduplicated");
  check(!end.containsKey("transportWriteCompletedNs"),"No invented write completion");
  System.out.println("UI-Utils verbose queue, overflow aggregation, session isolation, precise timing and stable packet identity passed.");
 }
}
