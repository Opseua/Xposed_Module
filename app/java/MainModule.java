package com.xposedmodule.module;

import android.hardware.camera2.CameraCharacteristics;
import android.util.Log;
import dalvik.system.DexClassLoader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import io.github.libxposed.api.XposedModule;
import androidx.annotation.Keep;

public class MainModule extends XposedModule {
    private static final String TAG = "MODULO_LOADER";
    private String currentPackageName = null;
    private WeakHashMap<Object, String> charToIdMap = new WeakHashMap<>();

    // ==============================================================================
    // SISTEMA CENTRAL DE LOG (Logcat + TXT local)
    // ==============================================================================
    @Keep
    public void logWrite(String msg) {
        // 1. Escreve no console para monitoramento via ADB
        Log.i(TAG, msg);
        this.log(Log.INFO, TAG, msg);

        // 2. Escreve no txt dentro da pasta segura do aplicativo
        if (currentPackageName == null) return;
        try {
            File dir = new File("/data/data/" + currentPackageName + "/files/xposed");
            if (!dir.exists()) dir.mkdirs();
            
            File logFile = new File(dir, "module_log.txt");
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
            
            FileWriter fw = new FileWriter(logFile, true);
            fw.write("[" + timestamp + "] " + msg + "\n");
            fw.close();
        } catch (Exception e) {
            Log.e(TAG, "FALHA AO ESCREVER LOG TXT: " + e.getMessage());
        }
    }

    private boolean eProcessoPrincipal(String packageName) {
        try {
            byte[] bytes = Files.readAllBytes(new File("/proc/self/cmdline").toPath());
            String cmdline = new String(bytes).trim();
            return cmdline.equals(packageName);
        } catch (Exception e) { return false; }
    }

    private boolean estaIgnorado(String packageName) {
        File ignoreFile = new File("/data/data/" + packageName + "/files/xposed/module_ignore.txt");
        if (!ignoreFile.exists()) return false;
        try {
            String conteudo = new String(Files.readAllBytes(ignoreFile.toPath()));
            return conteudo.contains(packageName);
        } catch (IOException e) { Log.e(TAG, "Falha ler ignore: " + e.getMessage()); }
        return false;
    }

    @Keep
    public void setupSpoofs(ClassLoader targetClassLoader, String packageName, Map<String, String> sysProps, String[] fakeIds, String fallbackId, Map<String, Map<String, Object>> multiSpoofs, boolean overrideResolutions, boolean hideVpn) {
        
        // ETAPA 1 e 2: HOOK DO SYSTEM PROPERTIES (Nativo)
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
        } catch (Throwable t) { logWrite("Erro SysProps: " + t.getMessage()); }

        // ETAPA 4: HOOK NO CAMERA MANAGER (Mapeamento de Fantasmas)
        try {
            Class<?> managerClass = Class.forName("android.hardware.camera2.CameraManager", false, targetClassLoader);
            for (Method m : managerClass.getDeclaredMethods()) {
                
                if (m.getName().equals("getCameraIdList") && m.getParameterTypes().length == 0) {
                    hook(m).intercept(chain -> {
                        if (fakeIds != null && fakeIds.length > 0) return fakeIds;
                        return chain.proceed();
                    });
                }
                
                if (m.getName().equals("getCameraCharacteristics") && m.getParameterTypes().length == 1) {
                    hook(m).intercept(chain -> {
                        String reqId = (String) chain.getArgs().get(0);
                        
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
        } catch (Throwable t) { logWrite("Erro CameraManager: " + t.getMessage()); }

        // ETAPA 5 e 7: HOOK NAS PROPRIEDADES 
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
            } catch (Throwable t) { logWrite("Erro CameraCharacteristics: " + t.getMessage()); }
        }

        // ETAPA 6: HOOK DE RESOLUÇÕES 
        if (overrideResolutions) {
            try {
                Class<?> streamMapClass = Class.forName("android.hardware.camera2.params.StreamConfigurationMap", false, targetClassLoader);
                for (Method m : streamMapClass.getDeclaredMethods()) {
                    if (m.getName().equals("getOutputSizes") && m.getParameterTypes().length == 1) {
                        hook(m).intercept(chain -> {
                            Object original = chain.proceed();
                            if (original == null) return null; 
                            return new android.util.Size[] {
                                new android.util.Size(4080, 3060), new android.util.Size(4000, 3000), 
                                new android.util.Size(3840, 2160), new android.util.Size(2560, 1440),
                                new android.util.Size(1920, 1080), new android.util.Size(1280, 720), new android.util.Size(640, 480)
                            };
                        });
                    }
                }
            } catch (Throwable t) { logWrite("Erro StreamMap: " + t.getMessage()); }
        }

        // ETAPA 8: HOOK DE VPN
        if (hideVpn) {
            try {
                Class<?> netCapClass = Class.forName("android.net.NetworkCapabilities", false, targetClassLoader);
                for (Method m : netCapClass.getDeclaredMethods()) {
                    if (m.getName().equals("hasTransport") && m.getParameterTypes().length == 1) {
                        hook(m).intercept(chain -> {
                            int transportType = (Integer) chain.getArgs().get(0);
                            if (transportType == 4) return false; 
                            return chain.proceed();
                        });
                    }
                }
            } catch (Throwable t) { logWrite("Erro NetworkCapabilities: " + t.getMessage()); }
        }
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        super.onPackageReady(param);
        currentPackageName = param.getPackageName();

        if (!eProcessoPrincipal(currentPackageName)) return;

        if (estaIgnorado(currentPackageName)) return;

        logWrite("--- INICIANDO VERIFICAÇÃO PARA: " + currentPackageName + " ---");

        File dexFile = new File("/data/data/" + currentPackageName + "/files/xposed/server.dex");
        if (!dexFile.exists()) {
            logWrite("ERRO CRÍTICO: server.dex NÃO ENCONTRADO em " + dexFile.getAbsolutePath());
            return;
        }

        // =================================================================
        // CORREÇÃO DE SEGURANÇA: Remove permissão de escrita do arquivo dex
        // =================================================================
        if (dexFile.canWrite()) {
            logWrite("Aviso: server.dex estava com permissão de escrita. Convertendo para Somente Leitura...");
            dexFile.setReadOnly();
            dexFile.setWritable(false, false);
        }

        logWrite("server.dex validado! Carregando classes...");

        try {
            File cacheDir = new File("/data/data/" + currentPackageName + "/cache");
            if (!cacheDir.exists()) cacheDir.mkdirs();
            DexClassLoader loader = new DexClassLoader(dexFile.getAbsolutePath(), cacheDir.getAbsolutePath(), null, MainModule.class.getClassLoader());
            Class<?> payloadClass = loader.loadClass("com.xposedmodule.payload.ServerPayload");
            Method startMethod = payloadClass.getMethod("start", Object.class, String.class, ClassLoader.class);
            
            logWrite("Invocando método start() do Payload...");
            startMethod.invoke(null, this, currentPackageName, param.getClassLoader());
        } catch (Throwable t) { 
            logWrite("ERRO FATAL AO EXECUTAR PAYLOAD: " + Log.getStackTraceString(t)); 
        }
    }
    
}
