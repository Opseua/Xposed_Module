import android.os.Build;
import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;

public class MainModule extends XposedModule {

@Override
    public void onPackageLoaded(PackageLoadedParam param) {
        log(Log.INFO, "Xposed_Module", "Processo do app iniciado -> " + param.getPackageName());

        try {
            String[][] propriedades = {
                {"MANUFACTURER", "samsung"},
                {"BRAND", "samsung"},
                {"MODEL", "SM-S911B"},
                {"DEVICE", "dm1q"},
                {"PRODUCT", "dm1q"}
            };

            // Acessa a propriedade interna do Android que controla os modificadores
            java.lang.reflect.Field accessFlagsField = java.lang.reflect.Field.class.getDeclaredField("accessFlags");
            accessFlagsField.setAccessible(true);

            for (String[] prop : propriedades) {
                java.lang.reflect.Field field = android.os.Build.class.getDeclaredField(prop[0]);
                field.setAccessible(true);
                
                // Remove o bloqueio 'final' manipulando os bits
                int flags = accessFlagsField.getInt(field);
                accessFlagsField.setInt(field, flags & ~java.lang.reflect.Modifier.FINAL);
                
                // O set tradicional agora funciona
                field.set(null, prop[1]);
            }
        } catch (Exception e) {
            log(Log.ERROR, "Xposed_Module", "Erro no spoofing: " + e.getMessage());
        }
    }
    
}
