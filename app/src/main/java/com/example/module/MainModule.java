package com.example.module;

import android.hardware.camera2.CameraCharacteristics;
import android.util.Log;
import android.util.Range;

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
        Log.i(TAG, "MODULO: APP ALVO " + packageName + " INICIOU");

        try {
            Class<?> cameraCharacteristicsClass = Class.forName(
                    "android.hardware.camera2.CameraCharacteristics",
                    false,
                    param.getClassLoader()
            );

            for (Method method : cameraCharacteristicsClass.getDeclaredMethods()) {
                // Filtramos o método get() que recebe exatamente 1 argumento (a chave)
                if (method.getName().equals("get") && method.getParameterTypes().length == 1) {
                    hook(method).intercept(chain -> {
                        // Resgata o primeiro argumento passado para o método get()
                        // Nota: Dependendo da versão do LibXposed, pode ser necessário usar chain.args()[0] ou chain.args[0]
                        Object arg = chain.getArgs()[0]; 

                        if (arg instanceof CameraCharacteristics.Key) {
                            CameraCharacteristics.Key<?> key = (CameraCharacteristics.Key<?>) arg;
                            
                            // Verifica se o nome da chave é o que queremos interceptar
                            if ("android.control.zoomRatioRange".equals(key.getName())) {
                                Log.i(TAG, "📸 [Spoof] Alterando Zoom para 0.5 - 8.0 no pacote: " + packageName);
                                return new Range<>(0.5f, 8.0f);
                            }
                        }

                        // Para qualquer outra chave, executamos e retornamos o método original
                        return chain.proceed();
                    });
                }
            }

        } catch (Throwable t) {
            Log.e(TAG, "MODULO: erro ao hookar em " + packageName + ": " + t, t);
        }
    }
}
