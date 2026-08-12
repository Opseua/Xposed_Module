package com.xposedmodule.module;

import android.hardware.camera2.CameraCharacteristics;
import android.os.Environment;
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

        hookCameraCharacteristics(param, packageName);
        hookSystemProperties(param, packageName);
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
                        Object arg = chain.getArgs().get(0);

                        if (arg instanceof CameraCharacteristics.Key) {
                            CameraCharacteristics.Key<?> key = (CameraCharacteristics.Key<?>) arg;
                            String keyName = key.getName();
                            
                            Object result = chain.proceed();
                            
                            // 🚀 INÍCIO DO SPOOFING DE CÂMERA (Galaxy S21)
                            
                            if ("android.control.zoomRatioRange".equals(keyName)) {
                                Range<Float> spoof = new Range<>(0.5f, 10.0f);
                                escreverLog(packageName, "CAMERA_KEY: " + keyName + " = " + formatarValor(result) + " -> SPOOF: " + spoof);
                                return spoof;
                            }
                            
                            if ("android.request.availableCapabilities".equals(keyName)) {
                                // Adiciona capacidades Premium (11 = Múltiplas Câmeras, 3 = RAW)
                                int[] spoof = new int[] {0, 1, 2, 3, 4, 5, 6, 8, 9, 11};
                                escreverLog(packageName, "CAMERA_KEY: " + keyName + " = " + formatarValor(result) + " -> SPOOF: " + formatarValor(spoof));
                                return spoof;
                            }
                            
                            if ("android.control.aeAvailableTargetFpsRanges".equals(keyName)) {
                                // Suporte a 60 FPS
                                @SuppressWarnings("unchecked")
                                Range<Integer>[] spoof = new Range[] {
                                    new Range<>(15, 30), new Range<>(30, 30), new Range<>(30, 60), new Range<>(60, 60)
                                };
                                escreverLog(packageName, "CAMERA_KEY: " + keyName + " = " + formatarValor(result) + " -> SPOOF: " + formatarValor(spoof));
                                return spoof;
                            }
                            
                            if ("android.sensor.info.physicalSize".equals(keyName)) {
                                // Tamanho de um sensor grande topo de linha (aprox. 1/1.7 polegadas)
                                SizeF spoof = new SizeF(7.6f, 5.7f);
                                escreverLog(packageName, "CAMERA_KEY: " + keyName + " = " + formatarValor(result) + " -> SPOOF: " + spoof);
                                return spoof;
                            }
                            
                            if ("android.lens.info.availableFocalLengths".equals(keyName)) {
                                // Múltiplas distâncias focais (Ultrawide, Wide, Telephoto)
                                float[] spoof = new float[] {1.8f, 5.4f, 7.1f};
                                escreverLog(packageName, "CAMERA_KEY: " + keyName + " = " + formatarValor(result) + " -> SPOOF: " + formatarValor(spoof));
                                return spoof;
                            }

                            // Loga o valor original caso não tenha sido falsificado
                            escreverLog(packageName, "CAMERA_KEY: " + keyName + " = " + formatarValor(result));
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

    // 📱 Hook para alterar modelo/fabricante do aparelho (Galaxy S21)
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
                        String propKey = (String) chain.getArgs().get(0);
                        Object result = chain.proceed();
                        
                        // 🚀 INÍCIO DO SPOOFING DE APARELHO
                        String spoofedResult = null;
                        
                        if ("ro.product.manufacturer".equals(propKey) || "ro.product.brand".equals(propKey)) {
                            spoofedResult = "samsung";
                        } else if ("ro.product.model".equals(propKey)) {
                            spoofedResult = "SM-G991B"; // Modelo do S21 Global 5G
                        } else if ("ro.product.device".equals(propKey) || "ro.product.name".equals(propKey)) {
                            spoofedResult = "o1s"; // Codinome do S21
                        } else if ("ro.miui.region".equals(propKey)) {
                            spoofedResult = ""; // Esconde a existência da MIUI
                        }

                        // Se falsificamos, retornamos o novo valor
                        if (spoofedResult != null) {
                            escreverLog(packageName, "SYSTEM_PROP: " + propKey + " = " + result + " -> SPOOF: " + spoofedResult);
                            return spoofedResult;
                        }
                        
                        // Log normal para as outras propriedades
                        if (propKey != null && (propKey.startsWith("ro.") || propKey.startsWith("hw."))) {
                            escreverLog(packageName, "SYSTEM_PROP: " + propKey + " = " + result);
                        }
                        
                        return result;
                    });
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "MODULO: erro ao hookar SystemProperties em " + packageName, t);
        }
    }

    // 🛠️ Converte os arrays da câmera em texto legível para o log
    private String formatarValor(Object obj) {
        if (obj == null) return "null";
        if (!obj.getClass().isArray()) return obj.toString();

        if (obj instanceof int[]) return Arrays.toString((int[]) obj);
        if (obj instanceof float[]) return Arrays.toString((float[]) obj);
        if (obj instanceof double[]) return Arrays.toString((double[]) obj);
        if (obj instanceof long[]) return Arrays.toString((long[]) obj);
        if (obj instanceof byte[]) return Arrays.toString((byte[]) obj);
        if (obj instanceof boolean[]) return Arrays.toString((boolean[]) obj);
        if (obj instanceof char[]) return Arrays.toString((char[]) obj);
        if (obj instanceof short[]) return Arrays.toString((short[]) obj);
        if (obj instanceof Range[]) return Arrays.toString((Range[]) obj);

        return Arrays.deepToString((Object[]) obj);
    }

    // 💾 Função de escrita de arquivo e Logcat
    private void escreverLog(String packageName, String consulta) {
        String timestamp = new SimpleDateFormat("HH:mm:ss:SSS", Locale.getDefault()).format(new Date());
        String linhaLog = timestamp + " ___" + packageName + "___ CONSULTOU " + consulta + "\n";
        
        Log.i(TAG, linhaLog.trim());

        try {
            String tempDir = System.getProperty("java.io.tmpdir");
            if (tempDir != null) {
                File arquivoLog = new File(tempDir, "camera_app_logs.txt");
                FileWriter writer = new FileWriter(arquivoLog, true);
                writer.append(linhaLog);
                writer.flush();
                writer.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "MODULO: Erro ao escrever no arquivo txt temporário: " + e.getMessage());
        }
    }
}
