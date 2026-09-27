# Security policy

nsai-deterministic-gate sits between an LLM and the rest of your application, so a flaw that lets a non-compliant output pass the gate is treated as a security issue.

## Supported versions

| Version | Supported |
| ------- | --------- |
| `main` (pre-1.0) | Yes |
| Older tags (`v1.0.0`, `v1.3.0`) | No — please upgrade to `main` |

Once 1.0 is released on Maven Central, the latest minor release will receive security fixes.

## Reporting a vulnerability

**Please do not open a public issue for security problems.**

Report privately through GitHub: go to the repository's **Security** tab and choose **Report a vulnerability**. Include:

- the version or commit you tested,
- the rule file and enforcement mode (`HARD_REJECT` or `SOFT_REPAIR`),
- the input that bypassed or broke the gate, and what you expected.

You can expect an acknowledgement within 5 business days and a status update at least every 14 days until the issue is resolved. Reporters are credited in the release notes unless they ask not to be.

## Scope

In scope: gate bypasses, rule-evaluation errors that allow a violating output, unsafe deserialization of LLM output or rule files, and secrets leaking into logs.

Out of scope: vulnerabilities in the LLM provider itself, and issues that need an attacker who can already edit your rule files or application configuration.
