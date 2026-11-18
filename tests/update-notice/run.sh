#!/usr/bin/env bash
# Tests for the dismissable package-update banner
# (packages/luci-app-update-notify/.../99_update-notice.js).
#
# check.js drives the include in node against stub LuCI globals; this wrapper
# adds the mutant check the other suites have.
#
#   bash tests/update-notice/run.sh [--self-check]
#
# --self-check mutates the include and requires check.js to notice.

set -u

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
JS="${UPDATE_NOTICE_JS:-$ROOT/packages/luci-app-update-notify/htdocs/luci-static/resources/view/status/include/99_update-notice.js}"

if ! command -v node >/dev/null 2>&1; then
    printf 'update-notice: node missing (needed to load the LuCI include)\n' >&2
    exit 2
fi

[ -f "$JS" ] || { printf 'update-notice: %s not found\n' "$JS" >&2; exit 2; }

run_check() {
    UPDATE_NOTICE_JS="$1" node "$HERE/check.js"
}

self_check() {
    local mutant failed=0
    mutant="$(mktemp)"

    # The banner must stay dismissed at the same count, not only below it.
    cp "$JS" "$mutant"
    sed -i 's/this\.count <= this\.dismissed()/this.count < this.dismissed()/' "$mutant"
    if run_check "$mutant" >/dev/null 2>&1; then
        echo "  FAIL mutant 'only a lower count is dismissed' was not caught"
        failed=$((failed + 1))
    else
        echo "  ok   mutant 'only a lower count is dismissed' caught"
    fi

    # The local override is what hides the banner before uci is reloaded.
    cp "$JS" "$mutant"
    sed -i 's/return Math\.max(n, this\.dismissedCount || 0);/return n;/' "$mutant"
    if run_check "$mutant" >/dev/null 2>&1; then
        echo "  FAIL mutant 'the local override is dropped' was not caught"
        failed=$((failed + 1))
    else
        echo "  ok   mutant 'the local override is dropped' caught"
    fi

    # The stored count has to be read back; ignoring it means never dismissing.
    cp "$JS" "$mutant"
    sed -i "s/var n = parseInt(uci.get(CONFIG, 'settings', 'dismissed_count'), 10);/var n = 0;/" "$mutant"
    if run_check "$mutant" >/dev/null 2>&1; then
        echo "  FAIL mutant 'the stored count is ignored' was not caught"
        failed=$((failed + 1))
    else
        echo "  ok   mutant 'the stored count is ignored' caught"
    fi

    # The count stored must be the one on screen.
    cp "$JS" "$mutant"
    sed -i "s/String(count));/String(0));/" "$mutant"
    if run_check "$mutant" >/dev/null 2>&1; then
        echo "  FAIL mutant 'a fixed count is stored' was not caught"
        failed=$((failed + 1))
    else
        echo "  ok   mutant 'a fixed count is stored' caught"
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
