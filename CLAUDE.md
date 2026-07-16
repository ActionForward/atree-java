# ai-automation demo

Minimal Spring Boot demo exposing a single `/hello-world` endpoint.

## Toolchain

This project uses [Devbox](https://www.jetify.com/devbox) to provision the JDK and Gradle. Do not assume `java` or `gradle` are on the host PATH — always run commands via `devbox run <script>` or from inside `devbox shell`.

CI (`.github/workflows/ci.yml`) assumes `devbox` is already installed on the self-hosted runner's PATH — it is not installed per-job.

## Common commands

Defined in `devbox.json`:

| Command            | Effect            |
|---------------------|-------------------|
| `devbox run build`  | `gradle assemble testClasses` — compiles main and test sources and builds the jar, without running tests |
| `devbox run test`   | `gradle test` — runs the tests compiled by `build` |
| `devbox run run`    | `gradle bootRun`  |
| `devbox run clean`  | `gradle clean`    |

## Project structure

- `src/main/java/com/actionforward/demo/` — `DemoApplication.java` (entrypoint), `HelloWorldController.java`
- `src/test/java/com/actionforward/demo/` — `DemoApplicationTests.java`, `HelloWorldControllerTests.java` (JUnit Platform)

## Conventions

- Package root: `com.actionforward.demo`
- Gradle build file uses the Kotlin DSL (`build.gradle.kts`), not Groovy
- Spring Boot 4.1.0 on Java 25 (Eclipse Temurin)
