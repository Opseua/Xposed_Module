
import android.util.Log;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;

public class MainModule extends XposedModule {

    // Dicionário centralizado para spoofing
    private static final Map<String, String> SPOOF_PROPS = new HashMap<>();

    static {
        // ------ SPOOF: [ro.product.system] ------
        SPOOF_PROPS.put("ro.product.system.model", "SM-S911B");
        SPOOF_PROPS.put("ro.product.system.device", "dm1q");
        SPOOF_PROPS.put("ro.product.system.name", "dm1q");

        // Descomente caso queira alterar marca e fabricante também
        // SPOOF_PROPS.put("ro.product.system.brand", "samsung");
        // SPOOF_PROPS.put("ro.product.system.manufacturer", "samsung");
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        log(Log.INFO, "Xposed_Module", "App aberto -> " + param.getPackageName());

        // Focamos diretamente na raiz (SystemProperties) que é o método mais confiável
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
                    // Correção: no seu libxposed, getArgs() retorna uma List<Object>
                    List<Object> args = chain.getArgs();

                    if (!args.isEmpty() && args.get(0) instanceof String) {
                        String key = (String) args.get(0);

                        // Se a propriedade requisitada estiver no nosso Dicionário, entrega o valor falso
                        if (SPOOF_PROPS.containsKey(key)) {
                            return SPOOF_PROPS.get(key);
                        }
                    }

                    // Se não estiver no dicionário (ex: ro.build.version), deixa o sistema responder normalmente
                    return chain.proceed();
                }
            };

            // Aplica os hooks
            hook(getMethod1, hooker);
            hook(getMethod2, hooker);

        } catch (Exception e) {
            log(Log.ERROR, "Xposed_Module", "Erro no spoofing SystemProperties: " + e.getMessage());
        }
    }
}
