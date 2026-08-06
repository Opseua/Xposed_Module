import android.os.Build;
import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;

public class MainModule extends XposedModule {

    private static final Map<String, String> SPOOF_PROPS = new HashMap<>();
    
    // Valores globais para evitar repetição
    private static final String NOVO_MODELO = "SM-S911B";
    private static final String NOVO_DISPOSITIVO = "dm1q";

    static {
        // ------ SPOOF DO MODELO ------
        SPOOF_PROPS.put("ro.product.model", NOVO_MODELO);
        SPOOF_PROPS.put("ro.product.odm.model", NOVO_MODELO);
        SPOOF_PROPS.put("ro.product.system.model", NOVO_MODELO);
        SPOOF_PROPS.put("ro.product.system_ext.model", NOVO_MODELO);
        SPOOF_PROPS.put("ro.product.vendor.model", NOVO_MODELO);
        SPOOF_PROPS.put("ro.product.product.model", NOVO_MODELO);
        SPOOF_PROPS.put("ro.build.model", NOVO_MODELO);

        // ------ SPOOF DO DISPOSITIVO ------
        SPOOF_PROPS.put("ro.product.device", NOVO_DISPOSITIVO);
        SPOOF_PROPS.put("ro.product.odm.device", NOVO_DISPOSITIVO);
        SPOOF_PROPS.put("ro.product.system.device", NOVO_DISPOSITIVO);
        SPOOF_PROPS.put("ro.product.system_ext.device", NOVO_DISPOSITIVO);
        SPOOF_PROPS.put("ro.product.vendor.device", NOVO_DISPOSITIVO);
        SPOOF_PROPS.put("ro.product.product.device", NOVO_DISPOSITIVO);

        // ------ SPOOF DO PRODUTO ------
        SPOOF_PROPS.put("ro.product.name", NOVO_DISPOSITIVO);
        SPOOF_PROPS.put("ro.product.odm.name", NOVO_DISPOSITIVO);
        SPOOF_PROPS.put("ro.product.system.name", NOVO_DISPOSITIVO);
        SPOOF_PROPS.put("ro.product.system_ext.name", NOVO_DISPOSITIVO);
        SPOOF_PROPS.put("ro.product.vendor.name", NOVO_DISPOSITIVO);
        SPOOF_PROPS.put("ro.product.product.name", NOVO_DISPOSITIVO);
        SPOOF_PROPS.put("ro.build.product", NOVO_DISPOSITIVO);
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        log(Log.INFO, "Xposed_Spoof", "Iniciando spoofing completo no app -> " + param.getPackageName());
        
        // 1. Altera as variáveis estáticas da classe Java
        alterarClasseBuild();
        
        // 2. Intercepta as consultas de propriedades de baixo nível
        aplicarSpoofingSystemProperties();
    }

    private void alterarClasseBuild() {
        try {
            Field accessFlagsField = Field.class.getDeclaredField("accessFlags");
            accessFlagsField.setAccessible(true);

            // Substitui os valores fixos da classe Build
            setBuildField("MODEL", NOVO_MODELO, accessFlagsField);
            setBuildField("DEVICE", NOVO_DISPOSITIVO, accessFlagsField);
            setBuildField("PRODUCT", NOVO_DISPOSITIVO, accessFlagsField);
            
        } catch (Exception e) {
            log(Log.ERROR, "Xposed_Spoof", "Erro no accessFlags da classe Build: " + e.getMessage());
        }
    }

    private void setBuildField(String fieldName, String novoValor, Field accessFlagsField) {
        try {
            Field field = Build.class.getDeclaredField(fieldName);
            field.setAccessible(true);

            // Burlar restrição 'final'
            int flags = accessFlagsField.getInt(field);
            accessFlagsField.setInt(field, flags & ~java.lang.reflect.Modifier.FINAL);

            field.set(null, novoValor);
        } catch (Exception e) {
            log(Log.ERROR, "Xposed_Spoof", "Erro ao alterar Build." + fieldName + ": " + e.getMessage());
        }
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

            hook(getMethod1).intercept(hooker);
            hook(getMethod2).intercept(hooker);

        } catch (Exception e) {
            log(Log.ERROR, "Xposed_Spoof", "Erro no spoofing SystemProperties: " + e.getMessage());
        }
    }
}
