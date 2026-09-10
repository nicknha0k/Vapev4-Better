package gg.vape.module.render;

import gg.vape.Vape;
import gg.vape.event.EventHandler;
import gg.vape.event.IEvent;
import gg.vape.event.impl.EventNameFormat;
import gg.vape.event.impl.EventPlayerTabOverlayDisplayName;
import gg.vape.event.impl.EventPostRenderTick;
import gg.vape.event.impl.EventPostRenderWorldPass;
import gg.vape.event.impl.EventPreRenderEntity;
import gg.vape.event.impl.EventPreRenderHand;
import gg.vape.event.impl.EventPreRenderLiving;
import gg.vape.event.impl.EventPreRenderLivingSpecials;
import gg.vape.event.impl.EventPreRenderPlayerSpec;
import gg.vape.event.impl.EventPreRenderWorldPass;
import gg.vape.event.impl.EventPostRenderHand;
import gg.vape.event.impl.EventPreTick;
import gg.vape.event.impl.EventRender3DBase;
import gg.vape.event.impl.EventRenderFirstPersonItemBase;
import gg.vape.event.impl.EventRenderFirstPersonItemPost;
import gg.vape.event.impl.EventRenderFirstPersonItemPre;
import gg.vape.event.impl.EventRenderHandBase;
import gg.vape.event.impl.EventRenderItemInFirstPerson;
import gg.vape.event.impl.EventRenderPlayerPost;
import gg.vape.event.impl.EventRenderPlayerPre;
import gg.vape.event.impl.EventRenderTracers3D;
import gg.vape.event.impl.EventRenderWorldPassBase;
import gg.vape.event.impl.EventRenderWorldPassExecutorDrain;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.value.BooleanValue;
import gg.vape.value.NumberValue;
import gg.vape.value.StringValue;
import gg.vape.wrapper.impl.EntityPlayer;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.GameProfile;
import gg.vape.wrapper.impl.ITextComponent;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.PlayerInfo;
import java.util.UUID;

/**
 * Anti screen share: esconde o cheat em screenshots (F2) e esconde seu nick.
 *
 * - "Hide name": troca seu nick por um falso na tab list e nos nomes
 *   renderizados pelo client.
 * - "Clean screenshots": ao apertar F2, esconde ESP/tracers/nametags/HUD/menu
 *   por alguns ms para a print sair limpa.
 *
 * Limite honesto: Discord/OBS capturam a tela no nivel do sistema, abaixo do
 * cheat, entao NAO ha como esconder o ESP de uma transmissao ao vivo. Este
 * modulo protege prints F2 e sua identidade (nick).
 */
public class AntiScreenShare
extends Mod {
    private static final long MODULE_ID = 8172634918273645102L;
    private static volatile long hideUntilMs = 0L;
    private static volatile Boolean lwjgl2KeyboardAvailable = null;

    public final BooleanValue hideName;
    public final StringValue fakeName;
    public final BooleanValue cleanScreenshots;
    public final NumberValue hideTime;
    private boolean wasF2Down = false;

    public AntiScreenShare() {
        super("AntiScreenShare", (int) MODULE_ID, Category.RENDER,
                "Hides cheat visuals on F2 screenshots and hides your name.\nDoes NOT hide ESP from live Discord/OBS streams (captured below the cheat).");
        this.hideName = BooleanValue.create(this, "Hide name", true, "Troca seu nick por um falso na tab e nos nomes");
        this.fakeName = StringValue.create(this, "Fake name", "You");
        this.cleanScreenshots = BooleanValue.create(this, "Clean screenshots", true, "Esconde ESP/HUD/menu ao apertar F2 para a print sair limpa");
        this.hideTime = NumberValue.create(this, "Hide time", "#", "ms", 250.0, 750.0, 2000.0, 50.0, "Quanto tempo os visuais ficam escondidos apos o F2");
        this.addValue(this.hideName, this.fakeName, this.cleanScreenshots, this.hideTime);
    }

    /** Janela de ocultacao ativa (screenshot em andamento). */
    public static boolean isScreenshotHiding() {
        try {
            if (Vape.INSTANCE == null) {
                return false;
            }
            AntiScreenShare mod = Vape.INSTANCE.getModManager().getMod(AntiScreenShare.class);
            if (mod == null || !mod.isEnabled()) {
                return false;
            }
            if (!mod.cleanScreenshots.getEffectiveValue()) {
                return false;
            }
            return System.currentTimeMillis() < hideUntilMs;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Eventos puramente visuais do cheat: pulados enquanto a print e capturada. */
    public static boolean shouldSuppressEvent(IEvent event) {
        if (event == null || !isScreenshotHiding()) {
            return false;
        }
        return event instanceof EventRender3DBase
                || event instanceof EventRenderTracers3D
                || event instanceof EventPreRenderEntity
                || event instanceof EventPreRenderLiving
                || event instanceof EventPreRenderLivingSpecials
                || event instanceof EventPreRenderPlayerSpec
                || event instanceof EventRenderPlayerPre
                || event instanceof EventRenderPlayerPost
                || event instanceof EventPreRenderWorldPass
                || event instanceof EventPostRenderWorldPass
                || event instanceof EventRenderWorldPassBase
                || event instanceof EventRenderWorldPassExecutorDrain
                || event instanceof EventPreRenderHand
                || event instanceof EventPostRenderHand
                || event instanceof EventRenderHandBase
                || event instanceof EventRenderFirstPersonItemPre
                || event instanceof EventRenderFirstPersonItemPost
                || event instanceof EventRenderFirstPersonItemBase
                || event instanceof EventRenderItemInFirstPerson;
    }

    private boolean isF2Down() {
        try {
            if (lwjgl2KeyboardAvailable == null) {
                try {
                    Class.forName("org.lwjgl.input.Keyboard");
                    lwjgl2KeyboardAvailable = Boolean.TRUE;
                } catch (Throwable notFound) {
                    lwjgl2KeyboardAvailable = Boolean.FALSE;
                }
            }
            if (!lwjgl2KeyboardAvailable) {
                return false;
            }
            return org.lwjgl.input.Keyboard.isKeyDown(60);
        } catch (Throwable ignored) {
            return false;
        }
    }

    @EventHandler
    public void onTick(EventPreTick event) {
        if (!this.isEnabled()) {
            this.wasF2Down = false;
            return;
        }
        boolean f2Down = this.isF2Down();
        try {
            if (f2Down && !this.wasF2Down && this.cleanScreenshots.getEffectiveValue()) {
                long window = 750L;
                try {
                    window = ((Double) this.hideTime.getValue()).longValue();
                } catch (Throwable ignored) {
                }
                hideUntilMs = System.currentTimeMillis() + window;
            }
        } catch (Throwable ignored) {
        }
        this.wasF2Down = f2Down;
    }

    private boolean isLocalPlayer(EntityPlayer player) {
        try {
            if (player == null || player.isNull()) {
                return false;
            }
            EntityPlayerSP me = Minecraft.thePlayer();
            if (me == null || me.isNull()) {
                return false;
            }
            GameProfile theirs = player.c$src$Lgg_vape_wrapper_impl_GameProfile_$ir8937();
            GameProfile mine = me.c$src$Lgg_vape_wrapper_impl_GameProfile_$ir8937();
            if (theirs == null || mine == null || !theirs.isNotNull() || !mine.isNotNull()) {
                return false;
            }
            UUID a = theirs.getUUID();
            UUID b = mine.getUUID();
            return a != null && a.equals(b);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String getFakeName() {
        try {
            String fake = this.fakeName.getValue();
            return fake != null && !fake.isEmpty() ? fake : "You";
        } catch (Throwable ignored) {
            return "You";
        }
    }

    @EventHandler
    public void onNameFormat(EventNameFormat event) {
        if (!this.isEnabled() || !this.hideName.getEffectiveValue()) {
            return;
        }
        try {
            if (!this.isLocalPlayer(event.getPlayer())) {
                return;
            }
            String realName = Minecraft.thePlayer().getName();
            ITextComponent current = event.getDisplayName();
            if (realName == null || current == null) {
                return;
            }
            String text = current.getFormattedText();
            if (text != null && text.contains(realName)) {
                event.setDisplayName(ITextComponent.a(text.replace(realName, this.getFakeName())));
            }
        } catch (Throwable ignored) {
        }
    }

    @EventHandler
    public void onTabName(EventPlayerTabOverlayDisplayName event) {
        if (!this.isEnabled() || !this.hideName.getEffectiveValue()) {
            return;
        }
        try {
            PlayerInfo info = event.getNetworkPlayerInfo();
            if (info == null || info.isNull()) {
                return;
            }
            EntityPlayerSP me = Minecraft.thePlayer();
            if (me == null || me.isNull()) {
                return;
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
            if (entryId != null && entryId.equals(myId)) {
                event.setDisplayName(ITextComponent.a(this.getFakeName()));
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        hideUntilMs = 0L;
        this.wasF2Down = false;
    }
}
