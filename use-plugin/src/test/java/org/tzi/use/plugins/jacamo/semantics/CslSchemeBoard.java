package org.tzi.use.plugins.jacamo.semantics;

/** Fixture organization label only; all operations and NPL semantics are inherited. */
public class CslSchemeBoard extends ora4mas.nopl.SchemeBoard {
    public static final java.util.Map<String,CslSchemeBoard> instances=new java.util.concurrent.ConcurrentHashMap<>();
    @Override public void init(String path,String type)throws npl.parser.ParseException,moise.common.MoiseException {
        init(path,type,null);
    }
    public void init(String path,String type,String organizationBoard)throws npl.parser.ParseException,moise.common.MoiseException {
        var config=jason.util.Config.get();synchronized(config){String previous=config.getProperty(jason.util.Config.START_WEB_OI);config.setProperty(jason.util.Config.START_WEB_OI,"false");try{super.init(path,type);}finally{if(previous==null)config.remove(jason.util.Config.START_WEB_OI);else config.setProperty(jason.util.Config.START_WEB_OI,previous);}}oeId="org";orgBoardName=organizationBoard;instances.put(getArtId(),this);
    }
}
