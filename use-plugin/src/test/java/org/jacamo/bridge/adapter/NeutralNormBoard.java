package org.jacamo.bridge.adapter;
/** Native NPL fixture; domain program is supplied by the test, not by production mapping code. */
public class NeutralNormBoard extends ora4mas.nopl.NormativeBoard {
    public static volatile NeutralNormBoard instance;
    @Override public void init() {
        var config=jason.util.Config.get();synchronized(config) {
            String old=config.getProperty(jason.util.Config.START_WEB_OI);config.setProperty(jason.util.Config.START_WEB_OI,"false");
            try{super.init();}finally{if(old==null)config.remove(jason.util.Config.START_WEB_OI);else config.setProperty(jason.util.Config.START_WEB_OI,old);}
        }
        instance=this;getNormativeEngine().setUpdateInterval(10);
    }
}
