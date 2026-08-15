package com.xposedmodule.module;

import android.os.SystemClock;
import android.util.Log;
import dalvik.system.DexClassLoader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.List;
import io.github.libxposed.api.XposedModule;

public class MainModule extends XposedModule {
    private static final String TAG = "MODULO_LOADER";
    private static final String BOOT_MARKER_FILE = "/data/data/%s/files/xposed/module_boot.txt";
    private static final String IGNORE_FILE_PATH = "/storage/emulated/0/Pictures/xposed/module_ignore.txt";

    private long instanteDoBootAtual() {
        return System.currentTimeMillis() - SystemClock.elapsedRealtime();
    }

    private boolean eProcessoPrincipal(String packageName) {
        try {
            byte[] bytes = Files.readAllBytes(new File("/proc/self/cmdline").toPath());
            String cmdline = new String(bytes).trim();
            return cmdline.equals(packageName);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean estaIgnorado(String packageName) {
        File ignoreFile = new File(IGNORE_FILE_PATH);
        if (!ignoreFile.exists()) {
            return false;
        }
        try {
            List<String> linhas = Files.readAllLines(ignoreFile.toPath());
            for (String linha : linhas) {
                if (linha.trim().equals(packageName)) {
                    return true;
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Falha ao ler module_ignore.txt: " + e.getMessage());
        }
        return false;
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        super.onPackageReady(param);
        String packageName = param.getPackageName();

        if (!eProcessoPrincipal(packageName) || estaIgnorado(packageName)) {
            return;
        }

        try {
            long bootAtual = instanteDoBootAtual();
            File marcador = new File(String.format(BOOT_MARKER_FILE, packageName));

            long ultimoBootMarcado = -1;
            if (marcador.exists()) {
                String conteudo = new String(Files.readAllBytes(marcador.toPath())).trim();
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

            Class<?> payloadClass = loader.loadClass("com.xposedmodule.payload.ServerPayload");
            Method startMethod = payloadClass.getMethod("start", Object.class, String.class, ClassLoader.class);
            startMethod.invoke(null, this, packageName, param.getClassLoader());

        } catch (Throwable t) {
            this.log(Log.ERROR, TAG, "Erro ao carregar o payload:\n" + Log.getStackTraceString(t));
        }
    }
}
