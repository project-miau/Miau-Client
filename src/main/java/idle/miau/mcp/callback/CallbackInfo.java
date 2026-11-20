package idle.miau.mcp.callback;

public class CallbackInfo implements Cancellable {
    private final String name;
    private final boolean cancellable;
    private boolean cancelled;

    public CallbackInfo(String name, boolean cancellable) {
        this.name = name;
        this.cancellable = cancellable;
    }

    public String getId() {
        return name;
    }

    @Override
    public String toString() {
        return String.format("CallbackInfo[TYPE=%s,NAME=%s,CANCELLABLE=%s]",
                getClass().getSimpleName(), name, cancellable);
    }

    @Override
    public final boolean isCancellable() {
        return cancellable;
    }

    @Override
    public final boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void cancel() throws CancellationException {
        if (!cancellable) {
            throw new CancellationException("The call " + name + " is not cancellable.");
        }
        cancelled = true;
    }
}
