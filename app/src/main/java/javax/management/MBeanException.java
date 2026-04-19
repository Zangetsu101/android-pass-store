package javax.management;

public class MBeanException extends JMException {
    private final Exception cause;
    public MBeanException(Exception e) { super(e.getMessage()); this.cause = e; }
    public MBeanException(Exception e, String message) { super(message); this.cause = e; }
    public Exception getTargetException() { return cause; }
}
