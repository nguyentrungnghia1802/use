package org.tzi.use.plugins.jacamo.ui;

import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.plugins.jacamo.JaCaMoFacade;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePresenter;

/** Read-only presentation replay of previously recorded native final results. */
public class PersistedCompletionProbe {
 public static void main(String[] args)throws Exception {
  for(String arg:args){
   Map<String,Object> snapshot;
   try(var stream=new GZIPInputStream(Files.newInputStream(Path.of(arg)))){
    snapshot=CanonicalJson.object(CanonicalJson.decode(stream.readAllBytes()));
   }
   var result=RuntimeVerificationResult.fromMap(CanonicalJson.object(snapshot.get("result")));
   String original=result.resultHash();
   var cut=new VerificationSnapshot(result.stateVersion(),result,null);
   var checks=VerifiedCheckpointInventory.evaluate(cut);
   var view=new VerifiedCheckpointService.View("RUNNING","recorded-cut","recorded","replay","","","",Set.of(),false,false,Map.of());
   var presenter=new ObservedConformancePresenter(new JaCaMoFacade(){public String status(){return "Recorded presentation";}});
   var rendered=presenter.captureCheckpoint(cut,view,checks).getLast();
   var badge=new VerificationStatusBadge();badge.displayed(result.sessionId(),view.token(),rendered,checks);
   String status=badge.status(view,"RUNNING","LIVE").text();
   if(!status.startsWith("Completed"))throw new AssertionError("Recorded final requirements not terminal: "+status);
   if(!original.equals(result.resultHash()))throw new AssertionError("Native evidence changed");
   System.out.println("PERSISTED_NATIVE_COMPLETION_PASS "+arg+" => "+status+" nativeHash="+original);
  }
 }
}
