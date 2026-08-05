plugins {
    id("com.android.application")
}

android {
    namespace = "opseua"
    compileSdk = 36

    defaultConfig {
        applicationId = "opseua"
        minSdk = 31
        versionCode = 1
        versionName = "1.0.0"
    }

    // Estrutura rasa: tudo dentro de app/src/, separado por tipo
    // (java/, res/, xposedMeta/), em vez do padrão do Android
    // (src/main/java/<pacote>/...).
    //
    // Dentro de xposedMeta/ existe META-INF/xposed/ - é o único
    // caminho fixo exigido pelo libxposed dentro do artefato final
    // (module.prop e java_init.list), não dá pra achatar sem quebrar
    // a detecção do módulo. Fora isso, tudo o que você edita fica
    // solto em app/src/.
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
