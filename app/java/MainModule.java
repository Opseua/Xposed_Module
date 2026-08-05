import android.util.Log;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;

/**
 * OPSEUA - módulo base.
 *
 * Único comportamento: ao carregar qualquer app presente no escopo
 * (escolhido pelo usuário no gerenciador Xposed/LSPosed, já que
 * staticScope=false no module.prop), registra no log o nome do
 * pacote carregado. Nada além disso.
 */
public class MainModule extends XposedModule {

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        log(Log.INFO, "OPSEUA", "pacote carregado -> " + param.getPackageName());
    }
}
