allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://raw.githubusercontent.com/saki4510t/libcommon/master/repository/") }
    }
}

val newBuildDir: Directory =
    rootProject.layout.buildDirectory
        .dir("../../build")
        .get()
rootProject.layout.buildDirectory.value(newBuildDir)

subprojects {
    val newSubprojectBuildDir: Directory = newBuildDir.dir(project.name)
    project.layout.buildDirectory.value(newSubprojectBuildDir)
}
subprojects {
    if (project.name != "libausbc" && project.name != "libuvc" && project.name != "libnative") {
        project.evaluationDependsOn(":app")
    }
}

// Define extras for imported modules
ext {
    set("versionCompiler", 34)
    set("versionTarget", 34)
    set("minSdkVersion", 24)
    set("versionCode", 1)
    set("versionNameString", "1.0")
    set("versionBuildTool", "34.0.0")
    set("javaSourceCompatibility", JavaVersion.VERSION_1_8)
    set("javaTargetCompatibility", JavaVersion.VERSION_1_8)
    set("androidXVersion", "1.6.1")
    set("constraintlayoutVersion", "2.1.4")
    set("materialVersion", "1.10.0")
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
