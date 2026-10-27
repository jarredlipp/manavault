ManaVault: restore missing SetWordmarkPolicy dependency

This patch adds only:
  app/src/main/java/com/manavault/app/SetWordmarkPolicy.java
  tools/SetWordmarkPolicySmokeTest.java

Extract the ZIP into the root of your ManaVault GitHub repository, preserving
the paths above. Keep the existing PrintingFingerprintVerifier.java unchanged.

Why: PrintingFingerprintVerifier calls SetWordmarkPolicy.supported(set) and
SetWordmarkPolicy.read(ocrLine,candidates), but the class is missing from
the repository. The dependency implements conservative candidate-constrained
M10-M15/M19-M21 type-bar wordmark recognition.

Optional quick test (requires local JDK):
  mkdir -p /tmp/manavault-wordmark-classes
  javac -d /tmp/manavault-wordmark-classes \
    app/src/main/java/com/manavault/app/SetWordmarkPolicy.java \
    tools/SetWordmarkPolicySmokeTest.java
  java -cp /tmp/manavault-wordmark-classes \
    com.manavault.app.SetWordmarkPolicySmokeTest

Then rerun your Android build:
  ./gradlew --no-daemon :app:assembleDebug

No scanner features, assets, tests, or existing files are removed.
This patch has passed standalone Java compilation/smoke tests,
but a complete Android build must still be checked in GitHub Actions.
