package gg.vape.ui.click.component;

import gg.vape.ui.click.GuiMouseEvent;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.ui.font.SmoothFontRenderer;
import gg.vape.utils.SkinRenderUtil;
import gg.vape.wrapper.impl.ResourceLocation;
import java.awt.Color;

public class SessionAccountFaceComponent
extends GuiComponent {
    private static final double FACE_SIZE = 16.0;

    @Override
    public void H() {
        String nick = SkinRenderUtil.getLocalUsername();
        if (nick == null) {
            nick = "Offline";
        }
        ResourceLocation skin = SkinRenderUtil.getLocalSkinLocation();
        double faceX = this.G$src$D$1b2f02a() + 5.0;
        double faceY = this.n() + 1.0;
        SkinRenderUtil.drawPlayerFace(skin, faceX, faceY, FACE_SIZE);
        SmoothFontRenderer fontRenderer = this.getFontRenderer(0.8);
        double textY = this.n() + this.L() / 2.0 - fontRenderer.d("A") / 2.0;
        fontRenderer.d(nick, faceX + FACE_SIZE + 5.0, textY, new Color(255, 255, 255));
    }

    @Override
    public double C() {
        return FACE_SIZE + 4.0;
    }

    @Override
    public double x() {
        return 140.0;
    }

    @Override
    public void g(GuiMouseEvent mouseEvent) {
    }

    @Override
    public void F() {
    }

    @Override
    public void u() {
    }

    @Override
    public void I() {
    }
}
