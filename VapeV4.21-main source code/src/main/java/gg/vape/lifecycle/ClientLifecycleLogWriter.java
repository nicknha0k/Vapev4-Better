package gg.vape.lifecycle;

public class ClientLifecycleLogWriter
implements ClientLifecycleCallback {
    // Log em arquivo desativado por pedido do usuario: o executavel/DLL nao
    // devem criar .log e o unico .txt mantido e o tutorial (Vape/LEIA-ME.txt).
    // Classe mantida como no-op para compatibilidade (ninguem instancia hoje).

    public ClientLifecycleLogWriter() {
        // no-op: nao cria diretorio, arquivo, nem shutdown hook.
    }

    @Override
    public void log(String message) {
        // no-op: sem escrita em arquivo.
    }

    @Override
    public void close() {
        // no-op.
    }
}
