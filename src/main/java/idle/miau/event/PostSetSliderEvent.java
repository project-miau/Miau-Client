package idle.miau.event;

import idle.miau.mcp.event.Event;

public class PostSetSliderEvent extends Event {
    public double previousVal;
    public double newVal;

    public PostSetSliderEvent(double previousVal, double newVal) {
        this.previousVal = previousVal;
        this.newVal = newVal;
    }
}
