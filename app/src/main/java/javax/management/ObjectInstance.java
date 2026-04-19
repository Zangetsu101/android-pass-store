package javax.management;

public class ObjectInstance {
    private final ObjectName objectName;
    private final String className;
    public ObjectInstance(ObjectName objectName, String className) {
        this.objectName = objectName;
        this.className = className;
    }
    public ObjectName getObjectName() { return objectName; }
    public String getClassName() { return className; }
}
