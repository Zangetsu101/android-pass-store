package javax.management;

public class ReflectionException extends JMException {
    private final Exception cause;
    public ReflectionException(Exception e) { super(e.getMessage()); this.cause = e; }
    public ReflectionException(Exception e, String message) { super(message); this.cause = e; }
    public Exception getTargetException() { return cause; }
}
