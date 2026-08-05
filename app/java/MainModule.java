import android.os.Build;
import android.util.Log;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;

public class MainModule extends XposedModule {

    // 💡 Dicionário central para organizar todos os blocos de spoofing de forma contínua
    private static final Map<String, String> SPOOF_PROPS = new HashMap<>();

    static {
        // ------ SPOOF: [ro.product] ------
        SPOOF_PROPS.put("ro.product.model", "SM-S911B");
        SPOOF_PROPS.put("ro.product.odm.model", "SM-S911B");
        SPOOF_PROPS.put("ro.product.system.model", "SM-S911B");
        SPOOF_PROPS.put("ro.product.system_ext.model", "SM-S911B");
        SPOOF_PROPS.put("ro.product.vendor.model", "SM-S911B");
        SPOOF_PROPS.put("ro.product.product.model", "SM-S911B");

        SPOOF_PROPS.put("ro.product.device", "dm1q");
        SPOOF_PROPS.put("ro.product.odm.device", "dm1q");
        SPOOF_PROPS.put("ro.product.vendor.device", "dm1q");
        SPOOF_PROPS.put("ro.product.system.device", "dm1q");
        SPOOF_PROPS.put("ro.product.system_ext.device", "dm1q");
        SPOOF_PROPS.put("ro.product.product.device", "dm1q");
        SPOOF_PROPS.put("ro.product.name", "dm1q");
        SPOOF_PROPS.put("ro.product.odm.name", "dm1q");
        SPOOF_PROPS.put("ro.product.vendor.name", "dm1q");
        SPOOF_PROPS.put("ro.product.system.name", "dm1q");
        SPOOF_PROPS.put("ro.product.system_ext.name", "dm1q");
        SPOOF_PROPS.put("ro.product.product.name", "dm1q");

        SPOOF_PROPS.put("ro.product.board", "kalama");
        SPOOF_PROPS.put("ro.product.build.version.incremental", "S911BXXU1AWBD");
        SPOOF_PROPS.put("ro.product.build.fingerprint", "samsung/dm1q/dm1q:14/UP1A.231005.007/S911BXXU1AWBD:user/release-keys");

        SPOOF_PROPS.put("ro.product.brand", "samsung");
        SPOOF_PROPS.put("ro.product.odm.brand", "samsung");
        SPOOF_PROPS.put("ro.product.vendor.brand", "samsung");
        SPOOF_PROPS.put("ro.product.system.brand", "samsung");
        SPOOF_PROPS.put("ro.product.system_ext.brand", "samsung");
        SPOOF_PROPS.put("ro.product.product.brand", "samsung");

        SPOOF_PROPS.put("ro.product.manufacturer", "samsung");
        SPOOF_PROPS.put("ro.product.odm.manufacturer", "samsung");
        SPOOF_PROPS.put("ro.product.vendor.manufacturer", "samsung");
        SPOOF_PROPS.put("ro.product.system.manufacturer", "samsung");
        SPOOF_PROPS.put("ro.product.system_ext.manufacturer", "samsung");
        SPOOF_PROPS.put("ro.product.product.manufacturer", "samsung");
        
        // ----------------------------------
        
        // Novos blocos (ex: [ro.build], [ro.vendor]) poderão ser adicionados aqui no futuro.
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        log(Log.INFO, "Xposed_Module", "App aberto -> " + param.getPackageName());
        alterarClasseBuild();
    }

    /**
     * 🛠️ Lê do Dicionário e aplica dinamicamente.
     */
    private void alterarClasseBuild() {
        try {
            Field accessFlagsField = Field.class.getDeclaredField("accessFlags");
            accessFlagsField.setAccessible(true);

            setBuildField("MODEL", SPOOF_PROPS.get("ro.product.model"), accessFlagsField);
            setBuildField("DEVICE", SPOOF_PROPS.get("ro.product.device"), accessFlagsField);
            setBuildField("PRODUCT", SPOOF_PROPS.get("ro.product.name"), accessFlagsField);
            setBuildField("BRAND", SPOOF_PROPS.get("ro.product.brand"), accessFlagsField);
            setBuildField("MANUFACTURER", SPOOF_PROPS.get("ro.product.manufacturer"), accessFlagsField);
            setBuildField("BOARD", SPOOF_PROPS.get("ro.product.board"), accessFlagsField);
            setBuildField("FINGERPRINT", SPOOF_PROPS.get("ro.product.build.fingerprint"), accessFlagsField);
        } catch (Exception e) {
            log(Log.ERROR, "Xposed_Module", "Erro fatal no accessFlags: " + e.getMessage());
        }
    }

    /**
     * 💡 Função modular genérica que encapsula a lógica de drible da variável 'final'.
     * Elimina a repetição massiva de blocos try/catch.
     */
    private void setBuildField(String fieldName, String novoValor, Field accessFlagsField) {
        if (novoValor == null) return;
        
        try {
            Field field = Build.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            
            int flags = accessFlagsField.getInt(field);
            accessFlagsField.setInt(field, flags & ~java.lang.reflect.Modifier.FINAL);
            
            field.set(null, novoValor);
        } catch (Exception e) {
            log(Log.ERROR, "Xposed_Module", "Erro ao alterar Build." + fieldName + ": " + e.getMessage());
        }
    }
}
