package gg.vape.lifecycle;

import java.io.File;

public class ClientDirectoryCleanupCallback
implements ClientLifecycleCallback {
    @Override
    public void log(String message) {
    }

    public ClientDirectoryCleanupCallback() {
        String appDataDirectory = System.getenv("APPDATA");
        String clientDirectoryPath = appDataDirectory + File.separator + ".vapeclient";
        File clientDirectory = new File(clientDirectoryPath);
        if (clientDirectory.exists()) {
            for (File child : clientDirectory.listFiles()) {
                if (child.getName().equals("cache")) continue;
                // Preserva configs locais em .json (ex.: vape421-config.json).
                // Sem isso, o cleanup apagava o save local e parecia que "nunca salva".
                if (child.isFile() && child.getName().toLowerCase().endsWith(".json")) continue;
                child.delete();
            }
        }
    }


    @Override
    public void close() {
    }
}

