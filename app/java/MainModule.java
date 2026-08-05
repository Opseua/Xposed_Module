import android.util.Log;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;

/**
 * Xposed_Module - módulo base.
 *
 * Único comportamento: ao carregar qualquer app presente no escopo
 * (escolhido pelo usuário no gerenciador Xposed/LSPosed, já que
 * staticScope=false no module.prop), registra no log o nome do
 * pacote carregado e aplica o spoofing.
 */
public class MainModule extends XposedModule {

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        log(Log.INFO, "Xposed_Module", "app aberto -> " + param.getPackageName());

        try {
            String[][] propriedades = {
                {"MANUFACTURER", "samsung"},
                {"BRAND", "samsung"},
                {"MODEL", "SM-S911B"},
                {"DEVICE", "dm1q"},
                {"PRODUCT", "dm1q"}
            };

            for (String[] prop : propriedades) {
                java.lang.reflect.Field field = android.os.Build.class.getDeclaredField(prop[0]);
                field.setAccessible(true);
                field.set(null, prop[1]);
            }
        } catch (Exception e) {
            log(Log.ERROR, "Xposed_Module", "Erro no spoofing: " + e.getMessage());
        }
    }
}
