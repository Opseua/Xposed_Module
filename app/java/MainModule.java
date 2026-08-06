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
    
    // Variáveis globais para não repetirmos código
    private static final String NOVO_MODELO = "SM-S911B";
    private static final String NOVO_DISPOSITIVO = "dm1q";
    private static final String NOVA_FINGERPRINT = "samsung/dm1q/dm1q:14/UP1A.231005.007/S911BXXU1AWBD:user/release-keys";
    private static final String NOVA_BUILD_ID = "UP1A.231005.007";
    private static final String NOVO_INCREMENTAL = "S911BXXU1AWBD";
    private static final String NOVA_BASEBAND = "S911BXXU1AWBD";
    private static final String NOVO_DISPLAY = "UP1A.231005.007.S911BXXU1AWBD";
    private static final String NOVO_CHIP = "kalama";

    static {
        // Modelo
        SPOOF_PROPS.put("ro.product.model", NOVO_MODELO);
        SPOOF_PROPS.put("ro.product.odm.model", NOVO_MODELO);
        SPOOF_PROPS.put("ro.product.system.model", NOVO_MODELO);
        SPOOF_PROPS.put("ro.product.system_ext.model", NOVO_MODELO);
        SPOOF_PROPS.put("ro.product.vendor.model", NOVO_MODELO);
        SPOOF_PROPS.put("ro.product.product.model", NOVO_MODELO);
        SPOOF_PROPS.put("ro.build.model", NOVO_MODELO);

        // Dispositivo / Produto
        SPOOF_PROPS.put("ro.product.device", NOVO_DISPOSITIVO);
        SPOOF_PROPS.put("ro.product.odm.device", NOVO_DISPOSITIVO);
        SPOOF_PROPS.put("ro.product.system.device", NOVO_DISPOSITIVO);
        SPOOF_PROPS.put("ro.product.system_ext.device", NOVO_DISPOSITIVO);
        SPOOF_PROPS.put("ro.product.vendor.device", NOVO_DISPOSITIVO);
        SPOOF_PROPS.put("ro.product.product.device", NOVO_DISPOSITIVO);
        
        SPOOF_PROPS.put("ro.product.name", NOVO_DISPOSITIVO);
        SPOOF_PROPS.put("ro.product.odm.name", NOVO_DISPOSITIVO);
        SPOOF_PROPS.put("ro.product.system.name", NOVO_DISPOSITIVO);
        SPOOF_PROPS.put("ro.product.system_ext.name", NOVO_DISPOSITIVO);
        SPOOF_PROPS.put("ro.product.vendor.name", NOVO_DISPOSITIVO);
        SPOOF_PROPS.put("ro.product.product.name", NOVO_DISPOSITIVO);
        SPOOF_PROPS.put("ro.build.product", NOVO_DISPOSITIVO);

        // Impressão Digital (Fingerprint)
        SPOOF_PROPS.put("ro.build.fingerprint", NOVA_FINGERPRINT);
        SPOOF_PROPS.put("ro.odm.build.fingerprint", NOVA_FINGERPRINT);
        SPOOF_PROPS.put("ro.product.build.fingerprint", NOVA_FINGERPRINT);
        SPOOF_PROPS.put("ro.system.build.fingerprint", NOVA_FINGERPRINT);
        SPOOF_PROPS.put("ro.system_ext.build.fingerprint", NOVA_FINGERPRINT);
        SPOOF_PROPS.put("ro.vendor.build.fingerprint", NOVA_FINGERPRINT);
        SPOOF_PROPS.put("ro.bootimage.build.fingerprint", NOVA_FINGERPRINT);
        SPOOF_PROPS.put("ro.build.description", "dm1q-user 14 UP1A.231005.007 S911BXXU1AWBD release-keys");

        // Builds ID / Display ID
        SPOOF_PROPS.put("ro.build.id", NOVA_BUILD_ID);
        SPOOF_PROPS.put("ro.product.build.id", NOVA_BUILD_ID);
        SPOOF_PROPS.put("ro.system.build.id", NOVA_BUILD_ID);
        SPOOF_PROPS.put("ro.system_ext.build.id", NOVA_BUILD_ID);
        SPOOF_PROPS.put("ro.build.version.incremental", NOVO_INCREMENTAL);
        SPOOF_PROPS.put("ro.build.display.id", NOVO_DISPLAY);

        // Hardware (Chip Qualcomm)
        SPOOF_PROPS.put("ro.hardware", NOVO_CHIP);
        SPOOF_PROPS.put("ro.board.platform", NOVO_CHIP);
        SPOOF_PROPS.put("ro.boot.hardware", NOVO_CHIP);
        
        // Baseband / Modem
        SPOOF_PROPS.put("gsm.version.baseband", NOVA_BASEBAND);
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        log(Log.INFO, "Xposed_Spoof", "Iniciando spoofing abrangente no app -> " + param.getPackageName());
        
        alterarClasseBuild();
        aplicarSpoofingSystemProperties();
        aplicarSpoofingUserAgent();
    }

    private void alterarClasseBuild() {
        try {
            Field accessFlagsField = Field.class.getDeclaredField("accessFlags");
            accessFlagsField.setAccessible(true);

            setBuildField("MODEL", NOVO_MODELO, accessFlagsField);
            setBuildField("DEVICE", NOVO_DISPOSITIVO, accessFlagsField);
            setBuildField("PRODUCT", NOVO_DISPOSITIVO, accessFlagsField);
            setBuildField("FINGERPRINT", NOVA_FINGERPRINT, accessFlagsField);
            setBuildField("HARDWARE", NOVO_CHIP, accessFlagsField);
            setBuildField("BOARD", NOVO_CHIP, accessFlagsField);
            setBuildField("DISPLAY", NOVO_DISPLAY, accessFlagsField);
            setBuildField("ID", NOVA_BUILD_ID, accessFlagsField);
            setBuildField("BOOTLOADER", NOVO_INCREMENTAL, accessFlagsField);
            
            // Falsifica também o método do modem presente na classe Build
            Method getRadioVersionMethod = Build.class.getDeclaredMethod("getRadioVersion");
            hook(getRadioVersionMethod).intercept(new XposedInterface.Hooker() {
                @Override
                public Object intercept(Chain chain) {
                    return NOVA_BASEBAND;
                }
            });

        } catch (Exception e) {
            log(Log.ERROR, "Xposed_Spoof", "Erro na alteração da classe Build: " + e.getMessage());
        }
    }

    private void setBuildField(String fieldName, String novoValor, Field accessFlagsField) {
        try {
            Field field = Build.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            int flags = accessFlagsField.getInt(field);
            accessFlagsField.setInt(field, flags & ~java.lang.reflect.Modifier.FINAL);
            field.set(null, novoValor);
        } catch (Exception e) {
            // Silencioso se uma variável específica não for achada
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
            log(Log.ERROR, "Xposed_Spoof", "Erro no spoofing de SystemProperties: " + e.getMessage());
        }
    }

    private void aplicarSpoofingUserAgent() {
        try {
            Method getPropertyMethod1 = System.class.getDeclaredMethod("getProperty", String.class);
            Method getPropertyMethod2 = System.class.getDeclaredMethod("getProperty", String.class, String.class);
            
            XposedInterface.Hooker userAgentHooker = new XposedInterface.Hooker() {
                @Override
                public Object intercept(Chain chain) throws Throwable {
                    List<Object> args = chain.getArgs();
                    Object result = chain.proceed();
                    
                    if (!args.isEmpty() && "http.agent".equals(args.get(0)) && result instanceof String) {
                        String realAgent = (String) result;
                        // Faz um replace no agente HTTP original substituindo o A52 pelo S911B
                        return realAgent.replace("SM-A525M", NOVO_MODELO);
                    }
                    return result;
                }
            };

            hook(getPropertyMethod1).intercept(userAgentHooker);
            hook(getPropertyMethod2).intercept(userAgentHooker);
            
        } catch (Exception e) {
            log(Log.ERROR, "Xposed_Spoof", "Erro no spoofing do UserAgent: " + e.getMessage());
        }
    }
}
