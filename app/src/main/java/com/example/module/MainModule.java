package com.example.module;

import android.hardware.camera2.CameraCharacteristics;
import android.util.Log;
import android.util.Range;
import android.util.SizeF;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Field;
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

        if ("com.android.systemui".equals(packageName)) {
            hookDisableShade(param, packageName);
            return;
        }

        spoofBuildProperties(packageName);
        hookSystemProperties(param, packageName);
        hookCameraCharacteristics(param, packageName);
    }

    /**
     * Bloqueia a expansão do shade (painel de notificações/quick settings),
     * sem desativar a status bar em si (hora, ícones, badges continuam normais).
     *
     * Alvo confirmado via strings do classes.dex desta build (Android 12):
     * com.android.systemui.statusbar.phone.NotificationPanelViewController
     * Métodos: expandNotificationPanel, instantExpandNotificationsPanel
     */
    private void hookDisableShade(PackageReadyParam param, String packageName) {
        try {
            Class<?> panelControllerClass = Class.forName(
                    "com.android.systemui.statusbar.phone.NotificationPanelViewController",
                    false,
                    param.getClassLoader()
            );

            int hookedCount = 0;
            for (Method method : panelControllerClass.getDeclaredMethods()) {
                String name = method.getName();
                if (name.equals("expandNotificationPanel")
                        || name.equals("instantExpandNotificationsPanel")
                        || name.equals("animateExpandNotificationsPanel")
                        || name.equals("animateExpandSettingsPanel")) {
                    hook(method).intercept(chain -> {
                        escreverLog(packageName, "SHADE_BLOCK: " + name + " interceptado, expansao bloqueada");
                        // não chama chain.proceed() -> bloqueia a expansão
                        return null;
                    });
                    hookedCount++;
                }
            }

            Log.i(TAG, "MODULO: hookDisableShade - " + hookedCount + " métodos hookados em NotificationPanelViewController");
            escreverLog(packageName, "SHADE_HOOK_INIT: " + hookedCount + " métodos hookados");
        } catch (Throwable t) {
            Log.e(TAG, "MODULO: erro ao hookar NotificationPanelViewController", t);
        }
    }

    private void spoofBuildProperties(String packageName) {
        try {
            aplicarElogarBuild(packageName, "MANUFACTURER", "samsung");
            aplicarElogarBuild(packageName, "BRAND", "samsung");
            aplicarElogarBuild(packageName, "MODEL", "SM-S911B");
            aplicarElogarBuild(packageName, "DEVICE", "dm1q");
            aplicarElogarBuild(packageName, "PRODUCT", "dm1q");
        } catch (Exception e) {
            Log.e(TAG, "MODULO: Erro ao alterar android.os.Build: " + e.getMessage());
        }
    }

    private void aplicarElogarBuild(String packageName, String nomeCampo, String novoValor) throws Exception {
        Field campo = android.os.Build.class.getDeclaredField(nomeCampo);
        campo.setAccessible(true);
        Object valorOriginal = campo.get(null);
        campo.set(null, novoValor);
        
        escreverLog(packageName, "BUILD_FIELD: " + nomeCampo + " | " + valorOriginal + " | " + novoValor);
    }

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
                        Object originalResult = chain.proceed();
                        Object finalResult = originalResult;
                        
                        if ("ro.product.manufacturer".equals(propKey) || "ro.product.brand".equals(propKey)) {
                            finalResult = "samsung";
                        } else if ("ro.product.model".equals(propKey)) {
                            finalResult = "SM-S911B";
                        } else if ("ro.product.device".equals(propKey) || "ro.product.name".equals(propKey)) {
                            finalResult = "dm1q";
                        } else if ("ro.miui.region".equals(propKey)) {
                            finalResult = "";
                        }

                        if (propKey != null && (propKey.startsWith("ro.") || propKey.startsWith("hw."))) {
                            escreverLog(packageName, "SYSTEM_PROP: " + propKey + " | " + originalResult + " | " + finalResult);
                        }
                        
                        return finalResult;
                    });
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "MODULO: erro ao hookar SystemProperties em " + packageName, t);
        }
    }

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
                            
                            Object originalResult = chain.proceed();
                            Object finalResult = originalResult;
                            
                            if ("android.sensor.info.physicalSize".equals(keyName)) {
                                finalResult = new SizeF(7.6f, 5.7f);
                            } else if ("android.lens.info.availableFocalLengths".equals(keyName)) {
                                finalResult = new float[] { 1.5f };
                            } else if ("android.sensor.info.timestampSource".equals(keyName)) {
                                finalResult = 1;
                            } else if ("android.control.zoomRatioRange".equals(keyName)) {
                                finalResult = new Range<>(0.5f, 10.0f);
                            } else if ("android.request.availableCapabilities".equals(keyName)) {
                                finalResult = new int[] {0, 1, 2, 3, 4, 5, 6, 8, 9, 11};
                            } else if ("android.control.aeAvailableTargetFpsRanges".equals(keyName)) {
                                @SuppressWarnings("unchecked")
                                Range<Integer>[] spoof = new Range[] {
                                    new Range<>(15, 30), new Range<>(30, 30)
                                };
                                finalResult = spoof;
                            }

                            escreverLog(packageName, "CAMERA_KEY: " + keyName + " | " + formatarValor(originalResult) + " | " + formatarValor(finalResult));
                            
                            return finalResult;
                        }

                        return chain.proceed();
                    });
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "MODULO: erro ao hookar CameraCharacteristics em " + packageName, t);
        }
    }

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

    private void escreverLog(String packageName, String dados) {
        String timestamp = new SimpleDateFormat("HH:mm:ss:SSS", Locale.getDefault()).format(new Date());
        String linhaLog = timestamp + ": " + dados + "\n";
        
        Log.i(TAG, linhaLog.trim());

        try {
            File pastaCache = new File("/data/user/0/" + packageName + "/cache");
            if (!pastaCache.exists()) {
                pastaCache.mkdirs();
            }

            File arquivoLog = new File(pastaCache, "camera_app_logs.txt");
            FileWriter writer = new FileWriter(arquivoLog, true);
            writer.append(linhaLog);
            writer.flush();
            writer.close();
        } catch (Exception e) {
            Log.e(TAG, "MODULO: Erro ao escrever txt em " + packageName + ": " + e.getMessage());
        }
    }
}
