import android.util.Log;
import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.util.List;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;

public class MainModule extends XposedModule {

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        log(Log.INFO, "Xposed_Monitor", "Iniciando monitoramento no app -> " + param.getPackageName());
        iniciarMonitoramento(param.getPackageName());
    }

    private void iniciarMonitoramento(String packageName) {
        try {
            Class<?> sysPropsClass = Class.forName("android.os.SystemProperties");
            Method getMethod1 = sysPropsClass.getDeclaredMethod("get", String.class);
            Method getMethod2 = sysPropsClass.getDeclaredMethod("get", String.class, String.class);

            XposedInterface.Hooker hooker = new XposedInterface.Hooker() {
                @Override
                public Object intercept(Chain chain) throws Throwable {
                    List<Object> args = chain.getArgs();
                    
                    // Executa o método original primeiro para descobrir qual é a resposta real do sistema
                    Object result = chain.proceed(); 
                    
                    if (!args.isEmpty() && args.get(0) instanceof String) {
                        String key = (String) args.get(0);
                        String realValue = result != null ? result.toString() : "null";
                        
                        // Registra no log do LSPosed/Xposed em tempo real
                        log(Log.INFO, "Xposed_Monitor", "Consultou: [" + key + "] -> Retornou: " + realValue);
                        
                        // Salva no arquivo de texto
                        salvarEmArquivo(packageName, key, realValue);
                    }
                    
                    return result; // Retorna o valor original para o app não quebrar
                }
            };

            hook(getMethod1).intercept(hooker);
            hook(getMethod2).intercept(hooker);

        } catch (Exception e) {
            log(Log.ERROR, "Xposed_Monitor", "Erro ao iniciar monitoramento: " + e.getMessage());
        }
    }

    private void salvarEmArquivo(String packageName, String key, String value) {
        try {
            // Usa o diretório de dados privados do aplicativo alvo, onde não precisamos de permissões
            File dir = new File("/data/user/0/" + packageName + "/cache");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            
            File logFile = new File(dir, "spoof_monitor.txt");
            
            // O parâmetro 'true' no FileWriter faz o texto ser adicionado no fim do arquivo, sem apagar o que já existe
            FileWriter writer = new FileWriter(logFile, true);
            writer.append("[").append(key).append("] = ").append(value).append("\n");
            writer.flush();
            writer.close();
        } catch (Exception e) {
            log(Log.ERROR, "Xposed_Monitor", "Falha ao gravar no arquivo txt: " + e.getMessage());
        }
    }
}
