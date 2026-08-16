// COMO VER OS CONSOLES EM TEMPO REAL (NÃO APAGAR ISSO!):
// cls & adb logcat -c && adb logcat MODULO_LOADER:* MODULO_PAYLOAD:* *:S
package com.xposedmodule.payload;

import android.util.Log;
import android.util.Range;
import android.util.SizeF;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ServerPayload {

    private static final String TAG = "MODULO_LOADER"; private static Object module; private static String pkgName; private static final String IGNORE_FILE_PATH = "/storage/emulated/0/Pictures/xposed/module_ignore.txt";
    private static boolean estaIgnorado(String pkgName) {
        File ignoreFile = new File(IGNORE_FILE_PATH); if (!ignoreFile.exists()) { return false;  }
        try { String conteudo = new String(Files.readAllBytes(ignoreFile.toPath())); return conteudo.contains(pkgName);} catch (IOException e) { Log.e(TAG, "Falha ao ler module_ignore.txt: " + e.getMessage());}; return false;
    }; private static void logWrite(String msg) { logWrite(Log.INFO, msg); }; private static void logWrite(int priority, String msg) {
        try { if (module != null) { Method logMethod = module.getClass().getMethod("log", int.class, String.class, String.class); logMethod.invoke(module, priority, TAG, msg); } } 
        catch (Throwable t) { Log.e(TAG, "Falha ao chamar module.log via reflection: " + Log.getStackTraceString(t)); }; try {
            File dir = new File("/data/data/" + pkgName + "/files/xposed"); if (!dir.exists()) dir.mkdirs(); String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
            FileWriter writer = new FileWriter(new File(dir, "module_log.txt"), true); writer.write("[" + timestamp + "] " + msg + "\n"); writer.close();
        } catch (IOException e) { Log.e(TAG, "Falha ao escrever log em arquivo: " + e.getMessage()); }
    }

    public static void start(Object hostModule, String packageName, ClassLoader classLoader) {
        module = hostModule; pkgName = packageName; if (estaIgnorado(pkgName)) { return; }; logWrite("PLAYLOAD CARREGADO para: " + pkgName);
        runServer(classLoader);
    }

    private static void runServer(ClassLoader classLoader) {
        
        // ==============================================================
        // 🎮 PAINEL DE CONTROLE DE SPOOFS (Mude para 'true' para testar)
        // ==============================================================
        boolean etapa1_modelo       = true;  // 1 - Modelo do dispositivo
        boolean etapa2_codigo       = true;  // 2 - Código do dispositivo
        boolean etapa4_qtd_cameras  = true;  // 4 - Quantidade de câmeras
        boolean etapa5_zoom         = true;  // 5 - Zoom das câmeras
        boolean etapa6_resolucao    = true;  // 6 - Resolução das câmeras
        boolean etapa7_restante     = true;  // 7 - Restante necessário (Matrizes e FPS)

        // Estruturas de dados enviadas ao Motor Estático
        Map<String, String> sysProps = new HashMap<>();
        String[] s23CameraIds = null;
        String fallbackId = "0"; 
        Map<String, Map<String, Object>> todasAsCameras = new HashMap<>();
        boolean overrideResolutions = false;

        // ------------------------------------------
        // ETAPA 1: MODELO
        // ------------------------------------------
        if (etapa1_modelo) {
            try {
                Field campo = android.os.Build.class.getDeclaredField("MODEL"); campo.setAccessible(true);
                Object valorOriginal = campo.get(null); campo.set(null, "SM-S911B");
                sysProps.put("ro.product.model", "SM-S911B");
                logWrite("OK ETAPA 1 (MODELO): (" + valorOriginal + " -> SM-S911B)");
            } catch (Exception e) { logWrite(Log.ERROR, "ERRO ETAPA 1: " + e.getMessage()); }
        }

        // ------------------------------------------
        // ETAPA 2: CÓDIGO
        // ------------------------------------------
        if (etapa2_codigo) {
            try {
                Field campoDev = android.os.Build.class.getDeclaredField("DEVICE"); campoDev.setAccessible(true);
                Object valDev = campoDev.get(null); campoDev.set(null, "dm1q");
                
                Field campoManu = android.os.Build.class.getDeclaredField("MANUFACTURER"); campoManu.setAccessible(true);
                campoManu.set(null, "samsung");
                
                sysProps.put("ro.product.device", "dm1q"); sysProps.put("ro.product.name", "dm1q");
                sysProps.put("ro.product.manufacturer", "samsung"); sysProps.put("ro.product.brand", "samsung");
                logWrite("OK ETAPA 2 (CÓDIGO): DEVICE (" + valDev + " -> dm1q)");
            } catch (Exception e) { logWrite(Log.ERROR, "ERRO ETAPA 2: " + e.getMessage()); }
        }

        // ------------------------------------------
        // ETAPA 4: QUANTIDADE
        // ------------------------------------------
        if (etapa4_qtd_cameras) {
            s23CameraIds = new String[] {"0", "1", "2", "3", "5", "6"};
            logWrite("OK ETAPA 4 (QUANTIDADE): APLICADA LISTA DO S23");
        }

        // ------------------------------------------
        // ETAPAS 5 e 7: ZOOM E PROPRIEDADES (Matrizes Ópticas e FPS)
        // ------------------------------------------
        if (etapa5_zoom || etapa7_restante) {
            todasAsCameras.put("0", fabricarLente("0", etapa5_zoom, etapa7_restante));
            todasAsCameras.put("1", fabricarLente("1", etapa5_zoom, etapa7_restante));
            todasAsCameras.put("2", fabricarLente("2", etapa5_zoom, etapa7_restante));
            todasAsCameras.put("3", fabricarLente("3", etapa5_zoom, etapa7_restante));
            todasAsCameras.put("5", fabricarLente("5", etapa5_zoom, etapa7_restante));
            todasAsCameras.put("6", fabricarLente("6", etapa5_zoom, etapa7_restante));
            logWrite("OK ETAPA 5/7 (LENTES): MATRIZES E FPS GERADOS");
        }

        // ------------------------------------------
        // ETAPA 6: RESOLUÇÃO
        // ------------------------------------------
        if (etapa6_resolucao) {
            overrideResolutions = true;
            logWrite("OK ETAPA 6 (RESOLUÇÃO): SUPORTE A 4K (STREAM MAP) ATIVADO");
        }

        // ==========================================
        // COMANDO FINAL PARA O MOTOR
        // ==========================================
        try {
            Method hookMethod = module.getClass().getMethod("setupSpoofs", ClassLoader.class, String.class, Map.class, String[].class, String.class, Map.class, boolean.class); 
            hookMethod.invoke(module, classLoader, pkgName, sysProps, s23CameraIds, fallbackId, todasAsCameras, overrideResolutions);
            logWrite("PAYLOAD INJETADO NO MOTOR ESTÁTICO COM SUCESSO");
        } catch (Exception e) { 
            logWrite(Log.ERROR, "ERRO CRÍTICO NA INJEÇÃO FINAL → " + e.getMessage()); 
        }
    }

    // ==============================================================================
    // FÁBRICA INTELIGENTE DE LENTES (S23 PRECISION)
    // ==============================================================================
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<String, Object> fabricarLente(String id, boolean incZoom, boolean incRestante) {
        Map<String, Object> map = new HashMap<>();
        
        if (incZoom) {
            if (id.equals("0")) map.put("android.control.zoomRatioRange", new Range<>(0.6f, 10.0f));
            else map.put("android.control.zoomRatioRange", new Range<>(1.0f, 8.0f));
        }

        if (incRestante) {
            switch (id) {
                case "0":
                case "5": // Traseira Principal (Lógica e Física)
                    map.put("android.lens.facing", 1);
                    map.put("android.info.supportedHardwareLevel", id.equals("0") ? 3 : 1);
                    map.put("android.request.availableCapabilities", new int[]{0, 1, 2, 3, 4, 5, 6, 8, 9, 11});
                    map.put("android.sensor.info.physicalSize", new SizeF(8.16f, 6.12f));
                    map.put("android.lens.info.minimumFocusDistance", 10.0f);
                    map.put("android.lens.info.hyperfocalDistance", 0.246914f);
                    map.put("android.lens.info.availableFocalLengths", new float[]{5.4f});
                    map.put("android.lens.info.availableApertures", new float[]{1.8f});
                    
                    // Matrizes Ópticas e FPS exatas do S23
                    map.put("android.lens.intrinsicCalibration", new float[]{2751.758789f, 2749.854248f, 2029.076050f, 1520.058105f, 0.000000f});
                    map.put("android.lens.distortion", new float[]{0.079093f, -0.127210f, 0.061308f, 0.000000f, 0.000000f});
                    map.put("android.control.aeAvailableTargetFpsRanges", new Range[]{new Range<>(10, 10), new Range<>(15, 15), new Range<>(24, 24), new Range<>(7, 26), new Range<>(26, 26), new Range<>(7, 27), new Range<>(27, 27), new Range<>(7, 30), new Range<>(30, 30)});
                    break;
                    
                case "1": // Frontal
                    map.put("android.lens.facing", 0);
                    map.put("android.info.supportedHardwareLevel", 1);
                    map.put("android.request.availableCapabilities", new int[]{0, 1, 2, 3, 4, 5, 6, 8, 9, 11});
                    map.put("android.sensor.info.physicalSize", new SizeF(4.48f, 3.36f));
                    map.put("android.lens.info.minimumFocusDistance", 5.0f);
                    map.put("android.lens.info.hyperfocalDistance", 0.452525f);
                    map.put("android.lens.info.availableFocalLengths", new float[]{3.3f});
                    map.put("android.lens.info.availableApertures", new float[]{2.2f});
                    
                    map.put("android.lens.intrinsicCalibration", new float[]{2946.428467f, 2946.428467f, 0.000000f, 0.000000f, 0.000000f});
                    map.put("android.lens.distortion", new float[]{0.000000f, 0.000000f, 0.000000f, 0.000000f, 0.000000f});
                    map.put("android.control.aeAvailableTargetFpsRanges", new Range[]{new Range<>(10, 10), new Range<>(7, 15), new Range<>(15, 15), new Range<>(24, 24), new Range<>(7, 30), new Range<>(30, 30)});
                    break;
                    
                case "2": // Ultrawide Traseira
                    map.put("android.lens.facing", 1);
                    map.put("android.info.supportedHardwareLevel", 1);
                    map.put("android.request.availableCapabilities", new int[]{0, 1, 2, 3, 4, 5, 6, 8, 9, 11});
                    map.put("android.sensor.info.physicalSize", new SizeF(5.6f, 4.2f));
                    map.put("android.lens.info.minimumFocusDistance", 0.0f);
                    map.put("android.lens.info.hyperfocalDistance", 1.272727f);
                    map.put("android.lens.info.availableFocalLengths", new float[]{2.2f});
                    map.put("android.lens.info.availableApertures", new float[]{2.2f});
                    
                    map.put("android.lens.intrinsicCalibration", new float[]{1631.911987f, 1630.648804f, 1981.491943f, 1478.059570f, 0.000000f});
                    map.put("android.lens.distortion", new float[]{0.003710f, 0.006093f, -0.007000f, 0.000000f, 0.000000f});
                    map.put("android.control.aeAvailableTargetFpsRanges", new Range[]{new Range<>(10, 10), new Range<>(15, 15), new Range<>(24, 24), new Range<>(7, 30), new Range<>(30, 30)});
                    break;
                    
                case "3":
                case "6": // Telephoto Traseira
                    map.put("android.lens.facing", id.equals("3") ? 0 : 1);
                    map.put("android.info.supportedHardwareLevel", 1);
                    map.put("android.request.availableCapabilities", new int[]{0, 1, 2, 3, 4, 5, 6, 8, 9, 11});
                    map.put("android.sensor.info.physicalSize", new SizeF(id.equals("3") ? 3.799040f : 3.648f, id.equals("3") ? 2.849280f : 2.736f));
                    map.put("android.lens.info.minimumFocusDistance", 2.5f);
                    map.put("android.lens.info.hyperfocalDistance", 0.097959f);
                    map.put("android.lens.info.availableFocalLengths", new float[]{7.0f});
                    map.put("android.lens.info.availableApertures", new float[]{2.4f});
                    
                    map.put("android.lens.intrinsicCalibration", new float[]{7143.485840f, 7136.674316f, 1878.276123f, 1310.611206f, 0.000000f});
                    map.put("android.lens.distortion", new float[]{-0.662010f, 11.767759f, -64.107414f, 0.000000f, 0.000000f});
                    map.put("android.control.aeAvailableTargetFpsRanges", new Range[]{new Range<>(10, 10), new Range<>(15, 15), new Range<>(24, 24), new Range<>(7, 30), new Range<>(30, 30)});
                    break;
            }
        }
        return map;
    }
}
