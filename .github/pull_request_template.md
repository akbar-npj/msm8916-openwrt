<!--
  Board pull requests: run the checks below before asking for review.
  `tests/board-profile/signoff.sh <board>` prints the exact commands.
-->

## What this changes

<!-- One or two sentences. If it adds a board, name it and link the issue. -->

## Board support (skip if this is not a board)

| | |
|---|---|
| Board / stock name | |
| SoC / baseband | |
| RAM / eMMC sectors | |
| Battery | none / capacity, charge limit |
| SIM slots | |
| Closes | # |

## Checklist

- [ ] `bash tests/board-profile/run.sh --self-check` passes locally
- [ ] The `board-build` label is on this PR and the board build is green
- [ ] `tests/board-profile/signoff.sh <board>` output pasted below
- [ ] `Docs/ADDING_A_DEVICE_BOARD.md` section 9 walked through
- [ ] Any change to a file under `msm89xx/base-files/` is gated on
      `board_name` or carries a `# BOARDS: <list>` marker saying who it is for

## Hardware evidence

```
<paste: identity, modem/data, failsafe, LEDs, three cold boots, 20 min soak, image sha256>
```

## Anything reviewers should know

<!-- Known gaps, untested paths, hardware you could not confirm. -->
