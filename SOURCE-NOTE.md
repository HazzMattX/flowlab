# Flow source — version 0.1 (first official build)

- Package: `com.hazzmattx.flowlab`
- Version: 0.1, versionCode 1 (build script set to 1; this exact source has not been compiled yet)
- Steps: 49 step types. Triggers: 23 trigger types.
- Dead code and pre-release step-type migrations were removed; behavior is unchanged.
- Multi-line comments in the Kotlin source use `/* */` blocks, kept clear and simple: what the code does, nothing self-explanatory.
- Build with `build-portable.sh` (see the usage notes at the top of that file). It needs a JDK 17, Kotlin, and the Android SDK (platforms android-34, build-tools 34.0.0). The signing keystore is not included; build with your own key, or with the original key to install over an existing install.
- No Gradle: the build runs aapt2, kotlinc, d8, zipalign, and apksigner directly. The app is local/offline only, no telemetry, no account.
