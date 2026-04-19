package javax.management;

public class MBeanInfo {
    private final String className;
    private final String description;
    public MBeanInfo(String className, String description,
                     Object[] attributes, Object[] constructors,
                     Object[] operations, Object[] notifications) {
        this.className = className;
        this.description = description;
    }
    public String getClassName() { return className; }
    public String getDescription() { return description; }
    public Object[] getAttributes() { return new Object[0]; }
    public Object[] getConstructors() { return new Object[0]; }
    public Object[] getOperations() { return new Object[0]; }
    public Object[] getNotifications() { return new Object[0]; }
}
