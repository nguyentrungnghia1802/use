package org.tzi.use.semantic.engine.api;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.vnu.sme.goal.dsl.csl.mm.*;

/** Versioned structural digest of the immutable original M1, independent of Java object identity. */
public final class CslModelFingerprint {
    private CslModelFingerprint() { }
    public static String of(CslModel model) {
        try {
            var digest=MessageDigest.getInstance("SHA-256");
            put(digest,"CSL-M1/1",model.version(),model.name());
            size(digest,model.enums());for(var type:model.enums()){put(digest,type.name());strings(digest,type.literals());}
            size(digest,model.entities());for(var entity:model.entities())entity(digest,entity);
            size(digest,model.roles());for(var role:model.roles())role(digest,role);
            size(digest,model.namedDataTypes());for(var type:model.namedDataTypes())put(digest,type.sourceName());
            size(digest,model.relations());for(var relation:model.relations()) {
                put(digest,relation.kind().name(),relation.name());size(digest,relation.endpoints());
                for(var end:relation.endpoints()){put(digest,end.type());optional(digest,end.roleName());cardinality(digest,end.multiplicity());}
            }
            compatibilities(digest,model.compatibilities());
            size(digest,model.generalizations());model.generalizations().forEach(g->put(digest,g.specific(),g.general()));
            size(digest,model.invariants());model.invariants().forEach(i->put(digest,i.contextType(),i.name(),i.expression()));
            size(digest,model.groups());for(var group:model.groups())group(digest,group,0);
            return HexFormat.of().formatHex(digest.digest());
        }catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    private static void group(MessageDigest digest,CslGroup group,int depth) {
        if(depth>256)throw new IllegalArgumentException("CSL_GROUP_DIGEST_DEPTH_LIMIT");
        put(digest,group.name(),Boolean.toString(group.isOrganizationalContext()));optional(digest,group.specializes());attributes(digest,group.attributes());
        size(digest,group.members());group.members().forEach(m->{put(digest,m.type());cardinality(digest,m.multiplicity());});
        size(digest,group.roles());group.roles().forEach(m->{put(digest,m.roleName());cardinality(digest,m.cardinality());});
        size(digest,group.entities());group.entities().forEach(m->{put(digest,m.entityName());cardinality(digest,m.cardinality());});
        compatibilities(digest,group.compatibilities());
        size(digest,group.roleEntityRelations());group.roleEntityRelations().forEach(r->{put(digest,r.name(),r.type().name(),r.scope().name(),Boolean.toString(r.extendsSubgroups()));role(digest,r.sourceRole());entity(digest,r.targetEntity());});
        size(digest,group.cardinalityConstraints());
        group.cardinalityConstraints().forEach(c->{put(digest,c.targetKind().name(),c.targetName());cardinality(digest,c.cardinality());});
        size(digest,group.subgroups());group.subgroups().forEach(s->{cardinality(digest,s.cardinality());group(digest,s.group(),depth+1);});
    }
    private static void entity(MessageDigest digest,CslEntity entity){put(digest,entity.name());optional(digest,entity.specializes());attributes(digest,entity.attributes());}
    private static void role(MessageDigest digest,CslRole role){put(digest,role.name());strings(digest,role.parentRoles());attributes(digest,role.attributes());}
    private static void attributes(MessageDigest digest,List<CslAttribute> attributes) {
        size(digest,attributes);attributes.forEach(a->{put(digest,a.name(),a.type().getClass().getName(),a.type().sourceName(),Boolean.toString(a.optional()),Boolean.toString(a.mutable()));optional(digest,a.defaultValue());});
    }
    private static void compatibilities(MessageDigest digest,List<CslCompatibility> compatibilities) {
        size(digest,compatibilities);compatibilities.forEach(c->put(digest,c.fromRole(),c.toRole(),c.type().name(),c.scope().name(),Boolean.toString(c.extendsSubgroups()),Boolean.toString(c.bidirectional()),c.groupName()));
    }
    private static void cardinality(MessageDigest digest,CslCardinality value){put(digest,Integer.toString(value.min()),value.max().isPresent()?Integer.toString(value.max().getAsInt()):"*");}
    private static void optional(MessageDigest digest,Optional<String> value){put(digest,Boolean.toString(value.isPresent()));value.ifPresent(v->put(digest,v));}
    private static void strings(MessageDigest digest,List<String> values){size(digest,values);values.forEach(v->put(digest,v));}
    private static void size(MessageDigest digest,Collection<?> values){put(digest,Integer.toString(values.size()));}
    private static void put(MessageDigest digest,String... fields) {
        for(var field:fields){byte[] bytes=field.getBytes(StandardCharsets.UTF_8);digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());digest.update(bytes);}
    }
}
