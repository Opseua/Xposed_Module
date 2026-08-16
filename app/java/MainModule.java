package com.xposedmodule.module;

import android.hardware.camera2.CameraCharacteristics;
import android.util.Log;
import android.util.Range;
import android.util.SizeF;
import dalvik.system.DexClassLoader;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.Arrays;
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

    // Método que ativa o hook da câmera usando nativamente a API do libxposed
    public void hookCameraCharacteristics(ClassLoader targetClassLoader, String packageName) {
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
                            
                            Object result = chain.proceed();
                            
                            if ("android.sensor.info.physicalSize".equals(keyName)) {
                                SizeF spoof = new SizeF(7.6f, 5.7f);
                                this.log(Log.INFO, TAG, "CAMERA: " + keyName + " -> " + spoof);
                                return spoof;
                            }
                            
                            if ("android.lens.info.availableFocalLengths".equals(keyName)) {
                                float[] spoof = new float[] { 1.5f };
                                this.log(Log.INFO, TAG, "CAMERA: " + keyName + " -> " + Arrays.toString(spoof));
                                return spoof;
                            }

                            if ("android.sensor.info.timestampSource".equals(keyName)) {
                                Integer spoof = 1;
                                this.log(Log.INFO, TAG, "CAMERA: " + keyName + " -> " + spoof);
                                return spoof;
                            }

                            if ("android.control.zoomRatioRange".equals(keyName)) {
                                Range<Float> spoof = new Range<>(0.5f, 10.0f);
                                this.log(Log.INFO, TAG, "CAMERA: " + keyName + " -> " + spoof);
                                return spoof;
                            }
                            
                            if ("android.request.availableCapabilities".equals(keyName)) {
                                int[] spoof = new int[] {0, 1, 2, 3, 4, 5, 6, 8, 9, 11};
                                this.log(Log.INFO, TAG, "CAMERA: " + keyName + " -> " + Arrays.toString(spoof));
                                return spoof;
                            }
                            
                            if ("android.control.aeAvailableTargetFpsRanges".equals(keyName)) {
                                @SuppressWarnings("unchecked")
                                Range<Integer>[] spoof = new Range[] {
                                    new Range<>(15, 30), new Range<>(30, 30)
                                };
                                this.log(Log.INFO, TAG, "CAMERA: " + keyName + " -> " + Arrays.toString(spoof));
                                return spoof;
                            }

                            return result;
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
