package javax.management;

import java.util.ArrayList;

public class AttributeList extends ArrayList<Object> {
    public AttributeList() { super(); }
    public AttributeList(int initialCapacity) { super(initialCapacity); }
    public void add(Attribute attr) { super.add(attr); }
}
