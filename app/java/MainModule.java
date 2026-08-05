import android.os.Build;
import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;

public class MainModule extends XposedModule {

    public MainModule(XposedInterface base, XposedModuleInterface.ModuleLoadedParam param) {
        super(base, param);
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        log(Log.INFO, "Xposed_Module", "Processo do app iniciado -> " + param.getPackageName());

        // 1. Spoofing da classe Build via accessFlags (como você já tem)
        aplicarSpoofingNaClasseBuild();

        // 2. Spoofing das propriedades do sistema (SystemProperties)
        aplicarSpoofingEmSystemProperties();
        
        // 3. Spoofing do User Agent (System.getProperty)
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

            java.lang.reflect.Field accessFlagsField = java.lang.reflect.Field.class.getDeclaredField("accessFlags");
            accessFlagsField.setAccessible(true);

            for (String[] prop : propriedades) {
                java.lang.reflect.Field field = android.os.Build.class.getDeclaredField(prop[0]);
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
            
            // Pega o método get(String key) e get(String key, String def)
            Method getMethod1 = systemPropertiesClass.getDeclaredMethod("get", String.class);
            Method getMethod2 = systemPropertiesClass.getDeclaredMethod("get", String.class, String.class);

            io.github.libxposed.api.XposedInterface.MethodHook propertyHook = new io.github.libxposed.api.XposedInterface.MethodHook() {
                @Override
                protected void afterInvocation(MethodHookParam param) throws Throwable {
                    String key = (String) param.args[0];
                    if (key == null) return;

                    if (key.contains("model")) {
                        param.setResult("SM-S911B");
                    } else if (key.contains("device") || key.contains("name")) {
                        // Cobre ro.product.name, ro.product.device, etc.
                        param.setResult("dm1q");
                    } else if (key.contains("fingerprint")) {
                        param.setResult("samsung/dm1q/dm1q:14/UP1A.231005.007/S911BXXU1AWBD:user/release-keys");
                    } else if (key.equals("ro.build.description")) {
                        param.setResult("dm1q-user 14 UP1A.231005.007 S911BXXU1AWBD release-keys");
                    } else if (key.contains("hardware") || key.equals("ro.board.platform")) {
                        // Altera o chip para corresponder ao S23 (Snapdragon 8 Gen 2 codename kalama)
                        param.setResult("kalama");
                    }
                }
            };

            hook(getMethod1, propertyHook);
            hook(getMethod2, propertyHook);

        } catch (Exception e) {
            log(Log.ERROR, "Xposed_Module", "Erro no spoofing SystemProperties: " + e.getMessage());
        }
    }

    private void aplicarSpoofingEmSystemGetProperty() {
        try {
            Method getPropertyMethod1 = System.class.getDeclaredMethod("getProperty", String.class);
            Method getPropertyMethod2 = System.class.getDeclaredMethod("getProperty", String.class, String.class);

            io.github.libxposed.api.XposedInterface.MethodHook userAgentHook = new io.github.libxposed.api.XposedInterface.MethodHook() {
                @Override
                protected void afterInvocation(MethodHookParam param) throws Throwable {
                    String key = (String) param.args[0];
                    if ("http.agent".equals(key)) {
                        String realAgent = (String) param.getResult();
                        if (realAgent != null && realAgent.contains("SM-A525M")) {
                            param.setResult(realAgent.replace("SM-A525M", "SM-S911B"));
                        }
                    }
                }
            };

            hook(getPropertyMethod1, userAgentHook);
            hook(getPropertyMethod2, userAgentHook);
            
        } catch (Exception e) {
            log(Log.ERROR, "Xposed_Module", "Erro no spoofing System.getProperty: " + e.getMessage());
        }
    }
}
