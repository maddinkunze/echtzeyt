import org.jetbrains.kotlin.gradle.internal.ensureParentDirsCreated
import org.apache.commons.io.FileUtils
import java.net.URI
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}


// Compile map

val osmosisPluginSourceDir = project.layout.projectDirectory.dir("src").dir("osmosis")
val compileMapDependencies = project.configurations.create("compileMap").extendsFrom(project.configurations.compileOnly.get()).also { it.isCanBeResolved = true }
val compileMapPluginDependencies = project.configurations.create("compileMapPlugin").extendsFrom(compileMapDependencies)
dependencies {
    compileMapPluginDependencies("org.openstreetmap.osmosis:osmosis-core:0.49.2")
    compileMapDependencies("org.openstreetmap.osmosis:osmosis-xml:0.49.2")
    compileMapDependencies("org.mapsforge:mapsforge-map-writer:0.23.0:jar-with-dependencies")
}

val compileMapPluginSource = sourceSets.create("osmosisMapPlugin") {
    compileClasspath = compileMapPluginDependencies
    java.srcDir(osmosisPluginSourceDir.dir("java"))
}

@Suppress("LeakingThis")
abstract class TaskDownloadRawMap : DefaultTask() {
    @get:Input
    abstract val lonMin: Property<Double>
    @get:Input
    abstract val latMin: Property<Double>
    @get:Input
    abstract val lonMax: Property<Double>
    @get:Input
    abstract val latMax: Property<Double>
    @get:Input
    abstract val fileOutName: Property<String>
    @get:Input
    abstract val fileOutExt: Property<String>
    @get:Internal
    var invalidateAfter: Duration
    @get:Internal
    abstract val dirOut: DirectoryProperty
    //@get:Internal
    private val fileLastUpdate by lazy { dirOut.file("lastUpdated").get().asFile }

    @get:OutputFile
    abstract val fileOut: RegularFileProperty

    init {
        dirOut.convention(project.layout.buildDirectory.get().dir("tmp").dir("osm"))
        fileOutName.convention("area")
        fileOutExt.convention("osm")
        fileOut.convention {
            val fileName = fileOutName.get() + fileOutExt.get().let { if (it.isBlank()) { "" } else { ".$it" } }
            dirOut.file(fileName).get().asFile
        }
        fileOut.disallowChanges()
        invalidateAfter = (5).days
        outputs.upToDateWhen { !shouldUpdate() }
    }

    protected fun createDownloadUrl() = "https://overpass-api.de/api/map?bbox=${lonMin.get()},${latMin.get()},${lonMax.get()},${latMax.get()}"

    protected fun shouldUpdate() : Boolean {
        if (!fileLastUpdate.exists()) { return true }
        try {
            val lastUpdate = fileLastUpdate.readText().toLong()
            return (Instant.now().epochSecond > lastUpdate + invalidateAfter.inWholeSeconds)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        return true
    }

    protected fun wasUpdated() {
        try {
            fileLastUpdate.ensureParentDirsCreated()
            fileLastUpdate.writeText("${Instant.now().epochSecond}")
        } catch(e: Throwable) {
            e.printStackTrace()
        }
    }

    @TaskAction
    fun download() {
        val fileOut = fileOut.get().asFile
        fileOut.ensureParentDirsCreated()
        val urlDownload = createDownloadUrl()
        project.logger.info("Downloading raw map data from OSM: $urlDownload -> $fileOut")
        try {
            URI(urlDownload).toURL().openStream().copyTo(fileOut.outputStream())
        } catch (e: Exception) {
            if (fileOut.exists() && fileOut.isFile && FileUtils.sizeOf(fileOut) > 0) {
                logger.error("Could not download new map data from OSM:")
                e.printStackTrace()
                return
            }
            throw e
        }
        wasUpdated()
        project.logger.info("Map download finished")
    }
}

@Suppress("LeakingThis")
abstract class TaskCompileMap : JavaExec() {
    @get:InputFile
    abstract val fileOsm: RegularFileProperty
    @get:InputFile
    abstract val fileTagMapping: RegularFileProperty
    @get:Input
    abstract val fileOutName: Property<String>
    @get:Input
    abstract val fileOutExt: Property<String>
    @get:OutputDirectory
    abstract val dirOut: DirectoryProperty

    @get:OutputFile
    abstract val fileOut: RegularFileProperty
    @get:Internal
    val nameFileOut by lazy { fileOutName.get() + fileOutExt.get().let { if (it.isBlank()) { "" } else { ".$it" } } }
    @get:Internal
    val resFileOut by lazy { "@raw/${fileOutName.get()}" }

    init {
        mainClass.convention("org.openstreetmap.osmosis.core.Osmosis")

        dirOut.convention(project.layout.projectDirectory.dir("src").dir("main").dir("res").dir("raw"))
        fileOutName.convention("area")
        fileOutExt.convention("map")
        fileOut.convention {
            dirOut.file(nameFileOut).get().asFile
        }
        fileOut.disallowChanges()
    }

    @TaskAction
    override fun exec() {
        val fileOut = fileOut.asFile.get()
        fileOut.ensureParentDirsCreated()

        args("--read-xml", "file=\"${fileOsm.asFile.get().absolutePath}\"")
        args("--map-complete")
        args("--mapfile-writer", "file=\"${fileOut.absolutePath}\"", "tag-conf-file=\"${fileTagMapping.asFile.get().absolutePath}\"", "tag-values=true", "simplification-factor=5")
        super.exec()
    }

    fun registerRunPreBuild() {
        project.tasks.preBuild.configure { dependsOn(this@TaskCompileMap) }
    }
}

private fun getDouble(key: String, default: Double) = properties[key]?.let { (it as? Double) ?: (it as? String)?.toDoubleOrNull() } ?: default
val taskDownloadRawMap = task<TaskDownloadRawMap>("downloadOsmMap") {
    latMin = getDouble("echtzeyt.latMin", 13.318)
    latMax = getDouble("echtzeyt.latMax", 13.522)
    lonMin = getDouble("echtzeyt.lonMin", 52.477)
    lonMax = getDouble("echtzeyt.lonMax", 52.547)
}

val taskCompileOsmosisPlugin = task<JavaCompile>("compileOsmosisMapCompletePlugin") {
    source = compileMapPluginSource.java
    classpath = compileMapPluginSource.compileClasspath
    destinationDirectory = project.layout.buildDirectory.get().dir("intermediates").dir("osmosis")
}

val taskPackageOsmosisPlugin = task<Jar>("packageOsmosisMapCompletePlugin") {
    val pluginResources = osmosisPluginSourceDir.dir("res")
    from(taskCompileOsmosisPlugin.destinationDirectory, pluginResources.file("plugin.xml"), pluginResources.file("osmosis-plugins.conf"))
    destinationDirectory = project.layout.buildDirectory.get().dir("outputs").dir("jar")
    archiveFileName = "mapcomplete.jar"
}

val taskCompileMap = task<TaskCompileMap>("compileMap") {
    classpath(taskPackageOsmosisPlugin.archiveFile, compileMapDependencies)
    fileOsm = taskDownloadRawMap.fileOut
    fileTagMapping = osmosisPluginSourceDir.dir("res").file("tag-mapping.xml")
    registerRunPreBuild()
}


// Compile echtzeyt library

android {
    compileSdk = 35

    defaultConfig {
        minSdk = 21
        multiDexEnabled = true
        vectorDrawables {
            useSupportLibrary = true
        }
        resValue("raw", "fileMapRaw", taskCompileMap.resFileOut)
        resValue("string", "fileMapExtracted", taskCompileMap.nameFileOut)
        resValue("string", "_libraryEchtzeytVersion", "${defaultConfig.versionName}")
        buildConfigField("String", "LIBRARY_VERSION_NAME", "\"${defaultConfig.versionName}\"")
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    namespace = "com.maddin.echtzeyt"
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:${rootProject.extra.get("kotlinVersion")}")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.multidex:multidex:2.0.1")
    implementation("androidx.startup:startup-runtime:1.2.0")

    api("org.mapsforge:vtm-android:0.23.0")
    runtimeOnly("org.mapsforge:vtm-android:0.23.0:natives-armeabi-v7a")
    runtimeOnly("org.mapsforge:vtm-android:0.23.0:natives-arm64-v8a")
    runtimeOnly("org.mapsforge:vtm-android:0.23.0:natives-x86")
    runtimeOnly("org.mapsforge:vtm-android:0.23.0:natives-x86_64")

    api(project(path=":transportapi"))
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
}