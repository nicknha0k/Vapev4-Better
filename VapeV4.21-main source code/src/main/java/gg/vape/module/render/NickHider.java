package gg.vape.module.render;

import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventPlayerTabOverlayDisplayName;
import gg.vape.event.impl.EventPlayerTabOverlayDisplayNameLegacy;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.value.StringValue;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.ITextComponent;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.PlayerInfo;
import java.util.UUID;

/**
 * NickHider solto (estilo CrewX NickHider.protectName): troca SEU nick por um
 * falso na tab. O nome falso e editavel em "Fake name". Funciona junto com o
 * NickTag (tag + nome falso).
 */
public class NickHider
extends Mod {
    private static final long MODULE_ID = 6102837409128734651L;

    public final StringValue fakeName;

    public NickHider() {
        super("NickHider", (int) MODULE_ID, Category.RENDER, "Troca seu nick por um falso na tab");
        this.fakeName = StringValue.create(this, "Fake name", "You");
        this.addValue(this.fakeName);
    }

    private String getFakeName() {
        try {
            Object value = this.fakeName.getValue();
            String fake = value != null ? value.toString() : "";
            return fake != null && !fake.isEmpty() ? fake : "You";
        } catch (Throwable ignored) {
            return "You";
        }
    }

    private boolean isLocalEntry(PlayerInfo info) {
        try {
            if (info == null || info.isNull()) {
                return false;
            }
            EntityPlayerSP me = Minecraft.thePlayer();
            if (me == null || me.isNull()) {
                return false;
            }
            UUID entryId = null;
            UUID myId = null;
            try {
                entryId = info.v().getUUID();
            } catch (Throwable ignored) {
            }
            try {
                myId = me.c$src$Lgg_vape_wrapper_impl_GameProfile_$ir8937().getUUID();
            } catch (Throwable ignored) {
            }
            return entryId != null && entryId.equals(myId);
        } catch (Throwable ignored) {
            return false;
        }
    }

    @EventHandler
    public void onTabName(EventPlayerTabOverlayDisplayName event) {
        if (!this.isEnabled()) {
            return;
        }
        try {
            if (!this.isLocalEntry(event.getNetworkPlayerInfo())) {
                return;
            }
            // Troca total (igual AntiScreenShare): vale mesmo que o texto da
            // tab nao contenha seu nick (cores de time, "You" do client, etc).
            event.setDisplayName(ITextComponent.a(this.getFakeName()));
        } catch (Throwable ignored) {
        }
    }

    @EventHandler
    public void onTabNameLegacy(EventPlayerTabOverlayDisplayNameLegacy event) {
        if (!this.isEnabled()) {
            return;
        }
        try {
            if (!this.isLocalEntry(event.getNetworkPlayerInfo())) {
                return;
            }
            event.setDisplayName(this.getFakeName());
        } catch (Throwable ignored) {
        }
    }

    @Override
    public String getDetailedSuffix() {
        return this.getFakeName();
    }
}
