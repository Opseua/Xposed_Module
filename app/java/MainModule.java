import android.os.Build;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;

public class MainModule extends XposedModule {

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        try {
            // 1. Acessa o controlador de restrições do Android
            Field accessFlags = Field.class.getDeclaredField("accessFlags");
            accessFlags.setAccessible(true);

            // 2. Aponta para a variável MODEL
            Field field = Build.class.getDeclaredField("MODEL");
            field.setAccessible(true);
            
            // 3. Remove a proteção 'final' e injeta o novo modelo
            accessFlags.setInt(field, accessFlags.getInt(field) & ~Modifier.FINAL);
            field.set(null, "SM-S911B");
            
        } catch (Exception e) {
            // Falha silenciosa
        }
    }
}
