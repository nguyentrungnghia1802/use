package org.vnu.sme.goal.dsl.istar.mm;

/** resource --o--> task  (circle arrowhead: resource needed by that specific task) */
public final class NeededBy {

    private String resource;
    private String task;

    public NeededBy(String resource, String task) {
        this.resource = resource;
        this.task     = task;
    }

    public String resource() { return resource; }
    public String task()     { return task; }

    public void setResource(String resource) { this.resource = resource; }
    public void setTask(String task)         { this.task = task; }
}
