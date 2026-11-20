package baritone.compat;

public enum ClickType {
    PICKUP, QUICK_MOVE, SWAP, CLONE, THROW, QUICK_CRAFT, PICKUP_ALL;
    public int getId() { return ordinal(); }
}
