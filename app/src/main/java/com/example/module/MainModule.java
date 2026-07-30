package com.example.module;

import android.util.Log;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

public class MainModule implements IXposedHookLoadPackage {

    private static final String TAG = "CameraWatcher";
    private static final String TARGET_PACKAGE = "de.weis.camera2probe";

    static {
        log("MÓDULO COMEÇOU A RODAR");
    }

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {

        if (!lpparam.packageName.equals(TARGET_PACKAGE)) {
            return;
        }

        log("APP ALVO ABERTO: " + lpparam.packageName);

        try {
            findAndHookMethod(
                    "android.hardware.camera2.CameraManager",
                    lpparam.classLoader,
                    "openCamera",
                    "java.lang.String",
                    "android.hardware.camera2.CameraDevice$StateCallback",
                    "android.os.Handler",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            log("CÂMERA ABERTA NO APP ALVO (" + TARGET_PACKAGE + ")");
                        }
                    }
            );

            log("Hook de CameraManager.openCamera registrado com sucesso em " + TARGET_PACKAGE);

        } catch (Throwable t) {
            log("ERRO ao registrar hook: " + t);
        }
    }

    private static void log(String message) {
        String fullMessage = "[" + TAG + "] " + message;
        XposedBridge.log(fullMessage);
        Log.d(TAG, message);
    }
}
