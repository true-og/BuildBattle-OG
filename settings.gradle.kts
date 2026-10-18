rootProject.name = "BuildBattle-OG"

// Deliberately does NOT run bootstrap.sh: that would break source tarballs (no .git), break Windows
// (no `sh`), and `git submodule update --force` on every build would discard in-tree submodule edits.
// Fail with instructions instead, matching Splegg-OG.
val requiredLibraries = listOf("MyWorlds", "Utilities-OG")
val missingLibraries = requiredLibraries.filter { libraryName -> !file("libs/$libraryName/build.gradle.kts").exists() }

if (missingLibraries.isNotEmpty()) {
    throw GradleException(
        "Missing initialized git submodules: ${missingLibraries.joinToString(", ")}. " +
            "Run ./bootstrap.sh or `git submodule update --init --recursive` before building."
    )
}

// Only directories that carry their own build script become subprojects. This deliberately skips the
// vendored jar directories (libs/BKCommonLib, libs/MyWorlds), which are consumed via compileOnly(files(...)).
file("libs")
    .listFiles()
    ?.filter { directory ->
        directory.isDirectory && !directory.name.startsWith(".") && file("${directory.path}/build.gradle.kts").exists()
    }
    ?.forEach { directory ->
        include(":libs:${directory.name}")
        project(":libs:${directory.name}").projectDir = directory
    }
