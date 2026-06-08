# Security Scanning

Run Gitleaks before publishing security-sensitive changes:

```sh
gitleaks detect --no-banner --redact --source .
```

The repository does not track `app/google-services.json`. Firebase and
Crashlytics are enabled only when that local client configuration file exists.
Use an OpenARDF-owned Firebase project for new configs; do not commit client
configuration from legacy projects.

Firebase Android API keys identify the Firebase project to client SDKs, but they
still create unwanted coupling to that project when checked in. New Gitleaks
findings should be reviewed instead of broadly allowlisted.

One historical Firebase client API key finding from a removed
`app/google-services.json` file is pinned in `.gitleaksignore` by exact
fingerprint so repository-wide history scans can pass. Do not add broad
allowlists for Firebase keys; new findings should continue to fail the scan.
