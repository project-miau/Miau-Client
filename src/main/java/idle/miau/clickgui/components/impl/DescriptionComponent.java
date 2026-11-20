package idle.miau.clickgui.components.impl;

import idle.miau.clickgui.components.Component;
import idle.miau.module.setting.impl.DescriptionSetting;
import idle.miau.utility.Theme;
import idle.miau.utility.font.MiauFontRenderer;
import org.lwjgl.opengl.GL11;

public class DescriptionComponent extends Component {
    public DescriptionSetting desc;
    private ModuleComponent p;
    public float o;
    public float x;
    public float y;

    public DescriptionComponent(DescriptionSetting desc, ModuleComponent b, float o) {
        this.desc = desc;
        this.p = b;
        this.x = b.categoryComponent.getX() + b.categoryComponent.getWidth();
        this.y = b.categoryComponent.getY() + b.yPos;
        this.o = o;
    }

    public void render() {
        MiauFontRenderer renderer = idle.miau.module.impl.client.ClickGui.getClickGuiSettingFontRenderer();
        GL11.glPushMatrix();
        GL11.glScaled(0.5D, 0.5D, 0.5D);
        renderer.drawString(this.desc.getDesc(), (float) ((this.p.categoryComponent.getX() + 4) * 2), (float) ((this.p.categoryComponent.getY() + this.o + 4) * 2), Theme.getGradient(Theme.descriptor[0], Theme.descriptor[1], 0), true);
        GL11.glPopMatrix();
    }

    public void updateHeight(float n) {
        this.o = n;
    }

    @Override
    public float getOffset() {
        return this.o;
    }

    @Override
    public boolean isBaseVisible() {
        return this.desc.visible;
    }
}
