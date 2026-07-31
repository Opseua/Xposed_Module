package com.example.module;

import android.hardware.camera2.CameraCharacteristics;
import android.util.Log;
import android.util.Range;
import android.util.SizeF;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

import io.github.libxposed.api.XposedModule;

public class MainModule extends XposedModule {

    private static final String TAG = "MODULO";

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        super.onModuleLoaded(param);
        Log.i(TAG, "MODULO: INICIOU");
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        super.onPackageReady(param);

        String packageName = param.getPackageName();
        Log.i(TAG, "MODULO: APP ALVO " + packageName + " INICIOU");

        hookSystemProperties(param, packageName);
        hookCameraCharacteristics(param, packageName);
    }

    // 📱 Hook para Spoof de Dispositivo (Galaxy S23)
    private void hookSystemProperties(PackageReadyParam param, String packageName) {
        try {
            Class<?> systemPropClass = Class.forName(
                    "android.os.SystemProperties",
                    false,
                    param.getClassLoader()
            );

            for (Method method : systemPropClass.getDeclaredMethods()) {
                if (method.getName().equals("get") && method.getParameterTypes().length >= 1) {
                    hook(method).intercept(chain -> {
                        String propKey = (String) chain.getArgs()[0];
                        Object result = chain.proceed();
                        
                        String spoofedResult = null;
                        
                        if ("ro.product.manufacturer".equals(propKey) || "ro.product.brand".equals(propKey)) {
                            spoofedResult = "samsung";
                        } else if ("ro.product.model".equals(propKey)) {
                            spoofedResult = "SM-S911B"; // Modelo do Galaxy S23 Global
                        } else if ("ro.product.device".equals(propKey) || "ro.product.name".equals(propKey)) {
                            spoofedResult = "dm1q"; // Codinome do Galaxy S23
                        } else if ("ro.miui.region".equals(propKey)) {
                            spoofedResult = ""; // Esconde a existência da MIUI
                        }

                        if (spoofedResult != null) {
                            escreverLog(packageName, "SYSTEM_PROP: " + propKey + " = " + result + " -> SPOOFADO PARA: " + spoofedResult);
                            return spoofedResult;
                        }
                        
                        return result;
                    });
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "MODULO: erro ao hookar SystemProperties em " + packageName, t);
        }
    }

    // 📸 Hook para capturar e alterar a Câmera
    private void hookCameraCharacteristics(PackageReadyParam param, String packageName) {
        try {
            Class<?> cameraCharacteristicsClass = Class.forName(
                    "android.hardware.camera2.CameraCharacteristics",
                    false,
                    param.getClassLoader()
            );

            for (Method method : cameraCharacteristicsClass.getDeclaredMethods()) {
                if (method.getName().equals("get") && method.getParameterTypes().length == 1) {
                    hook(method).intercept(chain -> {
                        Object arg = chain.getArgs()[0];

                        if (arg instanceof CameraCharacteristics.Key) {
                            CameraCharacteristics.Key<?> key = (CameraCharacteristics.Key<?>) arg;
                            String keyName = key.getName();
                            
                            Object result = chain.proceed();
                            
                            // 🚀 INÍCIO DO SPOOFING PARA EGO_CAPTURE
                            
                            // 1. Simula uma câmera Ultra-Wide forçando um FOV de ~144°
                            if ("android.sensor.info.physicalSize".equals(keyName)) {
                                SizeF spoof = new SizeF(7.6f, 5.7f); // Sensor grande
                                escreverLog(packageName, "CAMERA: " + keyName + " -> " + spoof);
                                return spoof;
                            }
                            
                            if ("android.lens.info.availableFocalLengths".equals(keyName)) {
                                float[] spoof = new float[] { 1.5f }; // Lente super curta
                                escreverLog(packageName, "CAMERA: " + keyName + " -> " + Arrays.toString(spoof));
                                return spoof;
                            }

                            // 2. Força o Timestamp Source para REALTIME (1)
                            if ("android.sensor.info.timestampSource".equals(keyName)) {
                                Integer spoof = 1;
                                escreverLog(packageName, "CAMERA: " + keyName + " -> " + spoof);
                                return spoof;
                            }

                            // 3. Zoom padrão
                            if ("android.control.zoomRatioRange".equals(keyName)) {
                                Range<Float> spoof = new Range<>(0.5f, 10.0f);
                                escreverLog(packageName, "CAMERA: " + keyName + " -> " + spoof);
                                return spoof;
                            }
                            
                            // Capacidades premium de câmera
                            if ("android.request.availableCapabilities".equals(keyName)) {
                                int[] spoof = new int[] {0, 1, 2, 3, 4, 5, 6, 8, 9, 11};
                                escreverLog(packageName, "CAMERA: " + keyName + " -> " + Arrays.toString(spoof));
                                return spoof;
                            }
                            
                            // Suporte a 30 FPS rígido conforme exigido pelo app
                            if ("android.control.aeAvailableTargetFpsRanges".equals(keyName)) {
                                @SuppressWarnings("unchecked")
                                Range<Integer>[] spoof = new Range[] {
                                    new Range<>(15, 30), new Range<>(30, 30)
                                };
                                escreverLog(packageName, "CAMERA: " + keyName + " -> " + Arrays.toString(spoof));
                                return spoof;
                            }

                            return result;
                        }

                        return chain.proceed();
                    });
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "MODULO: erro ao hookar CameraCharacteristics em " + packageName, t);
        }
    }

    // 💾 Função de escrita de arquivo e Logcat
    private void escreverLog(String packageName, String consulta) {
        String timestamp = new SimpleDateFormat("HH:mm:ss:SSS", Locale.getDefault()).format(new Date());
        String linhaLog = timestamp + " ___" + packageName + "___ " + consulta + "\n";
        
        Log.i(TAG, linhaLog.trim());

        try {
            // Tenta forçar de forma absoluta a pasta de cache do aplicativo
            File pastaCache = new File("/data/user/0/" + packageName + "/cache");
            if (!pastaCache.exists()) {
                pastaCache.mkdirs(); // Cria se não existir (evita erro em apps limpos)
            }

            File arquivoLog = new File(pastaCache, "camera_app_logs.txt");
            FileWriter writer = new FileWriter(arquivoLog, true);
            writer.append(linhaLog);
            writer.flush();
            writer.close();
        } catch (Exception e) {
            Log.e(TAG, "MODULO: Erro ao escrever txt (Possível processo webview/isolado): " + e.getMessage());
        }
    }
}
