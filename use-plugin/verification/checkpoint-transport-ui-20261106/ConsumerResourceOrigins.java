public class ConsumerResourceOrigins {
    public static void main(String[] args)throws Exception {
        var loader=ClassLoader.getSystemClassLoader();
        for(String name:java.util.List.of("org/antlr/v4/runtime/RuntimeMetaData.class","org/tzi/use/semantic/engine/api/EngineInstallationGuard.class")) {
            System.out.println(name);var values=loader.getResources(name);while(values.hasMoreElements())System.out.println(values.nextElement());
        }
    }
}
