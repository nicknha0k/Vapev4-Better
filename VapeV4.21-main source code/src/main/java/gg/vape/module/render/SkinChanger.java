package gg.vape.module.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.vape.Vape;
import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventPreTick;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.unmap.ModeOption;
import gg.vape.unmap.ModeSelection;
import gg.vape.value.BooleanValue;
import gg.vape.value.ModeValue;
import gg.vape.value.StringValue;
import gg.vape.wrapper.impl.EntityPlayer;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.GameProfile;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.PlayerInfo;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;

public class SkinChanger
extends Mod {
    private static final long MODULE_ID = 8172394829104753821L;
    private static final String[] RELOAD_METHOD_NAMES = {"loadPlayerTextures", "func_178478_a"};
    private static final String[] TEXTURE_MAP_FIELD_NAMES = {"playerTextures", "field_178488_e", "field_178485_d"};
    private static final long REAPPLY_DELAY_MS = 1000L;
    public final StringValue skinUsername;
    public final ModeValue model;
    public final ModeOption autoModelMode;
    public final ModeOption classicModelMode;
    public final ModeOption slimModelMode;
    public final BooleanValue showCape;
    public final BooleanValue showStatus;
    private volatile String status = "Idle";
    private volatile boolean busy = false;
    private volatile String appliedKey;
    private volatile String pendingKey;
    private volatile long pendingSince;
    private volatile boolean watcherStarted = false;
    private final List<Object> savedTextures = new ArrayList<Object>();
    private Object appliedInfoHandle;
    // Ashcon (fonte principal): alem de value/signature entrega as URLs.
    private volatile String fetchedSkinUrl;
    private volatile String fetchedCapeUrl;
    private volatile boolean fetchedSlim;
    // Caminho direto 1.7.10: PNG baixado na watcher thread, upload+set no tick.
    private volatile DirectPending pendingDirect;
    private Object directInfoHandle;
    private Object directSkinRL;
    private Object directCapeRL;
    private Object savedDirectSkin;
    private Object savedDirectCape;
    private boolean directOriginalsCaptured;

    private static final class DirectPending {
        final String key;
        final String username;
        final byte[] skin;
        final byte[] cape;
        DirectPending(String key, String username, byte[] skin, byte[] cape) {
            this.key = key;
            this.username = username;
            this.skin = skin;
            this.cape = cape;
        }
    }

    public SkinChanger() {
        super("SkinChanger", (int)MODULE_ID, Category.RENDER, "Wear another player's skin client-side\nEnter a Minecraft username and toggle on");
        this.skinUsername = StringValue.create(this, "Username", "");
        this.autoModelMode = new ModeOption("Auto");
        this.classicModelMode = new ModeOption("Classic");
        this.slimModelMode = new ModeOption("Slim");
        this.model = ModeValue.create((Object)this, "Model", "Which arm model to use\nAuto keeps the original skin model", (ModeSelection)this.autoModelMode, this.autoModelMode, this.classicModelMode, this.slimModelMode);
        this.showCape = BooleanValue.create(this, "Cape", true, "Show the account cape when it has one");
        this.showStatus = BooleanValue.create(this, "Show status", true);
        this.addValue(this.skinUsername, this.model, this.showCape, this.showStatus);
        this.skinUsername.addChangeListener(changed -> this.onUsernameChanged());
        this.model.addChangeListener(changed -> this.onUsernameChanged());
        this.showCape.addChangeListener(changed -> this.onUsernameChanged());
    }

    @Override
    public String getDetailedSuffix() {
        if (!this.showStatus.getEffectiveValue().booleanValue()) {
            return super.getDetailedSuffix();
        }
        return this.status;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        this.startWatcher();
        this.requestApply();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        this.restoreSkin();
        try {
            this.restoreDirect();
        } catch (Throwable t) {
            Vape.logThrowable(t);
        }
        this.appliedKey = null;
        this.pendingKey = null;
        this.pendingDirect = null;
        this.status = "Idle";
    }

    private void onUsernameChanged() {
        if (!this.isEnabled()) {
            return;
        }
        this.requestApply();
    }

    private synchronized void requestApply() {
        String username = ((String)this.skinUsername.getValue()).trim();
        if (username.isEmpty()) {
            this.status = "Set a username";
            return;
        }
        this.pendingKey = this.describeRequest(username);
        this.pendingSince = System.currentTimeMillis();
    }

    private String describeRequest(String username) {
        Object selectedModel = this.model.getValue();
        String modelName = selectedModel == this.slimModelMode ? "Slim"
                : selectedModel == this.classicModelMode ? "Classic" : "Auto";
        return username + "|" + modelName + "|" + this.showCape.getEffectiveValue().booleanValue();
    }

    private synchronized void startWatcher() {
        if (this.watcherStarted) {
            return;
        }
        this.watcherStarted = true;
        Thread watcher = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(300L);
                }
                catch (InterruptedException ignored) {
                    return;
                }
                if (!this.isEnabled() || this.busy) {
                    continue;
                }
                // PlayerInfo nova (respawn/relogin/troca de mundo): o aplicado
                // valia para o handle antigo, forca re-fetch.
                if (this.appliedKey != null) {
                    try {
                        EntityPlayerSP probe = Minecraft.thePlayer();
                        Object current = (probe == null || probe.isNull()) ? null : this.findLocalInfoHandle(probe);
                        if (current != null && current != this.appliedInfoHandle) {
                            this.appliedKey = null;
                            this.pendingDirect = null;
                        }
                    } catch (Throwable ignored) {
                    }
                }
                String wanted;
                long since;
                synchronized (this) {
                    wanted = this.pendingKey;
                    since = this.pendingSince;
                }
                if (wanted == null || wanted.equals(this.appliedKey)) {
                    continue;
                }
                if (System.currentTimeMillis() - since < REAPPLY_DELAY_MS) {
                    continue;
                }
                this.fetchAndApply();
            }
        }, "Vape skin changer watcher");
        watcher.setDaemon(true);
        watcher.start();
    }

    private void fetchAndApply() {
        String username = ((String)this.skinUsername.getValue()).trim();
        if (username.isEmpty() || !this.isEnabled()) {
            return;
        }
        this.busy = true;
        this.status = "Loading...";
        try {
            String[] textures = this.fetchTextures(username);
            if (textures == null) {
                this.status = "Failed";
                return;
            }
            if (!this.isEnabled()) {
                return;
            }
            // Caminho direto 1.7.10: baixa os PNGs aqui (thread de rede) e o
            // tick faz upload+set (GL so na thread do jogo).
            if (this.routeDirect(username)) {
                return;
            }
            String[] finalTextures = this.applyOverrides(textures[0], textures[1]);
            if (this.applyTextures(finalTextures[0], finalTextures[1])) {
                this.appliedKey = this.describeRequest(username);
                this.status = username;
            } else {
                this.status = "Failed";
            }
        }
        catch (Throwable throwable) {
            Vape.logThrowable(throwable);
            this.status = "Failed";
        }
        finally {
            this.busy = false;
        }
    }

    /**
     * Tenta o caminho direto 1.7.10: se o PlayerInfo local tem campos
     * locationSkin/locationCape, baixa os PNGs (thread de rede) e devolve true
     * para o tick fazer upload+set. Senao, false (segue via properties 1.8.9).
     */
    private boolean routeDirect(String username) {
        try {
            if (this.fetchedSkinUrl == null || this.fetchedSkinUrl.isEmpty()) {
                return false;
            }
            EntityPlayerSP probe = Minecraft.thePlayer();
            if (probe == null || probe.isNull()) {
                return false;
            }
            Object infoHandle = this.findLocalInfoHandle(probe);
            if (infoHandle == null || this.resolveDirectField(infoHandle, "skin") == null) {
                return false;
            }
            byte[] skinBytes = this.downloadBytes(this.fetchedSkinUrl);
            if (skinBytes == null || skinBytes.length == 0) {
                this.status = "Failed";
                return true;
            }
            byte[] capeBytes = null;
            if (this.showCape.getEffectiveValue().booleanValue() && this.fetchedCapeUrl != null && !this.fetchedCapeUrl.isEmpty()) {
                capeBytes = this.downloadBytes(this.fetchedCapeUrl);
            }
            this.pendingDirect = new DirectPending(this.describeRequest(username), username, skinBytes, capeBytes);
            this.status = "Loading...";
            return true;
        } catch (Throwable t) {
            Vape.logThrowable(t);
            return false;
        }
    }

    private byte[] downloadBytes(String urlText) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlText);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Vape421-SkinChanger");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            if (connection.getResponseCode() != 200) {
                return null;
            }
            InputStream input = connection.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } catch (Throwable throwable) {
            Vape.logThrowable(throwable);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @EventHandler
    public void onTick(EventPreTick event) {
        try {
            if (!this.isEnabled()) {
                return;
            }
            DirectPending pending = this.pendingDirect;
            if (pending == null) {
                return;
            }
            EntityPlayerSP player = event.getThePlayer();
            if (player == null || player.isNull()) {
                return;
            }
            if (this.applyDirectUpload(player, pending)) {
                this.appliedKey = pending.key;
                this.status = pending.username;
            } else {
                this.appliedKey = null;
            }
            this.pendingDirect = null;
        } catch (Throwable t) {
            Vape.logThrowable(t);
            this.pendingDirect = null;
        }
    }

    private boolean applyDirectUpload(EntityPlayerSP player, DirectPending pending) {
        try {
            Object infoHandle = this.findLocalInfoHandle(player);
            if (infoHandle == null) {
                return false;
            }
            Field skinField = this.resolveDirectField(infoHandle, "skin");
            if (skinField == null) {
                return false;
            }
            Field capeField = this.resolveDirectField(infoHandle, "cape");
            BufferedImage skinImage = ImageIO.read(new ByteArrayInputStream(pending.skin));
            if (skinImage == null) {
                return false;
            }
            BufferedImage capeImage = null;
            if (pending.cape != null && pending.cape.length > 0) {
                try {
                    capeImage = ImageIO.read(new ByteArrayInputStream(pending.cape));
                } catch (Throwable ignored) {
                }
            }
            Object skinRL = this.uploadPngTexture("skins/" + pending.username, skinImage);
            if (skinRL == null) {
                return false;
            }
            Object capeRL = null;
            if (capeImage != null) {
                capeRL = this.uploadPngTexture("capes/" + pending.username, capeImage);
                if (capeRL == null) {
                    this.deleteTextureRL(skinRL);
                    return false;
                }
            }
            if (infoHandle != this.directInfoHandle) {
                try {
                    this.savedDirectSkin = skinField.get(infoHandle);
                } catch (Throwable ignored) {
                    this.savedDirectSkin = null;
                }
                try {
                    this.savedDirectCape = capeField != null ? capeField.get(infoHandle) : null;
                } catch (Throwable ignored) {
                    this.savedDirectCape = null;
                }
                this.directOriginalsCaptured = true;
                this.directInfoHandle = infoHandle;
                this.deleteTextureRL(this.directSkinRL);
                this.deleteTextureRL(this.directCapeRL);
                this.directSkinRL = null;
                this.directCapeRL = null;
            }
            try {
                skinField.set(infoHandle, skinRL);
            } catch (Throwable t) {
                Vape.logThrowable(t);
                this.deleteTextureRL(skinRL);
                if (capeRL != null) {
                    this.deleteTextureRL(capeRL);
                }
                return false;
            }
            if (capeField != null) {
                try {
                    capeField.set(infoHandle, capeRL);
                } catch (Throwable ignored) {
                }
            }
            this.directSkinRL = skinRL;
            this.directCapeRL = capeRL;
            this.appliedInfoHandle = infoHandle;
            return true;
        } catch (Throwable t) {
            Vape.logThrowable(t);
            return false;
        }
    }

    private void restoreDirect() {
        try {
            Object infoHandle = this.directInfoHandle;
            if (infoHandle == null) {
                try {
                    EntityPlayerSP player = Minecraft.thePlayer();
                    if (player != null && !player.isNull()) {
                        infoHandle = this.findLocalInfoHandle(player);
                    }
                } catch (Throwable ignored) {
                }
            }
            if (infoHandle != null) {
                Field skinField = this.resolveDirectField(infoHandle, "skin");
                if (skinField != null) {
                    try {
                        skinField.set(infoHandle, this.directOriginalsCaptured ? this.savedDirectSkin : null);
                    } catch (Throwable ignored) {
                    }
                }
                Field capeField = this.resolveDirectField(infoHandle, "cape");
                if (capeField != null) {
                    try {
                        capeField.set(infoHandle, this.directOriginalsCaptured ? this.savedDirectCape : null);
                    } catch (Throwable ignored) {
                    }
                }
                try {
                    this.reloadSkin(infoHandle);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable t) {
            Vape.logThrowable(t);
        } finally {
            this.deleteTextureRL(this.directSkinRL);
            this.deleteTextureRL(this.directCapeRL);
            this.directInfoHandle = null;
            this.directSkinRL = null;
            this.directCapeRL = null;
            this.savedDirectSkin = null;
            this.savedDirectCape = null;
            this.directOriginalsCaptured = false;
            this.pendingDirect = null;
        }
    }

    private Field resolveDirectField(Object infoHandle, String keyword) {
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
                        if (f.getName().toLowerCase(Locale.ROOT).contains(keyword)) {
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

    private Object uploadPngTexture(String path, BufferedImage image) {
        try {
            Object textureManager = this.clientTextureManager();
            Class<?> dynamicClass = this.mapClass("net/minecraft/client/renderer/texture/DynamicTexture");
            Class<?> locationClass = MappedClasses.zC;
            Class<?> textureObjectInterface = MappedClasses.ut;
            if (textureManager == null || dynamicClass == null || locationClass == null || textureObjectInterface == null) {
                return null;
            }
            if (!textureObjectInterface.isAssignableFrom(dynamicClass)) {
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
                return null;
            }
            Constructor<?> ctor = dynamicClass.getConstructor(BufferedImage.class);
            Object dynamicTexture = ctor.newInstance(image);
            Constructor<?> rlCtor = locationClass.getConstructor(String.class, String.class);
            Object location = rlCtor.newInstance("vape421", sanitizeTexturePath(path));
            loadMethod.invoke(textureManager, location, dynamicTexture);
            return location;
        } catch (Throwable t) {
            Vape.logThrowable(t);
            return null;
        }
    }

    private void deleteTextureRL(Object location) {
        try {
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
                if (!java.lang.reflect.Modifier.isStatic(candidate.getModifiers()) || candidate.getParameterTypes().length != 0
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
                if (java.lang.reflect.Modifier.isStatic(candidate.getModifiers()) || candidate.getParameterTypes().length != 0
                        || candidate.getReturnType() != textureManagerClass) {
                    continue;
                }
                return candidate.invoke(gameInstance);
            }
        } catch (Throwable ignored) {
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

    private static String sanitizeTexturePath(String path) {
        String clean = path == null ? "skin" : path.toLowerCase(Locale.ROOT);
        clean = clean.replaceAll("[^a-z0-9/._-]", "_");
        if (clean.isEmpty()) {
            clean = "skin.png";
        }
        return clean;
    }

    private String[] applyOverrides(String value, String signature) {
        boolean wantCape = this.showCape.getEffectiveValue().booleanValue();
        Object selectedModel = this.model.getValue();
        boolean forceSlim = selectedModel == this.slimModelMode;
        boolean forceClassic = selectedModel == this.classicModelMode;
        if (wantCape && !forceSlim && !forceClassic) {
            return new String[]{value, signature};
        }
        try {
            String json = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject textures = root.has("textures") ? root.getAsJsonObject("textures") : null;
            if (textures == null) {
                return new String[]{value, signature};
            }
            boolean changed = false;
            if (!wantCape && textures.has("CAPE")) {
                textures.remove("CAPE");
                changed = true;
            }
            if (forceSlim || forceClassic) {
                JsonObject metadata = null;
                if (textures.has("SKIN") && textures.getAsJsonObject("SKIN").has("metadata")) {
                    metadata = textures.getAsJsonObject("SKIN").getAsJsonObject("metadata");
                }
                if (forceSlim) {
                    if (metadata == null) {
                        metadata = new JsonObject();
                        textures.getAsJsonObject("SKIN").add("metadata", metadata);
                    }
                    metadata.addProperty("model", "slim");
                    changed = true;
                } else if (metadata != null && metadata.has("model")) {
                    metadata.remove("model");
                    changed = true;
                }
            }
            if (!changed) {
                return new String[]{value, signature};
            }
            String rewritten = Base64.getEncoder().encodeToString(root.toString().getBytes(StandardCharsets.UTF_8));
            return new String[]{rewritten, null};
        }
        catch (Throwable throwable) {
            Vape.logThrowable(throwable);
            return new String[]{value, signature};
        }
    }

    /**
     * True quando o SkinChanger esta com capa aplicada (direta 1.7.10 ou via
     * properties). O modulo Cape usa isso para ceder a vez e nao roubar a capa.
     */
    public boolean hasActiveCape() {
        try {
            if (!this.isEnabled()) {
                return false;
            }
            if (this.directCapeRL != null) {
                return true;
            }
            return this.appliedKey != null && this.showCape.getEffectiveValue().booleanValue()
                    && this.fetchedCapeUrl != null && !this.fetchedCapeUrl.isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String[] fetchTextures(String username) {
        this.fetchedSkinUrl = null;
        this.fetchedCapeUrl = null;
        this.fetchedSlim = false;
        // Fonte principal: Ashcon entrega value/signature assinados pela
        // Mojang + URLs da skin/cape (o endpoint de nick da Mojang morreu).
        String[] ashcon = this.fetchAshcon(username);
        if (ashcon != null) {
            return ashcon;
        }
        try {
            String uuid = this.fetchUuid(username);
            if (uuid == null) {
                return null;
            }
            String profileJson = this.httpGet("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false");
            if (profileJson == null) {
                return null;
            }
            JsonObject profile = JsonParser.parseString(profileJson).getAsJsonObject();
            JsonArray properties = profile.getAsJsonArray("properties");
            if (properties == null) {
                return null;
            }
            for (JsonElement element : properties) {
                JsonObject property = element.getAsJsonObject();
                if (!"textures".equals(property.get("name").getAsString())) continue;
                String value = property.get("value").getAsString();
                String signature = property.has("signature") ? property.get("signature").getAsString() : null;
                if (signature == null) {
                    return null;
                }
                return new String[]{value, signature};
            }
        }
        catch (Throwable throwable) {
            Vape.logThrowable(throwable);
        }
        return null;
    }

    private String[] fetchAshcon(String username) {
        try {
            String json = this.httpGet("https://api.ashcon.app/mojang/v2/user/" + URLEncoder.encode(username, "UTF-8"));
            if (json == null) {
                return null;
            }
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (!root.has("textures")) {
                return null;
            }
            JsonObject textures = root.getAsJsonObject("textures");
            if (!textures.has("raw")) {
                return null;
            }
            JsonObject raw = textures.getAsJsonObject("raw");
            if (!raw.has("value") || !raw.has("signature")) {
                return null;
            }
            String value = raw.get("value").getAsString();
            String signature = raw.get("signature").getAsString();
            if (value == null || value.isEmpty() || signature == null || signature.isEmpty()) {
                return null;
            }
            if (textures.has("skin")) {
                try {
                    JsonObject skin = textures.getAsJsonObject("skin");
                    if (skin.has("url")) {
                        this.fetchedSkinUrl = skin.get("url").getAsString();
                    }
                } catch (Throwable ignored) {
                }
            }
            if (textures.has("cape")) {
                try {
                    JsonObject cape = textures.getAsJsonObject("cape");
                    if (cape.has("url")) {
                        this.fetchedCapeUrl = cape.get("url").getAsString();
                    }
                } catch (Throwable ignored) {
                }
            }
            if (textures.has("slim")) {
                try {
                    this.fetchedSlim = textures.get("slim").getAsBoolean();
                } catch (Throwable ignored) {
                }
            }
            return new String[]{value, signature};
        } catch (Throwable throwable) {
            Vape.logThrowable(throwable);
            return null;
        }
    }

    private String fetchUuid(String username) {
        try {
            String mojangJson = this.httpGet("https://api.mojang.com/users/profiles/minecraft/" + username);
            if (mojangJson != null) {
                JsonObject response = JsonParser.parseString(mojangJson).getAsJsonObject();
                if (response.has("id")) {
                    return response.get("id").getAsString().replace("-", "");
                }
            }
        }
        catch (Throwable throwable) {
            Vape.logThrowable(throwable);
        }
        try {
            String fallbackJson = this.httpGet("https://api.minetools.eu/uuid/" + username);
            if (fallbackJson != null) {
                JsonObject response = JsonParser.parseString(fallbackJson).getAsJsonObject();
                if (response.has("id")) {
                    return response.get("id").getAsString().replace("-", "");
                }
            }
        }
        catch (Throwable throwable) {
            Vape.logThrowable(throwable);
        }
        return null;
    }

    private String httpGet(String urlText) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlText);
            connection = (HttpURLConnection)url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Vape421-SkinChanger");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            if (connection.getResponseCode() != 200) {
                return null;
            }
            InputStream input = connection.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            StringBuilder response = new StringBuilder();
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                response.append(buffer, 0, read);
            }
            return response.toString();
        }
        catch (Throwable throwable) {
            Vape.logThrowable(throwable);
            return null;
        }
        finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private boolean applyTextures(String value, String signature) {
        try {
            EntityPlayerSP player = Minecraft.thePlayer();
            if (player.isNull()) {
                return false;
            }
            GameProfile localProfile = new EntityPlayer(player.getObject()).c$src$Lgg_vape_wrapper_impl_GameProfile_$ir8937();
            UUID localUuid = localProfile.getUUID();
            Object infoHandle = this.findLocalInfoHandle(localUuid);
            if (infoHandle == null) {
                return false;
            }
            Object profileObject = new PlayerInfo(infoHandle).v().getObject();
            Object properties = profileObject.getClass().getMethod("getProperties").invoke(profileObject);
            Collection<?> current = (Collection<?>)properties.getClass().getMethod("get", Object.class).invoke(properties, "textures");
            this.savedTextures.clear();
            if (current != null) {
                this.savedTextures.addAll(current);
            }
            properties.getClass().getMethod("removeAll", Object.class).invoke(properties, "textures");
            Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
            Object replacement;
            if (signature == null) {
                Constructor<?> constructor = propertyClass.getConstructor(String.class, String.class);
                replacement = constructor.newInstance("textures", value);
            } else {
                Constructor<?> constructor = propertyClass.getConstructor(String.class, String.class, String.class);
                replacement = constructor.newInstance("textures", value, signature);
            }
            properties.getClass().getMethod("put", Object.class, Object.class).invoke(properties, "textures", replacement);
            this.appliedInfoHandle = infoHandle;
            this.reloadSkin(infoHandle);
            return true;
        }
        catch (Throwable throwable) {
            Vape.logThrowable(throwable);
            return false;
        }
    }

    private Object findLocalInfoHandle(EntityPlayerSP player) {
        try {
            if (player == null || player.isNull()) {
                return null;
            }
            GameProfile localProfile = new EntityPlayer(player.getObject()).c$src$Lgg_vape_wrapper_impl_GameProfile_$ir8937();
            return this.findLocalInfoHandle(localProfile.getUUID());
        } catch (Throwable t) {
            Vape.logThrowable(t);
            return null;
        }
    }

    private Object findLocalInfoHandle(UUID localUuid) {
        try {
            EntityPlayerSP player = Minecraft.thePlayer();
            if (player.isNull()) {
                return null;
            }
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
                }
                catch (Throwable ignored) {
                }
            }
        }
        catch (Throwable throwable) {
            Vape.logThrowable(throwable);
        }
        return null;
    }

    private void reloadSkin(Object infoHandle) {
        for (String methodName : RELOAD_METHOD_NAMES) {
            try {
                Method reload = infoHandle.getClass().getMethod(methodName);
                reload.invoke(infoHandle);
                return;
            }
            catch (Throwable ignored) {
            }
        }
        for (String fieldName : TEXTURE_MAP_FIELD_NAMES) {
            try {
                Field field = infoHandle.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object map = field.get(infoHandle);
                if (map instanceof java.util.Map) {
                    ((java.util.Map<?, ?>)map).clear();
                    return;
                }
            }
            catch (Throwable ignored) {
            }
        }
    }

    private void restoreSkin() {
        if (this.appliedInfoHandle == null || this.savedTextures.isEmpty()) {
            this.appliedInfoHandle = null;
            return;
        }
        try {
            Object profileObject = new PlayerInfo(this.appliedInfoHandle).v().getObject();
            Object properties = profileObject.getClass().getMethod("getProperties").invoke(profileObject);
            properties.getClass().getMethod("removeAll", Object.class).invoke(properties, "textures");
            Method put = properties.getClass().getMethod("put", Object.class, Object.class);
            for (Object saved : this.savedTextures) {
                put.invoke(properties, "textures", saved);
            }
            this.reloadSkin(this.appliedInfoHandle);
        }
        catch (Throwable throwable) {
            Vape.logThrowable(throwable);
        }
        finally {
            this.savedTextures.clear();
            this.appliedInfoHandle = null;
        }
    }
}
