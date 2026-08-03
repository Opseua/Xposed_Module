package com.example.module;

import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Aciona/libera o bloqueio do shade (status bar expansion) via
 * DevicePolicyManager.setStatusBarDisabled, usando o Device Admin
 * registrado em ShadeAdminReceiver.
 *
 * Uso via ADB (não precisa de UI):
 *   adb shell am broadcast -a com.example.module.ACTION_DISABLE_SHADE
 *   adb shell am broadcast -a com.example.module.ACTION_ENABLE_SHADE
 *
 * Pré-requisito: o app precisa já estar ativo como Device Admin
 * (Configurações > Segurança > Administradores do dispositivo, ativar manualmente
 * na primeira vez - o Android exige confirmação humana para isso).
 */
public class ShadeControlReceiver extends BroadcastReceiver {

    private static final String TAG = "MODULO";
    public static final String ACTION_DISABLE_SHADE = "com.example.module.ACTION_DISABLE_SHADE";
    public static final String ACTION_ENABLE_SHADE = "com.example.module.ACTION_ENABLE_SHADE";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName admin = new ComponentName(context, ShadeAdminReceiver.class);

        if (dpm == null) {
            Log.e(TAG, "MODULO: DevicePolicyManager nulo");
            return;
        }

        boolean isAdminActive = dpm.isAdminActive(admin);
        Log.i(TAG, "MODULO: isAdminActive=" + isAdminActive);

        if (!isAdminActive) {
            Log.e(TAG, "MODULO: app NAO esta ativo como Device Admin. "
                    + "Ative manualmente em Configuracoes > Seguranca > Administradores do dispositivo.");
            return;
        }

        try {
            if (ACTION_DISABLE_SHADE.equals(action)) {
                dpm.setStatusBarDisabled(admin, true);
                Log.i(TAG, "MODULO: setStatusBarDisabled(true) chamado - shade bloqueado");
            } else if (ACTION_ENABLE_SHADE.equals(action)) {
                dpm.setStatusBarDisabled(admin, false);
                Log.i(TAG, "MODULO: setStatusBarDisabled(false) chamado - shade liberado");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "MODULO: SecurityException ao chamar setStatusBarDisabled", e);
        }
    }
}
