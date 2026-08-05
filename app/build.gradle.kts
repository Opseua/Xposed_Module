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

    // Estrutura rasa: tudo dentro de app/src/, um único nível,
    // separado por tipo (java/, res/, xposedMeta/), em vez do
    // padrão do Android (src/main/java/<pacote>/...).
    sourceSets {
        getByName("main") {
            manifest.srcFile("src/AndroidManifest.xml")
            java.setSrcDirs(listOf("src/java"))
            res.setSrcDirs(listOf("src/res"))
            // module.prop e java_init.list precisam ficar em
            // META-INF/xposed/ dentro do artefato final (exigência do
            // libxposed). A pasta gerada abaixo já tem essa estrutura
            // interna mínima; o que fica achatado em app/src/ é só o
            // que você edita.
            resources.srcDir(layout.buildDirectory.dir("generated/xposedMeta"))
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

// Copia module.prop e java_init.list de src/xposedMeta/ (raso) para
// build/generated/xposedMeta/META-INF/xposed/ (caminho exigido pelo
// libxposed dentro do artefato final). Roda automaticamente antes de
// qualquer processamento de resources.
val prepareXposedMeta = tasks.register<Copy>("prepareXposedMeta") {
    from("src/xposedMeta")
    into(layout.buildDirectory.dir("generated/xposedMeta/META-INF/xposed"))
}

tasks.matching { it.name.startsWith("process") && it.name.contains("Resources") }
    .configureEach { dependsOn(prepareXposedMeta) }
tasks.matching { it.name.startsWith("merge") && it.name.contains("JavaResource") }
    .configureEach { dependsOn(prepareXposedMeta) }
tasks.whenTaskAdded {
    if (name.startsWith("merge") && name.contains("JavaResource")) {
        dependsOn(prepareXposedMeta)
    }
}
