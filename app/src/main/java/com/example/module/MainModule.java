package com.example.module;

import android.hardware.camera2.CameraCharacteristics;
import android.os.Environment;
import android.util.Log;
import android.util.Range;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
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

    // 📸 Hook para capturar tudo da Câmera
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
                            
                            escreverLog(packageName, "CAMERA_KEY: " + keyName);
                            
                            if ("android.control.zoomRatioRange".equals(keyName)) {
                                return new Range<>(0.5f, 8.0f);
                            }
                        }

                        return chain.proceed();
                    });
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "MODULO: erro ao hookar CameraCharacteristics em " + packageName, t);
        }
    }

    // 📱 Hook para capturar consultas de modelo/fabricante do aparelho
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
                        
                        // Ignora logs muito ruidosos e foca nos importantes do sistema
                        if (propKey != null && (propKey.startsWith("ro.") || propKey.startsWith("hw."))) {
                            escreverLog(packageName, "SYSTEM_PROP: " + propKey);
                        }
                        
                        return chain.proceed();
                    });
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "MODULO: erro ao hookar SystemProperties em " + packageName, t);
        }
    }

    // 💾 Função de escrita de arquivo e Logcat
    private void escreverLog(String packageName, String consulta) {
        String timestamp = new SimpleDateFormat("HH:mm:ss:SSS", Locale.getDefault()).format(new Date());
        String linhaLog = timestamp + " ___" + packageName + "___ CONSULTOU " + consulta + "\n";
        
        Log.i(TAG, linhaLog.trim());

        try {
            // Busca o diretório temporário nativo do aplicativo alvo
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
