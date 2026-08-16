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
import androidx.annotation.Keep;

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
    @Keep
    public void hookCameraManager(ClassLoader targetClassLoader, String packageName, String[] fakeIds, String fallbackCameraId) {
        try {
            Class<?> cameraManagerClass = Class.forName(
                    "android.hardware.camera2.CameraManager",
                    false,
                    targetClassLoader
            );

            for (Method method : cameraManagerClass.getDeclaredMethods()) {
                
                // 1. Falsifica a quantidade de câmeras (A lista que aparece no app)
                if (method.getName().equals("getCameraIdList") && method.getParameterTypes().length == 0) {
                    hook(method).intercept(chain -> {
                        this.log(Log.INFO, TAG, "CAMERA: getCameraIdList injetado -> " + Arrays.toString(fakeIds));
                        return fakeIds; // Retorna ["0", "1", "2", "3", "5", "6"]
                    });
                }

                // 2. Cria o "Fantasma" para evitar Crash quando o app tentar abrir a câmera falsa
                if (method.getName().equals("getCameraCharacteristics") && method.getParameterTypes().length == 1) {
                    hook(method).intercept(chain -> {
                        String requestedId = (String) chain.getArgs().get(0);
                        
                        try {
                            // Tenta ler a câmera normalmente
                            return chain.proceed();
                        } catch (IllegalArgumentException e) {
                            // Se crashar, é porque seu celular não tem essa câmera física.
                            // Redirecionamos para a câmera fallback (ex: "0") silenciosamente.
                            this.log(Log.INFO, TAG, "CAMERA: Criando molde fantasma para ID " + requestedId + " usando lente " + fallbackCameraId);
                            
                            Method m = (Method) chain.getMember();
                            m.setAccessible(true);
                            return m.invoke(chain.getThisObject(), fallbackCameraId);
                        }
                    });
                }
            }
        } catch (Throwable t) {
            this.log(Log.ERROR, TAG, "MODULO: erro ao hookar CameraManager em " + packageName + " -> " + t.getMessage());
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
