package seu.pacote.modulo;

import android.app.Application;
import android.os.Build;
import android.util.Log;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;

public class MainModule extends XposedModule {

    private void writeToCache(String text) {
        try {
            // Usa reflexão para acessar o ActivityThread (evita erro de compilação)
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Method currentApplicationMethod = activityThreadClass.getDeclaredMethod("currentApplication");
            Application app = (Application) currentApplicationMethod.invoke(null);

            if (app == null) {
                Log.e("URL_MONITOR", "Contexto nulo, não foi possível acessar o cache.");
                return;
            }

            // Usa o diretório oficial de cache do app
            File cacheDir = app.getCacheDir();
            File file = new File(cacheDir, "urls_interceptadas.txt");
            
            if (!file.exists()) {
                file.createNewFile();
            }
            
            FileWriter fw = new FileWriter(file, true);
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write(text);
            bw.newLine();
            bw.close();
        } catch (Exception e) {
            Log.e("URL_MONITOR", "Erro ao gravar no TXT: " + e.getMessage());
        }
    }

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
        
        // 1. Hook Global Nativo
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
                                writeToCache("NATIVO: " + urlObj.toString());
                            }
                        } catch (Exception e) {
                            // Ignora erros internos
                        }
                    }
                    return connection;
                });
        } catch (Throwable t) {
            Log.e("URL_MONITOR", "Erro no hook nativo: " + t.getMessage());
        }

        // 2. Hook OkHttp
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

                    writeToCache("OKHTTP: " + httpUrl.toString());

                    return request;
                });
        } catch (ClassNotFoundException e) {
            writeToCache("AVISO: OkHttp não encontrado ou ofuscado no app.");
        } catch (Throwable t) {
            Log.e("URL_MONITOR", "Erro no hook OkHttp: " + t.getMessage());
        }
    }
}
