package seu.pacote.modulo;

import android.os.Build;
import android.util.Log;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;

public class MainModule extends XposedModule {

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        // ---- Spoofing do Build.MODEL ----
        try {
            Field accessFlags = Field.class.getDeclaredField("accessFlags");
            accessFlags.setAccessible(true);

            Field field = Build.class.getDeclaredField("MODEL");
            field.setAccessible(true);

            accessFlags.setInt(field, accessFlags.getInt(field) & ~Modifier.FINAL);
            field.set(null, "SM-S911B");
        } catch (Exception e) {
            log(Log.ERROR, "MainModule", "Erro no spoofing: " + e.getMessage(), e);
        }
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        
        // 1. Hook Global Nativo (Captura quase tudo)
        try {
            Method openConnectionMethod = URL.class.getDeclaredMethod("openConnection");

            hook(openConnectionMethod)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object connection = chain.proceed();

                    if (connection != null) {
                        try {
                            Method getURLMethod = connection.getClass().getMethod("getURL");
                            Object urlObj = getURLMethod.invoke(connection);
                            if (urlObj != null) {
                                // Usa Log.e (Error) para o texto ficar VERMELHO e fácil de achar
                                Log.e("URL_MONITOR", "🔗 NATIVO: " + urlObj.toString());
                            }
                        } catch (Exception e) {
                            // Ignora erros internos de reflexão
                        }
                    }
                    return connection;
                });
        } catch (Throwable t) {
            Log.e("URL_MONITOR", "Erro no hook nativo: " + t.getMessage());
        }

        // 2. Hook OkHttp (Para descobrir se está ofuscado)
        try {
            ClassLoader cl = param.getClassLoader();
            Class<?> builderClass = cl.loadClass("okhttp3.Request$Builder");
            Method buildMethod = builderClass.getDeclaredMethod("build");

            hook(buildMethod)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object request = chain.proceed();
                    Method urlMethod = request.getClass().getMethod("url");
                    Object httpUrl = urlMethod.invoke(request);

                    Log.e("URL_MONITOR", "🌐 OKHTTP: " + httpUrl.toString());

                    return request;
                });
        } catch (ClassNotFoundException e) {
            Log.e("URL_MONITOR", "⚠️ OkHttp NÃO ENCONTRADO (Pode estar ofuscado ou o app não usa)");
        } catch (Throwable t) {
            Log.e("URL_MONITOR", "Erro no hook OkHttp: " + t.getMessage());
        }
    }
}
