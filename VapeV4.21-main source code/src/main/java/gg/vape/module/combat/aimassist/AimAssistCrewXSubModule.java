package gg.vape.module.combat.aimassist;

import gg.vape.Vape;
import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventPostTick;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.Mod;
import gg.vape.module.SubModule;
import gg.vape.module.combat.AimAssist;
import gg.vape.module.combat.Reach;
import gg.vape.rotation.PlayerMouseRotationApplier;
import gg.vape.rotation.RotationManager;
import gg.vape.utils.MathUtil;
import gg.vape.utils.RayTraceUtil;
import gg.vape.utils.RotationUtil;
import gg.vape.utils.TimerUtil;
import gg.vape.utils.Vec3d;
import gg.vape.wrapper.impl.AxisAlignedBB;
import gg.vape.wrapper.impl.Entity;
import gg.vape.wrapper.impl.EntityLivingBase;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.RayTraceResult;
import gg.vape.wrapper.impl.RayTraceResult_type;
import gg.vape.wrapper.impl.WorldClient;
import java.util.ArrayList;
import org.jetbrains.annotations.Nullable;

/**
 * Modo CrewX do AimAssist (port de crewx/module/modules/combat/AimAssist.java).
 * Mira direta e suave: a cada tick (POST) escolhe o player valido mais proximo
 * e anda a mira ate ele com passo = delta * 0.1 * speed * (smoothing/100),
 * igual a formula do CrewX. So mira atacando (botao pressionado) ou ate 350ms
 * apos soltar, e pausa se estiver atacando olhando para bloco.
 */
public class AimAssistCrewXSubModule
extends SubModule<AimAssist> {
    private final TimerUtil clickTimer = new TimerUtil();
    private boolean wasAttackDown = false;
    @Nullable
    private EntityLivingBase target = null;

    public AimAssistCrewXSubModule(Mod parent, String name) {
        super(parent, name);
    }

    @Nullable
    public EntityLivingBase getTarget() {
        return this.target;
    }

    private boolean isLookingAtBlock() {
        try {
            RayTraceResult mouseOver = RayTraceUtil.o();
            return mouseOver != null && mouseOver.isNotNull()
                    && mouseOver.getTypeOfHit() != null
                    && mouseOver.getTypeOfHit().equals(RayTraceResult_type.block());
        } catch (Throwable ignored) {
            return false;
        }
    }

    @EventHandler
    public void onPostTick(EventPostTick event) {
        AimAssist aimAssist = (AimAssist) this.getParent();
        this.target = null;
        try {
            if (Minecraft.theWorld().isNull() || Minecraft.thePlayer().isNull()) {
                return;
            }
            EntityPlayerSP player = Minecraft.thePlayer();
            if (!aimAssist.canAim()) {
                return;
            }
            if (!Minecraft.currentScreen().isNull()) {
                return;
            }
            boolean attacking = gg.vape.config.ClientSettings.isAttackButtonDown();
            if (attacking && !this.wasAttackDown) {
                this.clickTimer.reset();
            }
            this.wasAttackDown = attacking;
            if (attacking && this.isLookingAtBlock()) {
                return;
            }
            if (!attacking && this.clickTimer.hasTimeElapsed(350L)) {
                return;
            }
            WorldClient world = Minecraft.theWorld();
            ArrayList<EntityLivingBase> inRange = new ArrayList<EntityLivingBase>();
            for (Object entityObject : new ArrayList<Object>(world.z())) {
                EntityLivingBase candidate;
                try {
                    candidate = new EntityLivingBase(entityObject);
                } catch (Throwable ignored) {
                    continue;
                }
                // CrewX mira apenas em players.
                try {
                    if (!new Entity(entityObject).isInstance(MappedClasses.lG)) {
                        continue;
                    }
                } catch (Throwable ignored) {
                    continue;
                }
                if (!aimAssist.isValidTarget(candidate)) {
                    continue;
                }
                // CrewX nao mira atraves de parede (rayTrace != null = bloqueado).
                try {
                    if (!player.canEntityBeSeen(new Entity(candidate.getObject()))) {
                        continue;
                    }
                } catch (Throwable ignored) {
                }
                inRange.add(candidate);
            }
            if (inRange.isEmpty()) {
                return;
            }
            inRange.sort((first, second) -> Double.compare(
                    player.getDistanceToEntity(first), player.getDistanceToEntity(second)));
            // Preferencia por alvos dentro do alcance (CrewX + modulo Reach).
            double reachDistance = 3.0;
            try {
                Reach reach = Vape.INSTANCE.getModManager().getMod(Reach.class);
                if (reach != null && reach.isEnabled()) {
                    reachDistance = reach.getReachDistance();
                }
            } catch (Throwable ignored) {
            }
            final double reachLimit = reachDistance;
            boolean anyInReach = false;
            for (EntityLivingBase candidate : inRange) {
                if (player.getDistanceToEntity(candidate) <= reachLimit) {
                    anyInReach = true;
                    break;
                }
            }
            if (anyInReach) {
                inRange.removeIf(candidate -> player.getDistanceToEntity(candidate) > reachLimit);
            }
            EntityLivingBase best = inRange.get(0);
            if (player.getDistanceToEntity(best) <= 0.0) {
                return;
            }
            this.target = best;
            AxisAlignedBB box = best.R$src$Lgg_vape_wrapper_impl_AxisAlignedBB_$r19dfl();
            Vec3d closest;
            try {
                closest = RotationUtil.T(player, box, 0.0, 0.0, 0.0);
            } catch (Throwable ignored) {
                return;
            }
            if (closest == null) {
                return;
            }
            float viewYaw = RotationManager.getViewYaw(player);
            float viewPitch = RotationManager.getViewPitch(player);
            float desiredYaw = (float) Math.toDegrees(
                    Math.atan2(-(closest.getX() - player.c()), closest.getZ() - player.h()));
            float desiredPitch = (float) RotationUtil.h(player, closest.getX(), closest.getY(), closest.getZ());
            float hSpeed = Math.min(Math.abs(((Double) aimAssist.getHorizontalSpeed().getValue()).floatValue()), 10.0f);
            float vSpeed = Math.min(Math.abs(((Double) aimAssist.getVerticalSpeed().getValue()).floatValue()), 10.0f);
            float smooth = ((Double) aimAssist.getSmoothing().getValue()).floatValue() / 100.0f;
            float newYaw = viewYaw + MathUtil.wrapAngleTo180(desiredYaw - viewYaw) * 0.1f * hSpeed * smooth;
            float newPitch = viewPitch + MathUtil.wrapAngleTo180(desiredPitch - viewPitch) * 0.1f * vSpeed * smooth;
            PlayerMouseRotationApplier.setLocalPlayerRotation(newYaw, newPitch);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onDisable() {
        this.target = null;
        this.wasAttackDown = false;
    }
}
