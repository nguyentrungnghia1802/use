package org.tzi.use.plugins.jacamo.semantics;

/** Real GroupBoard; only its organization label is fixture configuration. adoptRole/leaveRole are inherited operations. */
public class CslGroupBoard extends ora4mas.nopl.GroupBoard {
    public static volatile CslGroupBoard instance;
    @Override public void init(String path,String group) throws npl.parser.ParseException,moise.common.MoiseException,cartago.OperationException {
        var config=jason.util.Config.get();synchronized(config){String previous=config.getProperty(jason.util.Config.START_WEB_OI);config.setProperty(jason.util.Config.START_WEB_OI,"false");try{super.init(path,group);}finally{if(previous==null)config.remove(jason.util.Config.START_WEB_OI);else config.setProperty(jason.util.Config.START_WEB_OI,previous);}}oeId="org";instance=this;
    }
}
