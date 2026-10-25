# Interactive shells: heal a stale feed signing key before apk touches the
# feed, so a device running an old image cannot fail `apk update` with
# "UNTRUSTED signature".
#
# The refresh is best-effort: it warns (on stderr) when the key is stale and
# when a refresh attempt fails, but it NEVER blocks or fails the apk command --
# `command apk` always runs, whatever the refresh did.  A function, not an
# alias: no recursion.  Non-interactive callers are covered by the boot-time
# run, the iface hotplug hook and the apk commit hook instead.

if [ -x /usr/sbin/apk-key-refresh ]; then
	apk() {
		case "${1:-}" in
			update|add|upgrade|fix)
				RETRIES=1 DELAY=0 /usr/sbin/apk-key-refresh
				;;
		esac
		command apk "$@"
	}
fi
