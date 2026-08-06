# GitHub PR → Production deployment

The repository now contains two workflows:

- `Validate pull request` runs `./mvnw --batch-mode verify` for every pull request into `main`.
- `Deploy production` runs only after a commit reaches `main`. A merged `dev` → `main` pull request therefore triggers a VPS deployment.

## One-time GitHub setup

In **Settings → Environments**, create an environment named `production` and add these environment secrets:

| Secret | Required value |
| --- | --- |
| `VPS_HOST` | VPS IP address or hostname |
| `VPS_USERNAME` | SSH user, e.g. `root` or a dedicated deploy user |
| `VPS_SSH_KEY` | Private SSH key for that user (including `BEGIN` and `END` lines) |
| `VPS_KNOWN_HOSTS` | Output of `ssh-keyscan -H <VPS_HOST>` run from a trusted machine |
| `VPS_PORT` | SSH port; optional, defaults to `22` |

The public key that matches `VPS_SSH_KEY` must be present in the VPS user's `~/.ssh/authorized_keys` file. The project must already be set up at `/opt/stockspace`; `deploy.sh` pulls `origin/main`, rebuilds the Docker services, and checks the health endpoint.

## Enforce the PR flow

In **Settings → Branches → Add branch ruleset** (or a branch protection rule) for `main`:

1. Require a pull request before merging.
2. Require the `Build and test` status check to pass.
3. Restrict direct pushes to `main`.

Then use this release flow:

```text
feature branch → dev → pull request (dev → main) → merge → automatic VPS deployment
```

Direct pushes to `main` also trigger deployment by design, so the branch rule is what guarantees that production changes arrive only through a merged pull request.
