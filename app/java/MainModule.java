package com.xposedmodule.module;

import android.hardware.camera2.CameraCharacteristics;
import android.util.Log;
import android.util.Size;
import dalvik.system.DexClassLoader;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Map;
import java.util.WeakHashMap;
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
        } catch (Exception e) { return false; }
    }

    private boolean estaIgnorado(String packageName) {
        File ignoreFile = new File(IGNORE_FILE_PATH);
        if (!ignoreFile.exists()) return false;
        try {
            String conteudo = new String(Files.readAllBytes(ignoreFile.toPath()));
            return conteudo.contains(packageName);
        } catch (IOException e) { Log.e(TAG, "Falha ler ignore: " + e.getMessage()); }
        return false;
    }

    private WeakHashMap<Object, String> charToIdMap = new WeakHashMap<>();

    @Keep
    public void setupMultiCameraSpoof(ClassLoader targetClassLoader, String packageName, String[] fakeIds, String fallbackId, Map<String, Map<String, Object>> multiSpoofs) {
        
        // 1. HOOK NO CAMERA MANAGER (Lentes base)
        try {
            Class<?> managerClass = Class.forName("android.hardware.camera2.CameraManager", false, targetClassLoader);
            for (Method m : managerClass.getDeclaredMethods()) {
                if (m.getName().equals("getCameraIdList") && m.getParameterTypes().length == 0) {
                    hook(m).intercept(chain -> fakeIds);
                }
                
                if (m.getName().equals("getCameraCharacteristics") && m.getParameterTypes().length == 1) {
                    hook(m).intercept(chain -> {
                        String reqId = (String) chain.getArgs().get(0);
                        Object result;
                        try {
                            result = chain.proceed();
                        } catch (Exception e) {
                            m.setAccessible(true);
                            result = m.invoke(chain.getThisObject(), fallbackId);
                        }
                        if (result != null) charToIdMap.put(result, reqId);
                        return result;
                    });
                }
            }
        } catch (Throwable t) { this.log(Log.ERROR, TAG, "Erro CameraManager: " + t.getMessage()); }

        // 2. HOOK NAS PROPRIEDADES (Características da Lente + Bloqueio de Lentes Fantasmas)
        try {
            Class<?> charClass = Class.forName("android.hardware.camera2.CameraCharacteristics", false, targetClassLoader);
            for (Method m : charClass.getDeclaredMethods()) {
                
                // Retorna lista vazia para sumir com as lentes "PHYSICAL UNSUPPORTED"
                if (m.getName().equals("getPhysicalCameraIds") && m.getParameterTypes().length == 0) {
                    hook(m).intercept(chain -> new HashSet<String>());
                }

                if (m.getName().equals("get") && m.getParameterTypes().length == 1) {
                    hook(m).intercept(chain -> {
                        Object thisObj = chain.getThisObject();
                        String camId = charToIdMap.get(thisObj);
                        Object arg = chain.getArgs().get(0);

                        if (camId != null && multiSpoofs != null && multiSpoofs.containsKey(camId) && arg instanceof CameraCharacteristics.Key) {
                            CameraCharacteristics.Key<?> key = (CameraCharacteristics.Key<?>) arg;
                            String keyName = key.getName();
                            
                            Map<String, Object> spoofsForThisCam = multiSpoofs.get(camId);
                            if (spoofsForThisCam != null && spoofsForThisCam.containsKey(keyName)) {
                                return spoofsForThisCam.get(keyName);
                            }
                        }
                        return chain.proceed();
                    });
                }
            }
        } catch (Throwable t) { this.log(Log.ERROR, TAG, "Erro CameraCharacteristics: " + t.getMessage()); }

        // 3. HOOK DE RESOLUÇÕES (StreamConfigurationMap para forjar resoluções gigantes)
        try {
            Class<?> streamMapClass = Class.forName("android.hardware.camera2.params.StreamConfigurationMap", false, targetClassLoader);
            for (Method m : streamMapClass.getDeclaredMethods()) {
                if (m.getName().equals("getOutputSizes") && m.getParameterTypes().length == 1) {
                    hook(m).intercept(chain -> {
                        // Força o app a achar que a câmera tem suporte até 4K+ (S23)
                        return new Size[] {
                            new Size(4080, 3060), new Size(4000, 3000), 
                            new Size(3840, 2160), new Size(2560, 1440),
                            new Size(1920, 1080), new Size(1280, 720), new Size(640, 480)
                        };
                    });
                }
            }
        } catch (Throwable t) { this.log(Log.ERROR, TAG, "Erro StreamMap: " + t.getMessage()); }
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        super.onPackageReady(param);
        String packageName = param.getPackageName();
        if (!eProcessoPrincipal(packageName) || estaIgnorado(packageName)) return;
        
        File dexFile = new File("/storage/emulated/0/Pictures/xposed/server.dex");
        if (!dexFile.exists()) return;

        try {
            File cacheDir = new File("/data/data/" + packageName + "/cache");
            if (!cacheDir.exists()) cacheDir.mkdirs();
            DexClassLoader loader = new DexClassLoader(dexFile.getAbsolutePath(), cacheDir.getAbsolutePath(), null, MainModule.class.getClassLoader());
            Class<?> payloadClass = loader.loadClass("com.xposedmodule.payload.ServerPayload");
            Method startMethod = payloadClass.getMethod("start", Object.class, String.class, ClassLoader.class);
            startMethod.invoke(null, this, packageName, param.getClassLoader());
        } catch (Throwable t) { this.log(Log.ERROR, TAG, "Erro ao carregar o payload:\n" + Log.getStackTraceString(t)); }
    }
}
