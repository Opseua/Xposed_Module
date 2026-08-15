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
        
        // Log visível no LSPosed
        this.log(Log.INFO, TAG, "APP ABERTO: " + param.getPackageName());
        
        File dexFile = new File("/data/local/tmp/xposed/server.dex");
        
        if (!dexFile.exists()) {
            this.log(Log.ERROR, TAG, "FALHA CRÍTICA: Arquivo DEX não existe ou o aplicativo não tem permissão de leitura!");
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
            this.log(Log.ERROR, TAG, "Erro ao carregar o arquivo dex: " + t.getMessage());
        }
    }
}
