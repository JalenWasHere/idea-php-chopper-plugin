# Chopper

PhpStorm plugin that toggles a PHP list between one line and one item per line.

```php
public function __construct(private Foo $foo, private Bar $bar, private int $limit = 10) {}
```

```php
public function __construct(
    private Foo $foo,
    private Bar $bar,
    private int $limit = 10,
) {}
```

PhpStorm's built-in "Split comma-separated values into multiple lines" intention only handles arrays. Chopper covers the
other lists.

## Supported lists

| List | Example |
| --- | --- |
| Parameters, including promoted constructor properties and parameter attributes | `function f(int $a, #[Foo] string $b)` |
| Arguments: positional, named and mixed | `foo($a, b: 2)`, `new Foo(...)`, `Foo::bar(...)`, `$x->bar(...)` |
| Attribute arguments | `#[Route(path: '/x', methods: ['GET'])]` |
| `implements` and interface `extends` | `class A implements B, C, D` |
| Closure `use` | `function () use ($a, $b) {}` |
| Arrays | `[1, 2, 3]` |

## Behaviour

- One toggle action: a one-line list is split, a split list is joined. Available as a keyboard shortcut and under Alt+Enter.
- The caret selects the innermost supported list that encloses it. On `foo|Bar($a, $b)` the argument list is toggled.
- Nested lists are left alone. Toggle again with the caret in a nested list to split that one.
- Split adds a trailing comma and join removes it, when the project PHP language level allows one:
  - PHP 7.3+: arguments.
  - PHP 8.0+: parameters and closure `use`.
  - Never: `implements` and `extends`.
  A setting turns trailing commas off.
- Indentation and spacing follow the project code style.
- Comments inside a list are kept. Join is refused when a `//` comment would end up before code.
- One toggle is one undo step.

## Not supported

- Reformat on save or automatic chop-down. PhpStorm's code style wrapping settings cover that.

Candidates for a later version: chained method calls, `&&` / `||` conditions, `match` arms, group `use` imports
(`use A\{B, C}`), recursive split.

## Compatibility

PhpStorm 2021.3 (build 213) and later. 2021.3 introduced the platform list split/join API Chopper extends; older
versions are not supported. The Plugin Verifier checks 2021.3.3 and the local PhpStorm; the tests run on the local
PhpStorm only.

## Design

Kotlin, IntelliJ Platform Gradle Plugin 2.x, depends on `com.jetbrains.php`.

The platform already ships list split/join intentions, driven by the `listSplitJoinContext` extension point. PhpStorm
registers `PhpSplitJoinContext` (a `CommaListSplitJoinContext`) for arrays only. Chopper registers its own PHP context
for the lists above, so the platform intentions handle the editing. If the platform does not accept a second context per
language, Chopper falls back to its own intention and editor action.

## Development

The build compiles against the local PhpStorm (`phpstormPath` in `gradle.properties`) and runs on its bundled JBR, so no
separate JDK is needed. `gradlew` itself still needs `JAVA_HOME`:

```bash
export JAVA_HOME=/Applications/PhpStorm.app/Contents/jbr/Contents/Home
./gradlew test
./gradlew verifyPlugin
./gradlew runIde
```

While `runIde` is open, `./gradlew buildPlugin` in a second terminal reloads the plugin in the sandbox IDE once that
window regains focus. Reload does not work when the sandbox runs under a debugger.

## Known gaps

- Splitting `implements` leaves `{` on the last interface line. PSR-12 puts it on its own line.
- Arrays use PhpStorm's own split, which keeps `[` and `]` on the item lines and adds no trailing comma, unlike the
  other lists.
- No dedicated toggle shortcut yet: split and join are the two platform intentions under Alt+Enter.
