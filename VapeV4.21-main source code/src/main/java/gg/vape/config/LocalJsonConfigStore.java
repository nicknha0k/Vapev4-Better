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
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

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
    private static final String FILE_BACKUP_NAME = "vape421-config.bak.json";

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

    public static File getBackupFile() {
        File current = getConfigFile();
        File parent = current != null ? current.getParentFile() : null;
        if (parent == null) {
            return new File(FILE_BACKUP_NAME);
        }
        return new File(parent, FILE_BACKUP_NAME);
    }

    public static JsonObject loadLocalConfig() {
        JsonObject main = readConfigFile(getConfigFile());
        if (main != null) {
            return main;
        }
        // Principal ausente/corrompido: tenta o backup da geracao anterior.
        debugLog("loadLocalConfig: principal ausente/invalido, tentando .bak");
        JsonObject bak = readConfigFile(getBackupFile());
        debugLog(bak != null ? "loadLocalConfig: carregado do .bak" : "loadLocalConfig: .bak tambem indisponivel");
        return bak;
    }

    /** Log de diagnostico (uma linha por evento) para investigar save/load. */
    public static void debugLog(String message) {
        try {
            File parent = getConfigFile().getParentFile();
            if (parent != null && !parent.isDirectory()) {
                parent.mkdirs();
            }
            String stamp = new java.text.SimpleDateFormat("HH:mm:ss.SSS").format(new java.util.Date());
            byte[] line = (stamp + " " + message + "\r\n").getBytes(StandardCharsets.UTF_8);
            Files.write(new File(parent, "vape421-debug.log").toPath(), line,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Throwable ignored) {
        }
    }

    private static JsonObject readConfigFile(File file) {
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
        if (file != null && file.isFile() && file.length() > 2) {
            return true;
        }
        File backup = getBackupFile();
        return backup != null && backup.isFile() && backup.length() > 2;
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
            debugLog("saveAll: payload nulo (Vape ainda iniciando?)");
            return false;
        }
        // Guarda-corpo: nunca sobrescrever um arquivo COM perfis por um
        // payload SEM perfis. Saves disparados no init (antes do load) ou com
        // perfil em branco ativo capturam lista vazia e apagariam a config.
        if (!payloadHasProfiles(payload) && existingFileHasProfiles()) {
            debugLog("saveAll: RECUSADO overwrite sem perfis sobre arquivo com perfis");
            return false;
        }
        boolean ok = writePayload(payload);
        try {
            int nProfiles = Vape.INSTANCE.getProfilesManager().getProfiles().size();
            Profile ap = Vape.INSTANCE.getProfilesManager().getActiveProfileOrNull();
            String en = ap != null && ap.getEnabledModuleStates() != null ? ap.getEnabledModuleStates().toString() : "null";
            debugLog("saveAll: " + (ok ? "ok" : "FALHOU") + " perfis=" + nProfiles + " ativo=" + (ap == null ? "null" : ap.getName()) + " enabled=" + en);
        } catch (Throwable ignored) {
            debugLog("saveAll: " + (ok ? "ok" : "FALHOU"));
        }
        return ok;
    }

    private static final java.util.concurrent.atomic.AtomicBoolean SAVE_IN_FLIGHT = new java.util.concurrent.atomic.AtomicBoolean(false);
    private static volatile long lastAsyncSaveMs = 0L;

    /**
     * Save assincrono com coalescing (estilo CrewX: salva na hora da mudanca).
     * Chamado de Vape.saveAndStop() a cada toggle/valor/favorito.
     * Coalesce de 500ms evita spam de disco ao arrastar sliders.
     */
    public static void saveAllAsync() {
        long now = System.currentTimeMillis();
        // Coalescing simples: se salvou ha <500ms, o debounce worker salva depois mesmo assim.
        if (now - lastAsyncSaveMs < 500L && SAVE_IN_FLIGHT.get()) {
            return;
        }
        if (!SAVE_IN_FLIGHT.compareAndSet(false, true)) {
            return;
        }
        lastAsyncSaveMs = now;
        Thread t = new Thread(() -> {
            try {
                // Pequeno atraso para agrupar mudancas em rajada (slider, etc).
                try {
                    Thread.sleep(400L);
                } catch (InterruptedException ignored) {
                }
                saveAll();
            } catch (Throwable ignored) {
            } finally {
                SAVE_IN_FLIGHT.set(false);
                lastAsyncSaveMs = System.currentTimeMillis();
            }
        }, "Vape421-local-save");
        t.setDaemon(true);
        t.start();
    }

    /** Save sincrono para shutdown (hook de desligamento). */
    public static void saveAllSyncQuiet() {
        try {
            saveAll();
        } catch (Throwable ignored) {
        }
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
            // Rotaciona a geracao anterior para .bak antes de gravar: se um
            // estado em branco for salvo por engano, a geracao boa continua
            // recuperavel pelo backup e pelo fallback de load.
            rotateBackup(file);
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
            // Mantem o arquivo anterior intacto ate que o temporario esteja pronto.
            // O codigo antigo apagava o destino antes do rename, criando uma janela
            // em que um fechamento do jogo deixava a configuracao sem arquivo.
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception atomicMoveFailure) {
                // Alguns sistemas de arquivo nao suportam ATOMIC_MOVE; ainda assim,
                // REPLACE_EXISTING evita a exclusao antecipada do arquivo atual.
                try {
                    Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception moveFailure) {
                    // Ultimo recurso para sistemas que bloqueiam o rename durante a
                    // leitura: copia o temporario completo e so entao o remove.
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
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean payloadHasProfiles(JsonObject payload) {
        try {
            if (payload == null || !payload.has("profiles")) {
                return false;
            }
            JsonObject profiles = payload.getAsJsonObject("profiles");
            return profiles != null && !profiles.entrySet().isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean existingFileHasProfiles() {
        try {
            JsonObject current = readConfigFile(getConfigFile());
            if (current == null || !current.has("profiles")) {
                return false;
            }
            JsonObject profiles = current.getAsJsonObject("profiles");
            return profiles != null && !profiles.entrySet().isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void rotateBackup(File file) {        try {
            if (file == null || !file.isFile() || file.length() <= 2) {
                return;
            }
            Files.copy(file.toPath(), getBackupFile().toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Throwable ignored) {
        }
    }
}
