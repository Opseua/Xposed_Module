package com.xposedmodule.module;

import android.hardware.camera2.CameraCharacteristics;
import android.util.Log;
import dalvik.system.DexClassLoader;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Map;
import io.github.libxposed.api.XposedModule;

public class MainModule extends XposedModule {
    private static final String TAG = "MODULO_LOADER";
    private static final String LOG_FILE_PATH = "/data/data/%s/files/xposed/module_log.txt";
    private static final String IGNORE_FILE_PATH = "/storage/emulated/0/Pictures/xposed/module_ignore.txt";

    private boolean eProcessoPrincipal(String packageName) {
        try {
            byte[] bytes = Files.readAllBytes(new File("/proc/self/cmdline").toPath());
            String cmdline = new String(bytes).trim();
            return cmdline.equals(packageName);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean estaIgnorado(String packageName) {
        File ignoreFile = new File(IGNORE_FILE_PATH);
        if (!ignoreFile.exists()) {
            return false;
        }
        try {
            String conteudo = new String(Files.readAllBytes(ignoreFile.toPath()));
            return conteudo.contains(packageName);
        } catch (IOException e) {
            Log.e(TAG, "Falha ao ler module_ignore.txt: " + e.getMessage());
        }
        return false;
    }

    // Agora o método é genérico e aceita um mapa inteiro com as instruções de spoof
    public void hookCameraCharacteristics(ClassLoader targetClassLoader, String packageName, Map<String, Object> spoofMap) {
        try {
            Class<?> cameraCharacteristicsClass = Class.forName(
                    "android.hardware.camera2.CameraCharacteristics",
                    false,
                    targetClassLoader
            );

            for (Method method : cameraCharacteristicsClass.getDeclaredMethods()) {
                if (method.getName().equals("get") && method.getParameterTypes().length == 1) {
                    hook(method).intercept(chain -> {
                        Object arg = chain.getArgs().get(0);

                        if (arg instanceof CameraCharacteristics.Key) {
                            CameraCharacteristics.Key<?> key = (CameraCharacteristics.Key<?>) arg;
                            String keyName = key.getName();
                            
                            // Se a chave solicitada existir no nosso mapa injetado, retornamos o valor forjado
                            if (spoofMap != null && spoofMap.containsKey(keyName)) {
                                Object spoof = spoofMap.get(keyName);
                                
                                // Tratamento apenas para o log ficar legível caso seja Array
                                String logValue;
                                if (spoof instanceof int[]) logValue = Arrays.toString((int[]) spoof);
                                else if (spoof instanceof float[]) logValue = Arrays.toString((float[]) spoof);
                                else if (spoof instanceof Object[]) logValue = Arrays.toString((Object[]) spoof);
                                else logValue = String.valueOf(spoof);

                                this.log(Log.INFO, TAG, "CAMERA: " + keyName + " -> " + logValue);
                                return spoof;
                            }
                        }

                        return chain.proceed();
                    });
                }
            }
        } catch (Throwable t) {
            this.log(Log.ERROR, TAG, "MODULO: erro ao hookar CameraCharacteristics em " + packageName + " -> " + t.getMessage());
        }
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        super.onPackageReady(param);
        String packageName = param.getPackageName();

        if (!eProcessoPrincipal(packageName) || estaIgnorado(packageName)) {
            return;
        }

        File logFile = new File(String.format(LOG_FILE_PATH, packageName));
        if (!logFile.exists()) {
            this.log(Log.INFO, TAG, "MODULO INICIADO");
        }

        File dexFile = new File("/storage/emulated/0/Pictures/xposed/server.dex");
        if (!dexFile.exists()) {
            this.log(Log.ERROR, TAG, "FALHA CRÍTICA: dex não encontrado em " + dexFile.getAbsolutePath());
            return;
        }

        try {
            File cacheDir = new File("/data/data/" + packageName + "/cache");
            if (!cacheDir.exists()) cacheDir.mkdirs();

            DexClassLoader loader = new DexClassLoader(
                    dexFile.getAbsolutePath(),
                    cacheDir.getAbsolutePath(),
                    null,
                    MainModule.class.getClassLoader()
            );

            Class<?> payloadClass = loader.loadClass("com.xposedmodule.payload.ServerPayload");
            Method startMethod = payloadClass.getMethod("start", Object.class, String.class, ClassLoader.class);
            startMethod.invoke(null, this, packageName, param.getClassLoader());

        } catch (Throwable t) {
            this.log(Log.ERROR, TAG, "Erro ao carregar o payload:\n" + Log.getStackTraceString(t));
        }
    }
}
