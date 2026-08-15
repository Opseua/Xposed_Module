package com.xposedmodule.module;

import android.os.SystemClock;
import android.util.Log;
import dalvik.system.DexClassLoader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import io.github.libxposed.api.XposedModule;

public class MainModule extends XposedModule {
    private static final String TAG = "MODULO_LOADER";
    private static final String BOOT_MARKER_FILE = "/data/data/%s/files/ultimo_boot_marcado.txt";

    private long instanteDoBootAtual() {
        return System.currentTimeMillis() - SystemClock.elapsedRealtime();
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        String processName = param.getProcessName();
        String packageName = param.getPackageName();

        if (processName != null && !processName.startsWith(packageName)) {
            return;
        }

        super.onPackageReady(param);

        // Exibe 'MODULO INICIADO' apenas UMA VEZ por boot do sistema
        try {
            long bootAtual = instanteDoBootAtual();
            File marcador = new File(String.format(BOOT_MARKER_FILE, packageName));

            long ultimoBootMarcado = -1;
            if (marcador.exists()) {
                String conteudo = new String(java.nio.file.Files.readAllBytes(marcador.toPath())).trim();
                try {
                    ultimoBootMarcado = Long.parseLong(conteudo);
                } catch (NumberFormatException ignored) {}
            }

            boolean bootDiferente = Math.abs(bootAtual - ultimoBootMarcado) > 2000;

            if (bootDiferente) {
                if (!marcador.getParentFile().exists()) marcador.getParentFile().mkdirs();
                try (FileWriter w = new FileWriter(marcador, false)) {
                    w.write(String.valueOf(bootAtual));
                }
                this.log(Log.INFO, TAG, "MODULO INICIADO");
            }
        } catch (IOException e) {
            Log.e(TAG, "Falha ao checar/gravar marcador de boot: " + e.getMessage());
        }

        File dexFile = new File("/storage/emulated/0/Pictures/xposed/server.dex");
        if (!dexFile.exists()) {
            this.log(Log.ERROR, TAG, "FALHA CRÍTICA: dex não encontrado em " + dexFile.getAbsolutePath());
            return;
        }

        try {
            File cacheDir = new File("/data/data/" + packageName + "/cache");
            if (!cacheDir.exists()) cacheDir.mkdirs();

            DexClassLoader loader = new DexClassLoader(
                    dexFile.getAbsolutePath(),
                    cacheDir.getAbsolutePath(),
                    null,
                    MainModule.class.getClassLoader()
            );

            // Exibe 'LOADER CARREGADO' apenas UMA VEZ por boot do sistema
            try {
                File loaderMarker = new File("/data/data/" + packageName + "/files/loader_carregado.txt");
                long bootAtual = instanteDoBootAtual();
                long ultimoLoaderBoot = -1;
                if (loaderMarker.exists()) {
                    ultimoLoaderBoot = Long.parseLong(new String(java.nio.file.Files.readAllBytes(loaderMarker.toPath())).trim());
                }
                if (Math.abs(bootAtual - ultimoLoaderBoot) > 2000) {
                    if (!loaderMarker.getParentFile().exists()) loaderMarker.getParentFile().mkdirs();
                    try (FileWriter w = new FileWriter(loaderMarker, false)) {
                        w.write(String.valueOf(bootAtual));
                    }
                    this.log(Log.INFO, TAG, "LOADER CARREADO");
                }
            } catch (Exception ignored) {}

            Class<?> payloadClass = loader.loadClass("com.xposedmodule.payload.ServerPayload");
            Method startMethod = payloadClass.getMethod("start", Object.class, String.class, ClassLoader.class);
            startMethod.invoke(null, this, packageName, param.getClassLoader());

        } catch (Throwable t) {
            this.log(Log.ERROR, TAG, "Erro ao carregar o payload:\n" + Log.getStackTraceString(t));
        }
    }
}
