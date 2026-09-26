package org.jacamo.bridge.adapter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import jason.mas2j.AgentParameters;
import jacamo.project.JaCaMoAgentParameters;
import jacamo.project.JaCaMoOrgParameters;
import jacamo.project.JaCaMoProject;
import org.jacamo.bridge.contract.BridgeEntityId;
import org.jacamo.bridge.contract.CapabilityStatus;
import org.jacamo.bridge.contract.CanonicalJson;
import org.jacamo.bridge.contract.ContractPayloads;
import org.jacamo.bridge.contract.ModelFact;
import org.jacamo.bridge.contract.ModelSnapshot;
import org.jacamo.bridge.contract.UnresolvedFact;

/** Copies the merged official JaCaMoProject into immutable neutral facts. */
public final class OfficialProjectAdapter {
    private final OfficialJasonAdapter jason = new OfficialJasonAdapter();
    private final OfficialMoiseAdapter moise = new OfficialMoiseAdapter();

    public ModelSnapshot adapt(JaCaMoProject project, Path jcm) throws Exception {
        Path root=jcm.toAbsolutePath().normalize().getParent(); String projectKey=project.getSocName();
        var sourceEvidence=AdapterEvidence.file("jacamo-project-parser",root,jcm,"JaCaMoProjectParser merged project");
        var sources=new ArrayList<ModelFact>(); sources.add(new ModelFact("jcm-source",id("jacamo","project","source",projectKey,jcm.getFileName().toString()),Map.of("uri",sourceEvidence.sourceUri(),"digest",sourceEvidence.sourceDigest(),"kind","JCM"),Map.of(),CapabilityStatus.COMPLETE,List.of(sourceEvidence)));
        var agents=new ArrayList<ModelFact>(); var workspaces=new ArrayList<ModelFact>(); var artifacts=new ArrayList<ModelFact>(); var organisations=new ArrayList<ModelFact>(); var cross=new ArrayList<ModelFact>(); var unresolved=new ArrayList<UnresolvedFact>();
        for(AgentParameters base:project.getAgents().stream().sorted(Comparator.comparing(AgentParameters::getAgName)).toList()){
            JaCaMoAgentParameters agent=(JaCaMoAgentParameters)base; var agentId=id("jacamo","agent","declaration",projectKey,agent.getAgName());
            agents.add(new ModelFact("agent-declaration",agentId,Map.of("instances",Integer.toString(agent.getNbInstances()),"source",String.valueOf(agent.getSource())),Map.of(),CapabilityStatus.COMPLETE,List.of(sourceEvidence)));
            String sourceReference=agent.getSource().isAbsolute()?agent.getSource().toString():agent.getSource().getSchemeSpecificPart();
            if(agent.getSource().getScheme()!=null&&agent.getSource().getScheme().equals("file")&&agent.getSource().isOpaque())sourceReference=agent.getSource().getSchemeSpecificPart();
            String officialPath=project.getSourcePaths().fixPath(sourceReference);
            java.net.URI officialUri=officialPath.startsWith("file:")?java.net.URI.create(officialPath):null;
            Path source=officialUri==null?Path.of(officialPath):officialUri.isOpaque()?Path.of(officialUri.getSchemeSpecificPart()):Path.of(officialUri);
            if(!source.isAbsolute())source=root.resolve(source).normalize();
            if(java.nio.file.Files.exists(source))agents.addAll(jason.parse(root,source,projectKey,agent.getAgName()));
            else unresolved.add(new UnresolvedFact("agent-source",agent.getAgName(),CapabilityStatus.UNAVAILABLE,"official source path is unavailable: "+officialPath,List.of(sourceEvidence)));
            int ordinal=0; for(String[] focus:agent.getFocus()){var relation=id("jacamo","cross","focus",projectKey,agent.getAgName()+":"+ordinal++);cross.add(new ModelFact("focus",relation,Map.of("namespace",String.valueOf(focus[2])),Map.of("agent",List.of(agentId),"workspace",List.of(id("jacamo","environment","workspace-declaration",projectKey,String.valueOf(focus[1]))),"artifact",List.of(id("jacamo","environment","artifact-declaration",projectKey,String.valueOf(focus[1])+"/"+focus[0]))),CapabilityStatus.COMPLETE,List.of(sourceEvidence)));}
            ordinal=0; for(String[] role:agent.getRoles()){var relation=id("jacamo","cross","player-role",projectKey,agent.getAgName()+":"+ordinal++);if(role[0]==null)unresolved.add(new UnresolvedFact("player-role",relation.canonical(),CapabilityStatus.PARTIAL,"organisation was not resolved by official parser",List.of(sourceEvidence)));else cross.add(new ModelFact("player-role",relation,Map.of(),Map.of("agent",List.of(agentId),"group",List.of(id("moise","organisation","group",projectKey,role[1])),"role",List.of(id("moise","organisation","role",projectKey,role[2]))),CapabilityStatus.COMPLETE,List.of(sourceEvidence)));}
        }
        String rootWorkspace=cartago.CartagoEnvironment.ROOT_WSP_DEFAULT_NAME;
        var rootWorkspaceId=id("cartago","environment","workspace-declaration",projectKey,rootWorkspace);
        workspaces.add(new ModelFact("workspace-declaration",rootWorkspaceId,
                Map.of("implicitPlatformRoot","true"),Map.of(),CapabilityStatus.COMPLETE,List.of(sourceEvidence)));
        project.getWorkspaces().stream().sorted(Comparator.comparing(w->w.getName())).forEach(workspace->{
            var workspaceId=workspace.getName().equals(rootWorkspace)?rootWorkspaceId:id("jacamo","environment","workspace-declaration",projectKey,workspace.getName());
            if(!workspace.getName().equals(rootWorkspace))workspaces.add(new ModelFact("workspace-declaration",workspaceId,Map.of(),Map.of(),CapabilityStatus.COMPLETE,List.of(sourceEvidence)));
            workspace.getArtifacts().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry->artifacts.add(new ModelFact("artifact-declaration",id("jacamo","environment","artifact-declaration",projectKey,workspace.getName()+"/"+entry.getKey()),Map.of("configuredClass",entry.getValue().getClassName()),Map.of("workspace",List.of(workspaceId)),CapabilityStatus.COMPLETE,List.of(sourceEvidence))));
        });
        var roleCards=new ArrayList<org.jacamo.bridge.contract.RelationCardinality>(); var subgroupCards=new ArrayList<org.jacamo.bridge.contract.RelationCardinality>();
        for(JaCaMoOrgParameters org:project.getOrgs().stream().sorted(Comparator.comparing(JaCaMoOrgParameters::getName)).toList()){
            String source=org.getParameter("source"); if(source==null){unresolved.add(new UnresolvedFact("organisation-source",org.getName(),CapabilityStatus.UNAVAILABLE,"official project has no OS source",List.of(sourceEvidence)));continue;}
            Path path=root.resolve(source).normalize(); if(!java.nio.file.Files.exists(path))path=root.resolve("src/org").resolve(source).normalize();
            if(!java.nio.file.Files.exists(path)){unresolved.add(new UnresolvedFact("organisation-source",org.getName(),CapabilityStatus.UNAVAILABLE,"OS source does not exist: "+source,List.of(sourceEvidence)));continue;}
            var result=moise.load(root,path,projectKey); organisations.addAll(result.facts()); roleCards.addAll(result.groupRoleCardinalities()); subgroupCards.addAll(result.parentSubGroupCardinalities());
        }
        Comparator<ModelFact> order=Comparator.comparing(f->f.id().canonical()); for(var list:List.of(sources,agents,workspaces,artifacts,organisations,cross))list.sort(order);
        var provenance=Map.of("authority","official-jacamo-objects","frozenV2","projection-downstream");
        var pending=new ModelSnapshot("pending",sources,agents,workspaces,artifacts,organisations,roleCards,subgroupCards,cross,unresolved,provenance);
        String revision=AdapterEvidence.digest(CanonicalJson.encode(ContractPayloads.model(pending)));
        return new ModelSnapshot(revision,sources,agents,workspaces,artifacts,organisations,roleCards,subgroupCards,cross,unresolved,provenance);
    }
    private BridgeEntityId id(String authority,String dimension,String kind,String scope,String local){return new BridgeEntityId(authority,dimension,kind,scope,local,"model");}
}
