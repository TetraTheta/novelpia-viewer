plugins {
  alias(libs.plugins.android.application)
}

val isWindows = System.getProperty("os.name").lowercase().contains("windows")
val userHome: String? = System.getProperty("user.home")
val configuredBun: String? = providers.gradleProperty("bun.executable").orNull ?: System.getenv("BUN_BINARY")
val defaultWindowsBun = "$userHome/.bun/bin/bun.exe".takeIf { file(it).exists() } ?: "bun.exe"
val defaultUnixBun = "$userHome/.bun/bin/bun".takeIf { file(it).exists() } ?: "bun"
val bunCommand = configuredBun ?: if (isWindows) defaultWindowsBun else defaultUnixBun
val injectedScriptsDir = rootProject.layout.projectDirectory.dir("injected-scripts")
val injectedScriptsDistDir = injectedScriptsDir.dir("dist")
val generatedFilterAssetsDir = layout.buildDirectory.dir("generated/filter-assets")

android {
  namespace = "io.github.tetratheta.npviewer"
  compileSdk {
    version = release(36) {
      minorApiLevel = 1
    }
  }
  defaultConfig {
    applicationId = "io.github.tetratheta.npviewer"
    minSdk = 31
    targetSdk = 36
    versionCode = (property("app.versionCode") as String).toInt()
    versionName = property("app.versionName") as String
  }
  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
      )
    }
    create("prerelease") {
      initWith(getByName("release"))
      signingConfig = signingConfigs.getByName("debug")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  sourceSets.getByName("main").assets.directories.add(generatedFilterAssetsDir.get().asFile.absolutePath)

  androidResources {
    noCompress += "js"
  }
}

val bunInstall by tasks.registering(Exec::class) {
  group = "filters"
  description = "Install injected script dependencies used for Android assets."
  workingDir(injectedScriptsDir.asFile)
  commandLine(bunCommand, "install")
  inputs.file(injectedScriptsDir.file("package.json"))
}

val buildFilterBundle by tasks.registering(Exec::class) {
  group = "filters"
  description = "Build the injected script bundles for Android assets."
  dependsOn(bunInstall)
  workingDir(injectedScriptsDir.asFile)
  commandLine(bunCommand, "run", "build:filters")
  inputs.dir(injectedScriptsDir.dir("src"))
  inputs.dir(injectedScriptsDir.dir("scripts"))
  inputs.file(injectedScriptsDir.file("tsconfig.json"))
  inputs.file(injectedScriptsDir.file("package.json"))
  outputs.file(injectedScriptsDistDir.file("tsurlfilter.bundle.js"))
  outputs.file(injectedScriptsDistDir.file("webview-injected.bundle.js"))
}

val copyFilterBundle by tasks.registering(Copy::class) {
  group = "filters"
  description = "Copy the injected script bundles into generated Android assets."
  dependsOn(buildFilterBundle)
  from(injectedScriptsDistDir) {
    include("tsurlfilter.bundle.js")
    include("webview-injected.bundle.js")
  }
  into(generatedFilterAssetsDir)
}

tasks.named("preBuild") {
  dependsOn(copyFilterBundle)
}

dependencies {
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.javascriptengine)
  implementation(libs.androidx.preference)
  implementation(libs.androidx.preference.ktx)
  implementation(libs.androidx.swiperefreshlayout)
  implementation(libs.androidx.webkit)
  implementation(libs.material)
}
