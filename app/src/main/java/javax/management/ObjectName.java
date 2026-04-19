package javax.management;

public class ObjectName {
    private final String name;
    public ObjectName(String name) throws MalformedObjectNameException { this.name = name; }
    public String toString() { return name; }
    public String getCanonicalName() { return name; }
    public String getDomain() { return ""; }
    public boolean isPattern() { return false; }
}
