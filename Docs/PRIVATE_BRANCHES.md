# Private branches

Some work is not ready to be public: an experimental modem path, a voice stack
that still drops calls, a change that has only ever run on one bench unit. That
work lives on a `test/*` branch, and it is pushed to the **private** remote
only — a separate, private GitHub repository that exists to back these branches
up. `origin` and `upstream` are public.

Keeping that apart is not something to remember. Three guards do it, and each
one covers a route the others cannot.

| Route | Guard | Where |
|---|---|---|
| `git push origin test/…` | ruleset: no branch creation or update under `test/**` | GitHub, on the public repo |
| a pull request from `test/…` into `main` | `scripts/branch-guard.sh`, a required check | CI |
| private work merged into a `fix/…` branch, then pushed | `.githooks/pre-push` | your clone |
| the private branch drifting behind `main` | `.githooks/pre-push` + `scripts/private-sync.sh` | your clone |

## 1. The ruleset

A branch ruleset on the public repository blocks **creation** and **update** of
any ref under `refs/heads/test/**`, for everyone, with no bypass. A push cannot
get around it with `--no-verify`, and it does not depend on anyone having
installed anything.

Deletion is deliberately not blocked: retiring a `test/*` branch from the
public repository is legitimate, and has happened.

## 2. The pull-request check

`scripts/branch-guard.sh` runs as the first step of the `Shell test suites` job.
A pull request into `main` or `staging-main` from `test/*`, `private/*`, `wip/*`
or `scratch/*` fails, which blocks the merge.

Permission is granted by adding the **`publish-private`** label to the pull
request. A label rather than a commit trailer, so that granting it is visible on
the pull request and can be revoked by removing it.

## 3. The pre-push hook

```bash
scripts/install-hooks.sh      # sets core.hooksPath = .githooks, once per clone
```

`./build.sh guard` reports when this has not been done; the hooks are per-clone
and a clone without them behaves exactly like a hook with nothing to do.

The hook refuses:

- any `test/*` branch pushed to a remote whose URL is not the private one;
- any branch — whatever its name — that carries a commit existing **only** on
  the private remote. This is the accident the hook exists for: merging the
  private branch into a `fix/` branch and pushing that. Only the local clone can
  see it, because only the local clone knows what the private remote holds;
- a `test/*` push to the private remote while `origin/main` carries a commit
  the branch does not have.

Every remote whose URL does not name the private repository counts as public, so
a new or renamed remote fails closed.

## Keeping the private branch on top of main

```bash
scripts/private-sync.sh status   # behind/ahead counts; exits 1 when behind
scripts/private-sync.sh sync     # merges origin/main in; does not push
```

"Behind" counts non-merge commits on `origin/main` that the branch lacks. A
merge commit on `main` whose parents are both present is not content, so it does
not count — otherwise every merged pull request would look like drift.

The sync check is deliberately **not** in CI. A scheduled workflow in the public
repository would need a token that can read the private one, and that token in a
public repository's Actions is a bigger leak surface than the drift it would
prevent. It belongs where the push happens.

## What none of this catches

A cherry-pick. A cherry-picked commit has a new hash and can sit on a
plausibly-named branch, so every check above sees ordinary work. Deliberate
publication stays a review question; these guards stop accidents.

## State, 2026-11-18

Eleven `test/*` branches whose tips were already contained in `main` were
deleted from the public repository. Three remain, all unmerged, all still
carrying work that is not on `main`: `test/new-board-tool`,
`test/pure-software-modem`, `test/thermal`. The ruleset freezes them; what to do
with them is a separate decision.
