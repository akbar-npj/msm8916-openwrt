#!/usr/bin/env bash
# Tests for the WPA3/PMF dashboard warning
# (packages/luci-app-update-notify/.../98_wpa3-notice.js).
#
# check.js drives the include in node against stub LuCI globals; this wrapper
# adds the mutant checks the other suites have.
#
#   bash tests/wpa3-notice/run.sh [--self-check]
#
# --self-check mutates the include and requires check.js to notice.

set -u

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
JS="${WPA3_NOTICE_JS:-$ROOT/packages/luci-app-update-notify/htdocs/luci-static/resources/view/status/include/98_wpa3-notice.js}"

if ! command -v node >/dev/null 2>&1; then
    printf 'wpa3-notice: node missing (needed to load the LuCI include)\n' >&2
    exit 2
fi

[ -f "$JS" ] || { printf 'wpa3-notice: %s not found\n' "$JS" >&2; exit 2; }

run_check() {
    WPA3_NOTICE_JS="$1" node "$HERE/check.js"
}

self_check() {
    local mutant failed=0
    mutant="$(mktemp)"

    # Only AP interfaces are affected; a station in SAE mode must stay quiet.
    cp "$JS" "$mutant"
    sed -i "s/if (iface.mode !== 'ap')/if (false)/" "$mutant"
    if run_check "$mutant" >/dev/null 2>&1; then
        echo "  FAIL mutant 'station mode is not excluded' was not caught"
        failed=$((failed + 1))
    else
        echo "  ok   mutant 'station mode is not excluded' caught"
    fi

    # PMF optional (ieee80211w=1) with WPA2 is still the slow path.
    cp "$JS" "$mutant"
    sed -i "s/(enc !== 'none' \&\& pmf)/false/" "$mutant"
    if run_check "$mutant" >/dev/null 2>&1; then
        echo "  FAIL mutant 'PMF without SAE is ignored' was not caught"
        failed=$((failed + 1))
    else
        echo "  ok   mutant 'PMF without SAE is ignored' caught"
    fi

    # Dismiss must persist, or the banner returns on every 5 s poll.
    cp "$JS" "$mutant"
    sed -i "s/if (lsGet(LS_ACK) === this.signature)/if (false)/" "$mutant"
    if run_check "$mutant" >/dev/null 2>&1; then
        echo "  FAIL mutant 'dismiss is not persisted' was not caught"
        failed=$((failed + 1))
    else
        echo "  ok   mutant 'dismiss is not persisted' caught"
    fi

    rm -f "$mutant"
    return "$failed"
}

if [ "${1:-}" = "--self-check" ]; then
    echo "== self-check =="
    run_check "$JS" || exit 1
    echo
    self_check || exit 1
    echo "self-check passed"
    exit 0
fi

run_check "$JS"
