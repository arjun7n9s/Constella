# Gradle wrapper

This directory holds the Gradle wrapper used to build the project with a pinned
Gradle version (see `gradle-wrapper.properties` → Gradle 8.9).

## `gradle-wrapper.jar` is required and not committed by this task

The wrapper needs a small binary launcher, `gradle-wrapper.jar`, alongside
`gradle-wrapper.properties`. It is a binary artifact and was **not** authored as
part of the project-skeleton task. Generate it once with a local Gradle
installation:

```
gradle wrapper --gradle-version 8.9
```

or copy `gradle/wrapper/gradle-wrapper.jar` from any existing Android Studio /
Gradle project that uses Gradle 8.x. After that, `./gradlew` (POSIX) and
`gradlew.bat` (Windows) work without a system Gradle install.

Opening the project in Android Studio (Giraffe/Hedgehog or newer) will also
create the wrapper jar automatically on first sync.
