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

    // /data/local/tmp NÃO é gravável por apps normais (contexto SELinux
    // untrusted_app não alcança esse diretório, mesmo com o app hookado).
    // O log do console (this.log) funciona porque passa pelo framework
    // Xposed com privilégios elevados - mas escrita de arquivo via
    // FileWriter usa a permissão normal do processo do app.
    // Por isso escrevemos dentro do próprio diretório do app hookado
    // (sempre gravável por ele), passado dinamicamente por onPackageReady.
    private void logToFile(String basePath, String msg) {
        try {
            File dir = new File(basePath, "xposed_logs");
            if (!dir.exists()) dir.mkdirs();

            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
            FileWriter writer = new FileWriter(new File(dir, "modulo_log.txt"), true); // true = append
            writer.write("[" + timestamp + "] " + msg + "\n");
            writer.close();
        } catch (IOException e) {
            Log.e(TAG, "Falha ao escrever log em arquivo: " + e.getMessage());
        }
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        super.onPackageReady(param);

        // ETAPA DE TESTE ISOLADO: só confirma que o módulo está sendo carregado
        // e executado pelo LSPosed. Nada de DexClassLoader/payload por enquanto.
        this.log(Log.INFO, TAG, "RODANDO");

        // Grava dentro do diretório do próprio app hookado (ex: filemanager),
        // que sempre é gravável por ele. Acessível depois via:
        // adb shell run-as <pacote_do_app_hookado> cat files/xposed_logs/modulo_log.txt
        // (ou com root: adb shell cat /data/data/<pacote>/files/xposed_logs/modulo_log.txt)
        String appDataDir = "/data/data/" + param.getPackageName() + "/files";
        logToFile(appDataDir, "RODANDO - app: " + param.getPackageName());
    }
}
