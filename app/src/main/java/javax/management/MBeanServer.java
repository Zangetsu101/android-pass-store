package javax.management;

public interface MBeanServer {
    ObjectInstance registerMBean(Object object, ObjectName name)
        throws InstanceAlreadyExistsException, MBeanRegistrationException, NotCompliantMBeanException;
    void unregisterMBean(ObjectName name)
        throws InstanceNotFoundException, MBeanRegistrationException;
    boolean isRegistered(ObjectName name);
    Object getAttribute(ObjectName name, String attribute)
        throws MBeanException, AttributeNotFoundException, InstanceNotFoundException, ReflectionException;
    void setAttribute(ObjectName name, Attribute attribute)
        throws InstanceNotFoundException, AttributeNotFoundException, InvalidAttributeValueException,
               MBeanException, ReflectionException;
    Object invoke(ObjectName name, String operationName, Object[] params, String[] signature)
        throws InstanceNotFoundException, MBeanException, ReflectionException;
}
