package gg.vape.sync;

import gg.vape.Vape;
import gg.vape.module.none.ClientSettings;
import gg.vape.utils.SleepUtil;

public class SyncDebounceWorker
implements Runnable {
    private static final long DEBOUNCE_MILLIS = 3000L;
    private long lastChangeTime;

    @Override
    public void run() {
        while (!Vape.INSTANCE.isEnabled()) {
            this.processPendingSave();
        }
    }

    public void markChanged() {
        this.lastChangeTime = System.currentTimeMillis();
    }

    private void processPendingSave() {
        try {
            SleepUtil.sleep(1000L);
            if (!ClientSettings.INSTANCE.isMainGuiStack() && !ClientSettings.INSTANCE.inputEnabled) {
                return;
            }
            long observedChangeTime = this.lastChangeTime;
            if (Vape.INSTANCE.getSyncThread().hasPendingSave()) {
                SleepUtil.sleep(DEBOUNCE_MILLIS);
                if (this.lastChangeTime == observedChangeTime) {
                    if (Vape.INSTANCE.getPublicProfileSettings().autoSave.getEffectiveValue()) {
                        Vape.INSTANCE.getSyncThread().requestSave();
                    } else {
                        // Auto save (online) desligado: ainda assim persiste o .json
                        // local, senao a config "nunca salva" e a pasta nem e criada.
                        try {
                            gg.vape.config.LocalJsonConfigStore.saveAll();
                        } catch (Throwable ignored) {
                        }
                        Vape.INSTANCE.getSyncThread().clearPendingSave();
                    }
                }
            }
        }
        catch (Exception exception) {
            Vape.logThrowable(exception);
        }
    }
}
