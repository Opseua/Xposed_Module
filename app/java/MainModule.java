import android.os.Build;
import android.util.Log;
import java.lang.reflect.Field;
import sun.misc.Unsafe;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;

public class MainModule extends XposedModule {

    // Nota: O construtor obrigatório do libxposed foi omitido aqui, 
    // presumindo que você já o tenha em seu código original.

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        // Registra o log sempre que o processo do app for iniciado
        log(Log.INFO, "Xposed_Module", "Processo do app iniciado -> " + param.getPackageName());

        try {
            // 1. Obtém a instância do Unsafe para burlar a restrição 'final'
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Unsafe unsafe = (Unsafe) unsafeField.get(null);

            String[][] propriedades = {
                {"MANUFACTURER", "samsung"},
                {"BRAND", "samsung"},
                {"MODEL", "SM-S911B"},
                {"DEVICE", "dm1q"},
                {"PRODUCT", "dm1q"}
            };

            for (String[] prop : propriedades) {
                Field field = Build.class.getDeclaredField(prop[0]);
                field.setAccessible(true);
                
                // 2. Altera o valor diretamente na base de memória do campo estático
                Object base = unsafe.staticFieldBase(field);
                long offset = unsafe.staticFieldOffset(field);
                unsafe.putObject(base, offset, prop[1]);
            }
        } catch (Exception e) {
            log(Log.ERROR, "Xposed_Module", "Erro no spoofing: " + e.getMessage());
        }
    }
}
