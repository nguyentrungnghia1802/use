package org.tzi.use.plugins.jacamo.ui.use4goal.state;
import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.plugins.jacamo.ui.use4goal.aol.view.AolDiagram;
import org.tzi.use.plugins.jacamo.ui.use4goal.istar.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.Cut;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ExecutionScopeViews;
import org.tzi.use.plugins.jacamo.semantics.runtime.OccurrenceGoalModelExpander;
import org.tzi.use.semantic.engine.api.EngineSemanticsProfile;
import org.vnu.sme.goal.dsl.csl.mm.CslModel;
import org.vnu.sme.goal.dsl.istar.mm.GoalModel;
/** Adapts one selected immutable verification cut to original AOL/iStar renderers.
 * No native reads, evaluation, inferred success or modifications of semantic history. */
public final class RuntimeStateViews {
 public final JPanel aolPanel=new JPanel(new BorderLayout()),iscnPanel=new JPanel(new BorderLayout());
 private final AolDiagram aol=new AolDiagram(new java.io.PrintWriter(java.io.Writer.nullWriter()));
 private final IStarView iscn=new IStarView();
 private final JPanel iscnContent=new JPanel(new BorderLayout());
 private final Map<String,IStarView> instanceViews=new HashMap<>();
 private record Expanded(String signature,OccurrenceGoalModelExpander.Expansion expansion){}
 private final Map<String,Expanded> expansions=new HashMap<>();
 private final JComboBox<String> contextSelector=new JComboBox<>(),aolSelector=new JComboBox<>();
 private final JLabel aolStatus=new JLabel("Select a verified state"),iscnStatus=new JLabel("Select a verified state");
 private final CslModel csl;private final GoalModel goals;private final Map<String,OccurrenceGoalModelExpander.Quantifier> policies;
 private java.util.function.Consumer<String> scopeListener=id->{};
 public void onScopeSelected(java.util.function.Consumer<String> listener){scopeListener=Objects.requireNonNull(listener);}
 public void selectScope(String id){if(!id.isBlank()&&!id.equals(contextSelector.getSelectedItem())){loading=true;try{contextSelector.setSelectedItem(id);aolSelector.setSelectedItem(id);}finally{loading=false;}if(iscnActive)renderContext();}}
 private Cut selected;private List<Map<String,Object>> contexts=List.of();private boolean loading;
 private boolean aolActive=true,iscnActive=true;
 public void setActive(boolean showAol,boolean showIscn){
  if(aolActive==showAol&&iscnActive==showIscn)return;
  aolActive=showAol;iscnActive=showIscn;var pending=selected;selected=null;
  if(pending==null){aol.setState(null);iscn.setModel(null);contexts=List.of();return;}
  select(pending);
 }
 public RuntimeStateViews(CslModel csl,GoalModel goals,Map<String,OccurrenceGoalModelExpander.Quantifier> policies){
  this.csl=csl;this.goals=goals;this.policies=Map.copyOf(policies);
  aolPanel.setName("runtime-aol-view");iscnPanel.setName("runtime-iscn-view");contextSelector.setName("iscn-context-selector");aolSelector.setName("aol-context-selector");
  contextSelector.setRenderer(new DefaultListCellRenderer(){@Override public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focus){
   var label=(JLabel)super.getListCellRendererComponent(list,value,index,selected,focus);
   String id=Objects.toString(value,"");label.setText(ExecutionScopeViews.ALL.equals(id)?"All":contexts.stream().filter(c->id.equals(c.get("context"))).findFirst().map(c->ExecutionScopeViews.label(c,contexts.indexOf(c)+1)).orElse(id));
   label.setToolTipText(id);return label;}});
  aolSelector.setRenderer(contextSelector.getRenderer());
  var aolToolbar=new JPanel(new BorderLayout());aolToolbar.add(aolSelector,BorderLayout.NORTH);aolToolbar.add(aolStatus,BorderLayout.SOUTH);
  aolPanel.add(aolToolbar,BorderLayout.NORTH);aolPanel.add(new JScrollPane(aol),BorderLayout.CENTER);
  var toolbar=new JPanel(new BorderLayout());toolbar.add(contextSelector,BorderLayout.NORTH);toolbar.add(iscnStatus,BorderLayout.SOUTH);iscnPanel.add(toolbar,BorderLayout.NORTH);iscnContent.add(iscn,BorderLayout.CENTER);iscnPanel.add(iscnContent,BorderLayout.CENTER);
  contextSelector.addActionListener(e->{if(!loading){String id=Objects.toString(contextSelector.getSelectedItem(),"");
   loading=true;try{aolSelector.setSelectedItem(id);}finally{loading=false;}if(iscnActive)renderContext();scopeListener.accept(id);}});
  aolSelector.addActionListener(e->{if(!loading)contextSelector.setSelectedItem(aolSelector.getSelectedItem());});
 }
 public void select(Cut cut){
  if(selected==cut)return;selected=cut;if(!aolActive&&!iscnActive)return;contexts=List.of();String previous=cut==null?"":ExecutionScopeViews.selected(cut);if(previous.isBlank())previous=(String)contextSelector.getSelectedItem();loading=true;contextSelector.removeAllItems();aolSelector.removeAllItems();
  try{
   if(cut==null){aol.setState(null);iscn.setModel(null);iscnContent.removeAll();iscnContent.add(iscn,BorderLayout.CENTER);iscnContent.revalidate();iscnContent.repaint();aolStatus.setText("No selected CSL state");iscnStatus.setText("No selected iStar state");return;}
   var evidence=cut.visualEvidence();if(aolActive)aol.setState(aol(csl,evidence,cut.state()));
   aolStatus.setText("CSL state — native associations | "+cut.coverage()+" / "+cut.freshness()+(Boolean.TRUE.equals(object(evidence.get("presentationState")).get("qualifiedValues"))?"":" | State unavailable: projection pending or incomplete"));
   var core=object(evidence.get("originalEngine"));
   if(!"PINNED_NATIVE_CUT".equals(core.get("evaluated")))throw new IllegalArgumentException("Verification still pending; no qualified ISCN at this boundary");
   contexts=((List<?>)core.getOrDefault("contexts",List.of())).stream().map(CanonicalJson::object).toList();
   var ids=new HashSet<String>();contexts.forEach(c->ids.add(Objects.toString(c.get("context"))));instanceViews.keySet().retainAll(ids);expansions.keySet().retainAll(ids);
   for(var context:contexts){contextSelector.addItem(context.get("context").toString());aolSelector.addItem(context.get("context").toString());}
   contextSelector.addItem(ExecutionScopeViews.ALL);aolSelector.addItem(ExecutionScopeViews.ALL);
   // An explicitly selected execution that is absent at this cut must stay
   // unavailable. Never display the first other execution as its substitute.
   if(!ExecutionScopeViews.selected(cut).isBlank())contextSelector.setSelectedIndex(-1);
   if(previous!=null)for(int i=0;i<contextSelector.getItemCount();i++)if(previous.equals(contextSelector.getItemAt(i)))contextSelector.setSelectedIndex(i);
   aolSelector.setSelectedIndex(contextSelector.getSelectedIndex());
  }catch(RuntimeException unavailable){iscn.setModel(null);iscnStatus.setText("ISCN unavailable: "+unavailable.getMessage());}
  finally{loading=false;}
  if(iscnActive)renderContext();
 }
 static Map<String,Object> object(Object raw){return raw instanceof Map<?,?>?CanonicalJson.object(raw):Map.of();}
 public static AcceptedAolState aol(CslModel csl,Map<String,Object> evidence,String stateJson){
  var projection=object(evidence.get("presentationState"));
  if(!Boolean.TRUE.equals(projection.get("qualifiedValues")))return new AcceptedAolState(Map.of(),List.of(),List.of());
  var objects=new TreeMap<String,AcceptedAolState.ObjectValue>();
  var ordinals=new TreeMap<String,Integer>();
  for(var entry:new TreeMap<>(object(projection.get("objects"))).entrySet()){
   int colon=entry.getKey().indexOf(':');if(colon<1)continue;String type=entry.getKey().substring(0,colon),id=entry.getKey().substring(colon+1);
   var kind=csl.findEntity(type).isPresent()?AcceptedAolState.Kind.ENTITY:csl.findRole(type).isPresent()?AcceptedAolState.Kind.ROLE:AcceptedAolState.Kind.GROUP;
   objects.put(id,new AcceptedAolState.ObjectValue(id,type,kind,object(entry.getValue()),type+" #"+ordinals.merge(type,1,Integer::sum)));
  }
  var links=new ArrayList<AcceptedAolState.AssociationLink>();
  // Retained native image only. Never reconstruct a live state by replaying SOIL on the EDT.
  if(projection.containsKey("links")||stateJson.stripLeading().startsWith("{")){
   var image=projection.containsKey("links")?projection:object(CanonicalJson.decode(stateJson.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
   for(Object raw:(List<?>)image.getOrDefault("links",List.of())){
    var link=object(raw);var ids=(List<?>)link.getOrDefault("participants",List.of());
    if(ids.size()==2&&objects.containsKey(ids.get(0))&&objects.containsKey(ids.get(1))){
     String relation=Objects.toString(link.get("association"));
     if(relation.equals("participates (execution)"))continue;
     links.add(new AcceptedAolState.AssociationLink(relation,ids.get(0).toString(),ids.get(1).toString()));
     // A recorded join witness supplies a human label only. Graph identity remains object ID.
     if(relation.equals("enacts")&&link.get("via") instanceof String player&&!player.isBlank())for(Object endpoint:ids){
      var value=objects.get(endpoint);objects.put(value.id(),new AcceptedAolState.ObjectValue(value.id(),value.type(),value.kind(),value.attributes(),player+": "+value.type()));
     }
    }
   }
  }
  return new AcceptedAolState(objects,links,List.of());
 }
 private void renderContext(){
  String id=Objects.toString(contextSelector.getSelectedItem(),"");
  if(ExecutionScopeViews.ALL.equals(id)){
   var all=new JPanel();all.setLayout(new BoxLayout(all,BoxLayout.Y_AXIS));
   for(int i=0;i<contexts.size();i++){var view=instanceViews.computeIfAbsent(Objects.toString(contexts.get(i).get("context")),key->new IStarView());var section=new JPanel(new BorderLayout());section.setBorder(BorderFactory.createTitledBorder(ExecutionScopeViews.label(contexts.get(i),i+1)));section.setPreferredSize(new Dimension(1000,650));section.add(view,BorderLayout.CENTER);renderOne(contexts.get(i),i,view);all.add(section);}
   iscnContent.removeAll();iscnContent.add(new JScrollPane(all),BorderLayout.CENTER);iscnStatus.setText("All executions — separate admitted instance graphs");iscnContent.revalidate();iscnContent.repaint();return;
  }
  iscnContent.removeAll();iscnContent.add(iscn,BorderLayout.CENTER);iscnContent.revalidate();iscnContent.repaint();
  int index=-1;for(int i=0;i<contexts.size();i++)if(id.equals(contexts.get(i).get("context")))index=i;
  if(index<0||index>=contexts.size()){iscn.setModel(null);iscnStatus.setText("No admitted actor instances at this state");return;}
  renderOne(contexts.get(index),index,iscn);
 }
 private void renderOne(Map<String,Object> context,int index,IStarView target){
  if(target.getClientProperty("state-error") instanceof Component prior)target.remove(prior);
  target.putClientProperty("state-error",null);
  try{
   var core=object(selected.visualEvidence().get("originalEngine"));
   String id=Objects.toString(context.get("context"));
   String signature=org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.hash(List.of(context.getOrDefault("expansionTrace",Map.of()),core.get("engineProfile")));
   var cached=expansions.get(id);
   if(cached==null||!cached.signature().equals(signature)){cached=new Expanded(signature,expand(goals,context,EngineSemanticsProfile.valueOf(core.get("engineProfile").toString()),policies));expansions.put(id,cached);}
   var expanded=cached.expansion();
   target.setModel(expanded.model());var actorLabels=new TreeMap<String,String>();var nodeLabels=new TreeMap<String,String>();var badges=new TreeMap<String,NodeBadge>();
   for(Object raw:object(context.get("occurrences")).values()){
    var occurrence=object(raw);String recorded=Objects.toString(occurrence.get("executionElement"),"");
    var trace=object(object(context.get("expansionTrace")).get(recorded));
    String element=expanded.trace().entrySet().stream().filter(e->signature(trace).equals(signature(e.getValue()))).map(Map.Entry::getKey).findFirst().orElseThrow();nodeLabels.put(element,Objects.toString(occurrence.get("sourceElement"),element));
    var stack=(List<?>)occurrence.getOrDefault("stack",List.of());if(!stack.isEmpty()){var binding=object(stack.getFirst());actorLabels.put(Objects.toString(binding.get("actorType")),Objects.equals(occurrence.get("player"),context.get("anchor"))?ExecutionScopeViews.actorLabel(context,index+1):playerLabel(Objects.toString(occurrence.get("player"))));}
    String status=qualifiedStatus(context,occurrence);Color color=switch(status){case "FULFILLED"->new Color(30,150,60);case "PENDING"->new Color(180,130,0);case "FAILED","VIOLATED"->new Color(170,30,30);default->Color.GRAY;};
    badges.put(element,new NodeBadge(color,status.equals("FULFILLED")?"F":status.equals("PENDING")?"P":status.equals("NOT_ASSIGNED")?"–":status.equals("UNKNOWN")?"?":"!",status+" | player="+occurrence.get("player")+" | role="+occurrence.get("role")));
   }
   target.setActorLabelOverrides(actorLabels);target.setNodeLabelOverrides(nodeLabels);target.setNodeBadges(badges);
   iscnStatus.setText("ISCN — actor instances in selected context | F=fulfilled, P=pending, –=not assigned, ?=unknown; "+selected.freshness());
  }catch(RuntimeException unavailable){target.setModel(null);var message=new JLabel("Instance state unavailable: "+unavailable.getMessage());target.add(message,BorderLayout.SOUTH);target.putClientProperty("state-error",message);iscnStatus.setText("ISCN unavailable: "+unavailable.getMessage());}
 }
 public static String qualifiedStatus(Map<String,Object> context,Map<String,Object> row){
  if("SOURCE_NOT_ASSIGNED".equals(row.get("executionQuality"))&&"ORIGINAL_PROPAGATION_ON_ADMITTED_LEAVES/1.0".equals(context.get("executionQualification")))return "NOT_ASSIGNED";
  String quality=Objects.toString(row.get("executionQuality"),"");boolean history=Set.of("KNOWN_HISTORY","KNOWN_DERIVED_HISTORY").contains(quality)&&!object(row.get("executionEvidence")).isEmpty();
  boolean current=Set.of("KNOWN","KNOWN_DERIVED").contains(quality)&&"ORIGINAL_PROPAGATION_ON_ADMITTED_LEAVES/1.0".equals(context.get("executionQualification"));
  if(!history&&!current)return "UNKNOWN";
  String executionStatus=Objects.toString(row.get("executionStatus"),"");
  if((history||current)&&Set.of("FAILED","VIOLATED").contains(executionStatus)&&(current||object(row.get("executionEvidence")).values().stream().map(RuntimeStateViews::object).anyMatch(p->Set.of("TASK_FAILED","GOAL_VIOLATED").contains(p.get("kind"))&&"CERTIFIED_TERMINAL_EXECUTION/1.0".equals(p.get("policy")))))return executionStatus;
  if((history||current)&&"FULFILLED".equals(executionStatus))return "FULFILLED";
  return Objects.toString(row.get("aggregateStatus"),"UNKNOWN");
 }
 public static OccurrenceGoalModelExpander.Expansion expand(GoalModel goals,Map<String,Object> context,EngineSemanticsProfile profile,Map<String,OccurrenceGoalModelExpander.Quantifier> policies){
  var players=new TreeMap<String,OccurrenceGoalModelExpander.Player>();
  for(Object raw:object(context.get("occurrences")).values()){
   var row=object(raw);if(!"SOURCE_ELEMENT".equals(row.get("expansionKind")))continue;
   var stack=(List<?>)row.getOrDefault("stack",List.of());if(stack.size()!=1)throw new IllegalArgumentException("Unsupported nested instance graph");var bind=object(stack.getFirst());String actor=bind.get("actorType").toString();
   players.putIfAbsent(actor,new OccurrenceGoalModelExpander.Player(row.get("sourceActor").toString(),actor,bind.get("instanceId").toString(),row.get("player").toString(),Objects.toString(row.get("role"),""),""));
  }
  var expanded=new OccurrenceGoalModelExpander(goals,profile,policies).expand(List.copyOf(players.values()),"SelectedScenario");
  var expected=object(context.get("expansionTrace"));
  var recorded=expected.values().stream().map(RuntimeStateViews::object).map(RuntimeStateViews::signature).sorted().toList();
  var rebuilt=expanded.trace().values().stream().map(RuntimeStateViews::signature).sorted().toList();
  if(!recorded.equals(rebuilt)||new HashSet<>(recorded).size()!=recorded.size())throw new IllegalArgumentException("Recorded instance topology mismatch");
  return expanded;
 }
 private static String playerLabel(String id){try{return org.jacamo.bridge.contract.BridgeEntityId.parse(id).localId();}catch(org.jacamo.bridge.contract.ContractException invalid){return id.length()>26?"Context":id;}}
 private static String signature(Map<String,Object> trace){return org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.hash(List.of(trace.get("source"),trace.get("actor"),trace.get("player"),trace.get("role"),trace.get("kind")));}
 private static String signature(OccurrenceGoalModelExpander.Trace trace){return org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.hash(List.of(trace.source(),trace.actor(),trace.player(),trace.role(),trace.kind()));}
 public void dispose(){selected=null;contexts=List.of();instanceViews.clear();expansions.clear();iscnContent.removeAll();aol.setState(null);iscn.setModel(null);}
}
