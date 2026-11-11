package org.vnu.sme.goal.dsl.istar.mm;

import java.util.*;

public final class GoalModel {

    private final String          name;
    private final List<Actor>     actors       = new ArrayList<>();
    private final List<Dependency> dependencies = new ArrayList<>();

    private final Map<String, Actor>             actorMap    = new LinkedHashMap<>();
    private final Map<String, IntentionalElement> elementMap  = new LinkedHashMap<>();
    private final Map<String, String>             ownerMap    = new HashMap<>();

    public GoalModel(String name) { this.name = name; }

    public void addActor(Actor a) {
        actors.add(a);
        actorMap.put(a.name(), a);
        for (IntentionalElement e : a.elements()) {
            elementMap.put(e.id(), e);
            ownerMap.put(e.id(), a.name());
        }
    }

    public void addDependency(Dependency d) { dependencies.add(d); }

    public String             getName()        { return name; }
    public List<Actor>        getActors()      { return Collections.unmodifiableList(actors); }
    public List<Dependency>   getDependencies(){ return Collections.unmodifiableList(dependencies); }

    public Optional<Actor> findActor(String name) {
        return Optional.ofNullable(actorMap.get(name));
    }

    public Optional<IntentionalElement> findElement(String id) {
        return Optional.ofNullable(elementMap.get(id));
    }

    public Optional<String> ownerOf(String elementId) {
        return Optional.ofNullable(ownerMap.get(elementId));
    }

    public Map<String, IntentionalElement> allElements() {
        return Collections.unmodifiableMap(elementMap);
    }
}
