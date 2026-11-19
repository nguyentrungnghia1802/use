package org.tzi.use.plugins.jacamo.bridge.protocol;

import org.tzi.use.plugins.jacamo.bridge.SourceSnapshot;
import org.tzi.use.plugins.jacamo.bridge.mapping.AttributeMapping;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Authenticated, bounded local frames. Responses bind the complete request digest. */
public final class CheckpointProtocol {
    public static final String VERSION = "CSL_STATE_SYNC/3.0";
    private static final String PREVIOUS_VERSION = "CSL_STATE_SYNC/2.0";
    private static final String PLAN_BOUNDARY_POLICY = "JASON_PLAN_BOUNDARIES/1";
    private static final int MAX = 8 * 1024 * 1024;

    private CheckpointProtocol() { }

    /** One frozen barrier epoch shared by checks of the local source session. */
    public record Cut(String policy, String scope, long epoch, String revision) {
        public static Cut local(String run, long epoch, String revision) {
            return new Cut(PLAN_BOUNDARY_POLICY, "local-session:" + run, epoch, revision);
        }

        public Map<String, Object> payload() {
            return Map.of("policy", policy, "scope", scope, "epoch", epoch, "revision", revision);
        }

        public static Cut require(Object value, String run, String revision) throws IOException {
            if (!(value instanceof Map<?, ?> fields) || !(fields.get("epoch") instanceof Number number)) {
                throw new IOException("STATE_CUT_REQUIRED");
            }
            long epoch;
            try { epoch = Long.parseLong(number.toString()); }
            catch (NumberFormatException invalid) { throw new IOException("STATE_CUT_EPOCH", invalid); }
            var expected = local(run, epoch, revision);
            if (epoch < 1 || !expected.policy.equals(fields.get("policy"))
                    || !expected.scope.equals(fields.get("scope")) || !expected.revision.equals(fields.get("revision"))) {
                throw new IOException("STATE_CUT_MISMATCH");
            }
            return expected;
        }
    }

    /** Bind the authored models, project descriptor and effective mapping admitted for the run. */
    public static String inputRevision(String modelRevision, Path project, AttributeMapping mapping)
            throws IOException, NoSuchAlgorithmException {
        var attributes = mapping.attributes().stream().map(attribute -> List.of(attribute.sourceCategory(),
                attribute.sourceClass(), attribute.sourceKind(), attribute.sourceName(),
                attribute.targetClass(), attribute.targetName())).toList();
        return digest(Map.of("models", modelRevision, "project", digest(Files.readAllBytes(project)), "attributes", attributes));
    }

    public static boolean supportsReplay(Object version) {
        return VERSION.equals(version) || PREVIOUS_VERSION.equals(version);
    }

    /** Verify the exact cut and result before allowing an authenticated reply to release it. */
    public static boolean approved(Map<String, Object> response, String run, String token, String requestHash, Cut cut)
            throws IOException, NoSuchAlgorithmException {
        if (!VERSION.equals(response.get("version")) || !run.equals(response.get("run"))
                || !token.equals(response.get("token")) || !requestHash.equals(response.get("requestHash"))
                || !cut.equals(Cut.require(response.get("cut"), run, cut.revision()))) {
            throw new IOException("STATE_APPROVAL_IDENTITY_MISMATCH");
        }
        var result = CanonicalJson.object(response.get("result"));
        if (!digest(result).equals(response.get("resultHash"))) throw new IOException("STATE_APPROVAL_RESULT_MISMATCH");
        var decision = response.get("decision");
        if ("HOLD_FAILURE".equals(decision) || "HOLD_UNVERIFIED".equals(decision)) return false;
        if (!"APPROVE".equals(decision) || !decision.equals(result.get("decision"))) {
            throw new IOException("STATE_APPROVAL_DECISION_MISMATCH");
        }
        return true;
    }
    public static void write(OutputStream out,Map<String,Object> payload,byte[] secret)throws Exception {
        byte[] body=CanonicalJson.encode(payload,MAX);var data=new DataOutputStream(out);
        data.writeInt(body.length);data.write(body);data.write(signature(body,secret));data.flush();
    }
    public static Map<String,Object> read(InputStream in,byte[] secret)throws Exception {
        var data=new DataInputStream(in);int size=data.readInt();if(size<2||size>MAX)throw new IOException("STATE_FRAME_SIZE");
        byte[] body=data.readNBytes(size),mac=data.readNBytes(32);
        if(body.length!=size||mac.length!=32||!MessageDigest.isEqual(mac,signature(body,secret)))throw new IOException("STATE_FRAME_AUTHENTICATION");
        return CanonicalJson.object(CanonicalJson.decode(body,MAX,64,MAX));
    }
    public static String digest(Map<String, Object> payload) throws NoSuchAlgorithmException {
        return digest(CanonicalJson.encode(payload, MAX));
    }

    private static String digest(byte[] bytes) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static byte[] signature(byte[] body,byte[] secret)throws Exception {Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret,"HmacSHA256"));return mac.doFinal(body);}
    public static Map<String,Object> encodeSnapshot(SourceSnapshot snapshot) {
        return Map.of("run",snapshot.run(),"sequence",snapshot.sequence(),"complete",snapshot.complete(),"artifacts",snapshot.artifacts().stream().map(a->Map.of("identity",a.identity(),"javaClass",a.javaClass(),"name",a.name(),"workspace",a.workspace(),"properties",a.properties(),"observers",a.observers().stream().sorted().toList())).toList(),
                "nodes",snapshot.nodes().stream().map(n->Map.of("identity",n.identity(),"kind",n.kind(),"type",n.type(),"name",n.name(),"workspace",n.workspace(),"parent",n.parent(),"properties",n.properties())).toList(),
                "enactments",snapshot.enactments().stream().map(e->Map.of("context",e.context(),"player",e.player(),"role",e.role())).toList(),
                "memberships",snapshot.memberships().stream().map(m->Map.of("member",m.member(),"group",m.group())).toList());
    }
    public static SourceSnapshot decodeSnapshot(Map<String,Object> data) {
        if(!data.containsKey("memberships"))throw new IllegalArgumentException("MEMBERSHIP_COVERAGE_MISSING_V2");
        var artifacts=new ArrayList<SourceSnapshot.Artifact>();
        for(Object raw:(List<?>)data.get("artifacts")){var row=CanonicalJson.object(raw);var observers=new HashSet<String>();for(Object observer:(List<?>)row.getOrDefault("observers",List.of()))observers.add((String)observer);artifacts.add(new SourceSnapshot.Artifact((String)row.get("identity"),(String)row.get("javaClass"),(String)row.get("name"),(String)row.get("workspace"),CanonicalJson.object(row.get("properties")),observers));}
        var nodes=new ArrayList<SourceSnapshot.Node>();
        for(Object raw:(List<?>)data.getOrDefault("nodes",List.of())){var n=CanonicalJson.object(raw);nodes.add(new SourceSnapshot.Node((String)n.get("identity"),(String)n.get("kind"),(String)n.get("type"),(String)n.get("name"),(String)n.get("workspace"),(String)n.get("parent"),CanonicalJson.object(n.get("properties"))));}
        var enactments=new ArrayList<SourceSnapshot.Enactment>();
        for(Object raw:(List<?>)data.getOrDefault("enactments",List.of())){var e=CanonicalJson.object(raw);enactments.add(new SourceSnapshot.Enactment((String)e.get("context"),(String)e.get("player"),(String)e.get("role")));}
        var memberships=new ArrayList<SourceSnapshot.Membership>();
        for(Object raw:(List<?>)data.getOrDefault("memberships",List.of())){var m=CanonicalJson.object(raw);memberships.add(new SourceSnapshot.Membership((String)m.get("member"),(String)m.get("group")));}
        return new SourceSnapshot((String)data.get("run"),((Number)data.get("sequence")).longValue(),Boolean.TRUE.equals(data.get("complete")),artifacts,nodes,enactments,memberships);
    }
}
