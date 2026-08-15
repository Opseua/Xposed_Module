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
        
        File dexFile = new File("/data/local/tmp/xposed/server.dex");
        if (!dexFile.exists()) return;

        try {
            // Usa o cache do app alvo para otimizar o dex
            File cacheDir = new File("/data/user/0/" + param.getPackageName() + "/cache");
            if (!cacheDir.exists()) cacheDir.mkdirs();

            DexClassLoader loader = new DexClassLoader(
                    dexFile.getAbsolutePath(),
                    cacheDir.getAbsolutePath(),
                    null,
                    MainModule.class.getClassLoader()
            );

            // Carrega a classe do seu código externo
            Class<?> payloadClass = loader.loadClass("com.xposedmodule.payload.SpoofPayload");
            
            // Pega o método start() passando String e ClassLoader em vez do PackageReadyParam inteiro
            Method startMethod = payloadClass.getMethod("start", XposedModule.class, String.class, ClassLoader.class);
            startMethod.invoke(null, this, param.getPackageName(), param.getClassLoader());

        } catch (Throwable t) {
            Log.e(TAG, "Erro ao carregar o arquivo dex: ", t);
        }
    }
}
