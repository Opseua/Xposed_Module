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
import java.util.WeakHashMap;
import java.util.Map;

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

    private WeakHashMap<Object, String> charToIdMap = new WeakHashMap<>();

    // Agora o método é genérico e aceita um mapa inteiro com as instruções de spoof
    @Keep
    public void setupMultiCameraSpoof(ClassLoader targetClassLoader, String packageName, String[] fakeIds, String fallbackId, Map<String, Map<String, Object>> multiSpoofs) {
        
        // 1. HOOK NO CAMERA MANAGER (Para forjar a quantidade de câmeras e evitar crash)
        try {
            Class<?> managerClass = Class.forName("android.hardware.camera2.CameraManager", false, targetClassLoader);
            for (Method m : managerClass.getDeclaredMethods()) {
                if (m.getName().equals("getCameraIdList") && m.getParameterTypes().length == 0) {
                    hook(m).intercept(chain -> fakeIds); // Retorna ["0", "1", "2", "3", "5", "6"]
                }
                
                if (m.getName().equals("getCameraCharacteristics") && m.getParameterTypes().length == 1) {
                    hook(m).intercept(chain -> {
                        String reqId = (String) chain.getArgs().get(0);
                        Object result;
                        try {
                            result = chain.proceed();
                        } catch (Exception e) {
                            // Câmera não existe no hardware atual, aciona o fantasma
                            // CORREÇÃO AQUI: getMethod() em vez de getMember()
                            Method getChar = chain.getMethod(); 
                            getChar.setAccessible(true);
                            result = getChar.invoke(chain.getThisObject(), fallbackId);
                        }
                        
                        // Marca o objeto com o ID que o app ACHOU que estava abrindo
                        if (result != null) {
                            charToIdMap.put(result, reqId);
                        }
                        return result;
                    });
                }
            }
        } catch (Throwable t) { this.log(Log.ERROR, TAG, "Erro CameraManager: " + t.getMessage()); }

        // 2. HOOK NAS PROPRIEDADES (Para entregar os dados separados por lente)
        try {
            Class<?> charClass = Class.forName("android.hardware.camera2.CameraCharacteristics", false, targetClassLoader);
            for (Method m : charClass.getDeclaredMethods()) {
                if (m.getName().equals("get") && m.getParameterTypes().length == 1) {
                    hook(m).intercept(chain -> {
                        Object thisObj = chain.getThisObject();
                        String camId = charToIdMap.get(thisObj); // Descobre qual câmera está sendo lida
                        Object arg = chain.getArgs().get(0);

                        if (camId != null && multiSpoofs != null && multiSpoofs.containsKey(camId) && arg instanceof CameraCharacteristics.Key) {
                            CameraCharacteristics.Key<?> key = (CameraCharacteristics.Key<?>) arg;
                            String keyName = key.getName();
                            
                            // Pega as instruções exclusivas desta câmera
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
