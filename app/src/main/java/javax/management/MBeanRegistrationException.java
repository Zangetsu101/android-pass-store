package javax.management;

public class MBeanRegistrationException extends JMException {
    public MBeanRegistrationException(Exception e) { super(e.getMessage()); }
    public MBeanRegistrationException(Exception e, String message) { super(message); }
}
