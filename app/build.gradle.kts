plugins {
    id("com.android.application")
}

android {
    // O Android exige que namespace/applicationId tenha pelo menos
    // um "." (formato de domínio reverso) - "opseua" sozinho não é
    // aceito pelo manifest merger. Isso é só um identificador interno
    // do Android; o código Java continua sem "package" declarado.
    namespace = "com.opseua.module"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.opseua.module"
        minSdk = 31
        versionCode = 1
        versionName = "1.0.0"
    }

    // Estrutura rasa: tudo dentro de app/src/, separado por tipo
    // (java/, res/, xposedMeta/), em vez do padrão do Android
    // (src/main/java/<pacote>/...).
    //
    // Dentro de src/res/ o AAPT exige subpastas por tipo de resource
    // (xml/, values/ etc.) - isso é regra fixa do Android, não dá pra
    // achatar. Dentro de xposedMeta/ existe META-INF/xposed/ - é o
    // caminho fixo exigido pelo libxposed dentro do artefato final
    // (module.prop e java_init.list), também não dá pra achatar.
    // Fora essas duas exigências, tudo fica solto em app/src/.
    sourceSets {
        getByName("main") {
            manifest.srcFile("src/AndroidManifest.xml")
            java.setSrcDirs(listOf("src/java"))
            res.setSrcDirs(listOf("src/res"))
            resources.setSrcDirs(listOf("src/xposedMeta"))
        }
    }

    lint {
        targetSdk = 36
        checkReleaseBuilds = false
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    dependenciesInfo {
        includeInApk = false
    }
}

dependencies {
    compileOnly("androidx.annotation:annotation:1.9.1")
    compileOnly("io.github.libxposed:api:102.0.0")
}
