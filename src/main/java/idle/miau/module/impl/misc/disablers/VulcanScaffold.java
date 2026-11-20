package idle.miau.module.impl.misc.disablers;

import idle.miau.utility.Utils;

public final class VulcanScaffold extends DisablerMode {
    public VulcanScaffold(DisablerModule owner) {
        super("VulcanScaffold", owner);
    }

    @Override
    public void onEnable() {
        if (Utils.nullCheck()) {
            Utils.sendMessage("VulcanScaffold is retired: its packet-only sneak pulses trigger ScaffoldF.");
        }
    }
}
