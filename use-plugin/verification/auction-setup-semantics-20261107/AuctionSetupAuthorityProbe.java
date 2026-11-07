import java.nio.file.*;
import java.security.*;
import java.util.*;
import cartago.*;
import cartago.util.agent.*;

/** Native framework experiment, isolated from the original MAS and USE verdicts. */
public class AuctionSetupAuthorityProbe {
 static final long TIMEOUT=5000;
 static void act(CartagoBasicContext agent, ArtifactId artifact, String op, Object... args)throws Exception {
  agent.doAction(artifact,new Op(op,args),TIMEOUT);
 }
 static boolean attempt(String label,CartagoBasicContext agent,ArtifactId artifact,String op,Object... args)throws Exception {
  try {act(agent,artifact,op,args);System.out.println(label+" SUCCESS performer="+agent.getName());return true;}
  catch(ActionFailedException failure){System.out.println(label+" REJECTED performer="+agent.getName()+" reason="+failure.getFailureMsg()+" detail="+failure.getFailureDescr());return false;}
 }
 static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
 static void focus(CartagoBasicContext agent,ArtifactId artifact)throws Exception {
  agent.doAction(new Op("focus",artifact),artifact.getWorkspaceId(),TIMEOUT);
 }
 static Object property(CartagoEnvironment env,String artifact,String name)throws Exception {
  return env.getController("/main").getArtifactInfo(artifact).getObsProperties().stream().filter(p->p.getName().equals(name)).findFirst().orElseThrow().getValue();
 }
 public static void main(String[] args) {
  try {run(Path.of(args[0]));System.out.println("AUCTION_SETUP_AUTHORITY_PROBE_PASS");System.exit(0);}
  catch(Throwable error){error.printStackTrace();System.exit(1);}
 }
 static void run(Path os)throws Exception {
  var env=CartagoEnvironment.getInstance();env.init();
  try {
   var a=new CartagoBasicContext("setupCreator");var b=new CartagoBasicContext("otherAuctioneer");
   var main=a.getJoinedWspId("main");
   var org=a.makeArtifact(main,"probeOrg","ora4mas.nopl.OrgBoard",new Object[]{os.toRealPath().toString()});
   var groupOut=new OpFeedbackParam<ArtifactId>();act(a,org,"createGroup","probeGroupA","auctionGroup",groupOut);
   var group=groupOut.get();act(a,group,"adoptRole","auctioneer");
   require(!attempt("SECOND_AUCTIONEER_SAME_GROUP",b,group,"adoptRole","auctioneer"),"Original max=1 should reject simultaneous second auctioneer");
   var participants=new ArrayList<CartagoBasicContext>();
   for(int i=0;i<4;i++){var p=new CartagoBasicContext("participant"+i);act(p,group,"adoptRole","participant");participants.add(p);}

   // The same scheme-setup operations as +!do_auction, with explicit artifact IDs.
   String schemeName="sch_probe";var schemeOut=new OpFeedbackParam<ArtifactId>();
   act(a,org,"createScheme",schemeName,"doAuction",schemeOut);var scheme=schemeOut.get();
   act(a,scheme,"setArgumentValue","auction","Id","probe");
   act(a,scheme,"setArgumentValue","auction","Service","test_service");
   act(a,scheme,"setOwner",a.getName());focus(a,scheme);act(a,group,"addScheme",schemeName);
   focus(b,scheme);System.out.println("OTHER_AGENT_FOCUS_SCHEME SUCCESS performer="+b.getName());
   require(!attempt("OTHER_AGENT_COMMIT_BEFORE_CREATOR",b,scheme,"commitMission","mAuctioneer"),"Agent with no qualifying role should not commit the auctioneer mission");
   require(attempt("CREATOR_COMMIT",a,scheme,"commitMission","mAuctioneer"),"Creator with the role should commit");
   for(var p:participants)act(p,scheme,"commitMission","mParticipant");
   System.out.println("SCHEME_SETUP_COMPLETE scheme="+schemeName+" owner="+a.getName());

   var groupBOut=new OpFeedbackParam<ArtifactId>();act(a,org,"createGroup","probeGroupB","auctionGroup",groupBOut);
   act(b,groupBOut.get(),"adoptRole","auctioneer");
   System.out.println("SECOND_AUCTIONEER_DIFFERENT_GROUP SUCCESS performer="+b.getName());
   require(!attempt("OTHER_GROUP_AUCTIONEER_COMMIT_ORIGINAL_SCHEME",b,scheme,"commitMission","mAuctioneer"),"A role elsewhere must not bypass the original scheme's mission constraints");

   // +!start creates the domain Artifact; +!do_auction did not create it.
   var byB=b.makeArtifact(main,"probe","auction_env.AuctionArtifact");focus(b,byB);
   require(attempt("OTHER_AUCTIONEER_CREATE_FOCUS_START",b,byB,"start","test_service"),"Domain operation has no caller-role/owner guard");
   require(Boolean.TRUE.equals(property(env,"probe","running")),"Real domain running state should change");
   require("test_service".equals(property(env,"probe","task")),"Real domain service should change");
   attempt("OTHER_AUCTIONEER_REPORT_ORGANISATIONAL_START",b,scheme,"goalAchieved","start");
   require(!attempt("CREATOR_START_ALREADY_RUNNING",a,byB,"start","test_service"),"Original state guard must reject duplicate start");

   var byA=a.makeArtifact(main,"createdByA","auction_env.AuctionArtifact");focus(b,byA);
   require(attempt("OTHER_AGENT_START_CREATOR_ARTIFACT",b,byA,"start","second_service"),"Artifact creation ownership is not a start authorization guard");
   require(Boolean.TRUE.equals(property(env,"createdByA","running")),"Second real artifact should be running");
  }finally{env.shutdown();}
 }
}
