package gg.vape.module.render.hud;

import gg.vape.module.render.hud.HudModule;
import gg.vape.module.render.hud.HudModuleGroup;
import gg.vape.ui.click.frame.impl.hud.AccountHudFrame;

public class AccountHudModule
extends HudModule {
    public AccountHudModule() {
        super("Account", HudModuleGroup.HUD, "account", AccountHudFrame.class);
        this.setSuffix("Shows your nick and skin face");
    }
}
