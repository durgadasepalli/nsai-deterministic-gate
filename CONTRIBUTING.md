# Contributing to nsai-deterministic-gate

Thanks for your interest in improving the project. Bug reports, rule-engine ideas, new connectors and documentation fixes are all welcome.

## Ways to help

- **Report a bug** or **suggest a feature** with the issue templates.
- **Ask a question or share how you use the gate** in GitHub Discussions.
- **Send a pull request** for an open issue. For larger changes, open an issue first so we can agree on the approach.

## Build and test

Requirements: JDK 17 or 21. The Gradle wrapper downloads everything else.

```bash
./gradlew build            # compile and run all tests
./gradlew test             # tests only
```

## Pull request checklist

- [ ] The change has tests, and `./gradlew build` passes locally.
- [ ] Public behaviour changes are reflected in `README.md` or `docs/`.
- [ ] Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `docs:`, `test:`, `ci:`, `chore:`).
- [ ] Every commit is signed off (`git commit -s`), certifying the [Developer Certificate of Origin](https://developercertificate.org/).

## Code you contribute

By contributing you agree that your contribution is licensed under the [Apache License 2.0](LICENSE).

Only submit code you wrote yourself or that is available under an Apache-2.0-compatible license. **Do not submit code, configuration, rules, prompts or data from your employer or clients**, and keep examples generic (for example, a fictional bank or clinic).

## Style

- Java 17 language level; keep the core free of cloud-vendor SDKs where possible.
- Use SLF4J for logging, not `System.out`.
- Keep rule evaluation deterministic: the same input and rules must always give the same decision.

## Code of conduct

This project follows the [Contributor Covenant](CODE_OF_CONDUCT.md). By participating you agree to uphold it.
