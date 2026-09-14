package gg.vape.module.render;

import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventPlayerTabOverlayDisplayName;
import gg.vape.event.impl.EventPlayerTabOverlayDisplayNameLegacy;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.value.BooleanValue;
import gg.vape.value.StringValue;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.ITextComponent;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.PlayerInfo;
import java.util.UUID;

/**
 * Tag na tab estilo CrewX: prefixa o nick com um texto colorido
 * (ex.: &3[Vape] &r). Mostra sempre o nick real da tab; para trocar o nome,
 * use o modulo NickHider separado.
 */
public class NickTag
extends Mod {
    private static final long MODULE_ID = 7348291047561029384L;

    public final StringValue tag;
    public final BooleanValue selfOnly;
    private String lastTabDiag = "";
    private int tabDiagCount = 0;

    private void tabDiag(String text, boolean local, String src) {
        try {
            if (this.tabDiagCount >= 10) {
                return;
            }
            EntityPlayerSP me = Minecraft.thePlayer();
            Object rawName = me != null && !me.isNull() ? me.getName() : "?";
            String msg = src + " vanilla=[" + text + "] me=[" + rawName + "] local=" + local;
            if (!msg.equals(this.lastTabDiag)) {
                this.lastTabDiag = msg;
                this.tabDiagCount++;
                gg.vape.config.LocalJsonConfigStore.debugLog("nicktag: " + msg);
            }
        } catch (Throwable ignored) {
        }
    }

    private void tabDiagSet(String text, String src) {
        try {
            String msg = src + " SET [" + text + "]";
            if (msg.equals(this.lastTabDiag)) {
                return;
            }
            this.lastTabDiag = msg;
            gg.vape.config.LocalJsonConfigStore.debugLog("nicktag: " + msg);
        } catch (Throwable ignored) {
        }
    }

    public NickTag() {
        super("NickTag", (int) MODULE_ID, Category.RENDER, "Prefixa o nick na tab com uma tag colorida (estilo CrewX)\nUse & para cores (ex.: &3[Vape] &r)");
        this.tag = StringValue.create(this, "Tag", "&3[Vape] &r");
        this.selfOnly = BooleanValue.create(this, "Self only", true, "Marca so o seu nick (off = todo mundo na tab)");
        this.addValue(this.tag, this.selfOnly);
    }

    private String buildTag() {
        String raw = "";
        try {
            Object value = this.tag.getValue();
            if (value != null) {
                raw = value.toString();
            }
        } catch (Throwable ignored) {
        }
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String translated = raw.replace('&', '§');
        if (!translated.endsWith("§r")) {
            translated += "§r";
        }
        return translated;
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
            String prefix = this.buildTag();
            if (prefix.isEmpty()) {
                return;
            }
            PlayerInfo info = event.getNetworkPlayerInfo();
            boolean local = this.isLocalEntry(info);
            ITextComponent current = event.getDisplayName();
            if (current == null) {
                return;
            }
            String text = current.getFormattedText();
            if (text == null) {
                return;
            }
            this.tabDiag(text, local, "M");
            if (this.selfOnly.getEffectiveValue().booleanValue() && !local) {
                return;
            }
            if (text.startsWith(prefix)) {
                return;
            }
            String tagged = prefix + text;
            event.setDisplayName(ITextComponent.a(tagged));
            this.tabDiagSet(tagged, "M");
        } catch (Throwable ignored) {
        }
    }

    @EventHandler
    public void onTabNameLegacy(EventPlayerTabOverlayDisplayNameLegacy event) {
        if (!this.isEnabled()) {
            return;
        }
        try {
            String prefix = this.buildTag();
            if (prefix.isEmpty()) {
                return;
            }
            PlayerInfo info = event.getNetworkPlayerInfo();
            boolean local = this.isLocalEntry(info);
            String text = event.getDisplayName();
            if (text == null) {
                return;
            }
            this.tabDiag(text, local, "L");
            if (this.selfOnly.getEffectiveValue().booleanValue() && !local) {
                return;
            }
            if (text.startsWith(prefix)) {
                return;
            }
            String tagged = prefix + text;
            event.setDisplayName(tagged);
            this.tabDiagSet(tagged, "L");
        } catch (Throwable ignored) {
        }
    }

    @Override
    public String getDetailedSuffix() {
        try {
            Object value = this.tag.getValue();
            return value != null ? value.toString() : super.getDetailedSuffix();
        } catch (Throwable ignored) {
            return super.getDetailedSuffix();
        }
    }
}
