package idle.miau.command.impl;

import idle.miau.command.Command;
import idle.miau.command.CommandInput;
import idle.miau.Miau;
import idle.miau.module.ModuleManager;
import idle.miau.utility.Utils;

public class Script extends Command {
    public Script() {
        super("script", new String[0]);
    }

    @Override
    public void execute(CommandInput input) {
        String[] args = input.getArguments();
        if (args.length >= 1 && args[0].equalsIgnoreCase("edit")) {
            String target = args.length >= 2 ? args[1] : null;
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
            if (mc.isCallingFromMinecraftThread()) {
                mc.displayGuiScreen(new idle.miau.gui.GuiScriptEditor(target));
            } else {
                mc.addScheduledTask(() -> mc.displayGuiScreen(new idle.miau.gui.GuiScriptEditor(target)));
            }
            return;
        }
        if (args.length != 1 || (!args[0].equalsIgnoreCase("load") && !args[0].equalsIgnoreCase("refresh"))) {
            Utils.sendMessage("&cUsage: .script <load|refresh|edit [name]>");
            return;
        }
        long currentTimeMillis = System.currentTimeMillis();
        if (ModuleManager.scriptManager != null) {
            ModuleManager.scriptManager.loadScripts(currentTimeMillis);
        }
        else if (Miau.scriptManager != null) {
            Miau.scriptManager.loadScripts();
        }
    }
}
