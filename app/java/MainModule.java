package com.xposedmodule.module;

import android.os.SystemClock;
import android.util.Log;
import dalvik.system.DexClassLoader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import io.github.libxposed.api.XposedModule;

public class MainModule extends XposedModule {
    private static final String TAG = "MODULO_LOADER";

    private static final String BOOT_MARKER_FILE = "/data/data/%s/files/ultimo_boot_marcado.txt";

    private long instanteDoBootAtual() {
        return System.currentTimeMillis() - SystemClock.elapsedRealtime();
    }

    private String pkgName;

    private void logWrite(int priority, String msg) {
        this.log(priority, TAG, msg);

        try {
            File dir = new File("/data/data/" + pkgName + "/files");
            if (!dir.exists()) dir.mkdirs();

            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
            FileWriter writer = new FileWriter(new File(dir, "modulo_log.txt"), true);
            writer.write("[" + timestamp + "] " + msg + "\n");
            writer.close();
        } catch (IOException e) {
            Log.e(TAG, "Falha ao escrever log em arquivo: " + e.getMessage());
        }
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        String processName = param.getProcessName();
        String packageName = param.getPackageName();

        // BLinda para rodar APENAS no processo principal do app do escopo
        if (processName != null && !processName.startsWith(packageName)) {
            return;
        }

        super.onPackageReady(param);
        pkgName = packageName;

        this.log(Log.INFO, TAG, "RODANDO");

        try {
            long bootAtual = instanteDoBootAtual();
            File marcador = new File(String.format(BOOT_MARKER_FILE, pkgName));

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
                logWrite(Log.INFO, "MODULO PRONTO - app: " + pkgName);
            }
        } catch (IOException e) {
            Log.e(TAG, "Falha ao checar/gravar marcador de boot: " + e.getMessage());
        }

        File dexFile = new File("/storage/emulated/0/Pictures/xposed/server.dex");

        if (!dexFile.exists()) {
            logWrite(Log.ERROR, "FALHA CRÍTICA: dex não encontrado em " + dexFile.getAbsolutePath());
            return;
        }

        try {
            File cacheDir = new File("/data/data/" + pkgName + "/cache");
            if (!cacheDir.exists()) cacheDir.mkdirs();

            DexClassLoader loader = new DexClassLoader(
                    dexFile.getAbsolutePath(),
                    cacheDir.getAbsolutePath(),
                    null,
                    MainModule.class.getClassLoader()
            );

            logWrite(Log.INFO, "LOADER CARREGADO para: " + pkgName);

            Class<?> payloadClass = loader.loadClass("com.xposedmodule.payload.ServerPayload");
            Method startMethod = payloadClass.getMethod("start", Object.class, String.class, ClassLoader.class);
            startMethod.invoke(null, this, pkgName, param.getClassLoader());

        } catch (Throwable t) {
            logWrite(Log.ERROR, "Erro ao carregar o payload:\n" + Log.getStackTraceString(t));
        }
    }
}
