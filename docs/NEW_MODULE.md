# Adding a new Gradle module

Use `libs/api` (plain library) and `libs/query-language` (library with ErrorProne/NullAway) as references. Library modules go under `libs/`, deployable apps at the top level (`app/`).

## 1. Create the folders

```
libs/<name>/
  build.gradle
  README.md
  src/main/java/com/heldertheking/exochat/<name>/
  src/test/java/com/heldertheking/exochat/<name>/
```

Put a `.gitkeep` in empty folders so Git tracks them.

## 2. Register it

Add it to `settings.gradle` (keep alphabetical order within `libs:`):

```
include 'libs:<name>'
```

## 3. Write `build.gradle`

Minimal library:

```groovy
plugins {
    id 'java-library'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(libs.versions.java.get().toInteger())
    }
}

dependencies {
    api libs.jspecify                 // `api` = types that appear in the public API
    implementation libs.slf4j.api     // `implementation` = internal only

    testImplementation platform(libs.junit.bom)
    testImplementation 'org.junit.jupiter:junit-jupiter'
    testImplementation libs.assertj
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

Rules of thumb:

- Take versions from `gradle/libs.versions.toml` (add a new entry there, never a hard-coded version). Reference other modules as `project(':libs:<other>')`.
- Optional but recommended for main code: the `errorprone` plugin with NullAway as an error and `@NullMarked` package-info files (copy the setup from `libs/query-language/build.gradle`).
- Modules that use Spring or gRPC versions should import the platform: `implementation platform(project(':exochat-bom'))`.
- Add new third-party dependencies to the catalog; if they must be version-aligned for the app, add a constraint to `exochat-bom`.

Coverage needs nothing extra: the root `build.gradle` applies JaCoCo to every module with the `java` plugin and includes it in `./gradlew coverageReport`. If the module contains generated sources, add their class patterns to `coverageExcludes` in the root `build.gradle`.

## 4. Wire it into consumers

In the consuming module (usually `app/build.gradle`):

```groovy
implementation project(':libs:<name>')
```

## 5. Document it

- Write `libs/<name>/README.md`: purpose, layout, usage, how to build and test, gotchas.
- Add a line to the module list in the root `README.md`.
- If you made a lasting design choice, add an ADR (see `docs/adr/README.md`).

## 6. Verify

```
./gradlew :libs:<name>:build
./gradlew build
```

Then run `graphify update .` if you use the knowledge graph, and commit module, `settings.gradle` and docs together.
