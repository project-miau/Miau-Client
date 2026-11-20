package baritone.api.process;

/** A process that pilots movement directly, including ticks with all movement keys released. */
public interface IInputControlProcess extends IBaritoneProcess {
    boolean isControllingInput();
}
