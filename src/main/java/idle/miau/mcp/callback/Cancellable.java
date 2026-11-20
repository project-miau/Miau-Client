package idle.miau.mcp.callback;

public interface Cancellable {
    boolean isCancellable();

    boolean isCancelled();

    void cancel() throws CancellationException;
}
