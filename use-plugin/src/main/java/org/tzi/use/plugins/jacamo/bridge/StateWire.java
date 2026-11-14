package org.tzi.use.plugins.jacamo.bridge;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.tzi.use.plugins.jacamo.bridge.protocol.CanonicalJson;

/** Authenticated, bounded local frames. Responses bind the complete request digest. */
final class StateWire {
    static final String VERSION="CSL_STATE_SYNC/2.0";
    static final int MAX=8*1024*1024;
    static void write(OutputStream out,Map<String,Object> payload,byte[] secret)throws Exception {
        byte[] body=CanonicalJson.encode(payload,MAX);var data=new DataOutputStream(out);
        data.writeInt(body.length);data.write(body);data.write(signature(body,secret));data.flush();
    }
    static Map<String,Object> read(InputStream in,byte[] secret)throws Exception {
        var data=new DataInputStream(in);int size=data.readInt();if(size<2||size>MAX)throw new IOException("STATE_FRAME_SIZE");
        byte[] body=data.readNBytes(size),mac=data.readNBytes(32);
        if(body.length!=size||mac.length!=32||!MessageDigest.isEqual(mac,signature(body,secret)))throw new IOException("STATE_FRAME_AUTHENTICATION");
        return CanonicalJson.object(CanonicalJson.decode(body,MAX,64,MAX));
    }
    static String digest(Map<String,Object> payload)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(CanonicalJson.encode(payload,MAX)));}
    private static byte[] signature(byte[] body,byte[] secret)throws Exception {Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret,"HmacSHA256"));return mac.doFinal(body);}
    static Map<String,Object> snapshot(CslStateVerifier.Snapshot snapshot) {
        return Map.of("run",snapshot.run(),"sequence",snapshot.sequence(),"complete",snapshot.complete(),"artifacts",snapshot.artifacts().stream().map(a->Map.of("identity",a.identity(),"javaClass",a.javaClass(),"name",a.name(),"workspace",a.workspace(),"properties",a.properties(),"observers",a.observers().stream().sorted().toList())).toList(),
                "nodes",snapshot.nodes().stream().map(n->Map.of("identity",n.identity(),"kind",n.kind(),"type",n.type(),"name",n.name(),"workspace",n.workspace(),"parent",n.parent(),"properties",n.properties())).toList(),
                "enactments",snapshot.enactments().stream().map(e->Map.of("context",e.context(),"player",e.player(),"role",e.role())).toList(),
                "memberships",snapshot.memberships().stream().map(m->Map.of("member",m.member(),"group",m.group())).toList());
    }
    static CslStateVerifier.Snapshot snapshot(Map<String,Object> data) {
        if(!data.containsKey("memberships"))throw new IllegalArgumentException("MEMBERSHIP_COVERAGE_MISSING_V2");
        var artifacts=new ArrayList<CslStateVerifier.Artifact>();
        for(Object raw:(List<?>)data.get("artifacts")){var row=CanonicalJson.object(raw);var observers=new HashSet<String>();for(Object observer:(List<?>)row.getOrDefault("observers",List.of()))observers.add((String)observer);artifacts.add(new CslStateVerifier.Artifact((String)row.get("identity"),(String)row.get("javaClass"),(String)row.get("name"),(String)row.get("workspace"),CanonicalJson.object(row.get("properties")),observers));}
        var nodes=new ArrayList<CslStateVerifier.Node>();
        for(Object raw:(List<?>)data.getOrDefault("nodes",List.of())){var n=CanonicalJson.object(raw);nodes.add(new CslStateVerifier.Node((String)n.get("identity"),(String)n.get("kind"),(String)n.get("type"),(String)n.get("name"),(String)n.get("workspace"),(String)n.get("parent"),CanonicalJson.object(n.get("properties"))));}
        var enactments=new ArrayList<CslStateVerifier.Enactment>();
        for(Object raw:(List<?>)data.getOrDefault("enactments",List.of())){var e=CanonicalJson.object(raw);enactments.add(new CslStateVerifier.Enactment((String)e.get("context"),(String)e.get("player"),(String)e.get("role")));}
        var memberships=new ArrayList<CslStateVerifier.Membership>();
        for(Object raw:(List<?>)data.getOrDefault("memberships",List.of())){var m=CanonicalJson.object(raw);memberships.add(new CslStateVerifier.Membership((String)m.get("member"),(String)m.get("group")));}
        return new CslStateVerifier.Snapshot((String)data.get("run"),((Number)data.get("sequence")).longValue(),Boolean.TRUE.equals(data.get("complete")),artifacts,nodes,enactments,memberships);
    }
}
