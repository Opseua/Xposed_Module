package com.example.module;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

public class MainModule extends XposedModule {

    private static final String TAG = "CameraWatcher";
    private static final String TARGET_PACKAGE = "de.weis.camera2probe";

    public MainModule(XposedInterface base, ModuleLoadedParam param) {
        super(base, param);
        log(TAG + ": MÓDULO COMEÇOU A RODAR");
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        super.onPackageLoaded(param);

        if (!param.getPackageName().equals(TARGET_PACKAGE)) {
            return;
        }

        log(TAG + ": APP ALVO ABERTO: " + param.getPackageName());

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
                        log(TAG + ": CÂMERA ABERTA NO APP ALVO (" + TARGET_PACKAGE + ")");
                        return result;
                    });
                }
            }

            log(TAG + ": Hook de CameraManager.openCamera registrado com sucesso em " + TARGET_PACKAGE);

        } catch (Throwable t) {
            log(TAG + ": ERRO ao registrar hook: " + t);
        }
    }
}
