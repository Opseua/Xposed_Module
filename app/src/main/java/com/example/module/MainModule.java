package com.example.module;

import android.util.Log;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

public class MainModule extends XposedModule {

    private static final String TAG = "CameraWatcher";
    private static final String TARGET_PACKAGE = "de.weis.camera2probe";

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        super.onModuleLoaded(param);
        Log.i(TAG, "MÓDULO COMEÇOU A RODAR");
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        super.onPackageReady(param);

        if (!param.getPackageName().equals(TARGET_PACKAGE)) {
            return;
        }

        Log.i(TAG, "APP ALVO ABERTO: " + param.getPackageName());

        try {
            Class<?> cameraManagerClass = Class.forName(
                    "android.hardware.camera2.CameraManager",
                    false,
                    param.getClassLoader()
            );

            for (Method method : cameraManagerClass.getDeclaredMethods()) {
                if (method.getName().equals("openCamera")) {
                    hook(method).intercept(chain -> {
                        Object result = chain.proceed();
                        Log.i(TAG, "CÂMERA ABERTA NO APP ALVO (" + TARGET_PACKAGE + ")");
                        return result;
                    });
                }
            }

            Log.i(TAG, "Hook de CameraManager.openCamera registrado com sucesso em " + TARGET_PACKAGE);

        } catch (Throwable t) {
            Log.e(TAG, "ERRO ao registrar hook: " + t, t);
        }
    }
}
