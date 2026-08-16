package com.xposedmodule.module;

import android.hardware.camera2.CameraCharacteristics;
import android.util.Log;
import dalvik.system.DexClassLoader;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
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
    public void setupSpoofs(ClassLoader targetClassLoader, String packageName, Map<String, String> sysProps, String[] fakeIds, String fallbackId, Map<String, Map<String, Object>> multiSpoofs, boolean overrideResolutions) {
        
        // ETAPAS 1 a 3: HOOK DO SYSTEM PROPERTIES (Nativo)
        try {
            Class<?> sysPropsClass = Class.forName("android.os.SystemProperties", false, targetClassLoader);
            for (Method m : sysPropsClass.getDeclaredMethods()) {
                if (m.getName().equals("get") && m.getParameterTypes().length >= 1) {
                    hook(m).intercept(chain -> {
                        String key = (String) chain.getArgs().get(0);
                        if (sysProps != null && sysProps.containsKey(key)) {
                            return sysProps.get(key);
                        }
                        return chain.proceed();
                    });
                }
            }
        } catch (Throwable t) { this.log(Log.ERROR, TAG, "Erro SysProps: " + t.getMessage()); }

        // ETAPA 4: HOOK NO CAMERA MANAGER (Mapeamento de Fantasmas sempre roda)
        try {
            Class<?> managerClass = Class.forName("android.hardware.camera2.CameraManager", false, targetClassLoader);
            for (Method m : managerClass.getDeclaredMethods()) {
                
                // Forja a lista apenas se a etapa estiver ativada
                if (m.getName().equals("getCameraIdList") && m.getParameterTypes().length == 0) {
                    hook(m).intercept(chain -> {
                        if (fakeIds != null && fakeIds.length > 0) return fakeIds;
                        return chain.proceed();
                    });
                }
                
                // Marca o ID da câmera na memória sempre
                if (m.getName().equals("getCameraCharacteristics") && m.getParameterTypes().length == 1) {
                    hook(m).intercept(chain -> {
                        String reqId = (String) chain.getArgs().get(0);
                        
                        // Escudo Anti-Bruteforce ativo apenas na Etapa 4
                        if (fakeIds != null && fakeIds.length > 0) {
                            boolean isS23 = false;
                            for (String id : fakeIds) { if (id.equals(reqId)) { isS23 = true; break; } }
                            if (!isS23) throw new IllegalArgumentException("Unknown camera ID: " + reqId);
                        }

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

        // ETAPA 5 E 7: HOOK NAS PROPRIEDADES 
        if (multiSpoofs != null && !multiSpoofs.isEmpty()) {
            try {
                Class<?> charClass = Class.forName("android.hardware.camera2.CameraCharacteristics", false, targetClassLoader);
                for (Method m : charClass.getDeclaredMethods()) {
                    if (m.getName().equals("getPhysicalCameraIds") && m.getParameterTypes().length == 0) {
                        hook(m).intercept(chain -> new java.util.HashSet<String>());
                    }

                    if (m.getName().equals("get") && m.getParameterTypes().length == 1) {
                        hook(m).intercept(chain -> {
                            Object thisObj = chain.getThisObject();
                            String camId = charToIdMap.get(thisObj);
                            Object arg = chain.getArgs().get(0);

                            if (camId != null && multiSpoofs.containsKey(camId) && arg instanceof CameraCharacteristics.Key) {
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
        }

        // ETAPA 6: HOOK DE RESOLUÇÕES 
        if (overrideResolutions) {
            try {
                Class<?> streamMapClass = Class.forName("android.hardware.camera2.params.StreamConfigurationMap", false, targetClassLoader);
                for (Method m : streamMapClass.getDeclaredMethods()) {
                    if (m.getName().equals("getOutputSizes") && m.getParameterTypes().length == 1) {
                        hook(m).intercept(chain -> {
                            Object original = chain.proceed();
                            if (original == null) return null; // Evita NullPointerException em formatos nativos não suportados
                            return new android.util.Size[] {
                                new android.util.Size(4080, 3060), new android.util.Size(4000, 3000), 
                                new android.util.Size(3840, 2160), new android.util.Size(2560, 1440),
                                new android.util.Size(1920, 1080), new android.util.Size(1280, 720), new android.util.Size(640, 480)
                            };
                        });
                    }
                }
            } catch (Throwable t) { this.log(Log.ERROR, TAG, "Erro StreamMap: " + t.getMessage()); }
        }
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
