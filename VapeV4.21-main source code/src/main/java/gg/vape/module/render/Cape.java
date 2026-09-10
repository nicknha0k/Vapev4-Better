package gg.vape.module.render;

import gg.vape.Vape;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.unmap.ModeOption;
import gg.vape.unmap.ModeSelection;
import gg.vape.value.BooleanValue;
import gg.vape.value.ModeValue;

/**
 * Cape client-side (port CrewX render/Cape.java).
 * Seletor com as mesmas 16 capes do CrewX. Para a textura aparecer no jogo,
 * coloque os PNGs em src/main/resources/resources/capes/ com os nomes abaixo
 * (ex.: Cherry.png) e rebuild; sem o PNG, o modulo guarda a selecao e mostra
 * o nome no suffix (nao quebra nada).
 */
public class Cape
extends Mod {
    private static final long MODULE_ID = 9214728364719362111L;

    public static final String[] CAPE_NAMES = {
        "Founder's", "Zombie", "MCE", "2016", "2015", "2013",
        "2012", "2011", "Cherry", "MapMaker", "Mojang",
        "MojangStudios", "Mojira", "Classic", "Cobalt", "Moonlight"
    };

    public static final String[] CAPE_FILES = {
        "Founder's.png", "zombie.png", "MCE.png", "2016.png", "2015.png",
        "2013.png", "2012.png", "2011.png", "Cherry.png", "MapMaker.png",
        "Mojang.png", "MojangStudios.png", "Mojira.png", "Classic.png",
        "Cobalt.png", "Moonlight.png"
    };

    public final ModeValue selectedCape;
    public final BooleanValue showStatus;
    private volatile String status = "Idle";

    public Cape() {
        super("Cape", (int) MODULE_ID, Category.RENDER, "Cape client-side (port CrewX)\nEscolha uma das 16 capes no menu");
        ModeOption[] options = new ModeOption[CAPE_NAMES.length];
        for (int i = 0; i < CAPE_NAMES.length; ++i) {
            options[i] = new ModeOption(CAPE_NAMES[i]);
        }
        this.selectedCape = ModeValue.create(this, "Cape", "Qual cape usar (CrewX)", (ModeSelection) options[0], options);
        this.showStatus = BooleanValue.create(this, "Show status", true, "Mostra a cape atual no suffix");
        this.addValue(this.selectedCape, this.showStatus);
        this.selectedCape.addChangeListener(changed -> this.refreshStatus());
    }

    public String getSelectedCapeName() {
        try {
            return ((ModeSelection) this.selectedCape.getValue()).toString();
        } catch (Throwable t) {
            return CAPE_NAMES[0];
        }
    }

    public String getSelectedCapeFile() {
        int idx = this.selectedCape.getSelectedIndex();
        if (idx < 0 || idx >= CAPE_FILES.length) {
            idx = 0;
        }
        return CAPE_FILES[idx];
    }

    /** Tenta ler o PNG de resources/capes/. Retorna null se o usuario ainda nao adicionou. */
    public byte[] loadSelectedCapeBytes() {
        try {
            return Vape.readResource("capes/" + this.getSelectedCapeFile());
        } catch (Throwable t) {
            return null;
        }
    }

    private void refreshStatus() {
        try {
            byte[] bytes = this.loadSelectedCapeBytes();
            this.status = this.getSelectedCapeName() + (bytes != null && bytes.length > 0 ? "" : " (sem PNG)");
        } catch (Throwable t) {
            this.status = this.getSelectedCapeName();
        }
    }

    @Override
    public void onEnable() {
        super.onEnable();
        this.refreshStatus();
    }

    @Override
    public String getDetailedSuffix() {
        if (this.showStatus.getEffectiveValue()) {
            return this.status != null ? this.status : this.getSelectedCapeName();
        }
        return super.getDetailedSuffix();
    }
}
