package com.example.module;

import android.util.Log;

import java.lang.reflect.Method;

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

        Log.i(TAG, "MODULO: APP ALVO " + packageName + " ABERTO");

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
                        Log.i(TAG, "MODULO: APP ALVO " + packageName + " USOU A CAMERA");
                        return result;
                    });
                }
            }

        } catch (Throwable t) {
            Log.e(TAG, "MODULO: erro ao hookar em " + packageName + ": " + t, t);
        }

        try {
            Class<?> activityThreadClass = Class.forName(
                    "android.app.ActivityThread",
                    false,
                    param.getClassLoader()
            );

            for (Method method : activityThreadClass.getDeclaredMethods()) {
                if (method.getName().equals("handleDestroyActivity")
                        || method.getName().equals("performDestroyActivity")) {
                    hook(method).intercept(chain -> {
                        Object result = chain.proceed();
                        Log.i(TAG, "MODULO: APP ALVO " + packageName + " FECHADO");
                        return result;
                    });
                }
            }

        } catch (Throwable t) {
            Log.e(TAG, "MODULO: erro ao hookar fechamento em " + packageName + ": " + t, t);
        }
    }
}
