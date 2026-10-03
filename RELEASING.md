# Releasing USB Input Bridge

F-Droid verifies the versioned GitHub universal APK against its own source build and publishes the developer-signed APK only if they match. The metadata pins the public signing certificate. Never commit or upload the private keystore.

## Build and sign

1. Use the source commit/tag listed in the F-Droid recipe and initialize its submodules. Build from a clean checkout without `release.keystore`, local Gradle overrides or existing build output.
2. Build on Linux with JDK 21, the repository's Gradle wrapper, Android SDK 36, CMake 3.22.1 and Android NDK 28.2.13676358 (r28c). Follow the `rm` entries in `fdroid/metadata/io.github.dotcomdomain.usbinputbridge.yml`. Do not enable `termuxBuild` or replace `aapt2` with the Termux binary.
3. Prefer the F-Droid buildserver environment and recipe. Its CI unsigned artifact is under `tmp/io.github.dotcomdomain.usbinputbridge_<versionCode>.apk`. Verify the source commit, build success and artifact checksum before signing it. A source build from another environment must pass the comparison below.
4. Sign the unsigned output with the existing release key using the official Android Build Tools **34.0.0** `apksigner`. Pass keystore/key passwords through the tool's environment or prompt support. Disable v4 signing for the release APK. Build Tools 36 signatures failed the signature-copy check during the initial investigation; Build Tools 34 worked.
5. Verify the signed APK using `apksigner verify --print-certs`. The expected certificate SHA-256 is:

   `667794945847bc72aa382b07f4a788cb930e177f4c404f4b2c6182784fe00adb`

The private key can remain on the phone: transfer the unsigned APK there, sign it locally with the existing keystore, then retrieve only the signed APK. Signing does not require compiling on the phone.

## Verify before publishing

Obtain an independent source build of the same version. With `apksigcopier` installed:

```sh
apksigcopier copy signed.apk independent-unsigned.apk reconstructed.apk
apksigner verify reconstructed.apk
```

Verification must succeed. Also compare the reconstructed and original signed APK hashes. Comparing only extracted files is insufficient because APK signatures cover ZIP structure as well. If a comparison fails, investigate the differing files, startup profile, toolchain versions and ZIP metadata; do not disable verification in the F-Droid recipe.

Two independent F-Droid CI builds of 3.4.2 produced identical unsigned APKs. Signing their output with Build Tools 34 and the existing release key passed signature-copy verification. This does not guarantee future versions or other build environments will match.

## Publish and update the submission

Upload `usb-input-bridge-<versionName>-universal.apk` to the matching GitHub `v<versionName>` release. Keep versioned asset URLs stable. The older 3.4.2 phone-built APK is retained under its original filename; it is not the F-Droid reference.

F-Droid metadata uses:

```yaml
Binaries: https://github.com/dotcomdomain/android-spen-hid-client/releases/download/v%v/usb-input-bridge-%v-universal.apk
AllowedAPKSigningKeys: 667794945847bc72aa382b07f4a788cb930e177f4c404f4b2c6182784fe00adb
```

Update the version and full source commit in both recipe copies, and wait for source build, reproducibility and APK checks to pass. For a new version, prepare its unsigned release before enabling its reference URL in CI, so the reference APK is available when verification runs.

Official requirements: https://f-droid.org/en/docs/Reproducible_Builds/