# Stubbing guide: running classes that depend on types you do not have

Instructions for AI coding agents (and people) who need to mutation test a Gosu class taken from a
larger code base, typically a platform platform configuration, with
`./gradlew :mutation-cli:mutateClass`.

Such a class usually references types that only exist inside its code base or platform: enum types,
other configuration classes, utility classes, test base classes. Open source Gosu cannot resolve
them, so the baseline fails to compile. Minimal stand-ins ("stubs") for those types let the class
and its tests compile and run unchanged.

**Stubs are never committed to this repository.** They are derived from someone else's code base
and live next to the trial project, outside this repository.

## When to create stubs

Run the task first:

```bash
./gradlew :mutation-cli:mutateClass -PsourceFile=<class .gs> [-PtestFile=<test .gs>] -PtrialDir=<trial dir>
```

Stubs are needed when the baseline fails with errors such as:

```
error: "SomeType" is not a valid type.
error: No type actions found in package com.example.feature
error: Could not resolve symbol for : SomeEnumType
error: "enumtype" is not a valid type.
error: No function defined for assertEquals.
```

Errors in your own test code are not a stub problem; fix the tests instead.

## Where they go

- A folder outside this repository, for example `<trials>/stubs`, holding Gosu sources by package:
  `<trials>/stubs/com/example/util/DateUtils.gs`.
- Pass it with `-PstubsDir=<folder>`, or put it at `<trialDir>/src/stubs/gosu` where the task picks
  it up automatically.
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
   `assertEquals(status, "U")` tells you a enumtype code; a test passing `null` and expecting a
   result tells you how a helper must treat `null`.
6. **Prefer plain Gosu `.gs` files.** The task compiles Gosu sources only.
7. **Iterate.** Add stubs, rerun the task, read the next errors, repeat until the baseline passes.
   Then read the results critically before presenting them.

## Recipes

All recipes below were verified with Gosu 1.18.14.

### Enum type value referenced with its package (`enumtype.Status_Ext`)

A Gosu enum in package `enumtype` with the `TC_` constants that are used:

```gosu
package enumtype

/** STUB for the enum type Status_Ext. TC_OTHER is invented so tests can use a second value. */
enum Status_Ext {
  TC_ACTIVE, TC_OTHER
}
```

### Enum type referenced without its package (`Status_Ext.TC_ACTIVE`)

platform imports `enumtype` implicitly; open source Gosu does not. Without editing the class, add a
shim class with the same simple name **in the package of the class under test**:

```gosu
package com.example.feature

/** STUB: makes the unqualified name resolve as the platform's implicit enumtype import would. */
class Status_Ext {
  public static final var TC_ACTIVE : enumtype.Status_Ext = enumtype.Status_Ext.TC_ACTIVE
}
```

If the enum type is only ever used unqualified, a plain enum in that package is enough.

### Enum value converted to a string (`SomeEnumType.TC_X as String`)

On platform this yields the enumtype code. Give the enum a code and return it from `toString()`.
Take the codes from the tests' assertions; if no test shows them, say they are guessed.

```gosu
package com.example.feature

/** STUB for the enum type CommStatus. Codes taken from the expected values in the tests. */
enum CommStatus {
  TC_A("A"), TC_R("R")

  final var _code : String

  private construct(code : String) {
    _code = code
  }

  override function toString() : String {
    return _code
  }
}
```

### Classes used only as types (`Type<BaseAction>`, `switch` on `MetaType`, `instanceof`)

Empty classes that reproduce the hierarchy:

```gosu
package com.example.feature.actions

/** STUB: only the type hierarchy matters. */
class EnrollAction extends BaseAction {
}
```

### Static helpers (`DateUtils.sameDay(a, b)`, `com.example.api.util.DateUtil.currentDate()`)

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

### Test base classes (`class FooTest extends CustomUnitTestBase` with `function testXxx()`)

When the tests only need assertions (no entities, bundles, database or server), stub the base class
as a JUnit 3 `TestCase`. The bundled JUnit Vintage engine runs `testXxx` methods and `TestCase`
provides `assertEquals` and the other assertions:

```gosu
package com.example.test

/** STUB: the real base class needs the platform test runtime; only assertions are provided. */
abstract class CustomUnitTestBase extends junit.framework.TestCase {
}
```

If the tests call other members of the base class, add those members with minimal behaviour.

## What cannot be stubbed sensibly

Stop and tell the user when the class or its tests depend on:

- entity instances, queries, bundles or the database;
- `PlatformTestBase` server start-up, sample data or test fixtures provided by the platform;
- PCF, display keys or product model behaviour that the logic actually depends on;
- a large chain of configuration classes (stubbing them would mean rewriting the code base).

In those cases mutation testing has to run inside a platform environment
(see `architecture.md`). Suggest extracting the pure logic into a class with simple inputs
if the user wants to test it here.

## Delivering stubs to another machine

When the user runs the task on a different machine than yours, files you send may not arrive where
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

## Reporting

Along with the mutation results, tell the user:

- which stubs were created and every assumption they encode;
- which survivors are equivalent mutants (no test can kill them) rather than missing tests;
- that results describe the class under the stubs' assumptions, not inside the real platform.
