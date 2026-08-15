package com.xposedmodule.module;

import android.util.Log;
import dalvik.system.DexClassLoader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
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

        this.log(Log.INFO, TAG, "RODANDO");

        String appDataDir = "/data/data/" + param.getPackageName() + "/files";
        logToFile(appDataDir, "RODANDO - app: " + param.getPackageName());

        // Caminho confirmado via "adb shell find": a pasta compartilhada do
        // MuMuPlayer aparece dentro do Android em /storage/emulated/0/...
        // (a raiz "de baixo nível" /data/media/0/... é a mesma coisa vista
        // pelo shell root, mas apps normais devem usar o caminho de app).
        File dexFile = new File("/storage/emulated/0/Pictures/xposed/server.dex");

        if (!dexFile.exists()) {
            this.log(Log.ERROR, TAG, "FALHA CRÍTICA: Arquivo DEX não existe ou sem permissão de leitura! Caminho: " + dexFile.getAbsolutePath());
            logToFile(appDataDir, "FALHA CRÍTICA: dex não encontrado em " + dexFile.getAbsolutePath());
            return;
        }

        try {
            File cacheDir = new File("/data/data/" + param.getPackageName() + "/cache");
            if (!cacheDir.exists()) cacheDir.mkdirs();

            // XposedModule NÃO é uma classe "normal" resolvível em qualquer
            // classloader: ela não é empacotada no APK do módulo nem existe
            // no app hookado. O framework Vector injeta a implementação real
            // dela via attachFramework() diretamente no processo/classloader
            // do PRÓPRIO módulo (com.xposedmodule.module) no momento em que
            // MainModule é instanciado. Por isso o parent aqui precisa ser
            // o classloader do MainModule, não o do app hookado.
            DexClassLoader loader = new DexClassLoader(
                    dexFile.getAbsolutePath(),
                    cacheDir.getAbsolutePath(),
                    null,
                    MainModule.class.getClassLoader()
            );

            this.log(Log.INFO, TAG, "LOADER CARREGADO para: " + param.getPackageName());
            logToFile(appDataDir, "LOADER CARREGADO para: " + param.getPackageName());

            Class<?> payloadClass = loader.loadClass("com.xposedmodule.payload.ServerPayload");

            // IMPORTANTE: getMethod("start", XposedModule.class, ...) força o
            // classloader que fez a chamada reflexiva a RESOLVER XposedModule
            // como tipo. Só que XposedModule não é empacotada em nenhum dex
            // físico (compileOnly) - ela existe apenas como instância já
            // resolvida internamente pelo framework via attachFramework().
            // Isso quebra a resolução por reflection em qualquer combinação
            // de classloader. Solução: usar Object.class na assinatura do
            // start() (tanto aqui quanto no ServerPayload.java). Dentro do
            // ServerPayload, "this" (a instância real) também é acessado só
            // via reflection (hostModule.getClass().getMethod("log", ...)),
            // nunca com um cast/import direto para XposedModule - assim o
            // payload nunca precisa resolver esse tipo.
            Method startMethod = payloadClass.getMethod("start", Object.class, String.class, ClassLoader.class);
            startMethod.invoke(null, this, param.getPackageName(), param.getClassLoader());

        } catch (Throwable t) {
            // Log.getStackTraceString mostra a causa real (ex: ClassNotFoundException,
            // NoSuchMethodError por assinatura errada) - t.getMessage() sozinho
            // costuma vir null e esconder o erro de verdade.
            String erro = Log.getStackTraceString(t);
            this.log(Log.ERROR, TAG, "Erro ao carregar o payload:\n" + erro);
            logToFile(appDataDir, "ERRO ao carregar payload:\n" + erro);
        }
    }
}
