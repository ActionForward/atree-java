plugins {
    `java-library`
    antlr
}

group = "com.actionforward"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    antlr("org.antlr:antlr4:4.13.2")
    implementation("org.antlr:antlr4-runtime:4.13.2")
    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// The antlr plugin leaks the ANTLR tool (compiler) onto the api configuration;
// consumers only need the runtime, declared above as `implementation`.
configurations.api {
    setExtendsFrom(extendsFrom.filterNot { it.name == "antlr" })
}

tasks.generateGrammarSource {
    arguments = arguments + listOf("-package", "com.actionforward.atree.grammar", "-no-listener", "-no-visitor")
}

tasks.withType<Test> {
    useJUnitPlatform()
    maxParallelForks = 1
    maxHeapSize = "384m"
}
