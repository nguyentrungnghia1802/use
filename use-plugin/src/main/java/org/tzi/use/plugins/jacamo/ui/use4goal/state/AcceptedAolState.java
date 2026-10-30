package org.tzi.use.plugins.jacamo.ui.use4goal.state;
import java.util.*;
/** Read-only diagram DTO. Never an evaluator or a second MSystem. Values remain USE-rendered text. */
public record AcceptedAolState(Map<String,ObjectValue> objects,List<AssociationLink> associationLinks,List<PlayLink> playLinks) {
 public enum Kind {ENTITY,ROLE,GROUP,AGENT}
 public record ObjectValue(String id,String type,Kind kind,Map<String,Object> attributes,String displayName){
  public ObjectValue(String id,String type,Kind kind,Map<String,Object> attributes){this(id,type,kind,attributes,id+": "+type);}
  public ObjectValue{attributes=Map.copyOf(attributes);}
 }
 public record AssociationLink(String relationName,String sourceId,String targetId){}
 public record PlayLink(String parentRoleId,String childRoleId){}
 public AcceptedAolState{objects=Map.copyOf(objects);associationLinks=List.copyOf(associationLinks);playLinks=List.copyOf(playLinks);}
}
