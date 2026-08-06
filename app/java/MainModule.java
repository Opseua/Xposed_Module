import android.util.Log;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;

public class MainModule extends XposedModule {

    private static final Map<String, String> SPOOF_PROPS = new HashMap<>();

    static {
        SPOOF_PROPS.put("ro.product.system.model", "SM-S911B");
        SPOOF_PROPS.put("ro.product.system.device", "dm1q");
        SPOOF_PROPS.put("ro.product.system.name", "dm1q");
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        log(Log.INFO, "Xposed_Module", "App aberto -> " + param.getPackageName());
        aplicarSpoofingSystemProperties();
    }

    private void aplicarSpoofingSystemProperties() {
        try {
            Class<?> sysPropsClass = Class.forName("android.os.SystemProperties");
            Method getMethod1 = sysPropsClass.getDeclaredMethod("get", String.class);
            Method getMethod2 = sysPropsClass.getDeclaredMethod("get", String.class, String.class);

            XposedInterface.Hooker hooker = new XposedInterface.Hooker() {
                @Override
                public Object intercept(Chain chain) throws Throwable {
                    List<Object> args = chain.getArgs(); 
                    if (!args.isEmpty() && args.get(0) instanceof String) {
                        String key = (String) args.get(0);
                        if (SPOOF_PROPS.containsKey(key)) {
                            return SPOOF_PROPS.get(key);
                        }
                    }
                    return chain.proceed();
                }
            };

            // Sintaxe correta usando encadeamento
            hook(getMethod1).intercept(hooker);
            hook(getMethod2).intercept(hooker);

        } catch (Exception e) {
            log(Log.ERROR, "Xposed_Module", "Erro no spoofing SystemProperties: " + e.getMessage());
        }
    }
}
