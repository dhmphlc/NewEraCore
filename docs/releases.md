# Releases and versioning

## PaperMC version mapping

| PaperMC | Plugin version |
|---------|----------------|
| 26.1.2  | 0.1.0+         |

Keep the Paper API version in `build.gradle.kts`, `api-version` in `plugin.yml`, and this table in
sync whenever server support changes.

## Versioning strategy

Stable plugin versions are tagged `vX.Y.Z` and have an associated GitHub release.

Testing plugin versions are tagged `vX.Y.Z-RC-N` and have an associated GitHub pre-release.

Development plugin versions are pushed to the `main` branch and are **not** tagged.

| Event             | Plugin version        | CI action                      | Release type      |
|-------------------|-----------------------|--------------------------------|-------------------|
| PR                | yyMMdd-HHmm-SNAPSHOT  | Build and test                 | None              |
| Push to `main`    | 0.0.0-RC-1-SNAPSHOT   | Build and upload               | None              |
| Tag `vX.Y.Z-RC-N` | X.Y.Z-RC-N-SNAPSHOT   | Build and draft                | Pre-release draft |
| Tag `vX.Y.Z`      | X.Y.Z                 | Build and draft                | Release draft     |

## Creating a release

1. Create a semantic version tag on `main`, such as `v0.1.0` or `v0.1.0-RC-1`.
2. Push the tag and wait for the tag workflow to create a draft release.
3. Review and publish the generated draft.
