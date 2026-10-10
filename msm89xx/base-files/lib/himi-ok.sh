# SPDX-License-Identifier: GPL-2.0-only
#
# himi-ok.sh -- decide whether the HiMI_OK guard should run on this board.
#
# WHY A BOARD LIST AND NOT "IS THE BASEBAND HIMI?"
#   The ~900 s fatal is a property of the modem FIRMWARE, and HMU05 / UFI001B /
#   UZ801 / UF02 all carry the same "HIMI_U01_MODEM" line.  But only *some*
#   boards actually manifest the fatal -- UFI001B (V2.0) ran 12 h+ with a data
#   bearer and zero crashes.  So "the baseband is HiMI" is too broad a gate; the
#   gate is the set of boards where the fatal is OBSERVED.  (See
#   Docs/Modem Stability/PATCH_MODEM_REVISION_SCOPE_AUDIT.md.)
#
#   The guard is also self-limiting: himi-ok-guard only ever writes when NV item
#   2500 reads back ALL-ZERO, so enabling it on a board where the item holds
#   real factory data is harmless (it leaves the data untouched).
#
# DEFAULT ALLOW-LIST (board_name globs, matched against the DT compatible):
#   *hmu05*   Generic HMU05        (HIMI_U01_MODEM_V1.0) -- ours
#   *uz801*   YiMing UZ801 v3      (same HiMI baseband)  -- user-confirmed
#   *uf02*    Generic UF02         (HIMI_U01_MODEM_V1.0, Sep 09 2015) -- user-confirmed
#
# EXTEND WITHOUT A REBUILD (optional; not in the default config):
#   uci set modem-watchdog.recovery.himi_ok_boards='*hmu05* *uz801* *uf02*'
#   uci commit modem-watchdog
#   /etc/init.d/himi-ok restart
#
# The caller must have sourced /lib/functions.sh (for board_name()).

himi_ok_board_match() {
	local bn list
	bn="${1:-$(board_name 2>/dev/null)}"
	[ -n "$bn" ] || return 1
	list="$(uci -q get modem-watchdog.recovery.himi_ok_boards 2>/dev/null)"
	[ -n "$list" ] || list="*hmu05* *uz801* *uf02*"
	# Match bn against each space-separated glob.  Disable pathname expansion
	# in a subshell, otherwise the unquoted $list is glob-expanded against the
	# CWD and a file like "hmu05_fsg_extracted" would replace the pattern.
	(
		set -f
		for pat in $list; do
			case "$bn" in
				$pat) exit 0 ;;
			esac
		done
		exit 1
	)
}
