# Releases

All six artifacts share `GROUP` and `VERSION_NAME` in `gradle.properties`. A release tag is `v` followed by that version. The Android, Kotlin, Compose, and NDK versions are pinned in the checked-in Gradle configuration.

## Local staging

```sh
./gradlew testDebugUnitTest lintDebug :sample:assembleDebug \
  publishAllPublicationsToStagingRepository
python3 scripts/verify_consumer.py
python3 scripts/release_bundle.py --allow-unsigned
```

The staging repository is `build/maven`. It includes AARs, sources, Javadoc, POMs, and Gradle module metadata. The consumer check builds a separate application from the Maven coordinates without an included source build. The bundle check validates the six modules, required POM fields, metadata versions, and required artifacts before generating `build/glasmio-central.zip` with checksums.

`--allow-unsigned` is for local inspection. Uploads require signatures.

## Maven Central

Configure these GitHub repository or `maven-central` environment secrets:

| Secret | Value |
| --- | --- |
| `MAVEN_CENTRAL_USERNAME` | Central Publisher Portal user-token username |
| `MAVEN_CENTRAL_PASSWORD` | Central Publisher Portal user-token password |
| `MAVEN_SIGNING_KEY` | ASCII-armored private PGP signing key |
| `MAVEN_SIGNING_PASSWORD` | Signing key password, if configured |

The `io.github.neoruaa.glasmio` namespace must be verified in the publishing account. The signing public key must be available to Maven Central's signature verification. Refer to the [Central token documentation](https://central.sonatype.org/publish/generate-portal-token/) and [Publisher API](https://central.sonatype.org/publish/publish-portal-api/).

The Release workflow runs tests, lint, release assembly, staging, and a fresh consumer build before uploading an automatically published deployment. It checks the tag against `VERSION_NAME` and exits if credentials are absent. It reports success only after Central returns `PUBLISHED`.

For a local signed publication, provide the same names as environment variables, stage artifacts with Gradle, and run:

```sh
python3 scripts/release_bundle.py --upload
```

After publication, confirm each artifact resolves from Maven Central in a clean consumer, update the release status in README and CHANGELOG, and update MeiloX with the fixed version. CI should use published coordinates. Developers can explicitly opt into the local composite build for ongoing work.
