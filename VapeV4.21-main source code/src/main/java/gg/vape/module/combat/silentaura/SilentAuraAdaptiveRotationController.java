package gg.vape.module.combat.silentaura;

import gg.vape.rotation.AdaptiveRotationController;

public class SilentAuraAdaptiveRotationController
extends AdaptiveRotationController {
    private final SilentAuraTargetingModule targetingModule;


    public SilentAuraAdaptiveRotationController(SilentAuraTargetingModule targetingModule) {
        this.targetingModule = targetingModule;
    }

    @Override
    public float getSpeed() {
        switch (SilentAuraAdaptiveRotationEntry.MODE_ORDINALS[this.targetingModule.getRotationMode().ordinal()]) {
            case 1: {
                return this.targetingModule.getAttackRotationSpeed();
            }
            case 2: {
                return this.targetingModule.getFlickAwayRotationSpeed();
            }
        }
        return 48.0f;
    }
}

