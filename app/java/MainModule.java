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

    // Controla se já logamos "MODULO PRONTO" NESTE BOOT do sistema (não só
    // neste processo do app). Comparamos o horário atual de boot do sistema
    // (uptimeMillis atrás de agora = instante exato do último boot) com um
    // valor salvo em arquivo. Se forem diferentes, é a primeira vez que o
    // módulo roda desde o boot mais recente - grava o marcador e atualiza
    // o arquivo. Isso é confiável independente de /data/local/tmp ser
    // limpo ou não no reboot (comportamento varia por fabricante/versão).
    private static final String BOOT_MARKER_FILE = "/data/data/%s/files/ultimo_boot_marcado.txt";

    private long instanteDoBootAtual() {
        // System.currentTimeMillis() = horário real agora.
        // SystemClock.elapsedRealtime() = tempo decorrido desde o último boot.
        // A diferença entre os dois é (aproximadamente) o instante exato em
        // que o sistema ligou - único por boot, então serve como "ID do boot".
        return System.currentTimeMillis() - SystemClock.elapsedRealtime();
    }

    private String pkgName;

    // Função única que agrupa console (this.log) + arquivo, em vez de
    // chamar this.log(...) e logToFile(...) em sequência toda vez.
    private void logWrite(int priority, String msg) {
        this.log(priority, TAG, msg);

        try {
            File dir = new File("/data/data/" + pkgName + "/files");
            if (!dir.exists()) dir.mkdirs();

            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
            FileWriter writer = new FileWriter(new File(dir, "modulo_log.txt"), true); // true = append
            writer.write("[" + timestamp + "] " + msg + "\n");
            writer.close();
        } catch (IOException e) {
            Log.e(TAG, "Falha ao escrever log em arquivo: " + e.getMessage());
        }
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        super.onPackageReady(param);

        pkgName = param.getPackageName();

        this.log(Log.INFO, TAG, "RODANDO");

        // Só grava "MODULO PRONTO" se essa é a primeira vez que o módulo
        // roda desde o último boot do sistema (não a cada abertura do app).
        try {
            long bootAtual = instanteDoBootAtual();
            File marcador = new File(String.format(BOOT_MARKER_FILE, pkgName));

            long ultimoBootMarcado = -1;
            if (marcador.exists()) {
                String conteudo = new String(java.nio.file.Files.readAllBytes(marcador.toPath())).trim();
                try {
                    ultimoBootMarcado = Long.parseLong(conteudo);
                } catch (NumberFormatException ignored) {
                    // arquivo corrompido/vazio - trata como se nunca tivesse marcado
                }
            }

            // Tolerância de 2 segundos: os dois cálculos de "instante do boot"
            // (em chamadas diferentes) podem variar minimamente por causa do
            // arredondamento entre currentTimeMillis()/elapsedRealtime().
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

        // Caminho confirmado via "adb shell find": a pasta compartilhada do
        // MuMuPlayer aparece dentro do Android em /storage/emulated/0/...
        // (a raiz "de baixo nível" /data/media/0/... é a mesma coisa vista
        // pelo shell root, mas apps normais devem usar o caminho de app).
        File dexFile = new File("/storage/emulated/0/Pictures/xposed/server.dex");

        if (!dexFile.exists()) {
            logWrite(Log.ERROR, "FALHA CRÍTICA: dex não encontrado em " + dexFile.getAbsolutePath());
            return;
        }

        try {
            File cacheDir = new File("/data/data/" + pkgName + "/cache");
            if (!cacheDir.exists()) cacheDir.mkdirs();

            // XposedModule NÃO é uma classe "normal" resolvível em qualquer
            // classloader: ela não é empacotada no APK do módulo nem existe
            // no app hookado. O framework Vector injeta a implementação real
            // dela via attachFramework() diretamente no processo/classloader
            // do PRÓPRIO módulo (com.xposedmodule.module) no momento em que
            // MainModule é instanciado. Por isso o parent aqui precisa ser
            // o classloader do MainModule, não o do app hookado.
            DexClassLoader loader = new DexClassLoader(
                    dexFile.getAbsolutePath(),
                    cacheDir.getAbsolutePath(),
                    null,
                    MainModule.class.getClassLoader()
            );

            logWrite(Log.INFO, "LOADER CARREGADO para: " + pkgName);

            Class<?> payloadClass = loader.loadClass("com.xposedmodule.payload.ServerPayload");

            // IMPORTANTE: getMethod("start", XposedModule.class, ...) força o
            // classloader que fez a chamada reflexiva a RESOLVER XposedModule
            // como tipo. Só que XposedModule não é empacotada em nenhum dex
            // físico (compileOnly) - ela existe apenas como instância já
            // resolvida internamente pelo framework via attachFramework().
            // Isso quebra a resolução por reflection em qualquer combinação
            // de classloader. Solução: usar Object.class na assinatura do
            // start() (tanto aqui quanto no ServerPayload.java). Dentro do
            // ServerPayload, "this" (a instância real) também é acessado só
            // via reflection (hostModule.getClass().getMethod("log", ...)),
            // nunca com um cast/import direto para XposedModule - assim o
            // payload nunca precisa resolver esse tipo.
            Method startMethod = payloadClass.getMethod("start", Object.class, String.class, ClassLoader.class);
            startMethod.invoke(null, this, pkgName, param.getClassLoader());

        } catch (Throwable t) {
            // Log.getStackTraceString mostra a causa real (ex: ClassNotFoundException,
            // NoSuchMethodError por assinatura errada) - t.getMessage() sozinho
            // costuma vir null e esconder o erro de verdade.
            logWrite(Log.ERROR, "Erro ao carregar o payload:\n" + Log.getStackTraceString(t));
        }
    }
}
