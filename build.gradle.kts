plugins {
    `java-library`
    antlr
    id("me.champeau.jmh") version "0.7.2"
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

// Kept modest so `devbox run jmh` finishes in minutes by default; bump these before
// trusting a result for anything other than local iteration.
jmh {
    warmupIterations = 2
    warmup = "1s"
    iterations = 3
    timeOnIteration = "1s"
    fork = 1
    benchmarkMode.add("avgt")
    timeUnit = "us"
    resultFormat = "TEXT"
}

// me.champeau.jmh 0.7.2's jmhJar task serializes a Project reference, which the configuration
// cache (enabled repo-wide in gradle.properties) rejects; opt it out rather than the whole build.
tasks.named("jmhJar") {
    notCompatibleWithConfigurationCache("me.champeau.jmh jmhJar leaks a Project reference (plugin issue)")
}
