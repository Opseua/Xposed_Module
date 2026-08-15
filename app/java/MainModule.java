package com.xposedmodule.module;

import android.util.Log;
import java.io.File;
import java.lang.reflect.Method;
import dalvik.system.DexClassLoader;
import io.github.libxposed.api.XposedModule;

public class MainModule extends XposedModule {
    private static final String TAG = "MODULO_LOADER";

    @Override
    public void onPackageReady(PackageReadyParam param) {
        super.onPackageReady(param);

        // Log fixo pra confirmar que o módulo está rodando, sempre que qualquer
        // app do escopo abrir - independente do resto do fluxo funcionar ou não.
        this.log(Log.INFO, TAG, "MODULO RODANDO");

        // Log visível no LSPosed
        this.log(Log.INFO, TAG, "APP ABERTO: " + param.getPackageName());

        // ATENÇÃO: ajuste esse caminho conforme onde a pasta compartilhada do
        // MuMuPlayer realmente é montada dentro do Android (confirme com
        // "adb shell find /sdcard -iname server.dex"). /data/local/tmp
        // normalmente NÃO é a pasta compartilhada e pode ter restrição de leitura.
        File dexFile = new File("/sdcard/Pictures/xposed/server.dex");

        if (!dexFile.exists()) {
            this.log(Log.ERROR, TAG, "FALHA CRÍTICA: Arquivo DEX não existe ou o aplicativo não tem permissão de leitura! Caminho: " + dexFile.getAbsolutePath());
            return;
        }

        try {
            File cacheDir = new File("/data/user/0/" + param.getPackageName() + "/cache");
            if (!cacheDir.exists()) cacheDir.mkdirs();

            DexClassLoader loader = new DexClassLoader(
                    dexFile.getAbsolutePath(),
                    cacheDir.getAbsolutePath(),
                    null,
                    MainModule.class.getClassLoader()
            );

            this.log(Log.INFO, TAG, "LOADER CARREGADO para: " + param.getPackageName());

            Class<?> payloadClass = loader.loadClass("com.xposedmodule.payload.ServerPayload");
            
            Method startMethod = payloadClass.getMethod("start", XposedModule.class, String.class, ClassLoader.class);
            startMethod.invoke(null, this, param.getPackageName(), param.getClassLoader());

        } catch (Throwable t) {
            // Usar Log.getStackTraceString mostra a causa real (ex: ClassNotFoundException,
            // NoSuchMethodError por assinatura errada, etc). t.getMessage() sozinho
            // costuma vir null e esconder o erro de verdade.
            this.log(Log.ERROR, TAG, "Erro ao carregar o arquivo dex:\n" + Log.getStackTraceString(t));
        }
    }
}
