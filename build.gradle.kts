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

version = "5.1.4"

description = "BuildBattle-OG" // Declare plugin version (will be in .jar).

val apiVersion = "1.19" // Declare minecraft server target version.

/* ----------------------------- Resources ----------------------------- */
tasks.named<ProcessResources>("processResources") {
    val props = mapOf("version" to version, "apiVersion" to apiVersion)
    inputs.properties(props) // Indicates to rerun if version changes.
    filesMatching("plugin.yml") { expand(props) }
    from("LICENSE.md") { into("/") } // Bundle licenses into jarfiles.
    from("libs/BKCommonLib/LICENSE") { into("META-INF/licenses/BKCommonLib") }
    from("libs/MyWorlds/LICENSE") { into("META-INF/licenses/MyWorlds") }
}

/* ---------------------------- Repos ---------------------------------- */
repositories {
    mavenCentral() // Import the Maven Central Maven Repository.
    gradlePluginPortal() // Import the Gradle Plugin Portal Maven Repository.
    maven { url = uri("https://repo.purpurmc.org/snapshots") } // Import the PurpurMC Maven Repository.
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
    implementation("plugily.projects:MiniGamesBox-Classic:1.4.5") { attributes { attribute(kotlinAttribute, true) } }
}

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
    isEnableRelocation = true
    relocationPrefix = "${project.group}.shadow"
    relocate("com.zaxxer.hikari", "plugily.projects.buildbattle.database.hikari")
    relocate("plugily.projects.minigamesbox", "plugily.projects.buildbattle.minigamesbox")
    archiveClassifier.set("") // Use empty string instead of null.
    minimize()
}

tasks.jar { archiveClassifier.set("part") } // Applies to root jarfile only.

tasks.build { dependsOn(tasks.spotlessApply, tasks.shadowJar) } // Build depends on spotless and shadow.

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
