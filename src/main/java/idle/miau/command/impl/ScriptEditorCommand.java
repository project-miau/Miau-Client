package idle.miau.command.impl;

import idle.miau.command.Command;
import idle.miau.command.CommandCategory;
import idle.miau.command.CommandInput;
import idle.miau.gui.GuiScriptEditor;
import net.minecraft.client.Minecraft;

import java.util.Collections;
import java.util.List;

public class ScriptEditorCommand extends Command {
    public ScriptEditorCommand() {
        super("scripteditor", "Open the in-game script editor.", CommandCategory.UTILITY,
                new String[]{"scripteditor", "se"}, "se");
    }

    @Override
    public void execute(CommandInput input) {
        String target = input.getArguments().length >= 1 ? input.getArgument(0) : null;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.isCallingFromMinecraftThread()) {
            mc.displayGuiScreen(new GuiScriptEditor(target));
        } else {
            mc.addScheduledTask(() -> mc.displayGuiScreen(new GuiScriptEditor(target)));
        }
    }

    @Override
    public List<String> suggest(CommandInput input) {
        return Collections.emptyList();
    }
}
