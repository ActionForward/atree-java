# atree

Java library implementing the A-Tree data structure (Ji & Jacobsen, SIGMOD 2021) for indexing
and matching arbitrary Boolean expressions against events, with an ANTLR-based expression parser
whose word operators are user-redefinable.

## Toolchain

This project uses [Devbox](https://www.jetify.com/devbox) to provision the JDK and Gradle. Do not assume `java` or `gradle` are on the host PATH — always run commands via `devbox run <script>` or from inside `devbox shell`.

CI (`.github/workflows/ci.yml`) assumes `devbox` is already installed on the self-hosted runner's PATH — it is not installed per-job.

## Common commands

Defined in `devbox.json`:

| Command            | Effect            |
|---------------------|-------------------|
| `devbox run build`  | `gradle assemble testClasses` — generates ANTLR sources, compiles main and test sources and builds the jar, without running tests |
| `devbox run test`   | `gradle test` — runs the tests compiled by `build` |
| `devbox run jmh`    | `gradle jmh` — runs the JMH performance benchmarks under `src/jmh/` |
| `devbox run clean`  | `gradle clean`    |

## Project structure

- `src/main/java/com/actionforward/atree/` — core: `ATree` (the index), `Expr` (expression AST),
  `Predicate`, `Op`, `Event` (static values + memoized `Supplier`s); package-private `Node` and
  `NormExpr` (zero-suppression normal form and order-insensitive expression IDs)
- `src/main/java/com/actionforward/atree/parser/` — `ExpressionParser` (ANTLR),
  `ExpressionVocabulary` (user-redefinable operator words), `ExpressionParseException`
- `src/main/antlr/com/actionforward/atree/grammar/BoolExpr.g4` — grammar; word operators are
  imaginary tokens produced by re-typing IDENTs against the vocabulary, never lexer keywords
- `src/test/java/` — JUnit 5 tests, including a randomized differential test against a
  three-valued brute-force evaluator (`ATreeTests.randomizedExpressionsMatchBruteForce`)
- `src/jmh/java/com/actionforward/atree/jmh/` — JMH benchmarks (`me.champeau.jmh` plugin):
  `IndexBuildBenchmark` (`subscribe`, Alg. 2–4), `MatchBenchmark` (`match`, Alg. 6),
  `UnsubscribeBenchmark` (`unsubscribe`, Alg. 5), sharing seeded random expression/event
  generators from the package-private `Corpus`. Default iteration counts in `build.gradle.kts`
  are kept low for fast local runs; raise them before trusting a result

## Conventions

- Package root: `com.actionforward.atree`; generated ANTLR code goes to
  `com.actionforward.atree.grammar` (configured in `build.gradle.kts`, not in the grammar file)
- Gradle build file uses the Kotlin DSL (`build.gradle.kts`), not Groovy
- Java 25 (Eclipse Temurin), `java-library` + `antlr` plugins, JUnit 5
- The library keeps the paper's vocabulary in comments/Javadoc (l-node/i-node/r-node, zero
  suppression, propagation on demand, access child) — keep citing section/algorithm numbers when
  touching `ATree`
