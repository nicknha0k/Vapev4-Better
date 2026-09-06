package gg.vape.module.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.vape.Vape;
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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

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
        this.appliedKey = null;
        this.pendingKey = null;
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

    private String[] fetchTextures(String username) {
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
