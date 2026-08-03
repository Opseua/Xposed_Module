package com.example.module;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class ShadeAdminReceiver extends DeviceAdminReceiver {

    private static final String TAG = "MODULO";

    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
        Log.i(TAG, "MODULO: ShadeAdminReceiver ATIVADO como Device Admin");
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        super.onDisabled(context, intent);
        Log.i(TAG, "MODULO: ShadeAdminReceiver DESATIVADO como Device Admin");
    }
}
