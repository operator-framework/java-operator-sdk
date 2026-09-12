---
name: update-k8s-ci-versions
description: >
  Update the Kubernetes versions used by CI to the latest patch releases.
  Checks the current Kubernetes releases at https://kubernetes.io/releases/,
  determines the 4 newest minor lines at their latest patch, and updates the
  GitHub Actions workflows accordingly. Use when asked to "update the
  Kubernetes versions", "bump k8s in CI", or refresh the test matrix.
---

# Update Kubernetes CI versions

Keep the Kubernetes versions tested in CI current: the integration-test matrix
runs the **4 newest minor lines**, each pinned to its **latest patch**, and a
few single-version references track the **newest** version.

## What to update

There are exactly three Kubernetes-version references in CI. **Do not touch the
`minikube version:` entries** — those pin the minikube *tool*, not Kubernetes.

| File | Line | What it holds | New value |
| --- | --- | --- | --- |
| `.github/workflows/build.yml` | `kubernetes: [ ... ]` matrix | the 4 newest minors at latest patch | `[ 'v1.A.x', 'v1.B.x', 'v1.C.x', 'v1.D.x' ]` (oldest → newest) |
| `.github/workflows/build.yml` | `kube-version: '...'` (httpclient-tests) | the newest version | the newest of the four |
| `.github/workflows/e2e-test.yml` | `kubernetes version: '...'` | the newest version | the newest of the four |

If a future grep finds additional real Kubernetes versions, update those too —
verify with the discovery step below rather than assuming only these three.

## Steps

### 1. Find the current references

```bash
grep -rn "kubernetes\|kube-version" .github/workflows/build.yml .github/workflows/e2e-test.yml
```

Note the existing values so you can report the diff. The newest minor among them
tells you where the matrix currently ends.

### 2. Determine the latest versions

The source of truth is <https://kubernetes.io/releases/>. Fetch it and read off
the latest stable version and the supported minor lines:

```
WebFetch https://kubernetes.io/releases/
  → "List the latest stable Kubernetes version and every supported minor
     release with its latest patch version. Give exact x.y.z numbers."
```

From the newest minor `1.N`, the four lines you need are `1.N`, `1.N-1`,
`1.N-2`, `1.N-3`.

`kubernetes.io/releases` only lists *supported* minors (usually the newest
three), so the oldest of the four (`1.N-3`) is often end-of-life and missing
there. **Get the latest patch for each of the four minors** from a per-minor
source that includes EOL lines. Prefer git tags, which need no API token and no
auth:

```bash
# Latest stable (non-prerelease) patch for a given minor, e.g. 1.32:
git ls-remote --tags --refs https://github.com/kubernetes/kubernetes.git 'v1.32.*' \
  | awk -F/ '{print $NF}' \
  | grep -E '^v1\.32\.[0-9]+$' \
  | sort -V | tail -1
```

Run that for each of the four minor numbers. If `git ls-remote` is unavailable,
fall back to `https://dl.k8s.io/release/stable-1.<minor>.txt` (one request per
minor) or `gh api repos/kubernetes/kubernetes/releases`.

Cross-check the newest version against what `kubernetes.io/releases` reported as
latest stable — they should agree.

### 3. Apply the updates

Edit the three references from the table. Keep the exact surrounding format —
the `v` prefix, single quotes, and matrix ordering (oldest → newest). Example
if the latest stable is `1.36` and patches are `1.33.14 / 1.34.10 / 1.35.4 / 1.36.1`:

```yaml
# build.yml matrix
kubernetes: [ 'v1.33.14', 'v1.34.10', 'v1.35.4', 'v1.36.1' ]

# build.yml httpclient-tests
kube-version: 'v1.36.1'

# e2e-test.yml
kubernetes version: 'v1.36.1'
```

If every resolved version already matches what is in the files, report "already
up to date" and make no changes.

### 4. Verify and report

```bash
grep -rn "v1\.[0-9]\+\.[0-9]\+" .github/workflows/build.yml .github/workflows/e2e-test.yml \
  | grep -v "minikube version"
```

Confirm all three references hold the new values and the minikube versions are
untouched. Show the user a before/after summary of each changed line.

### 5. (Optional) Open a PR

Only if the user asks. Branch from `main`, commit the workflow changes with a
message like `chore(ci): bump Kubernetes test versions to latest patches`, push,
and open a PR with `gh`. Otherwise leave the edits in the working tree.
