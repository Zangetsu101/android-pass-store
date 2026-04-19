package javax.management;

import java.util.ArrayList;

public class MBeanServerFactory {
    private MBeanServerFactory() {}

    public static ArrayList<MBeanServer> findMBeanServer(String agentId) {
        return new ArrayList<>();
    }

    public static MBeanServer createMBeanServer() { return null; }
    public static MBeanServer createMBeanServer(String domain) { return null; }
    public static MBeanServer newMBeanServer() { return null; }
    public static MBeanServer newMBeanServer(String domain) { return null; }
}
