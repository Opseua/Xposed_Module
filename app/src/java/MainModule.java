import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * =====================================================================
 * OPSEUA - arquivo único do módulo
 * =====================================================================
 *
 * Tudo do módulo mora aqui dentro, dividido em seções com marcadores
 * "// ==== NOME ====" para facilitar achar e editar.
 *
 * Para adicionar um novo recurso/hook:
 *   1. Crie um novo método/bloco dentro da seção "MAIN MODULE (hooks)".
 *   2. Se precisar de um novo BroadcastReceiver, siga o padrão de
 *      ShadeControlReceiver e registre-o no AndroidManifest.xml.
 *
 * O escopo (quais apps o módulo afeta) NÃO é mais fixo em arquivo:
 * staticScope=false no module.prop faz o usuário escolher os apps
 * direto no gerenciador Xposed/LSPosed. Nada aqui precisa mudar
 * quando o usuário adiciona/remove apps do escopo.
 * =====================================================================
 */
public class MainModule {

    static final String TAG = "OPSEUA";

    // ================= MAIN MODULE (hooks) =================
    // TODO: implemente aqui a classe/entrypoint real do libxposed
    // (ex: implementar XposedModuleInterface / IXposedHookLoadPackage,
    // conforme a API do libxposed). Este é o ponto central para
    // adicionar novos hooks conforme o app crescer.
    //
    // Exemplo de estrutura esperada (ajustar conforme a versão da API):
    //
    // public class MainModule implements XposedModuleInterface {
    //     @Override
    //     public void onPackageLoaded(PackageLoadedParam param) {
    //         // Aqui você recebe QUALQUER pacote presente no escopo
    //         // escolhido pelo usuário no LSPosed Manager.
    //         Log.i(TAG, "Hookando: " + param.getPackageName());
    //     }
    // }


    // ================= DEVICE ADMIN =================
    /** Ativa/desativa o app como Device Admin. Necessário para o ShadeControlReceiver. */
    public static class ShadeAdminReceiver extends DeviceAdminReceiver {

        @Override
        public void onEnabled(Context context, Intent intent) {
            super.onEnabled(context, intent);
            Log.i(TAG, "ShadeAdminReceiver ATIVADO como Device Admin");
        }

        @Override
        public void onDisabled(Context context, Intent intent) {
            super.onDisabled(context, intent);
            Log.i(TAG, "ShadeAdminReceiver DESATIVADO como Device Admin");
        }
    }


    // ================= CONTROLE DO SHADE (status bar) =================
    /**
     * Aciona/libera o bloqueio do shade (status bar expansion) via
     * DevicePolicyManager.setStatusBarDisabled, usando o Device Admin
     * registrado em ShadeAdminReceiver.
     *
     * Uso via ADB (não precisa de UI):
     *   adb shell am broadcast -a opseua.ACTION_DISABLE_SHADE
     *   adb shell am broadcast -a opseua.ACTION_ENABLE_SHADE
     *
     * Pré-requisito: o app precisa já estar ativo como Device Admin
     * (Configurações > Segurança > Administradores do dispositivo).
     */
    public static class ShadeControlReceiver extends BroadcastReceiver {

        public static final String ACTION_DISABLE_SHADE = "opseua.ACTION_DISABLE_SHADE";
        public static final String ACTION_ENABLE_SHADE = "opseua.ACTION_ENABLE_SHADE";

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;

            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = new ComponentName(context, ShadeAdminReceiver.class);

            if (dpm == null) {
                Log.e(TAG, "DevicePolicyManager nulo");
                return;
            }

            boolean isAdminActive = dpm.isAdminActive(admin);
            Log.i(TAG, "isAdminActive=" + isAdminActive);

            if (!isAdminActive) {
                Log.e(TAG, "App NAO esta ativo como Device Admin. Ative manualmente em "
                        + "Configuracoes > Seguranca > Administradores do dispositivo.");
                return;
            }

            try {
                if (ACTION_DISABLE_SHADE.equals(action)) {
                    dpm.setStatusBarDisabled(admin, true);
                    Log.i(TAG, "setStatusBarDisabled(true) chamado - shade bloqueado");
                } else if (ACTION_ENABLE_SHADE.equals(action)) {
                    dpm.setStatusBarDisabled(admin, false);
                    Log.i(TAG, "setStatusBarDisabled(false) chamado - shade liberado");
                }
            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException ao chamar setStatusBarDisabled", e);
            }
        }
    }
}
