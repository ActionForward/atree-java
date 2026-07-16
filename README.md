# atree

[![Tests](https://gist.githubusercontent.com/neoXfire/bbf3758964337822462500444a3a6b3e/raw/badge.svg)](https://github.com/ActionForward/ai-automation/actions/workflows/ci.yml)

A Java library implementing **A-Tree**, a dynamic data structure for efficiently indexing and
matching large numbers of **arbitrary Boolean expressions**, from
[Ji & Jacobsen, *A-Tree: A Dynamic Data Structure for Efficiently Indexing Arbitrary Boolean
Expressions*, SIGMOD 2021](https://doi.org/10.1145/3448016.3457266).

An A-Tree indexes expressions built from predicates over user-named attributes, combined with
`and`, `or`, `not`, `xor` and `xnor`. Matching an *event* (a set of attribute–value bindings)
against **all** subscribed expressions is a single bottom-to-top traversal in which shared
predicates and subexpressions are evaluated once. The index is fully dynamic: expressions can be
inserted and removed at any time, and the structure continuously reorganizes itself to maximize
sharing (node uniqueness, expression reorganization, index self-adjustment), with the paper's
matching optimizations — zero suppression filter and propagation on demand — always enabled.

## Usage

### Building and matching expressions

```java
ATree<String> tree = new ATree<>();

// (age between 16 and 18) and city in ('paris', 'lyon')
tree.subscribe("teens-in-town", Expr.and(
        Expr.of(Predicate.of("age", Op.BETWEEN, 16, 18)),
        Expr.of(Predicate.of("city", Op.IN, "paris", "lyon"))));

tree.subscribe("no-errors", Expr.not(Expr.of(Predicate.of("level", Op.EQ, "error"))));

Set<String> matches = tree.match(Event.of("age", 17, "city", "paris", "level", "info"));
// -> ["teens-in-town", "no-errors"]

tree.unsubscribe("no-errors");
```

Attribute names (`age`, `city`, …) are chosen freely by you. A predicate whose attribute is
absent from an event evaluates to *undefined*, which never satisfies an expression — even
through negation.

### Lazily computed attribute values

Any attribute can be backed by a `Supplier` instead of a static value. The supplier is invoked
at most once per event, and only if the index actually contains a predicate referencing that
attribute:

```java
Event event = Event.builder()
        .with("city", "paris")
        .supply("age", () -> expensiveProfileLookup().age())
        .build();
tree.match(event);
```

### Parsing expressions from text

`ExpressionParser` (ANTLR-based) parses the expression language directly:

```java
ExpressionParser parser = new ExpressionParser();
Expr expr = parser.parse("(age between 16 and 18) and city in ('paris', 'lyon') or not vip = 'yes'");
```

Values are numbers or quoted strings; relational operators are `< <= > >= = != <>`, `in` and
`between` (both combinable with `not`). Precedence, tightest first: `not`, `and`, `xor`/`xnor`,
`or`.

Every word operator of the language can be renamed (or given synonyms) through
`ExpressionVocabulary`, without touching the grammar:

```java
ExpressionVocabulary french = ExpressionVocabulary.builder()
        .word(Word.AND, "et")
        .word(Word.OR, "ou")
        .word(Word.NOT, "non", "pas")
        .word(Word.IN, "dans")
        .word(Word.BETWEEN, "entre")
        .build();
Expr expr = new ExpressionParser(french)
        .parse("(age entre 16 et 18) et ville dans ('paris', 'lyon')");
```

## Requirements

- **[Devbox](https://www.jetify.com/devbox)** — the only tool you need to install yourself. It
  provisions the rest of the toolchain (JDK, Gradle), so you don't need Java or Gradle on your
  host.

## Stack

- **Java** 25 (Eclipse Temurin), no runtime dependency besides the ANTLR runtime
- **ANTLR** 4 for the expression grammar
- **Gradle** with the Kotlin DSL (`build.gradle.kts`), `java-library` + `antlr` plugins
- **Devbox** to provision the toolchain (JDK, Gradle) and expose convenience scripts

## Getting started

### 1. Install Devbox

```bash
curl -fsSL https://get.jetify.com/devbox | bash
```

See the [official install docs](https://www.jetify.com/devbox/docs/installing_devbox/) for other
install methods (Nix, Homebrew, etc.).

### 2. Launch a shell

```bash
# Enter a shell with the JDK and Gradle on PATH
devbox shell
```

### 3. Build and test

Either from inside `devbox shell`, or as one-off commands without entering the shell:

```bash
devbox run build
devbox run test
```

Available scripts (defined in `devbox.json`):

| Script  | Description                                        |
|---------|-----------------------------------------------------|
| `build` | `gradle assemble testClasses` — generates the ANTLR sources, compiles main and test sources and builds the jar, without running tests |
| `test`  | `gradle test` — runs the tests compiled by `build`   |
| `clean` | `gradle clean`                                       |

These same `devbox run <script>` commands can be used from CI or from Claude Code, so the
toolchain doesn't need to be installed separately on the host.

## CI

`.github/workflows/ci.yml` runs `devbox run -- gradle build --no-daemon` on the self-hosted
`netcup` runner. Devbox must already be installed on that runner (e.g. via
`curl -fsSL https://get.jetify.com/devbox | bash`) — the workflow does not install it per-job.

Test results are published via
[`EnricoMi/publish-unit-test-result-action`](https://github.com/EnricoMi/publish-unit-test-result-action)
as a check summary and, on pushes to `main`, as the badge above. The badge SVG is hosted on
[this Gist](https://gist.github.com/neoXfire/bbf3758964337822462500444a3a6b3e) and requires a
repo secret named `GIST_TOKEN` (a personal access token with `gist` scope) to be set for the
upload step to work.
