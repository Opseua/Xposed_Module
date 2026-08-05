import android.os.Build;
import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;
import io.github.libxposed.api.XposedInterface.HookParam;

public class MainModule extends XposedModule {

    public MainModule(XposedInterface base, XposedModuleInterface.ModuleLoadedParam param) {
        super(base, param);
    }

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

            // No libxposed passamos a referência da classe que contém os métodos estáticos do hook
            hook(getMethod1, SystemPropHook.class);
            hook(getMethod2, SystemPropHook.class);

        } catch (Exception e) {
            log(Log.ERROR, "Xposed_Module", "Erro no spoofing SystemProperties: " + e.getMessage());
        }
    }

    private void aplicarSpoofingEmSystemGetProperty() {
        try {
            Method getPropertyMethod1 = System.class.getDeclaredMethod("getProperty", String.class);
            Method getPropertyMethod2 = System.class.getDeclaredMethod("getProperty", String.class, String.class);

            hook(getPropertyMethod1, UserAgentHook.class);
            hook(getPropertyMethod2, UserAgentHook.class);
            
        } catch (Exception e) {
            log(Log.ERROR, "Xposed_Module", "Erro no spoofing System.getProperty: " + e.getMessage());
        }
    }

    // 🛠️ Classes Hooker exigidas pela arquitetura do libxposed
    
    public static class SystemPropHook implements XposedInterface.Hooker {
        public static void afterInvocation(HookParam param) {
            String key = (String) param.args[0];
            if (key == null) return;

            if (key.contains("model")) {
                param.setResult("SM-S911B");
            } else if (key.contains("device") || key.contains("name")) {
                param.setResult("dm1q");
            } else if (key.contains("fingerprint")) {
                param.setResult("samsung/dm1q/dm1q:14/UP1A.231005.007/S911BXXU1AWBD:user/release-keys");
            } else if (key.equals("ro.build.description")) {
                param.setResult("dm1q-user 14 UP1A.231005.007 S911BXXU1AWBD release-keys");
            } else if (key.contains("hardware") || key.equals("ro.board.platform")) {
                param.setResult("kalama");
            }
        }
    }

    public static class UserAgentHook implements XposedInterface.Hooker {
        public static void afterInvocation(HookParam param) {
            String key = (String) param.args[0];
            if ("http.agent".equals(key)) {
                String realAgent = (String) param.getResult();
                if (realAgent != null && realAgent.contains("SM-A525M")) {
                    param.setResult(realAgent.replace("SM-A525M", "SM-S911B"));
                }
            }
        }
    }
}
