package com.xposedmodule.module;

import android.hardware.camera2.CameraCharacteristics;
import android.util.Log;
import dalvik.system.DexClassLoader;
import java.io.File;
import java.io.FileWriter;
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

    @Keep
    public void logWrite(String msg) {
        logWrite(msg, false);
    }

    @Keep
    public void logWrite(String msg, boolean skipIfExists) {
        if (currentPackageName == null) return;
        File dir = new File("/data/data/" + currentPackageName + "/files/xposed");
        File logFile = new File(dir, "module_log.txt");

        if (skipIfExists && logFile.exists()) return;

        Log.i(TAG, msg);
        this.log(Log.INFO, TAG, msg);

        try {
            if (!dir.exists()) dir.mkdirs();
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
            FileWriter fw = new FileWriter(logFile, true);
            fw.write("[" + timestamp + "] " + msg + "\n");
            fw.close();
        } catch (Exception e) { Log.e(TAG, "FALHA LOG TXT: " + e.getMessage()); }
    }

    private boolean eProcessoPrincipal(String packageName) {
        try {
            byte[] bytes = Files.readAllBytes(new File("/proc/self/cmdline").toPath());
            return new String(bytes).trim().equals(packageName);
        } catch (Exception e) { return false; }
    }

    @Keep
    public void setupSpoofs(ClassLoader targetClassLoader, String packageName, Map<String, String> sysProps, String[] fakeIds, String fallbackId, Map<String, Map<String, Object>> multiSpoofs, boolean overrideResolutions, boolean hideVpn) {
        try {
            Class<?> sysPropsClass = Class.forName("android.os.SystemProperties", false, targetClassLoader);
            for (Method m : sysPropsClass.getDeclaredMethods()) {
                if (m.getName().equals("get") && m.getParameterTypes().length >= 1) {
                    hook(m).intercept(chain -> {
                        String key = (String) chain.getArgs().get(0);
                        return sysProps.containsKey(key) ? sysProps.get(key) : chain.proceed();
                    });
                }
            }
        } catch (Throwable t) { logWrite("Erro SysProps: " + t.getMessage()); }

        try {
            Class<?> managerClass = Class.forName("android.hardware.camera2.CameraManager", false, targetClassLoader);
            for (Method m : managerClass.getDeclaredMethods()) {
                if (m.getName().equals("getCameraIdList") && m.getParameterTypes().length == 0) {
                    hook(m).intercept(chain -> (fakeIds != null && fakeIds.length > 0) ? fakeIds : chain.proceed());
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
                        try { result = chain.proceed(); } 
                        catch (Exception e) { m.setAccessible(true); result = m.invoke(chain.getThisObject(), fallbackId); }
                        if (result != null) charToIdMap.put(result, reqId);
                        return result;
                    });
                }
            }
        } catch (Throwable t) { logWrite("Erro CameraManager: " + t.getMessage()); }

        if (multiSpoofs != null && !multiSpoofs.isEmpty()) {
            try {
                Class<?> charClass = Class.forName("android.hardware.camera2.CameraCharacteristics", false, targetClassLoader);
                for (Method m : charClass.getDeclaredMethods()) {
                    if (m.getName().equals("getPhysicalCameraIds") && m.getParameterTypes().length == 0) {
                        hook(m).intercept(chain -> new java.util.HashSet<String>());
                    }
                    if (m.getName().equals("get") && m.getParameterTypes().length == 1) {
                        hook(m).intercept(chain -> {
                            String camId = charToIdMap.get(chain.getThisObject());
                            Object arg = chain.getArgs().get(0);
                            if (camId != null && multiSpoofs.containsKey(camId) && arg instanceof CameraCharacteristics.Key) {
                                String keyName = ((CameraCharacteristics.Key<?>) arg).getName();
                                Map<String, Object> spoofs = multiSpoofs.get(camId);
                                if (spoofs != null && spoofs.containsKey(keyName)) return spoofs.get(keyName);
                            }
                            return chain.proceed();
                        });
                    }
                }
            } catch (Throwable t) { logWrite("Erro CameraCharacteristics: " + t.getMessage()); }
        }

        if (overrideResolutions) {
            try {
                Class<?> streamMapClass = Class.forName("android.hardware.camera2.params.StreamConfigurationMap", false, targetClassLoader);
                for (Method m : streamMapClass.getDeclaredMethods()) {
                    if (m.getName().equals("getOutputSizes") && m.getParameterTypes().length == 1) {
                        hook(m).intercept(chain -> {
                            if (chain.proceed() == null) return null; 
                            return new android.util.Size[] { new android.util.Size(4080, 3060), new android.util.Size(4000, 3000), new android.util.Size(3840, 2160), new android.util.Size(2560, 1440), new android.util.Size(1920, 1080), new android.util.Size(1280, 720), new android.util.Size(640, 480) };
                        });
                    }
                }
            } catch (Throwable t) { logWrite("Erro StreamMap: " + t.getMessage()); }
        }

        if (hideVpn) {
            try {
                Class<?> netCapClass = Class.forName("android.net.NetworkCapabilities", false, targetClassLoader);
                for (Method m : netCapClass.getDeclaredMethods()) {
                    if (m.getName().equals("hasTransport") && m.getParameterTypes().length == 1) {
                        hook(m).intercept(chain -> ((Integer) chain.getArgs().get(0) == 4) ? false : chain.proceed());
                    }
                }
            } catch (Throwable t) { logWrite("Erro VPN: " + t.getMessage()); }
        }
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        super.onPackageReady(param);
        currentPackageName = param.getPackageName();
        if (!eProcessoPrincipal(currentPackageName)) return;

        logWrite("--- INICIANDO VERIFICAÇÃO PARA: " + currentPackageName + " ---", true);

        File dexFile = new File("/data/data/" + currentPackageName + "/files/xposed/server.dex");
        if (!dexFile.exists()) {
            logWrite("ERRO CRÍTICO: server.dex NÃO ENCONTRADO em " + dexFile.getAbsolutePath());
            return;
        }

        if (dexFile.canWrite()) {
            logWrite("Aviso: server.dex com permissão de escrita. Convertendo para Somente Leitura...");
            dexFile.setReadOnly(); dexFile.setWritable(false, false);
        }

        logWrite("server.dex encontrado! Carregando classes...", true);

        try {
            File cacheDir = new File("/data/data/" + currentPackageName + "/cache");
            if (!cacheDir.exists()) cacheDir.mkdirs();
            DexClassLoader loader = new DexClassLoader(dexFile.getAbsolutePath(), cacheDir.getAbsolutePath(), null, MainModule.class.getClassLoader());
            Method startMethod = loader.loadClass("com.xposedmodule.payload.ServerPayload").getMethod("start", Object.class, String.class, ClassLoader.class);
            logWrite("Invocando método start() do Payload...", true);
            startMethod.invoke(null, this, currentPackageName, param.getClassLoader());
        } catch (Throwable t) { logWrite("ERRO FATAL AO EXECUTAR PAYLOAD: " + Log.getStackTraceString(t)); }
    }
}
