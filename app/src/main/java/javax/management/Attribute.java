package javax.management;

public class Attribute {
    private final String name;
    private final Object value;
    public Attribute(String name, Object value) { this.name = name; this.value = value; }
    public String getName() { return name; }
    public Object getValue() { return value; }
}
