plugins {
    java
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

group = "me.marti"
version = "1.1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://repo.dmulloy2.net/repository/public/")
    maven("https://repo.nexomc.com/releases")
}

sourceSets {
    main {
        java.setSrcDirs(listOf("src/main/java", "src/paper/java"))
        resources.setSrcDirs(listOf("src/main/resources"))
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("net.luckperms:api:5.4")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("net.dmulloy2:ProtocolLib:5.4.0")
    compileOnly("com.nexomc:nexo:1.8.0")
    compileOnly("net.dv8tion:JDA:5.2.1")
    compileOnly("redis.clients:jedis:5.2.0")
    compileOnly("com.mysql:mysql-connector-j:9.4.0")
    compileOnly("org.xerial:sqlite-jdbc:3.50.3.0")

    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation("net.luckperms:api:5.4")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testImplementation("org.mockito:mockito-core:5.12.0")
    testImplementation("redis.clients:jedis:5.2.0")
    testImplementation("org.xerial:sqlite-jdbc:3.50.3.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.processResources {
    inputs.property("version", project.version)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.runServer {
    minecraftVersion("1.21.11")
}
