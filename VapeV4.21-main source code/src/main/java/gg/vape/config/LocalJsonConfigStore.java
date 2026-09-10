package gg.vape.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import gg.vape.Vape;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * Local .json persistence (estilo CrewX: arquivo legivel em disco).
 * Motivo: o save original so tentava a API online (saveUserData/saveProfileData)
 * e o load standalone lia de NativeBridge.gp("all") que e hardcoded.
 * Resultado: "nunca salva". Este store grava tudo localmente.
 */
public final class LocalJsonConfigStore {
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();
    private static final String DIR_NAME = "Vape421";
    private static final String FILE_NAME = "vape421-config.json";

    private LocalJsonConfigStore() {
    }

    public static File getConfigFile() {
        String appData = System.getenv("APPDATA");
        File dir;
        if (appData != null && !appData.isEmpty()) {
            dir = new File(appData, DIR_NAME);
        } else {
            dir = new File(System.getProperty("user.home", "."), DIR_NAME);
        }
        return new File(dir, FILE_NAME);
    }

    public static JsonObject loadLocalConfig() {
        File file = getConfigFile();
        if (file == null || !file.isFile()) {
            return null;
        }
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            JsonReader reader = new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            reader.setLenient(true);
            JsonObject parsed = PRETTY.fromJson(reader, JsonObject.class);
            try {
                reader.close();
            } catch (Exception ignored) {
            }
            return parsed;
        } catch (Exception ignored) {
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static boolean hasLocalConfig() {
        File file = getConfigFile();
        return file != null && file.isFile() && file.length() > 2;
    }

    /** Monta o payload completo no mesmo formato que Vape.loadConfigData espera. */
    public static JsonObject buildFullPayload() {
        try {
            if (Vape.INSTANCE == null) {
                return null;
            }
            // Garante que o perfil ativo capturou o estado atual antes de serializar.
            try {
                if (Vape.INSTANCE.getProfilesManager() != null
                        && Vape.INSTANCE.getProfilesManager().getActiveProfileOrNull() != null) {
                    Vape.INSTANCE.getProfilesManager().getActiveProfileOrNull().captureCurrentState();
                }
            } catch (Throwable ignored) {
            }
            JsonObject root = new JsonObject();
            try {
                JsonArray friends = Vape.INSTANCE.getFriendManager().toJson();
                root.add("friends", friends != null ? friends : new JsonArray());
            } catch (Throwable ignored) {
                root.add("friends", new JsonArray());
            }
            try {
                JsonObject profiles = Vape.INSTANCE.getProfilesManager().toJson(false);
                root.add("profiles", profiles != null ? profiles : new JsonObject());
            } catch (Throwable ignored) {
                root.add("profiles", new JsonObject());
            }
            try {
                JsonArray other = Vape.INSTANCE.getSettingsManager().toJson();
                if (other == null) {
                    other = new JsonArray();
                }
                // loadConfigData aceita "otherData" (novo) ou "otherdata" (legado).
                // Gravamos os dois apontando para o mesmo conteudo.
                root.add("otherData", other);
                root.add("otherdata", other);
            } catch (Throwable ignored) {
                root.add("otherData", new JsonArray());
                root.add("otherdata", new JsonArray());
            }
            return root;
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean saveAll() {
        JsonObject payload = buildFullPayload();
        if (payload == null) {
            return false;
        }
        return writePayload(payload);
    }

    public static boolean writePayload(JsonObject payload) {
        if (payload == null) {
            return false;
        }
        File file = getConfigFile();
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory()) {
                parent.mkdirs();
            }
            File tmp = new File(parent, FILE_NAME + ".tmp");
            FileOutputStream out = new FileOutputStream(tmp, false);
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
            try {
                PRETTY.toJson(payload, writer);
                writer.flush();
            } finally {
                try {
                    writer.close();
                } catch (Exception ignored) {
                }
            }
            // Troca atomica: evita corromper o .json se o jogo fechar no meio do save.
            if (file.isFile() && !file.delete()) {
                // Se nao conseguir deletar, tenta renomear por cima mesmo assim.
            }
            if (!tmp.renameTo(file)) {
                // Fallback: copia por cima.
                FileInputStream in = new FileInputStream(tmp);
                FileOutputStream dst = new FileOutputStream(file, false);
                try {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) >= 0) {
                        dst.write(buf, 0, n);
                    }
                } finally {
                    try {
                        in.close();
                    } catch (Exception ignored) {
                    }
                    try {
                        dst.close();
                    } catch (Exception ignored) {
                    }
                }
                tmp.delete();
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
