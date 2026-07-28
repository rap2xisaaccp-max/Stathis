# Fix Build Error: No value passed for parameter 'classifyPose'

The `ExerciseViewModel` constructor was recently updated to require a `ClassifyPoseUseCase` dependency. However, the existing unit test `ExerciseViewModelTest` still attempts to instantiate the ViewModel using a parameterless constructor, causing a build failure.

## User Review Required

> [!IMPORTANT]
> I am proposing to add **MockK** to the project's test dependencies. This is the standard mocking library for Kotlin and will allow us to properly test ViewModels and other components with dependencies without writing extensive manual stubs.

## Proposed Changes

### Build Configuration

#### [MODIFY] [libs.versions.toml](file:///C:/Users/DONZ/Stathis/mobile/stathis-mobile/gradle/libs.versions.toml)
- Add `mockk` version and library definition.

#### [MODIFY] [app/build.gradle.kts](file:///C:/Users/DONZ/Stathis/mobile/stathis-mobile/app/build.gradle.kts)
- Add `testImplementation(libs.mockk)` to the dependencies block.

### Testing

#### [MODIFY] [ExerciseViewModelTest.kt](file:///C:/Users/DONZ/Stathis/mobile/stathis-mobile/app/src/test/java/citu/edu/stathis/mobile/features/exercise/ui/viewmodel/ExerciseViewModelTest.kt)
- Update the test to use a mocked `ClassifyPoseUseCase` when instantiating `ExerciseViewModel`.
- Initialize the mock in a `@Before` method.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:compileDebugUnitTestKotlin` to verify the build error is resolved.
- Run `./gradlew :app:testDebugUnitTest --tests "citu.edu.stathis.mobile.features.exercise.ui.viewmodel.ExerciseViewModelTest"` to verify the test passes.
