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
        // ------ SPOOF DO MODELO (S911B) ------
        String novoModelo = "SM-S911B";
        SPOOF_PROPS.put("ro.product.model", novoModelo);
        SPOOF_PROPS.put("ro.product.odm.model", novoModelo);
        SPOOF_PROPS.put("ro.product.system.model", novoModelo);
        SPOOF_PROPS.put("ro.product.system_ext.model", novoModelo);
        SPOOF_PROPS.put("ro.product.vendor.model", novoModelo);
        SPOOF_PROPS.put("ro.product.product.model", novoModelo);
        SPOOF_PROPS.put("ro.build.model", novoModelo);

        // ------ SPOOF DO DISPOSITIVO (dm1q) ------
        String novoDispositivo = "dm1q";
        SPOOF_PROPS.put("ro.product.device", novoDispositivo);
        SPOOF_PROPS.put("ro.product.odm.device", novoDispositivo);
        SPOOF_PROPS.put("ro.product.system.device", novoDispositivo);
        SPOOF_PROPS.put("ro.product.system_ext.device", novoDispositivo);
        SPOOF_PROPS.put("ro.product.vendor.device", novoDispositivo);
        SPOOF_PROPS.put("ro.product.product.device", novoDispositivo);

        // ------ SPOOF DO PRODUTO (NOME DO PROJETO) ------
        // (Isso tira o a52qub / qssi do seu log)
        SPOOF_PROPS.put("ro.product.name", novoDispositivo);
        SPOOF_PROPS.put("ro.product.odm.name", novoDispositivo);
        SPOOF_PROPS.put("ro.product.system.name", novoDispositivo);
        SPOOF_PROPS.put("ro.product.system_ext.name", novoDispositivo);
        SPOOF_PROPS.put("ro.product.vendor.name", novoDispositivo);
        SPOOF_PROPS.put("ro.product.product.name", novoDispositivo);
        SPOOF_PROPS.put("ro.build.product", novoDispositivo);
        
        // Se você quiser no futuro alterar a BOARD (chip), o local é esse:
        // SPOOF_PROPS.put("ro.board.platform", "kalama");
        // SPOOF_PROPS.put("ro.product.board", "kalama");
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        log(Log.INFO, "Xposed_Spoof", "Iniciando spoofing no app -> " + param.getPackageName());
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
                        
                        // Se a propriedade requisitada estiver no nosso Dicionário, entrega o valor falso IMEDIATAMENTE
                        if (SPOOF_PROPS.containsKey(key)) {
                            return SPOOF_PROPS.get(key);
                        }
                    }
                    
                    // Se não estiver no dicionário (como versão do android, id da build, etc), deixa o app ler do sistema
                    return chain.proceed();
                }
            };

            // Aplica os hooks interceptando as chamadas
            hook(getMethod1).intercept(hooker);
            hook(getMethod2).intercept(hooker);

        } catch (Exception e) {
            log(Log.ERROR, "Xposed_Spoof", "Erro no spoofing SystemProperties: " + e.getMessage());
        }
    }
}
