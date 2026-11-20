package idle.miau.mcp.event;

public class Event {
    private boolean canceled;

    public boolean isCancelable() {
        return getClass().isAnnotationPresent(Cancelable.class);
    }

    public boolean isCanceled() {
        return canceled;
    }

    public void setCanceled(boolean canceled) {
        if (canceled && !isCancelable()) {
            throw new IllegalArgumentException("Attempted to cancel a non-cancelable event: " + getClass().getName());
        }
        this.canceled = canceled;
    }
}
