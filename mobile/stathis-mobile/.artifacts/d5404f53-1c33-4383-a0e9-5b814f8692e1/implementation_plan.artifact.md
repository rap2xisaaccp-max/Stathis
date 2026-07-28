# Implementation Plan - Fix `ExerciseViewModelTest` compilation error

The `ExerciseViewModel` constructor now requires a `ClassifyPoseUseCase` dependency, which is missing in the current unit tests. This causes a compilation error.

## User Review Required

> [!IMPORTANT]
> I am proposing to add **MockK** as a test dependency. MockK is a standard mocking library for Kotlin that allows for clean dependency injection in unit tests.

## Proposed Changes

### Build Configuration

#### [MODIFY] [libs.versions.toml](file:///C:/Users/DONZ/Stathis/mobile/stathis-mobile/gradle/libs.versions.toml)
- Add `mockk` version and library definition.

#### [MODIFY] [app/build.gradle.kts](file:///C:/Users/DONZ/Stathis/mobile/stathis-mobile/app/build.gradle.kts)
- Add `testImplementation(libs.mockk)` to the dependencies.

### Testing

#### [MODIFY] [ExerciseViewModelTest.kt](file:///C:/Users/DONZ/Stathis/mobile/stathis-mobile/app/src/test/java/citu/edu/stathis/mobile/features/exercise/ui/viewmodel/ExerciseViewModelTest.kt)
- Mock `ClassifyPoseUseCase` and provide it to the `ExerciseViewModel` constructor.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:compileDebugUnitTestKotlin` to verify the fix resolves the compilation error.
- Run `./gradlew :app:testDebugUnitTest --tests "citu.edu.stathis.mobile.features.exercise.ui.viewmodel.ExerciseViewModelTest"` to ensure the tests pass.
