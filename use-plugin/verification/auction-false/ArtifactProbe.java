import java.nio.file.*;
import java.util.*;
import cartago.*;
import cartago.util.agent.CartagoBasicContext;
import auction_env.AuctionArtifact;
class ArtifactProbe {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static Map<String,Object> state(CartagoEnvironment env,ArtifactId id)throws Exception{
  var result=new TreeMap<String,Object>();for(var p:env.getController("/main").getArtifactInfo(id.getName()).getObsProperties())result.put(p.getName(),p.getValue().toString());return result;
 }
 public static void main(String[] args)throws Exception{
  boolean faulty=Boolean.parseBoolean(args[0]);var env=CartagoEnvironment.getInstance();env.init();
  var actor=new CartagoBasicContext("probe"+UUID.randomUUID().toString().replace("-",""));
  try{
   var id=actor.makeArtifact(actor.getJoinedWspId("main"),"observed",AuctionArtifact.class.getName());
   actor.doAction(id,new Op("start","service"));actor.doAction(id,new Op("bid",42.0));
   var before=state(env,id);check(before.get("running").equals("true"),"PRE running");
   actor.doAction(id,new Op("stop"));var after=state(env,id);
   check(after.get("running").equals(Boolean.toString(faulty)),"Actual stop state must match the copied implementation");
   check(after.get("winner").equals(actor.getName()),"Real bidder must still be published");
   check(before.get("best_bid").equals(after.get("best_bid")),"stop must retain the real bid");
   byte[] bytes;try(var in=AuctionArtifact.class.getResourceAsStream("AuctionArtifact.class")){bytes=in.readAllBytes();}
   String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
   var receipt=Map.of("status","PASS","faultyVariant",faulty,"sourceOperationSucceeded",true,"before",before,"after",after,"loadedClass",AuctionArtifact.class.getProtectionDomain().getCodeSource().getLocation().toString(),"classSha256",hash);
   new com.fasterxml.jackson.databind.ObjectMapper().writeValue(Path.of(args[1]).toFile(),receipt);System.out.println(receipt);
  }finally{env.shutdown();}System.exit(0);
 }
}
