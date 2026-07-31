package com.example.module;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

public class MainModule extends XposedModule {

    private static final String TAG = "CameraWatcher";
    private static final String TARGET_PACKAGE = "de.weis.camera2probe";

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        super.onModuleLoaded(param);
        log(XposedInterface.LOG_PRIORITY_INFO, TAG, "MÓDULO COMEÇOU A RODAR");
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        super.onPackageReady(param);

        if (!param.getPackageName().equals(TARGET_PACKAGE)) {
            return;
        }

        log(XposedInterface.LOG_PRIORITY_INFO, TAG, "APP ALVO ABERTO: " + param.getPackageName());

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
                        log(XposedInterface.LOG_PRIORITY_INFO, TAG,
                                "CÂMERA ABERTA NO APP ALVO (" + TARGET_PACKAGE + ")");
                        return result;
                    });
                }
            }

            log(XposedInterface.LOG_PRIORITY_INFO, TAG,
                    "Hook de CameraManager.openCamera registrado com sucesso em " + TARGET_PACKAGE);

        } catch (Throwable t) {
            log(XposedInterface.LOG_PRIORITY_ERROR, TAG, "ERRO ao registrar hook: " + t, t);
        }
    }
}
