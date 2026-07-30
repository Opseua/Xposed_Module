package com.seumodulo.zoom; // Mantenha o pacote original do template aqui

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import android.util.Range;

public class MainHook implements IXposedHookLoadPackage {
    
    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        
        // Filtro para aplicar apenas no app desejado (substitua pelo pacote correto)
        if (!lpparam.packageName.equals("com.nome.do.app")) {
            return;
        }

        XposedHelpers.findAndHookMethod(
            "android.hardware.camera2.CameraCharacteristics",
            lpparam.classLoader,
            "get",
            "android.hardware.camera2.CameraCharacteristics$Key",
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Object key = param.args[0];
                    if (key != null) {
                        String keyName = (String) XposedHelpers.callMethod(key, "getName");
                        if ("android.control.zoomRatioRange".equals(keyName)) {
                            Range<Float> newRange = new Range<>(0.5f, 8.0f);
                            param.setResult(newRange);
                        }
                    }
                }
            }
        );
    }
}
