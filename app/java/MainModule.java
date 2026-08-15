package com.xposedmodule.module;

import android.util.Log;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import io.github.libxposed.api.XposedModule;

public class MainModule extends XposedModule {
    private static final String TAG = "MODULO_LOADER";

    // Arquivo de log simples, fora do app hookado, pra conseguirmos ler mesmo
    // se o console do LSPosed falhar por algum motivo. Usamos /data/local/tmp
    // porque é acessível via "adb shell cat" com root, sem depender de
    // permissão de storage do app.
    private static final String LOG_FILE = "/data/local/tmp/modulo_log.txt";

    private void logToFile(String msg) {
        try {
            File dir = new File("/data/local/tmp");
            if (!dir.exists()) dir.mkdirs();

            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
            FileWriter writer = new FileWriter(LOG_FILE, true); // true = append
            writer.write("[" + timestamp + "] " + msg + "\n");
            writer.close();
        } catch (IOException e) {
            // Se nem isso funcionar, ao menos tenta deixar rastro no logcat do sistema
            Log.e(TAG, "Falha ao escrever log em arquivo: " + e.getMessage());
        }
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        super.onPackageReady(param);

        // ETAPA DE TESTE ISOLADO: só confirma que o módulo está sendo carregado
        // e executado pelo LSPosed. Nada de DexClassLoader/payload por enquanto.
        this.log(Log.INFO, TAG, "RODANDO");
        logToFile("RODANDO - app: " + param.getPackageName());
    }
}
