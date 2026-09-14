package gg.vape.module.render;

import gg.vape.Vape;
import gg.vape.config.LocalJsonConfigStore;
import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventPreTick;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.unmap.ModeOption;
import gg.vape.unmap.ModeSelection;
import gg.vape.value.BooleanValue;
import gg.vape.value.ModeValue;
import gg.vape.wrapper.impl.EntityPlayer;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.GameProfile;
import gg.vape.wrapper.impl.PlayerInfo;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;

/**
 * Cape client-side (port CrewX render/Cape.java).
 * Seletor com as mesmas 16 capes do CrewX (PNGs em resources/capes/).
 * Quando ligada, aplica a capa no player local via DynamicTexture e
 * RECOLOCA a cada 1s se outro client/capa passar por cima (Force override).
 */
public class Cape
extends Mod {
    private static final long MODULE_ID = 9214728364719362111L;
    private static final long ENFORCE_INTERVAL_TICKS = 20L;
    private static final String[] TEXTURE_MAP_FIELD_NAMES = {"playerTextures", "field_178488_e", "field_178485_d"};
    private static final String CAPE_DOMAIN = "vape421";

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
    public final BooleanValue forceOverride;
    private volatile String status = "Idle";
    private long tickCounter = 0L;
    private String appliedCapeFile;
    private Object ourCapeLocation;
    private Object ourDynamicTexture;
    private Object appliedInfoHandle;
    private Object savedOriginalCape;
    private boolean savedOriginalCaptured;
    private String lastCapeLog = "";

    private void capeLog(String message) {
        try {
            if (message != null && !message.equals(this.lastCapeLog)) {
                this.lastCapeLog = message;
                LocalJsonConfigStore.debugLog("cape: " + message);
            }
        } catch (Throwable ignored) {
        }
    }

    public Cape() {
        super("Cape", (int) MODULE_ID, Category.RENDER, "Cape client-side (port CrewX)\nEscolha uma das 16 capes no menu");
        ModeOption[] options = new ModeOption[CAPE_NAMES.length];
        for (int i = 0; i < CAPE_NAMES.length; ++i) {
            options[i] = new ModeOption(CAPE_NAMES[i]);
        }
        this.selectedCape = ModeValue.create(this, "Cape", "Qual cape usar (CrewX)", (ModeSelection) options[0], options);
        this.showStatus = BooleanValue.create(this, "Show status", true, "Mostra a cape atual no suffix");
        this.forceOverride = BooleanValue.create(this, "Force override", true, "Recoloca sua cape se outro client/capa passar por cima");
        this.addValue(this.selectedCape, this.showStatus, this.forceOverride);
        this.selectedCape.addChangeListener(changed -> {
            this.appliedCapeFile = null;
            this.tickCounter = ENFORCE_INTERVAL_TICKS;
            this.refreshStatus();
        });
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
        this.appliedCapeFile = null;
        this.ourCapeLocation = null;
        this.appliedInfoHandle = null;
        this.savedOriginalCape = null;
        this.savedOriginalCaptured = false;
        this.tickCounter = ENFORCE_INTERVAL_TICKS;
        this.refreshStatus();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        try {
            this.restoreOriginalCape();
        } catch (Throwable t) {
            Vape.logThrowable(t);
        }
        this.appliedCapeFile = null;
        this.ourCapeLocation = null;
        this.ourDynamicTexture = null;
        this.appliedInfoHandle = null;
        this.directCapeField = null;
        this.savedOriginalCape = null;
        this.savedOriginalCaptured = false;
        this.status = "Idle";
    }

    @EventHandler
    public void onTick(EventPreTick event) {
        try {
            if (!this.isEnabled()) {
                return;
            }
            EntityPlayerSP player = event.getThePlayer();
            if (player == null || player.isNull() || event.getWorld() == null || event.getWorld().isNull()) {
                return;
            }
            if (++this.tickCounter < ENFORCE_INTERVAL_TICKS) {
                return;
            }
            this.tickCounter = 0L;
            this.enforceCape(player);
        } catch (Throwable t) {
            Vape.logThrowable(t);
        }
    }

    private void enforceCape(EntityPlayerSP player) {
        // SkinChanger com capa ativa tem prioridade: nao rouba a capa dele.
        if (this.isSkinChangerCapeActive()) {
            this.status = "SkinChanger";
            return;
        }
        String file = this.getSelectedCapeFile();
        byte[] bytes = this.loadSelectedCapeBytes();
        if (bytes == null || bytes.length == 0) {
            this.status = this.getSelectedCapeName() + " (sem PNG)";
            this.capeLog("sem PNG para " + file);
            return;
        }
        Object infoHandle = this.findLocalInfoHandle(player);
        if (infoHandle == null) {
            this.capeLog("sem playerinfo local");
            return;
        }
        if (infoHandle != this.appliedInfoHandle) {
            this.appliedInfoHandle = infoHandle;
            this.savedOriginalCaptured = false;
            this.appliedCapeFile = null;
            this.directCapeField = null;
        }
        if (this.appliedCapeFile == null || !this.appliedCapeFile.equals(file) || this.ourCapeLocation == null) {
            if (this.ourCapeLocation != null) {
                this.deleteOurTexture(this.ourCapeLocation);
                this.ourCapeLocation = null;
                this.ourDynamicTexture = null;
            }
            Object location = this.uploadCapeTexture(file, bytes);
            if (location == null) {
                this.status = this.getSelectedCapeName() + " (falhou)";
                this.capeLog("upload falhou para " + file);
                return;
            }
            this.ourCapeLocation = location;
            this.appliedCapeFile = file;
        }
        boolean force = this.forceOverride.getEffectiveValue().booleanValue();
        // Via direta (1.7.10/Silent): campo locationCape no PlayerInfo.
        if (this.directCapeField == null) {
            this.directCapeField = this.resolveDirectCapeField(infoHandle);
        }
        if (this.directCapeField != null) {
            Object current = null;
            try {
                current = this.directCapeField.get(infoHandle);
            } catch (Throwable ignored) {
            }
            if (!this.savedOriginalCaptured) {
                this.savedOriginalCape = current;
                this.savedOriginalCaptured = true;
            }
            if (current == null || force || !this.ourCapeLocation.equals(current)) {
                try {
                    this.directCapeField.set(infoHandle, this.ourCapeLocation);
                    this.capeLog("aplicada direto " + file + " (tinha " + (current == null ? "nada" : current.toString()) + ")");
                } catch (Throwable t) {
                    Vape.logThrowable(t);
                    this.capeLog("set direto falhou: " + t);
                    return;
                }
            } else {
                this.capeLog("ativa direto " + file);
            }
            this.forceTexturePixels(this.ourCapeLocation);
            if (force && current != null && !this.ourCapeLocation.equals(current)) {
                this.forceTexturePixels(current);
            }
            this.status = this.getSelectedCapeName();
            return;
        }
        Map<?, ?> textureMap = this.textureMap(infoHandle);
        if (textureMap == null) {
            this.capeLog("sem playerTextures map");
            return;
        }
        Object capeKey = this.capeTypeKey();
        if (capeKey == null) {
            this.capeLog("sem CAPE key");
            return;
        }
        if (!this.savedOriginalCaptured) {
            try {
                this.savedOriginalCape = textureMap.get(capeKey);
            } catch (Throwable ignored) {
                this.savedOriginalCape = null;
            }
            this.savedOriginalCaptured = true;
        }
        Object current;
        try {
            current = textureMap.get(capeKey);
        } catch (Throwable ignored) {
            return;
        }
        boolean oursActive = current != null && current.equals(this.ourCapeLocation);
        // 1) Nossos pixels na NOSSA chave (cobre renderer vanilla).
        this.forceTexturePixels(this.ourCapeLocation);
        // 2) Nossos pixels na chave DELES (cobre Lunar/Silent que ignoram o mapa
        // e amarram o proprio renderer na propria textura).
        if (force && current != null && !current.equals(this.ourCapeLocation)) {
            this.forceTexturePixels(current);
        }
        if (!oursActive && (current == null || force)) {
            try {
                @SuppressWarnings({"unchecked", "rawtypes"})
                Map rawMap = (Map) textureMap;
                rawMap.put(capeKey, this.ourCapeLocation);
                this.capeLog("aplicada " + file + " (tinha " + (current == null ? "nada" : current.toString()) + ")");
            } catch (Throwable t) {
                Vape.logThrowable(t);
                this.capeLog("put falhou: " + t);
                return;
            }
        } else if (oursActive) {
            this.capeLog("ativa " + file);
        }
        this.status = this.getSelectedCapeName();
    }

    private boolean isSkinChangerCapeActive() {
        try {
            if (Vape.INSTANCE == null || Vape.INSTANCE.getModManager() == null) {
                return false;
            }
            SkinChanger skinChanger = Vape.INSTANCE.getModManager().getMod(SkinChanger.class);
            return skinChanger != null && skinChanger.hasActiveCape();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void restoreOriginalCape() {
        Object infoHandle = this.appliedInfoHandle;
        Object ours = this.ourCapeLocation;
        if (infoHandle == null && ours == null) {
            return;
        }
        // Recarrega as texturas Mojang (restaura a capa original, inclusive os
        // pixels que tenham sido substituidos na chave de outro client).
        try {
            if (infoHandle != null) {
                this.reloadSkin(infoHandle);
            }
        } catch (Throwable ignored) {
        }
        // Via direta (1.7.10/Silent): devolve o locationCape original ao campo.
        try {
            if (infoHandle != null && this.directCapeField != null) {
                Object current = null;
                try {
                    current = this.directCapeField.get(infoHandle);
                } catch (Throwable ignored) {
                }
                if (ours != null && ours.equals(current)) {
                    this.directCapeField.set(infoHandle, this.savedOriginalCaptured ? this.savedOriginalCape : null);
                    this.capeLog("campo direto restaurado");
                }
            }
        } catch (Throwable t) {
            Vape.logThrowable(t);
        }
        try {
            if (infoHandle != null) {
                Map<?, ?> textureMap = this.textureMap(infoHandle);
                Object capeKey = this.capeTypeKey();
                if (textureMap != null && capeKey != null) {
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    Map rawMap = (Map) textureMap;
                    Object current = null;
                    try {
                        current = textureMap.get(capeKey);
                    } catch (Throwable ignored) {
                    }
                    if (ours != null && ours.equals(current)) {
                        if (this.savedOriginalCaptured && this.savedOriginalCape != null) {
                            rawMap.put(capeKey, this.savedOriginalCape);
                        } else {
                            rawMap.remove(capeKey);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Vape.logThrowable(t);
        }
        try {
            if (ours != null) {
                this.deleteOurTexture(ours);
            }
        } catch (Throwable ignored) {
        }
    }

    private Object uploadCapeTexture(String file, byte[] bytes) {
        try {
            BufferedImage image;
            ByteArrayInputStream in = new ByteArrayInputStream(bytes);
            try {
                image = ImageIO.read(in);
            } finally {
                try {
                    in.close();
                } catch (Throwable ignored) {
                }
            }
            if (image == null) {
                this.capeLog("PNG nao decodificou");
                return null;
            }
            Object textureManager = this.clientTextureManager();
            if (textureManager == null) {
                this.capeLog("sem texturemanager");
                return null;
            }
            Class<?> dynamicClass = this.mapClass("net/minecraft/client/renderer/texture/DynamicTexture");
            Class<?> textureManagerClass = MappedClasses.Dt;
            Class<?> locationClass = MappedClasses.zC;
            Class<?> textureObjectInterface = MappedClasses.ut;
            if (dynamicClass == null || textureManagerClass == null || locationClass == null || textureObjectInterface == null) {
                this.capeLog("sem mapeamento dyn=" + (dynamicClass != null) + " tm=" + (textureManagerClass != null) + " rl=" + (locationClass != null) + " itex=" + (textureObjectInterface != null));
                return null;
            }
            if (!textureObjectInterface.isAssignableFrom(dynamicClass)) {
                this.capeLog("DynamicTexture nao e ITextureObject");
                return null;
            }
            Method loadMethod = null;
            for (Method candidate : textureManager.getClass().getMethods()) {
                Class<?>[] params = candidate.getParameterTypes();
                if (params.length != 2 || !params[0].isAssignableFrom(locationClass) || params[1] != textureObjectInterface) {
                    continue;
                }
                loadMethod = candidate;
                break;
            }
            if (loadMethod == null) {
                this.capeLog("sem loadTexture(RL,ITextureObject)");
                return null;
            }
            Constructor<?> dynamicCtor = dynamicClass.getConstructor(BufferedImage.class);
            Object dynamicTexture = dynamicCtor.newInstance(image);
            Object location = this.newResourceLocation(CAPE_DOMAIN, sanitizePath(file));
            if (location == null) {
                this.capeLog("sem resourcelocation");
                return null;
            }
            loadMethod.invoke(textureManager, location, dynamicTexture);
            this.ourDynamicTexture = dynamicTexture;
            return location;
        } catch (Throwable t) {
            Vape.logThrowable(t);
            return null;
        }
    }

    /**
     * Garante nossos pixels sob a ResourceLocation dada: se o TextureManager
     * nao aponta mais para a nossa DynamicTexture (outro client recarregou a
     * dele por cima), re-registra a nossa. E o que forca de verdade no
     * Lunar/Silent, cujo renderer ignora o mapa e usa a propria textura.
     */
    private void forceTexturePixels(Object location) {
        try {
            if (location == null || this.ourDynamicTexture == null) {
                return;
            }
            Object textureManager = this.clientTextureManager();
            if (textureManager == null) {
                return;
            }
            Object entry = this.getTextureObject(textureManager, location);
            if (entry == this.ourDynamicTexture) {
                return;
            }
            Method loadMethod = this.findLoadTextureMethod(textureManager);
            if (loadMethod == null) {
                return;
            }
            loadMethod.invoke(textureManager, location, this.ourDynamicTexture);
            this.capeLog("pixels forcados em " + location);
        } catch (Throwable t) {
            Vape.logThrowable(t);
        }
    }

    private Object getTextureObject(Object textureManager, Object location) {
        try {
            Class<?> locationClass = MappedClasses.zC;
            Class<?> textureObjectInterface = MappedClasses.ut;
            if (textureManager == null || locationClass == null || location == null) {
                return null;
            }
            for (Method candidate : textureManager.getClass().getMethods()) {
                Class<?>[] params = candidate.getParameterTypes();
                if (params.length != 1 || !params[0].isAssignableFrom(locationClass)) {
                    continue;
                }
                Class<?> rt = candidate.getReturnType();
                if (rt == Void.TYPE || rt == Boolean.TYPE) {
                    continue;
                }
                if (textureObjectInterface != null && !textureObjectInterface.isAssignableFrom(rt)) {
                    continue;
                }
                return candidate.invoke(textureManager, location);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private Method findLoadTextureMethod(Object textureManager) {
        try {
            Class<?> locationClass = MappedClasses.zC;
            Class<?> textureObjectInterface = MappedClasses.ut;
            Class<?> dynamicClass = this.ourDynamicTexture != null ? this.ourDynamicTexture.getClass() : null;
            for (Method candidate : textureManager.getClass().getMethods()) {
                Class<?>[] params = candidate.getParameterTypes();
                if (params.length != 2 || !params[0].isAssignableFrom(locationClass) || params[1] != textureObjectInterface) {
                    continue;
                }
                if (dynamicClass != null && !params[1].isAssignableFrom(dynamicClass)) {
                    continue;
                }
                return candidate;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void reloadSkin(Object infoHandle) {
        String[] reloadNames = {"loadPlayerTextures", "func_178478_a"};
        for (String methodName : reloadNames) {
            try {
                Method reload = infoHandle.getClass().getMethod(methodName);
                reload.invoke(infoHandle);
                return;
            } catch (Throwable ignored) {
            }
        }
        Class<?> clazz = infoHandle.getClass();
        while (clazz != null) {
            for (String fieldName : TEXTURE_MAP_FIELD_NAMES) {
                try {
                    Field field = clazz.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object map = field.get(infoHandle);
                    if (map instanceof Map) {
                        ((Map<?, ?>) map).clear();
                        return;
                    }
                } catch (Throwable ignored) {
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    private void deleteOurTexture(Object location) {        try {
            Object textureManager = this.clientTextureManager();
            Class<?> locationClass = MappedClasses.zC;
            if (textureManager == null || locationClass == null || location == null) {
                return;
            }
            for (Method candidate : textureManager.getClass().getMethods()) {
                Class<?>[] params = candidate.getParameterTypes();
                if (params.length != 1 || !params[0].isAssignableFrom(locationClass) || candidate.getReturnType() != Boolean.TYPE) {
                    continue;
                }
                candidate.invoke(textureManager, location);
                return;
            }
        } catch (Throwable ignored) {
        }
    }

    private Object clientTextureManager() {
        try {
            Class<?> minecraftClass = this.mapClass("net/minecraft/client/Minecraft");
            Class<?> textureManagerClass = MappedClasses.Dt;
            if (minecraftClass == null || textureManagerClass == null) {
                return null;
            }
            Object gameInstance = null;
            for (Method candidate : minecraftClass.getMethods()) {
                if (!Modifier.isStatic(candidate.getModifiers()) || candidate.getParameterTypes().length != 0
                        || candidate.getReturnType() != minecraftClass) {
                    continue;
                }
                gameInstance = candidate.invoke(null);
                break;
            }
            if (gameInstance == null) {
                return null;
            }
            for (Method candidate : minecraftClass.getMethods()) {
                if (Modifier.isStatic(candidate.getModifiers()) || candidate.getParameterTypes().length != 0
                        || candidate.getReturnType() != textureManagerClass) {
                    continue;
                }
                return candidate.invoke(gameInstance);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private Object newResourceLocation(String domain, String path) {
        try {
            Class<?> locationClass = MappedClasses.zC;
            if (locationClass == null) {
                return null;
            }
            Constructor<?> ctor = locationClass.getConstructor(String.class, String.class);
            return ctor.newInstance(domain, path);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Object capeTypeKey() {
        try {
            Class<?> typeClass = MappedClasses.zk;
            if (typeClass == null) {
                typeClass = Class.forName("com.mojang.authlib.minecraft.MinecraftProfileTexture$Type");
            }
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object key = Enum.valueOf((Class) typeClass, "CAPE");
            return key;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String lastDiagClass = "";
    private Field directCapeField;

    /**
     * Caminho direto 1.7.10/Silent: NetworkPlayerInfo guarda locationCape
     * como campo ResourceLocation (sem mapa). Procura por tipo + nome.
     */
    private Field resolveDirectCapeField(Object infoHandle) {
        try {
            Class<?> locationClass = MappedClasses.zC;
            Class<?> clazz = infoHandle.getClass();
            while (clazz != null) {
                Field[] fields;
                try {
                    fields = clazz.getDeclaredFields();
                } catch (Throwable t) {
                    fields = new Field[0];
                }
                for (Field f : fields) {
                    try {
                        boolean isLocation = (locationClass != null && locationClass.isAssignableFrom(f.getType()))
                                || f.getType().getSimpleName().equals("ResourceLocation");
                        if (!isLocation) {
                            continue;
                        }
                        String name = f.getName().toLowerCase(Locale.ROOT);
                        if (name.contains("cape")) {
                            f.setAccessible(true);
                            return f;
                        }
                    } catch (Throwable ignored) {
                    }
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private Map<?, ?> textureMap(Object infoHandle) {
        try {
            Class<?> clazz = infoHandle.getClass();
            StringBuilder diag = new StringBuilder();
            // Sobe a hierarquia: clients como Lunar/Silent usam subclasses do
            // NetworkPlayerInfo vanilla, e getDeclaredField nao enxerga herdado.
            while (clazz != null) {
                diag.append(clazz.getName()).append('[');
                Field[] fields;
                try {
                    fields = clazz.getDeclaredFields();
                } catch (Throwable t) {
                    fields = new Field[0];
                }
                for (Field f : fields) {
                    diag.append(f.getName()).append(':').append(f.getType().getSimpleName()).append(',');
                }
                diag.append(']');
                for (String fieldName : TEXTURE_MAP_FIELD_NAMES) {
                    try {
                        Field field = clazz.getDeclaredField(fieldName);
                        field.setAccessible(true);
                        Object map = field.get(infoHandle);
                        if (map instanceof Map) {
                            return (Map<?, ?>) map;
                        }
                    } catch (Throwable ignored) {
                    }
                }
                clazz = clazz.getSuperclass();
            }
            String dump = diag.toString();
            if (dump.length() > 1500) {
                dump = dump.substring(0, 1500);
            }
            if (!dump.equals(this.lastDiagClass)) {
                this.lastDiagClass = dump;
                this.capeLog("info-sem-mapa " + dump);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private Object findLocalInfoHandle(EntityPlayerSP player) {
        try {
            if (player == null || player.isNull()) {
                return null;
            }
            GameProfile localProfile = new EntityPlayer(player.getObject()).c$src$Lgg_vape_wrapper_impl_GameProfile_$ir8937();
            UUID localUuid = localProfile.getUUID();
            Collection<?> infos = player.sendQueue().getPlayerInfoMap();
            if (infos == null) {
                return null;
            }
            for (Object info : infos) {
                try {
                    UUID candidate = new PlayerInfo(info).v().getUUID();
                    if (localUuid.equals(candidate)) {
                        return info;
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable t) {
            Vape.logThrowable(t);
        }
        return null;
    }

    private Class<?> mapClass(String notchName) {
        try {
            Method mapper = MappedClasses.class.getDeclaredMethod("m", String.class);
            mapper.setAccessible(true);
            Object mapped = mapper.invoke(null, notchName);
            if (mapped instanceof Class) {
                return (Class<?>) mapped;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String sanitizePath(String file) {
        String clean = file == null ? "cape" : file.toLowerCase(Locale.ROOT);
        clean = clean.replaceAll("[^a-z0-9/._-]", "_");
        if (clean.isEmpty()) {
            clean = "cape.png";
        }
        return clean;
    }

    @Override
    public String getDetailedSuffix() {
        if (this.showStatus.getEffectiveValue()) {
            return this.status != null ? this.status : this.getSelectedCapeName();
        }
        return super.getDetailedSuffix();
    }
}
