import android.os.Build;
import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;

public class MainModule extends XposedModule {

    // Construtor removido para usar o padrão sem argumentos exigido pela sua build.

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        log(Log.INFO, "Xposed_Module", "Processo do app iniciado -> " + param.getPackageName());

        aplicarSpoofingNaClasseBuild();
        aplicarSpoofingEmSystemProperties();
        aplicarSpoofingEmSystemGetProperty();
    }

    private void aplicarSpoofingNaClasseBuild() {
        try {
            String[][] propriedades = {
                {"MANUFACTURER", "samsung"},
                {"BRAND", "samsung"},
                {"MODEL", "SM-S911B"},
                {"DEVICE", "dm1q"},
                {"PRODUCT", "dm1q"}
            };

            Field accessFlagsField = Field.class.getDeclaredField("accessFlags");
            accessFlagsField.setAccessible(true);

            for (String[] prop : propriedades) {
                Field field = Build.class.getDeclaredField(prop[0]);
                field.setAccessible(true);
                
                int flags = accessFlagsField.getInt(field);
                accessFlagsField.setInt(field, flags & ~java.lang.reflect.Modifier.FINAL);
                field.set(null, prop[1]);
            }
        } catch (Exception e) {
            log(Log.ERROR, "Xposed_Module", "Erro no spoofing Build: " + e.getMessage());
        }
    }

    private void aplicarSpoofingEmSystemProperties() {
        try {
            Class<?> systemPropertiesClass = Class.forName("android.os.SystemProperties");
            
            Method getMethod1 = systemPropertiesClass.getDeclaredMethod("get", String.class);
            Method getMethod2 = systemPropertiesClass.getDeclaredMethod("get", String.class, String.class);

            XposedInterface.Hooker systemPropHooker = new XposedInterface.Hooker() {
                @Override
                public Object intercept(Chain chain) throws Throwable {
                    Object[] args = chain.getArgs();
                    if (args.length > 0 && args[0] instanceof String) {
                        String key = (String) args[0];
                        
                        if (key.contains("model")) return "SM-S911B";
                        if (key.contains("device") || key.contains("name")) return "dm1q";
                        if (key.contains("fingerprint")) return "samsung/dm1q/dm1q:14/UP1A.231005.007/S911BXXU1AWBD:user/release-keys";
                        if (key.equals("ro.build.description")) return "dm1q-user 14 UP1A.231005.007 S911BXXU1AWBD release-keys";
                        if (key.contains("hardware") || key.equals("ro.board.platform")) return "kalama";
                    }
                    // Retorna o valor original para propriedades que não estamos alterando
                    return chain.proceed();
                }
            };

            hook(getMethod1, systemPropHooker);
            hook(getMethod2, systemPropHooker);

        } catch (Exception e) {
            log(Log.ERROR, "Xposed_Module", "Erro no spoofing SystemProperties: " + e.getMessage());
        }
    }

    private void aplicarSpoofingEmSystemGetProperty() {
        try {
            Method getPropertyMethod1 = System.class.getDeclaredMethod("getProperty", String.class);
            Method getPropertyMethod2 = System.class.getDeclaredMethod("getProperty", String.class, String.class);

            XposedInterface.Hooker userAgentHooker = new XposedInterface.Hooker() {
                @Override
                public Object intercept(Chain chain) throws Throwable {
                    Object[] args = chain.getArgs();
                    
                    // Executa o método original primeiro para obter o valor real
                    Object result = chain.proceed();
                    
                    if (args.length > 0 && "http.agent".equals(args[0]) && result instanceof String) {
                        String realAgent = (String) result;
                        if (realAgent.contains("SM-A525M")) {
                            return realAgent.replace("SM-A525M", "SM-S911B");
                        }
                    }
                    return result;
                }
            };

            hook(getPropertyMethod1, userAgentHooker);
            hook(getPropertyMethod2, userAgentHooker);
            
        } catch (Exception e) {
            log(Log.ERROR, "Xposed_Module", "Erro no spoofing System.getProperty: " + e.getMessage());
        }
    }
}
