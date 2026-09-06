package gg.vape.ui.click.frame.impl.hud;

import gg.vape.Vape;
import gg.vape.module.render.hud.AccountHudModule;
import gg.vape.ui.click.frame.impl.hud.HudModuleConfigFrameBase;
import gg.vape.ui.font.SmoothFontRenderer;
import gg.vape.utils.SkinRenderUtil;
import gg.vape.wrapper.impl.ResourceLocation;
import java.awt.Color;

public class AccountHudFrame
extends HudModuleConfigFrameBase {
    private static final int SHADOW_COLOR_ARGB = 0x80000000;

    @Override
    public double A() {
        return 110.0;
    }

    @Override
    public void renderHudContent() {
        String nick = SkinRenderUtil.getLocalUsername();
        if (nick == null) {
            nick = "Offline";
        }
        ResourceLocation skin = SkinRenderUtil.getLocalSkinLocation();
        double faceSize = 16.0;
        double faceX = this.G$src$D$1b2f02a() + 3.0;
        double faceY = this.n() + this.L() / 2.0 - faceSize / 2.0;
        SkinRenderUtil.drawPlayerFace(skin, faceX, faceY, faceSize);
        SmoothFontRenderer smoothFontRenderer = Vape.INSTANCE.getFontManager().W(1.2, false);
        double textY = (int)(this.n() + this.L() / 2.0 - smoothFontRenderer.d(nick) / 2.0);
        if (this.shouldRenderHudBackground()) {
            smoothFontRenderer.d(nick, (float)(faceX + faceSize + 4.0), (float)textY, this.getEditorForegroundColor());
        } else {
            smoothFontRenderer.T(nick, (float)(faceX + faceSize + 4.0), (float)textY,
                    this.getEditorForegroundColor(),
                    this.applyDefaultEditorAlpha(new Color(SHADOW_COLOR_ARGB, true)));
        }
    }

    public AccountHudFrame() {
        super(AccountHudModule.class);
    }

    @Override
    public String getName() {
        return "AccountFrame";
    }

    @Override
    public double L() {
        return 22.0;
    }
}
