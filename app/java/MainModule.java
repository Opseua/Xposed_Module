package seu.pacote.modulo;

import android.os.Build;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.regex.Pattern;

public class MainModule extends XposedModule {

    private static final Pattern BLOCKED_URL = Pattern.compile(
        "^https://figcbapp\\.blob\\.core\\.windows\\.net/data/_assets/task/.*\\.mp4.*$"
    );

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
            log(XposedInterface.LogLevel.ERROR, "MainModule", "Erro no spoofing: " + e.getMessage());
        }
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        // ---- Bloqueio de URL via OkHttp ----
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
                    String url = httpUrl.toString();

                    if (BLOCKED_URL.matcher(url).matches()) {
                        log(XposedInterface.LogLevel.INFO, "MainModule", "Bloqueado: " + url);
                        throw new RuntimeException("Blocked by module: " + url);
                    }

                    return request;
                });

        } catch (Throwable t) {
            log(XposedInterface.LogLevel.ERROR, "MainModule", "Erro no hook: " + t.getMessage());
        }
    }
}
