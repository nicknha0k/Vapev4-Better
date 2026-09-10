package gg.vape.module.combat;

import gg.vape.config.ClientSettings;
import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventPreAttack;
import gg.vape.event.impl.EventPreTick;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.unmap.ModeOption;
import gg.vape.unmap.ModeSelection;
import gg.vape.utils.TimerUtil;
import gg.vape.value.BooleanValue;
import gg.vape.value.ModeValue;
import gg.vape.value.NumberValue;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.KeyBinding;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.Packet;
import gg.vape.wrapper.impl.PotionRegistry;

public class WTap
extends Mod {
    private final NumberValue chance = NumberValue.createWithDescription(this, "Chance", "#", "%", 0.0, 90.0, 100.0, "Chance of WTapping when hitting a target");
    private final TimerUtil rePressTimer;
    private final NumberValue releaseDelay = NumberValue.create(this, "Release delay", "#", "", 0.0, 0.0, 500.0, 50.0, "Delay before releasing W key after hitting a target");
    private boolean releasePending;
    private final TimerUtil releaseTimer;
    private final NumberValue rePressDelay = NumberValue.create(this, "Re-press delay", "#", "", 0.0, 0.0, 500.0, 50.0, "Delay before re-pressing W key after releasing it");
    private final BooleanValue selectHits = BooleanValue.create(this, "Select hits", true, "Only WTap when the target is vulnerable");
    private boolean rePressPending;
    private static final long MODULE_ID = -5147998889622254014L;
    // Port CrewX Wtap (crewx/module/modules/combat/Wtap.java).
    private final TimerUtil attackCooldown = new TimerUtil();
    private final NumberValue delayTicks = NumberValue.create(this, "Delay ticks", "#.#", "", 0.0, 5.5, 10.0, 0.1, "CrewX delay: ticks de espera apos o hit antes de soltar o W (x50ms)");
    private final NumberValue durationTicks = NumberValue.create(this, "Duration ticks", "#.#", "", 1.0, 1.5, 5.0, 0.1, "CrewX duration: ticks com W solto para resetar o sprint");
    private final BooleanValue requireSprinting = BooleanValue.create(this, "Require sprinting", true, " Usa o canTrigger completo do CrewX (frente + fome + sprint/tecla). Desligar volta ao check simples");
    private final ModeOption keyMethod = new ModeOption("Key");
    private final ModeOption motionMethod = new ModeOption("Motion");
    private final ModeValue method = ModeValue.create(this, "Method", "Key = solta/reaperta a tecla W (original). Motion = zera o input pra frente durante a duracao (CrewX, mais forte contra anti-cheat de movimento)", (ModeSelection) this.motionMethod, this.keyMethod, this.motionMethod);
    private long delayRemainingMs;
    private long durationRemainingMs;
    private boolean motionActive;

    private void handleRePress() {
        if (this.rePressTimer.hasTimeElapsed(((Double)this.rePressDelay.getValue()).longValue())) {
            KeyBinding forwardKey = Minecraft.gameSettings().Y();
            if (ClientSettings.isPhysicalKeyDown(forwardKey)) {
                forwardKey.setPressed(true);
            }
            this.rePressPending = false;
        }
    }


    @EventHandler
    public void onPreAttack(EventPreAttack event) {
        if (!event.getTarget().isInstance(MappedClasses.lG)) {
            return;
        }
        if (this.releasePending || this.rePressPending || this.motionActive) {
            return;
        }
        if (this.selectHits.getEffectiveValue() && event.getTarget().V$src$I$fk0dv5() > 14) {
            return;
        }
        if (!this.shouldTrigger()) {
            return;
        }
        // Port CrewX: cooldown de 500ms + canTrigger completo.
        if (!this.attackCooldown.hasTimeElapsed(500L)) {
            return;
        }
        if (this.requireSprinting.getEffectiveValue()) {
            if (!this.canTriggerFull()) {
                return;
            }
            // CrewX so dispara o wtap sprintando de verdade.
            try {
                if (!Minecraft.thePlayer().B$src$Z$f90iek()) {
                    return;
                }
            } catch (Throwable ignored) {
                return;
            }
        } else if (!this.isMovingForward()) {
            return;
        }
        this.attackCooldown.reset();
        if (this.method.getValue() == this.motionMethod) {
            this.delayRemainingMs = (long) (((Double) this.delayTicks.getValue()) * 50.0);
            this.durationRemainingMs = (long) (((Double) this.durationTicks.getValue()) * 50.0);
            this.motionActive = true;
            return;
        }
        this.releasePending = true;
        this.releaseTimer.reset();
        this.handleRelease();
    }

    /** CrewX canTrigger completo (crewx/module/modules/combat/Wtap.java). */
    private boolean canTriggerFull() {
        try {
            EntityPlayerSP player = Minecraft.thePlayer();
            if (player == null || player.isNull()) {
                return false;
            }
            if (player.F() < 0.8f) {
                return false;
            }
            KeyBinding forwardKey = Minecraft.gameSettings().Y();
            if (!ClientSettings.isPhysicalKeyDown(forwardKey)) {
                return false;
            }
            boolean fed = false;
            try {
                fed = player.Y$src$Lgg_vape_wrapper_impl_FoodStats_$fakh1z().getFoodLevel() > 6;
            } catch (Throwable ignored) {
            }
            boolean flying = false;
            try {
                flying = player.f$src$Z$fst3rk();
            } catch (Throwable ignored) {
            }
            if (!fed && !flying) {
                return false;
            }
            boolean sprinting = false;
            try {
                sprinting = player.B$src$Z$f90iek();
            } catch (Throwable ignored) {
            }
            if (sprinting) {
                return true;
            }
            boolean using = false;
            try {
                using = ClientSettings.isUseItemButtonDown();
            } catch (Throwable ignored) {
            }
            boolean blind = false;
            try {
                blind = player.i(PotionRegistry.K);
            } catch (Throwable ignored) {
            }
            KeyBinding sprintKey = null;
            try {
                sprintKey = Minecraft.gameSettings().r();
            } catch (Throwable ignored) {
            }
            return !using && !blind && sprintKey != null && sprintKey.isKeyDown();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** CrewX canTrigger simplificado com APIs verificadas: W fisico + input pra frente. */
    private boolean isMovingForward() {
        try {
            EntityPlayerSP player = Minecraft.thePlayer();
            if (player == null || player.isNull()) {
                return false;
            }
            KeyBinding forwardKey = Minecraft.gameSettings().Y();
            if (!ClientSettings.isPhysicalKeyDown(forwardKey)) {
                return false;
            }
            return player.F() >= 0.8f;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public WTap() {
        super("WTap", (int)MODULE_ID, Category.COMBAT);
        this.releaseTimer = new TimerUtil();
        this.rePressTimer = new TimerUtil();
        this.addValue(this.chance, this.releaseDelay, this.rePressDelay, this.selectHits,
                this.method, this.delayTicks, this.durationTicks, this.requireSprinting);
        this.releaseDelay.setMaximumFractionDigits(0);
        this.method.addActiveMode(this.delayTicks, this.motionMethod);
        this.method.addActiveMode(this.durationTicks, this.motionMethod);
    }

    @EventHandler
    public void onTick(EventPreTick event) {
        if (Packet.A()) {
            if (Minecraft.currentScreen().isNull()) {
                this.handleRePress();
                this.handleMotionTick();
            }
            return;
        }
        if (Minecraft.currentScreen().isNotNull()) {
            return;
        }
        if (this.motionActive) {
            this.handleMotionTick();
            return;
        }
        if (this.releasePending) {
            this.handleRelease();
            return;
        }
        if (this.rePressPending) {
            this.handleRePress();
            return;
        }
    }

    /** Port CrewX onMoveInput: espera delay, zera o forward durante duration, libera. */
    private void handleMotionTick() {
        if (!this.motionActive) {
            return;
        }
        boolean stillValid;
        if (this.requireSprinting.getEffectiveValue()) {
            stillValid = this.canTriggerFull();
        } else {
            stillValid = this.isMovingForward();
        }
        if (!stillValid) {
            // Parou de andar: cancela como o CrewX faz quando !canTrigger.
            this.motionActive = false;
            this.delayRemainingMs = 0L;
            this.durationRemainingMs = 0L;
            return;
        }
        if (this.delayRemainingMs > 0L) {
            this.delayRemainingMs -= 50L;
            return;
        }
        if (this.durationRemainingMs > 0L) {
            this.durationRemainingMs -= 50L;
            try {
                // Metodo Key: solta a tecla. Metodo Motion faz os dois.
                Minecraft.gameSettings().Y().setPressed(false);
                Minecraft.thePlayer().movementInput().B(0.0f);
            } catch (Throwable ignored) {
            }
            if (this.durationRemainingMs <= 0L) {
                this.motionActive = false;
                try {
                    KeyBinding forwardKey = Minecraft.gameSettings().Y();
                    if (ClientSettings.isPhysicalKeyDown(forwardKey)) {
                        forwardKey.setPressed(true);
                    }
                } catch (Throwable ignored) {
                }
            }
        } else {
            this.motionActive = false;
        }
    }

    private void handleRelease() {
        if (this.releaseTimer.hasTimeElapsed(((Double)this.releaseDelay.getValue()).longValue())) {
            KeyBinding forwardKey = Minecraft.gameSettings().Y();
            forwardKey.setPressed(false);
            this.releasePending = false;
            this.rePressTimer.reset();
            this.rePressPending = true;
        }
    }

    @Override
    public String getDetailedSuffix() {
        return this.releaseDelay.getDisplayValue();
    }

    public boolean shouldTrigger() {
        return (Double)this.chance.getValue() >= Math.random() * 100.0;
    }
}
