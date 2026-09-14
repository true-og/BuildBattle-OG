/* ------------------------------ Plugins ------------------------------ */
plugins {
    id("java") // Import Java plugin.
    id("java-library") // Import Java Library plugin.
    id("com.diffplug.spotless") version "8.1.0" // Import Spotless plugin.
    id("com.gradleup.shadow") version "8.3.9" // Import Shadow plugin.
    id("checkstyle") // Import Checkstyle plugin.
    eclipse // Import Eclipse plugin.
}

extra["kotlinAttribute"] = Attribute.of("kotlin-tag", Boolean::class.javaObjectType)

val kotlinAttribute: Attribute<Boolean> by rootProject.extra

val bkCommonLibJar = files("libs/BKCommonLib/BKCommonLib-1.19.4-v2.jar")
val myWorldsJar = files("libs/MyWorlds/MyWorlds-1.19.4-v1.jar")

/* --------------------------- JDK / Kotlin ---------------------------- */
java {
    sourceCompatibility = JavaVersion.VERSION_17 // Compile with JDK 17 compatibility.
    toolchain { // Select Java toolchain.
        languageVersion.set(JavaLanguageVersion.of(17)) // Use JDK 17.
        vendor.set(JvmVendorSpec.GRAAL_VM) // Use GraalVM CE.
    }
}

/* ----------------------------- Metadata ------------------------------ */
group = "plugily.projects"

version = "5.1.10"

description = "BuildBattle-OG" // Declare plugin description.

val apiVersion = "1.19" // Declare minecraft server target version.

// Harvest every upstream license shipped alongside a vendored jar in libs/, so adding a library does
// not also require editing this file.
val vendoredLicenseFiles =
    fileTree("libs") {
        include("**/LICENSE", "**/LICENSE.*", "**/License", "**/License.*", "**/COPYING", "**/COPYING.*")
        include("**/NOTICE", "**/NOTICE.*")
        exclude("**/build/**", "**/.git/**")
    }

/* ----------------------------- Resources ----------------------------- */
tasks.named<ProcessResources>("processResources") {
    val props = mapOf("version" to version, "apiVersion" to apiVersion)
    inputs.properties(props) // Indicates to rerun if version changes.
    filesMatching("plugin.yml") { expand(props) }
    from("LICENSE.md") { into("/") } // Bundle licenses into jarfiles.
}

/* ---------------------------- Repos ---------------------------------- */
repositories {
    mavenCentral() // Import the Maven Central Maven Repository.
    gradlePluginPortal() // Import the Gradle Plugin Portal Maven Repository.
    maven { url = uri("https://repo.purpurmc.org/snapshots") } // Import the PurpurMC Maven Repository.
    maven { url = uri("file://${System.getProperty("user.home")}/.m2/repository") }
    System.getProperty("SELF_MAVEN_LOCAL_REPO")?.let { // TrueOG Bootstrap mavenLocal().
        val dir = file(it)
        if (dir.isDirectory) {
            println("Using SELF_MAVEN_LOCAL_REPO at: $it")
            maven { url = uri("file://${dir.absolutePath}") }
        } else {
            logger.error("TrueOG Bootstrap not found, defaulting to ~/.m2 for mavenLocal()")
            mavenLocal()
        }
    } ?: logger.error("TrueOG Bootstrap not found, defaulting to ~/.m2 for mavenLocal()")
    maven { url = uri("https://maven.plugily.xyz/releases") }
    maven { url = uri("https://maven.plugily.xyz/snapshots") }
    maven { url = uri("https://repo.citizensnpcs.co/") }
    maven { url = uri("https://jitpack.io") }
}

/* ---------------------- Java project deps ---------------------------- */
dependencies {
    compileOnly("org.purpurmc.purpur:purpur-api:1.19.4-R0.1-SNAPSHOT") // Declare Purpur API version to be packaged.
    compileOnly("org.jetbrains:annotations:23.0.0")
    compileOnly(myWorldsJar)
    compileOnly(bkCommonLibJar)
    compileOnly("net.citizensnpcs:citizensapi:2.0.26-SNAPSHOT") {
        exclude(group = "ch.ethz.globis.phtree", module = "phtree")
    }
    // Import TrueOG Network Utilities-OG Java API (from source), used for TrueOG color/gradient rendering.
    // NEVER promote this to implementation: Utilities-OG shades the Kotlin stdlib and coroutines, and with
    // isEnableRelocation = true below they would be rewritten under plugily.projects.shadow.* and break at
    // runtime. compileOnly/compileOnlyApi stay off runtimeClasspath, so Shadow never bundles or relocates them.
    compileOnlyApi(project(":libs:Utilities-OG"))
    compileOnly(files("libs/Chat-OG/Chat-OG.jar")) // Import Chat-OG API for world chat formatting.
    compileOnly(files("libs/Scoreboard-OG/Scoreboard-OG.jar")) // Import Scoreboard-OG sidebar API.
    implementation("plugily.projects:MiniGamesBox-Classic:1.4.5")
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.3") // Add JUnit API to testing environment.
    // compileOnly/compileOnlyApi do not propagate to the test classpath, so restate what the tests need.
    testImplementation("org.purpurmc.purpur:purpur-api:1.19.4-R0.1-SNAPSHOT") // Adventure lives here.
    testImplementation(project(":libs:Utilities-OG")) // The colorizer under test.
    testRuntimeOnly("org.junit.platform:junit-platform-launcher") // Add JUnit engine to the testing runtime.
}

apply(from = "eclipse.gradle.kts") // Import eclipse classpath support script.

/* ---------------------- Reproducible jars ---------------------------- */
tasks.withType<AbstractArchiveTask>().configureEach { // Ensure reproducible .jars
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

/* ----------------------------- Shadow -------------------------------- */
tasks.shadowJar {
    exclude("io.github.miniplaceholders.*") // Exclude the MiniPlaceholders package from being shadowed.
    exclude("plugily/projects/minigamesbox/classic/utils/services/locale/LocaleService.class")
    exclude("plugily/projects/minigamesbox/classic/utils/services/locale/LocaleService$*.class")
    exclude("plugily/projects/minigamesbox/classic/utils/services/ServiceRegistry.class")
    exclude("plugily/projects/minigamesbox/classic/utils/services/metrics/Metrics.class")
    exclude("plugily/projects/minigamesbox/classic/utils/services/metrics/Metrics$*.class")
    exclude("plugily/projects/minigamesbox/classic/utils/services/metrics/MetricsService.class")
    exclude("plugily/projects/minigamesbox/classic/utils/services/metrics/MetricsService$*.class")
    exclude("plugily/projects/minigamesbox/classic/utils/services/UpdateChecker.class")
    exclude("plugily/projects/minigamesbox/classic/utils/services/UpdateChecker$*.class")
    from("LICENSE.md") {
        into("META-INF/licenses/BuildBattle-OG")
        includeEmptyDirs = false
    }
    from(vendoredLicenseFiles) {
        into("META-INF/licenses/libs")
        includeEmptyDirs = false
    }
    isEnableRelocation = true
    relocationPrefix = "${project.group}.shadow"
    relocate("com.zaxxer.hikari", "plugily.projects.buildbattle.database.hikari")
    relocate("plugily.projects.minigamesbox", "plugily.projects.buildbattle.minigamesbox")
    archiveClassifier.set("") // Use empty string instead of null.
    minimize()
}

tasks.jar { archiveClassifier.set("part") } // Applies to root jarfile only.

tasks.build { dependsOn(tasks.spotlessApply, tasks.shadowJar) } // Build depends on spotless and shadow.

/* --------------------------------- Testing ---------------------------- */
tasks.withType<Test>().configureEach {
    useJUnitPlatform() // Enable testing with JUnit 5.
}

/* --------------------------- Javac opts ------------------------------- */
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters") // Enable reflection for java code.
    options.isFork = true // Run javac in its own process.
    options.compilerArgs.add("-Xlint:deprecation") // Trigger deprecation warning messages.
    options.encoding = "UTF-8" // Use UTF-8 file encoding.
}

/* ----------------------------- Auto Formatting ------------------------ */
spotless {
    java {
        eclipse().configFile("config/formatter/eclipse-java-formatter.xml") // Eclipse java formatting.
        leadingTabsToSpaces() // Convert leftover leading tabs to spaces.
        removeUnusedImports() // Remove imports that aren't being called.
    }
    kotlinGradle {
        ktfmt().kotlinlangStyle().configure { it.setMaxWidth(120) } // JetBrains Kotlin formatting.
        target("build.gradle.kts", "settings.gradle.kts") // Gradle files to format.
    }
}

checkstyle {
    toolVersion = "10.18.1" // Declare checkstyle version to use.
    configFile = file("config/checkstyle/checkstyle.xml") // Point checkstyle to config file.
    isIgnoreFailures = true // Don't fail the build if checkstyle does not pass.
    isShowViolations = true // Show the violations in any IDE with the checkstyle plugin.
}

tasks.named("compileJava") {
    dependsOn("spotlessApply") // Run spotless before compiling with the JDK.
}

tasks.named("spotlessCheck") {
    dependsOn("spotlessApply") // Run spotless before checking if spotless ran.
}

/* ------------------------------ Eclipse SHIM ------------------------- */

// This can't be put in eclipse.gradle.kts because Gradle is weird.
// Qualifying subproject names keeps :libs:Utilities-OG from colliding with the standalone
// Utilities-OG project that also lives in the Eclipse workspace.
subprojects {
    // ":libs" is only an implicit container created by include(":libs:Utilities-OG"). Applying the
    // Java plugin to it produced a junk libs/build/libs/libs-BuildBattle-OG.jar next to the vendored
    // dependency directories.
    if (project.path == ":libs") {
        return@subprojects
    }
    apply(plugin = "java-library")
    apply(plugin = "eclipse")
    eclipse.project.name = "${project.name}-${rootProject.name}"
    tasks.withType<Jar>().configureEach { archiveBaseName.set("${project.name}-${rootProject.name}") }
}
