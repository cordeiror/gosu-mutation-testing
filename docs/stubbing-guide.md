# Stubbing guide: running classes that depend on types you do not have

Instructions for AI coding agents (and people) who need to mutation test a Gosu class taken from a
larger code base with `gosu-mutation class` (or `./gradlew :mutation-cli:mutateClass`).

Such a class usually references types that only exist inside its own code base or runtime: other
classes, enum-like types, utility classes, test base classes. Open source Gosu cannot resolve them,
so the baseline fails to compile. Minimal stand-ins ("stubs") for those types let the class and its
tests compile and run unchanged.

**Stubs are never committed to this repository.** They are derived from someone else's code base
and live next to the trial project, outside this repository.

## When to create stubs

Run the command first:

```bash
gosu-mutation class --source <class .gs> [--test <test .gs>] --trial-dir <trial dir>
```

Stubs are needed when the baseline fails with errors such as:

```
error: "SomeType" is not a valid type.
error: No type actions found in package com.example.feature
error: Could not resolve symbol for : SomeType
error: "somepackage" is not a valid type.
error: No function defined for assertEquals.
```

Errors in your own test code are not a stub problem; fix the tests instead.

Many errors only follow from a missing type: `Could not resolve symbol` for a local variable,
field or block parameter the class declares itself (`var current = vehicle.Value` when `Vehicle`
is missing), or `Expecting ":" clause for function declaration` on a block such as
`\opt -> opt.Value`. Do not stub those names; they disappear once the types exist.

To check stubs quickly, add `--baseline-only`: the class and its tests are compiled and run, but
nothing is mutated. Every compile error is reported, not only the first 100.

## Where they go

- A folder outside this repository, for example `<trials>/stubs`, holding Gosu sources by package:
  `<trials>/stubs/com/example/util/DateUtils.gs`.
- Pass it with `--stubs-dir <folder>` (`-PstubsDir` for the Gradle task), or put it at
  `<trial dir>/src/stubs/gosu`, which is picked up automatically.
- Stubs are compiled together with the tests and are never mutated.

## Rules

1. **Never edit the class under test or the original tests.** If something cannot compile without
   changing them, stop and report it.
2. **Same package, same name, same signatures** as used by the class and its tests. Nothing more:
   only the members that are actually referenced.
3. **No real implementation.** Do not copy code from the original code base. Write the smallest
   behaviour that the class and tests need.
4. **State every assumption** in the stub's doc comment, starting with `STUB:`. Report the list of
   assumptions to the user: mutation results are only as accurate as these assumptions.
5. **Derive behaviour from evidence.** Read the tests: an assertion such as
   `assertEquals(status, "U")` tells you the string form of a value; a test passing `null` and
   expecting a result tells you how a helper must treat `null`.
6. **Write plain Gosu `.gs` files.** The tool compiles Gosu sources only.
7. **Iterate.** Add stubs, rerun, read the next errors, repeat until the baseline passes. Then read
   the results critically before presenting them.

## Recipes

All recipes below were verified with Gosu 1.18.14.

### Enum-like values referenced with their package (`com.example.types.Status.ACTIVE`)

A Gosu enum in that package with the constants that are used:

```gosu
package com.example.types

/** STUB: only ACTIVE is used by the class; OTHER is invented so tests can use a second value. */
enum Status {
  ACTIVE, OTHER
}
```

### A type referenced without its package and without a `uses` statement

Some Gosu runtimes import extra packages implicitly, so code can write `Status.ACTIVE` where
open source Gosu needs `com.example.types.Status.ACTIVE`. Without editing the class, add a shim
class with the same simple name **in the package of the class under test**:

```gosu
package com.example.feature

/** STUB: makes the unqualified name resolve as the original runtime's implicit import would. */
class Status {
  public static final var ACTIVE : com.example.types.Status = com.example.types.Status.ACTIVE
}
```

If the type is only ever used unqualified, a plain enum in the package of the class is enough.

### Values converted to a string (`CommStatus.A as String`)

`as String` calls `toString()`. When the code relies on a specific string form, return it from
`toString()`. Take the strings from the tests' assertions; if no test shows them, say they are
guessed.

```gosu
package com.example.feature

/** STUB: string codes taken from the expected values in the tests. */
enum CommStatus {
  A("A"), R("R")

  final var _code : String

  private construct(code : String) {
    _code = code
  }

  override function toString() : String {
    return _code
  }
}
```

### Classes used only as types (`Type<BaseAction>`, `switch` on `MetaType`, `typeis`)

Empty classes that reproduce the hierarchy:

```gosu
package com.example.feature.actions

/** STUB: only the type hierarchy matters. */
class EnrollAction extends BaseAction {
}
```

### Static helpers (`DateUtils.sameDay(a, b)`, `Clock.currentDate()`)

Same package and name, only the methods used, the simplest correct behaviour. Handle `null` the way
the tests expect:

```gosu
package com.example.util

uses java.time.ZoneId
uses java.util.Date

/** STUB: sameDay = same calendar day in the default time zone; null is never the same day. */
class DateUtils {
  static function sameDay(first : Date, second : Date) : boolean {
    if (first == null or second == null) {
      return false
    }
    var zone = ZoneId.systemDefault()
    return first.toInstant().atZone(zone).toLocalDate() == second.toInstant().atZone(zone).toLocalDate()
  }
}
```

### Test base classes (`class FooTest extends ProjectTestBase` with `function testXxx()`)

When the tests only need assertions (no database, server or framework start-up), stub the base
class as a JUnit 3 `TestCase`. The bundled JUnit Vintage engine runs `testXxx` methods and
`TestCase` provides `assertEquals` and the other assertions:

```gosu
package com.example.test

/** STUB: the real base class starts a runtime the tests do not need; only assertions are provided. */
abstract class ProjectTestBase extends junit.framework.TestCase {
}
```

If the tests call other members of the base class, add those members with minimal behaviour.

## What cannot be stubbed sensibly

Stop and tell the user when the class or its tests depend on:

- persistence, queries or a database;
- a server or framework that must start before the tests, or data it provides;
- behaviour of the original runtime that the logic actually depends on;
- a large chain of other classes (stubbing them would mean rewriting the code base).

Suggest extracting the pure logic into a class with simple inputs if the user wants to test it here.

## Delivering stubs to another machine

When the user runs the tool on a different machine than yours, files you send may not arrive where
expected (for example, a zip extracted into an extra folder level). A script the user pastes into a
terminal is more reliable. On Windows, use PowerShell and write UTF-8 without a byte order mark:

```powershell
$root = 'D:\trials\stubs'
function W($rel, $text) { $p = Join-Path $root $rel; New-Item -ItemType Directory -Force (Split-Path $p) | Out-Null; [IO.File]::WriteAllText($p, $text) }
W 'com\example\util\DateUtils.gs' @'
package com.example.util
...
'@
Get-ChildItem -Recurse -Filter *.gs $root | Select-Object -ExpandProperty FullName
```

(`Set-Content -Encoding UTF8` in Windows PowerShell 5 writes a byte order mark; Gosu sources with a
byte order mark were not tested.)

In Windows PowerShell, quote arguments that start with `-` and contain a dot, such as
`"-PsourceFile=C:\code\MyClass.gs"`; otherwise they may be split in two.

## Reporting

Along with the mutation results, tell the user:

- which stubs were created and every assumption they encode;
- which survivors are equivalent mutants (no test can kill them) rather than missing tests;
- that results describe the class under the stubs' assumptions, not inside its original runtime.
