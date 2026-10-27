# Maintainers

This project uses **per-board maintainers**: each device has one or more
hardware owners who keep its device-tree patches and config current, test
release candidates on real hardware, and review PRs that touch their board. The
lead owns the shared build pipeline and the device table. A board with two
owners is deliberate — it is one more person who can flash and one fewer
single point of failure.

## Branch protection and review flow

- `main` takes changes **only via Pull Request**. Direct `git push` to `main`
  is rejected by GitHub — the PR-review gate implies "require a pull request
  before merging". The "Shell test suites" CI check must pass.
- **The lead (@akbar-npj) is the final approver on every PR.** `CODEOWNERS`
  lists `@akbar-npj` on every line (each board's files *and* the `*` fallback),
  so `require_code_owner_reviews: true` makes the lead a *required* reviewer for
  all files. A board maintainer reviews their own board's changes, but the PR
  cannot merge until the lead also approves.
- `enforce_admins: false` — the lead is not bound by the gate, so they can
  approve and merge their own PRs (and any PR) through the normal PR flow.
  Non-admin collaborators (`Write`) cannot bypass the required reviews.
- Board maintainers get **Write** access so they can push their own topic / test
  branches (e.g. `uz801/test`), experiment there, and open PRs — but they cannot
  merge to `main` without the lead's approval. Stale approvals are dismissed on
  new pushes (`dismiss_stale_reviews: true`).

## Roster

One row per supported board. **Hardware** is the state of the maintainer's unit
— `bench` (on the desk and testable), `field` (in daily use), `dead` (owned but
not bootable) or `none` (no unit at all). It is the column that decides whether
a release candidate has actually been tested on that board, so keep it honest
and update it when a unit arrives, dies or is repaired.

| Board (codename) | Silkscreen / marketing | Compatible | Maintainer(s) | Hardware | Owns |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `hmuf02v5` | HMUF02-V5 (was HMU05) | `hmuf02v5,250605v0s` (legacy `hmu05,250605v0s`) | @akbar-npj | bench | patches 805, 849; `diffconfigs/hmuf02v5` |
| `mifib-v12` | HM-MIFIB-V12 | `mifib-v12,generic` | @akbar-npj | bench | patch 853; `diffconfigs/mifib-v12` |
| `ufi001b` | UFI001B (V2.0) | `ufi001b,250605v0s` | @akbar-npj | **dead** — owned, repair pending | patch 806; `diffconfigs/ufi001b` |
| `mf800b` | MF800B | `mf800b,generic` | @akbar-npj | **none — needs a tester** | patch 807; `diffconfigs/mf800b` |
| `uf02` | UF02 (250605 V0S) | `uf02,250605v0s` | @akbar-npj | **none — needs a tester** | patches 804, 851; `diffconfigs/uf02` |
| `uz801` | UZ801 (v3) | `yiming,uz801-v3` | @isprogrammer, @giraygokirmak | bench (@isprogrammer) + field, daily in-vehicle (@giraygokirmak) | patches 803, 851; `diffconfigs/uz801` |
| `ufi003` | UFI003 MB V02 | `ufi003,mb-v02` | @isprogrammer | bench | patch 852; `diffconfigs/ufi003` |

Boards with **no working unit** (`dead` or `none`) are still maintained — the
code is kept current and the image builds — but nothing in a release has been
flashed on them, and a bug there is found by a user, not by us. Those rows are
what issue [#13](https://github.com/akbar-npj/msm8916-openwrt/issues/13) is
asking for.

### Shared files (lead-owned)

Not owned by any one board, because they affect all of them:

- `msm89xx/image/msm8916.mk` — the device table (one file, cannot be line-split
  by `CODEOWNERS`; board maintainers review their own block by convention)
- patches 801/802 (DTS skeleton, reserved-memory labels), 818/824/834/838/843/846
  (PMIC, RPM stats, CPU interconnect/OPP/CPR), 861 (Wi-Fi/PMIC thermal
  mitigation in `msm8916-ufi.dtsi`, shared by the UFI-family boards)
- `build.sh`, `diffconfigs/*` shared options, `msm89xx/base-files/`,
  `msm89xx/modules.mk`, the CI workflows and `RELEASE_NOTES.md`

### Keeping this current

This table and `CODEOWNERS` are one record in two forms: `CODEOWNERS` is what
GitHub enforces, this file is what a human reads. Change them in the same
commit — a name in one and not the other is how a board quietly loses its
reviewer. When a board gains or loses an owner, update issue #13's mirrored
table as well.

## Duties

- **Keep board current** — rebase the board's DTS/patches when the kernel or
  shared SoC code changes; flag breakage early.
- **Test release candidates** — flash RCs to your hardware and report
  boot/modem/Wi-Fi status before a release is tagged.
- **Review PRs** — GitHub auto-requests your review on files in your column.
- **No pressure** — even "happy to test RCs" is valuable. Bandwidth is
  understood to be variable.

## Naming convention (applies everywhere)

One board = one identity, used **consistently** across all of:

- device-tree file: `msm8916-generic-<codename>.dts`
- `compatible` string: `<codename>,<rev>` (e.g. `hmuf02v5,250605v0s`)
- OpenWrt device name / `DEVICE_MODEL` (e.g. `HMUF02-V5`)
- `diffconfigs/<codename>`
- release filenames and build commands

Rename **early, rename once**. The longer a board ships under two names, the
more churn it causes later.

### History: HMU05 → HMUF02-V5

The board previously called `hmu05` (silkscreen `HMU05 4G Modem Stick`) is
physically labelled **HMUF02-V5**. It was renamed in this repo to match the
physical silkscreen. The old `hmu05,250605v0s` compatible is kept in
`SUPPORTED_DEVICES` so existing sysupgrade images still match — sysupgrade
from an old image to a new one is supported; the reverse is not.

> Note: this repo also ships a **separate** `uf02` device (compatible
> `uf02,250605v0s`). `uf02` is NOT the same board as `hmuf02v5`; do not
> confuse the two. They are distinct V1.0-HiMI sticks with different DTS.

## Identifying the installed release (Note #2)

OpenWrt itself always reports the upstream base version, so it cannot tell you
which project build is on a given stick. This repo bakes a **project release
version** into every image via `CONFIG_VERSION_*`.

On a live system:

```sh
ubus call system board | jsonfilter -e @.release.version
# -> e.g. 25.12.5-r5
cat /etc/os-release | grep VERSION_
```

The single source of truth is the repo **`version`** file. To bump it:

```sh
./sync-version.sh 25.12.6-r1   # rewrites version + propagates to all diffconfigs
```

`CONFIG_VERSION_DIST` is `msm8916-openwrt`; `CONFIG_VERSION_NUMBER` is the full
`X.Y.Z-rN` string; `CONFIG_VERSION_CODE` is the `rN` part. The repo/bug URLs
point at this GitHub project so `opkg` and the LuCI "report bug" flow land here.
