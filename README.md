# ai-automation demo

[![Tests](https://gist.githubusercontent.com/neoXfire/bbf3758964337822462500444a3a6b3e/raw/badge.svg)](https://github.com/ActionForward/ai-automation/actions/workflows/ci.yml)

A minimal Spring Boot demo exposing a single `/hello-world` endpoint.

## Requirements

- **[Devbox](https://www.jetify.com/devbox)** — the only tool you need to install yourself. It provisions the rest of the toolchain (JDK, Gradle), so you don't need Java or Gradle on your host.

## Stack

- **Spring Boot** 4.1.0
- **Java** 25 (Eclipse Temurin)
- **Gradle** with the Kotlin DSL (`build.gradle.kts`)
- **Devbox** to provision the toolchain (JDK, Gradle) and expose convenience scripts

## Getting started

### 1. Install Devbox

```bash
curl -fsSL https://get.jetify.com/devbox | bash
```

See the [official install docs](https://www.jetify.com/devbox/docs/installing_devbox/) for other install methods (Nix, Homebrew, etc.).

### 2. Launch a shell

```bash
# Enter a shell with the JDK and Gradle on PATH
devbox shell
```

### 3. Build, test, or run

Either from inside `devbox shell`, or as one-off commands without entering the shell:

```bash
devbox run build
devbox run test
devbox run run
```

Available scripts (defined in `devbox.json`):

| Script  | Description                                        |
|---------|-----------------------------------------------------|
| `build` | `gradle assemble testClasses` — compiles main and test sources and builds the jar, without running tests |
| `test`  | `gradle test` — runs the tests compiled by `build`   |
| `run`   | `gradle bootRun`                                     |
| `clean` | `gradle clean`                                       |

These same `devbox run <script>` commands can be used from CI or from Claude Code, so the toolchain doesn't need to be installed separately on the host.

## CI

`.github/workflows/ci.yml` runs `devbox run -- gradle build --no-daemon` on the self-hosted `netcup` runner. Devbox must already be installed on that runner (e.g. via `curl -fsSL https://get.jetify.com/devbox | bash`) — the workflow does not install it per-job.

Test results are published via [`EnricoMi/publish-unit-test-result-action`](https://github.com/EnricoMi/publish-unit-test-result-action) as a check summary and, on pushes to `main`, as the badge above. The badge SVG is hosted on [this Gist](https://gist.github.com/neoXfire/bbf3758964337822462500444a3a6b3e) and requires a repo secret named `GIST_TOKEN` (a personal access token with `gist` scope) to be set for the upload step to work.

## Endpoint

```
GET /hello-world -> "Hello, World!"
```

Once running (`devbox run run`), try:

```bash
curl http://localhost:8080/hello-world
```

## Notes

Exact "latest" Spring Boot / Gradle / JDK patch versions were selected from training knowledge since this environment had no outbound network access at the time this project was scaffolded. Run `devbox update` and bump `build.gradle.kts` plugin versions if newer patches are available.
