plugins {
    `java-library`
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.14"
    id("xyz.jpenilla.run-paper") version "2.3.0"
}

group = "me.marti"
version = "1.0.5-SNAPSHOT-FIX"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/groups/public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://repo.dmulloy2.net/repository/public/")
    maven("https://repo.nexomc.com/releases")
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

// ── Configurations (must be declared before sourceSets that reference them) ───
val arclightExtraDeps by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

// ── Source sets ───────────────────────────────────────────────────────────────
// main     : shared code compiled with Paper API
// paper    : Paper-specific listeners + VChat entry for Paper
// arclight : Bukkit-compatible listeners + VChat entry for Arclight

sourceSets {
    named("main") {
        java.srcDirs("src/main/java")
        resources.srcDirs("src/main/resources")
    }

    create("paper") {
        java.srcDirs("src/paper/java")
        resources.srcDirs("src/paper/resources")
        compileClasspath += sourceSets["main"].output + sourceSets["main"].compileClasspath
        runtimeClasspath += sourceSets["main"].output
    }

    create("arclight") {
        java.srcDirs("src/arclight/java")
        resources.srcDirs("src/arclight/resources")
        compileClasspath += sourceSets["main"].output + arclightExtraDeps
        runtimeClasspath += sourceSets["main"].output
    }
}

// ── Dependencies ──────────────────────────────────────────────────────────────
dependencies {
    // Paper (main + paper source sets)
    paperweight.paperDevBundle("1.21.4-R0.1-SNAPSHOT")
    compileOnly("net.luckperms:api:5.4")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("net.dmulloy2:ProtocolLib:5.4.0")
    compileOnly("com.nexomc:nexo:1.8.0")
    compileOnly("net.dv8tion:JDA:5.2.1")
    implementation("net.kyori:adventure-text-minimessage:4.17.0")

    // Arclight (Spigot 1.21.1, no Paper extras)
    arclightExtraDeps("org.spigotmc:spigot-api:1.21.1-R0.1-SNAPSHOT")
    arclightExtraDeps("net.luckperms:api:5.4")
    arclightExtraDeps("me.clip:placeholderapi:2.11.6")
    arclightExtraDeps("net.dmulloy2:ProtocolLib:5.4.0")
    arclightExtraDeps("net.dv8tion:JDA:5.2.1")
    arclightExtraDeps("net.kyori:adventure-text-minimessage:4.17.0")
    arclightExtraDeps("net.kyori:adventure-text-serializer-legacy:4.17.0")
    arclightExtraDeps("net.kyori:adventure-text-serializer-plain:4.17.0")
    arclightExtraDeps("net.kyori:adventure-text-serializer-gson:4.17.0")

    // Tests
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testImplementation("org.mockito:mockito-core:5.12.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

// ── Resources ─────────────────────────────────────────────────────────────────
val props = mapOf("version" to version)

tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") { expand(props) }
}

tasks.named<ProcessResources>("processArclightResources") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") { expand(props) }
}

tasks.named<ProcessResources>("processPaperResources") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") { expand(props) }
}

// ── JARs ──────────────────────────────────────────────────────────────────────

val paperJar by tasks.registering(Jar::class) {
    archiveBaseName.set("vChat")
    archiveClassifier.set("paper")
    dependsOn(tasks.classes, tasks.named("paperClasses"), tasks.named("processPaperResources"))
    from(sourceSets["main"].output)
    from(sourceSets["paper"].output)
    from(sourceSets["paper"].output.resourcesDir)
    from(tasks.processResources.get().destinationDir)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

val arclightJar by tasks.registering(Jar::class) {
    archiveBaseName.set("vChat-arclight")
    archiveClassifier.set("")
    dependsOn(tasks.classes, tasks.named("arclightClasses"), tasks.named("processArclightResources"))
    from(sourceSets["main"].output)
    from(sourceSets["arclight"].output)
    from(sourceSets["arclight"].output.resourcesDir)
    from(tasks.processResources.get().destinationDir)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    // Bundle adventure libs since Arclight doesn't provide them natively
    from(arclightExtraDeps.filter {
        it.name.contains("adventure") || it.name.contains("minimessage") || it.name.contains("gson")
    }.map { zipTree(it) })
}

tasks.test {
    useJUnitPlatform()
}

tasks.build {
    dependsOn(paperJar, arclightJar)
}
