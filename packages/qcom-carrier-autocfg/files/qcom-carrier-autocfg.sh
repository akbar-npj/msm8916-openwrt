#!/bin/sh
#
# Qualcomm MSM8916 SIM Carrier Auto-Provisioning Engine
# Automatically detects SIM card insertion/change, configures optimal APN,
# IP stack (IPv4/IPv6), and establishes high-stability connection.
#

# --- --help ---------------------------------------------------------------
case "${1:-}" in
	-h|--help)
		cat <<'USAGE'
Usage: qcom-carrier-autocfg [--help]

Carrier auto-provisioning daemon.  Polls ModemManager for the SIM's operator
and IMSI, looks the carrier up in /usr/share/qcom-carrier-autocfg/apns.tsv
(overridable per operator in /etc/qcom-carrier-autocfg/custom-apns.tsv), then
sets the APN / IP type / allowed mode / bands in /etc/config/network and brings
the modem interface up.  Re-runs on a SIM swap.  Started by procd
(/etc/init.d/qcom-carrier-autocfg); takes no arguments.

Files:
  /usr/share/qcom-carrier-autocfg/apns.tsv        shipped carrier table
  /etc/qcom-carrier-autocfg/custom-apns.tsv       local overrides
  /etc/qcom-carrier-autocfg/stored-imsi           last provisioned IMSI

Options:
  -h, --help   Show this help and exit

Log:  logread -e carrier-autocfg
USAGE
		exit 0
		;;
esac

TAG="carrier-autocfg"
LAST_OPERATOR_CODE=""
LAST_IMSI=""

log() {
	logger -t "$TAG" "$*"
	echo "[$TAG] $*"
}

# Database paths for Hybrid APN Architecture
CUSTOM_APN_DB="/etc/qcom-carrier-autocfg/custom-apns.tsv"
SYSTEM_APN_DB="/usr/share/qcom-carrier-autocfg/apns.tsv"

# Lookup optimal APN and settings based on MCC-MNC and Operator Name
lookup_carrier_profile() {
	local op_code="$1"
	local op_name="$2"
	local imsi="$3"

	# Defaults
	CARRIER_NAME="Generic"
	CARRIER_APN="internet"
	CARRIER_IPTYPE="ipv4v6"
	CARRIER_AUTH="none"
	CARRIER_MODE="4g"
	CARRIER_MBN="generic/common/row/gen_3gpp/mcfg_sw.mbn"

	local match=""

	# Priority 1: User Custom Database in /etc (persists across upgrades)
	if [ -f "$CUSTOM_APN_DB" ] && [ -n "$op_code" ]; then
		match=$(awk -F'\t' -v code="$op_code" '$1 ~ "^"code"$" { print $2"|"$3"|"$4"|"$5"|"$6; exit }' "$CUSTOM_APN_DB" 2>/dev/null)
		if [ -n "$match" ]; then
			log "Matched user custom APN override in $CUSTOM_APN_DB for MCC-MNC $op_code"
		fi
	fi

	# Priority 2: Built-in Global APN Database in /usr/share (TSV lookup)
	if [ -z "$match" ] && [ -f "$SYSTEM_APN_DB" ] && [ -n "$op_code" ]; then
		match=$(awk -F'\t' -v code="$op_code" '$1 ~ "^"code"$" { print $2"|"$3"|"$4"|"$5"|"$6; exit }' "$SYSTEM_APN_DB" 2>/dev/null)
		if [ -n "$match" ]; then
			log "Matched carrier in global APN database for MCC-MNC $op_code"
		fi
	fi

	# Extract fields if matched from database
	if [ -n "$match" ]; then
		local f_name f_apn f_ip f_mode f_mbn
		f_name=$(echo "$match" | cut -d'|' -f1)
		f_apn=$(echo "$match" | cut -d'|' -f2)
		f_ip=$(echo "$match" | cut -d'|' -f3)
		f_mode=$(echo "$match" | cut -d'|' -f4)
		f_mbn=$(echo "$match" | cut -d'|' -f5)

		[ -n "$f_name" ] && CARRIER_NAME="$f_name"
		[ -n "$f_apn" ] && CARRIER_APN="$f_apn"
		[ -n "$f_ip" ] && CARRIER_IPTYPE="$f_ip"
		[ -n "$f_mode" ] && CARRIER_MODE="$f_mode"
		[ -n "$f_mbn" ] && CARRIER_MBN="$f_mbn"
		return 0
	fi

	# Priority 3: Operator Name Pattern Match fallback
	if echo "$op_name" | grep -qi -E "ntc|namaste|nepal telecom"; then
		CARRIER_NAME="Nepal Telecom (NTC)"
		CARRIER_APN="ntnet"
		CARRIER_IPTYPE="ipv4v6"
		CARRIER_MODE="4g"
		CARRIER_MBN="generic/common/row/gen_3gpp/mcfg_sw.mbn"
	elif echo "$op_name" | grep -qi -E "ncell"; then
		CARRIER_NAME="Ncell"
		CARRIER_APN="web"
		# IPv4 only: the PDN this operator grants for APN 'web' on this
		# subscription is IPv4.  A dual-stack request gets IPv4 and then fails
		# its IPv6 half with "ip-version-mismatch", which only adds noise.
		CARRIER_IPTYPE="ipv4"
		CARRIER_MODE="4g"
		# The ROW generic profile has no IMS feature, so VoLTE is impossible
		# with it; the Reliance profile is the only IMS-capable one here.
		CARRIER_MBN="generic/apac/reliance/commerci/mcfg_sw.mbn"
	elif echo "$op_name" | grep -qi -E "smart.*cell|smart.*telecom"; then
		CARRIER_NAME="Smart Telecom"
		CARRIER_APN="smart"
		CARRIER_IPTYPE="ipv4v6"
		CARRIER_MODE="4g"
		CARRIER_MBN="generic/common/row/gen_3gpp/mcfg_sw.mbn"
	elif echo "$op_name" | grep -qi "jio"; then
		CARRIER_NAME="Reliance Jio"
		CARRIER_APN="jionet"
		CARRIER_IPTYPE="ipv4v6"
		CARRIER_MODE="4g"
		CARRIER_MBN="generic/apac/reliance/commerci/mcfg_sw.mbn"
	elif echo "$op_name" | grep -qi "airtel"; then
		CARRIER_NAME="Airtel"
		CARRIER_APN="airtelgprs.com"
		CARRIER_IPTYPE="ipv4v6"
		CARRIER_MODE="4g"
		CARRIER_MBN="generic/apac/airtel/commerci/mcfg_sw.mbn"
	elif echo "$op_name" | grep -qi -E "vi|vodafone|idea"; then
		CARRIER_NAME="Vodafone Idea"
		CARRIER_APN="portalnmms"
		CARRIER_IPTYPE="ipv4v6"
		CARRIER_MODE="4g"
		CARRIER_MBN="generic/common/row/gen_3gpp/mcfg_sw.mbn"
	elif echo "$op_name" | grep -qi -E "bsnl|cellone"; then
		CARRIER_NAME="BSNL"
		CARRIER_APN="bsnlnet"
		CARRIER_IPTYPE="ipv4"
		CARRIER_MODE="4g"
		CARRIER_MBN="generic/common/row/gen_3gpp/mcfg_sw.mbn"
	else
		# Unknown / unrecognised carrier: keep what the user set.
		# Previously we always fell through to CARRIER_APN="internet", which
		# silently overwrote a manually-configured APN at every boot for any
		# MCC-MNC / operator name we cannot match -- even when the user had
		# explicitly entered a working one.  That made the engine actively
		# harmful on travel SIMs / MVNOs / private networks whose data only
		# exists in the user-override DB (or not at all).
		#
		# New behaviour: preserve the current uci setting if one exists;
		# only fall back to "internet" for the very first boot (empty config).
		local cur_apn
		cur_apn=$(uci -q get network.modem.apn 2>/dev/null || echo "")
		if [ -n "$cur_apn" ]; then
			log "Carrier not recognised (MCC-MNC=$op_code, name='$op_name'); keeping existing APN '$cur_apn' — add a $CUSTOM_APN_DB entry for automatic provisioning"
			CARRIER_APN="$cur_apn"
		else
			log "Carrier not recognised and no stored APN; defaulting to 'internet' — add a $CUSTOM_APN_DB entry for automatic provisioning"
			CARRIER_APN="internet"
		fi
		CARRIER_NAME="Generic"
		CARRIER_IPTYPE="ipv4v6"
		CARRIER_MODE="4g"
		CARRIER_MBN="generic/common/row/gen_3gpp/mcfg_sw.mbn"
	fi
}

# ---------------------------------------------------------------------------
# Carrier MCFG (the profile that decides whether the modem runs its IMS stack)
#
# The modem loads one carrier profile at boot.  It sets the network/band policy
# AND which firmware features are enabled -- including IMS.  The generic ROW
# profile has no IMS feature, so with it active the modem publishes no IMS QMI
# service at all: `qrtr-lookup` has no 33 (IMSA), and every --imsa-* call fails
# with "QMI protocol error (31) InvalidServiceType".  That reads like "not
# registered" but means the IMS task is not running, and VoLTE is impossible
# until a profile that carries IMS is active.
#
# Two traps made the naive implementation unsafe, and both are handled here:
#
#  * `mcfg_autoselect_by_uim` (= 05 on a stock unit) makes the modem re-pick
#    the profile from the SIM's IMSI at every boot.  For a SIM whose IMSI
#    matches no shipped MBN -- i.e. any carrier outside the built-in set --
#    the activation silently reverts: --pdc-activate-config returns SUCCESS,
#    the modem restarts, and the old profile is Active again.  Clearing the
#    item (00) is what makes the choice stick, and it survives reboots.
#
#  * The activation costs one modem restart (mcfg_utils.c:186, "MCFG:Modem
#    Initiated Reset. This crash is expected!!!"), and retrying it on every
#    boot turns that into an SSR per boot, which can desync the A2 handshake
#    (a2_power.c:1189).  So act only when the active profile is not the wanted
#    one, wait the restart out, and give up after a few consecutive failures.
#
# Identity is the config SIZE: the PDC store reports each uploaded config's
# byte size, and it equals the MBN file size exactly.  Names are not usable --
# the PDC description ("Commercial-Reliance") differs from the name inside the
# MBN ("Reliance").
# ---------------------------------------------------------------------------

MCFG_DIR=/lib/firmware/modem_pr/mcfg/configs
MCFG_ATTEMPTS=/etc/qcom-carrier-autocfg/mbn-attempts
MCFG_MAX_ATTEMPTS=3

mbn_file() {
	local rel="$1"

	[ -n "$rel" ] || return 1
	if [ -f "$MCFG_DIR/mcfg_sw/$rel" ]; then
		printf '%s\n' "$MCFG_DIR/mcfg_sw/$rel"
	elif [ -f "/lib/firmware/$rel" ]; then
		printf '%s\n' "/lib/firmware/$rel"
	else
		return 1
	fi
}

# Size of the currently Active software config ("" if none).
pdc_active_size() {
	qmi_retry qmicli -p -d "$1" --pdc-list-configs=software 2>/dev/null | awk '
		/^[[:space:]]*Size:/   { s = $2 }
		/^[[:space:]]*Status:/ { if ($2 == "Active") { print s; exit } }'
}

# Config ID of an already-uploaded config with the given size ("" if absent).
# Size precedes ID inside each block, so pairing them is enough; do not key on
# Status here -- ID comes after it, and a Status-driven rule reads the
# previous block's ID.
pdc_id_for_size() {
	qmi_retry qmicli -p -d "$1" --pdc-list-configs=software 2>/dev/null | awk '
		/^[[:space:]]*Size:/ { s = $2 }
		/^[[:space:]]*ID:/   { print s " " $2 }' |
		awk -v want="$2" '$1 == want { print $2; exit }'
}

# 0 = the modem is publishing the IMS application service.
ims_stack_present() {
	qrtr-lookup 2>/dev/null | awk '$1 == 33 { found = 1 } END { exit found ? 0 : 1 }'
}

set_mcfg_autoselect_off() {
	local p=/nv/item_files/mcfg/mcfg_autoselect_by_uim cur

	cur=$(diag_efs get "$p" 2>/dev/null | tail -n 1 | sed 's/.*== //' | tr -d ' \r')
	[ "$cur" = "00" ] && return 0

	log "MCFG auto-selection by SIM is on ($cur); disabling it so the activation can stick."
	printf '\000' > /tmp/mcfg-autoselect.bin
	diag_efs put /tmp/mcfg-autoselect.bin "$p" --force >/dev/null 2>&1
	rm -f /tmp/mcfg-autoselect.bin
	diag_logtool req 4b13300001002f00 0x4b >/dev/null 2>&1
	sleep 3

	cur=$(diag_efs get "$p" 2>/dev/null | tail -n 1 | sed 's/.*== //' | tr -d ' \r')
	[ "$cur" = "00" ]
}

# The activation resets the modem; the caller touches the bearer right after.
wait_modem_back() {
	local qmi="$1" i=0

	while [ "$i" -lt 40 ]; do
		sleep 2
		qmicli -p -d "$qmi" --pdc-list-configs=software >/dev/null 2>&1 && return 0
		i=$((i + 1))
	done
	return 1
}

# 0 = the wanted MBN is active, 1 = left alone or failed.
provision_carrier_mbn() {
	local rel="$1" qmi file want active id attempts

	[ -n "$rel" ] || return 1
	file=$(mbn_file "$rel") || {
		log "Carrier MCFG $rel is not in the firmware; leaving the active profile alone."
		return 1
	}
	qmi=$(ls /dev/wwan*qmi* 2>/dev/null | head -n 1)
	[ -n "$qmi" ] || return 1

	want=$(wc -c < "$file" | tr -d ' ')
	active=$(pdc_active_size "$qmi")

	if [ "$active" = "$want" ]; then
		log "Carrier MCFG $rel is already active ($want bytes)."
		rm -f "$MCFG_ATTEMPTS"
		return 0
	fi

	# The generic ROW profile carries no IMS, and it is what the modem falls
	# back to on its own; activating it buys nothing and the PDC size of that
	# config does not match the file size, so it would never look "already
	# active" and would restart the modem on every boot.
	case "$rel" in
		*common/row/*)
			log "Carrier MCFG $rel is the generic ROW profile; nothing to activate."
			rm -f "$MCFG_ATTEMPTS"
			return 0
			;;
	esac

	# Some profile that carries IMS is already serving, which is all this
	# function is for.  Do not restart the modem to swap one IMS profile for
	# another.
	if ims_stack_present; then
		log "The modem already runs its IMS stack; leaving the active MCFG profile alone."
		rm -f "$MCFG_ATTEMPTS"
		return 0
	fi

	# Each attempt costs a modem restart, so do not loop over boots forever.
	attempts=$(cat "$MCFG_ATTEMPTS" 2>/dev/null || echo 0)
	case "$attempts" in ''|*[!0-9]*) attempts=0 ;; esac
	if [ "$attempts" -ge "$MCFG_MAX_ATTEMPTS" ]; then
		log "Carrier MCFG still not active after $attempts attempts; leaving it alone (remove $MCFG_ATTEMPTS to retry)."
		return 1
	fi

	log "Carrier MCFG: active profile is ${active:-unknown} bytes, want $rel ($want bytes)."

	set_mcfg_autoselect_off ||
		log "WARNING: could not disable MCFG auto-selection by SIM; the activation may revert."

	id=$(pdc_id_for_size "$qmi" "$want")
	if [ -z "$id" ]; then
		log "Uploading $rel to the PDC store..."
		qmi_retry qmicli -p -d "$qmi" --pdc-load-config="$file" >/dev/null 2>&1
		id=$(pdc_id_for_size "$qmi" "$want")
	fi
	if [ -z "$id" ]; then
		log "WARNING: the PDC store did not accept $rel."
		echo $((attempts + 1)) > "$MCFG_ATTEMPTS"
		return 1
	fi

	log "Activating MCFG $id (the modem restarts itself)..."
	qmi_retry qmicli -p -d "$qmi" --pdc-activate-config="software,$id" >/dev/null 2>&1
	wait_modem_back "$qmi" ||
		log "WARNING: the modem did not answer after the MCFG activation."

	active=$(pdc_active_size "$qmi")
	if [ "$active" = "$want" ]; then
		rm -f "$MCFG_ATTEMPTS"
		if ims_stack_present; then
			log "Carrier MCFG $rel is active; the IMS stack is running."
		else
			log "Carrier MCFG $rel is active but no IMS service appeared."
		fi
		return 0
	fi

	# The PDC size is not always the file size, so an unchanged size after the
	# restart does not by itself mean the activation failed.
	if ims_stack_present; then
		rm -f "$MCFG_ATTEMPTS"
		log "The IMS stack is running after activating $rel; treating the activation as done."
		return 0
	fi

	echo $((attempts + 1)) > "$MCFG_ATTEMPTS"
	log "WARNING: the MCFG activation did not stick (active profile is ${active:-unknown} bytes, wanted $want)."
	return 1
}

provision_network() {
	local apn="$1"
	local iptype="$2"
	local mode="$3"
	local is_jio="${4:-0}"

	local cur_apn cur_iptype cur_proto cur_allowed cur_preferred
	cur_apn=$(uci -q get network.modem.apn || echo "")
	cur_iptype=$(uci -q get network.modem.iptype || echo "")
	cur_proto=$(uci -q get network.modem.proto || echo "")
	cur_allowed=$(uci -q get network.modem.allowedmode || echo "")
	cur_preferred=$(uci -q get network.modem.preferredmode || echo "")

	local target_allowed target_preferred
	if [ "$is_jio" = "1" ]; then
		target_allowed="4g"
		target_preferred=""
	else
		# Non-Jio carriers: 4G preferred with 3G/2G fallback
		target_allowed="3g|4g"
		target_preferred="4g"
	fi

	local cur_apn cur_iptype cur_proto cur_allowed cur_preferred cur_eps
	# `init_epsbearer=default` makes the modemmanager proto call
	# `mmcli --3gpp-set-initial-eps-bearer-settings` on every connect.  On the
	# HMU05-family modem firmware (HM02_MODEM_V1.2) that call fails with QMI
	# error 81 ("DS profile error: invalid-parameter-length") and, because the
	# command powers the modem down before modifying the profile, leaves the
	# modem re-probing (netifd: "couldn't find modem", interface NO_DEVICE).
	# The profile is written with qmicli by provision_modem_profile() instead.
	local target_eps="default"
	case "$(cat /tmp/sysinfo/board_name 2>/dev/null)" in
		*hmu05*|*hmuf02v5*|*mifib-v12*) target_eps="none" ;;
	esac

	cur_apn=$(uci -q get network.modem.apn || echo "")
	cur_iptype=$(uci -q get network.modem.iptype || echo "")
	cur_proto=$(uci -q get network.modem.proto || echo "")
	cur_allowed=$(uci -q get network.modem.allowedmode || echo "")
	cur_preferred=$(uci -q get network.modem.preferredmode || echo "")
	cur_eps=$(uci -q get network.modem.init_epsbearer || echo "none")

	# An empty network.modem.dns is a valid state, not a reason to reconfigure:
	# with peerdns=1 the bearer supplies the resolvers.  It used to be a trigger,
	# so deleting the fixed pair below brought it straight back on the next boot.
	if [ "$cur_apn" != "$apn" ] || [ "$cur_iptype" != "$iptype" ] || [ "$cur_proto" != "modemmanager" ] || [ "$cur_allowed" != "$target_allowed" ] || [ "$cur_preferred" != "$target_preferred" ] || [ "$cur_eps" != "$target_eps" ]; then
		log "Configuring /etc/config/network: APN='$apn', IP-Type='$iptype', Allowed='$target_allowed', Preferred='${target_preferred:-none}'..."
		uci set network.modem=interface
		uci set network.modem.proto='modemmanager'
		uci set network.modem.device='qcom-soc'
		uci set network.modem.apn="$apn"
		uci set network.modem.iptype="$iptype"
		if [ "$target_eps" = "none" ]; then
			uci -q delete network.modem.init_epsbearer
		else
			uci set network.modem.init_epsbearer="$target_eps"
		fi
		uci set network.modem.peerdns='1'
		uci set network.modem.allowedmode="$target_allowed"
		if [ -n "$target_preferred" ]; then
			uci set network.modem.preferredmode="$target_preferred"
		else
			uci -q delete network.modem.preferredmode
		fi
		uci set network.modem.defaultroute='1'
		uci set network.modem.metric='10'
		uci commit network
		log "Network configuration updated. Reloading netifd..."
		"${NETWORK_INIT:-/etc/init.d/network}" reload
		sleep 3
	fi

	# Units provisioned before this change still carry the pair this script used
	# to write.  With peerdns=1 the bearer supplies DNS, so the pair only added a
	# second, distant resolver that is often unreachable or answers AAAA first.
	# Remove exactly the value that was shipped, and only while peerdns is on:
	# any other value is the operator's own and is left alone.  No reload here --
	# this runs once per unit and the next boot or bearer reconfigure picks it up,
	# which is cheaper than dropping the link to change a resolver list.
	if [ "$(uci -q get network.modem.peerdns)" = "1" ] &&
	   [ "$(uci -q get network.modem.dns)" = "8.8.8.8 1.1.1.1" ]; then
		uci -q delete network.modem.dns
		uci commit network
		log "Removed the fixed DNS pair 8.8.8.8 1.1.1.1; peerdns=1 supplies DNS."
	fi
}

provision_carrier_bands() {
	local m_path="$1"
	local mode="$2"
	local carrier="$3"
	local op_code="$4"
	local op_name="$5"
	local is_jio="${6:-0}"

	# HMUF02-V5-only guard: prevent affecting other board targets
	case "$(cat /tmp/sysinfo/board_name 2>/dev/null)" in
		*hmu05*|*hmuf02v5*) ;;
		*) return 0 ;;
	esac

	if [ "$is_jio" = "1" ] && [ -n "$m_path" ]; then
		local cur_bands
		cur_bands=$(mmcli -m "$m_path" --output-keyvalue 2>/dev/null | awk -F': ' '/modem.generic.current-bands.value/ {print $2}')

		if echo "$cur_bands" | grep -qi -E '\<utran|\<geran'; then
			log "HMUF02-V5 (Jio): Restricting modem bands to LTE-only (B1/B3/B5/B8) to eliminate 2G/3G IRAT measurement gap crashes..."
			mmcli -m "$m_path" --set-current-bands="eutran-1|eutran-3|eutran-5|eutran-8" 2>/dev/null || true
		fi
	elif [ "$is_jio" = "0" ] && [ -n "$m_path" ]; then
		# Non-Jio carrier (Airtel, BSNL, Vi, Ncell, NTC, etc.): Restore all supported bands if previously restricted
		local sup_bands cur_bands all_bands=""
		sup_bands=$(mmcli -m "$m_path" --output-keyvalue 2>/dev/null | awk -F': ' '/modem.generic.supported-bands.value/ {print $2}')
		cur_bands=$(mmcli -m "$m_path" --output-keyvalue 2>/dev/null | awk -F': ' '/modem.generic.current-bands.value/ {print $2}')

		for b in $(echo "$sup_bands" | tr -s ', ' '\n'); do
			[ -n "$b" ] || continue
			all_bands="${all_bands:+${all_bands}|}$b"
		done

		if [ -n "$all_bands" ] && ! echo "$cur_bands" | grep -qi -E '\<utran|\<geran'; then
			log "HMUF02-V5 (Non-Jio Carrier '$carrier'): Restoring full multi-mode bands ($all_bands) for 2G/3G/4G support..."
			mmcli -m "$m_path" --set-current-bands="$all_bands" 2>/dev/null || true
		fi
	fi
}

reset_baseband_cache() {
	local m_path="$1"
	log "Flushing baseband radio cache requested for modem '$m_path'..."

	# Under pure-software modem operation with pristine stock firmware,
	# raw AT+CFUN=0/1 triggers a fatal baseband assertion (lte_ml1_common_dump.c:213).
	# For pure-software stability, raw AT+CFUN is strictly prohibited.
	log "Pure-software modem profile: raw AT+CFUN=0/1 is disabled to prevent baseband crash."

	# Cycle ModemManager power state via QMI DMS to ensure registration states are refreshed cleanly
	if [ -n "$m_path" ]; then
		mmcli -m "$m_path" --set-power-state-low 2>/dev/null || true
		sleep 1
		mmcli -m "$m_path" --set-power-state-on 2>/dev/null || true
		sleep 1
		mmcli -m "$m_path" -e 2>/dev/null || true
	fi
}

# The modem starts every bearer from its default 3GPP profile.  When that
# profile has an EMPTY APN the modem attaches with the subscription's default
# APN, and the network then refuses IPv4 on the APN we actually asked for:
#
#   couldn't start IPv4 network: QMI protocol error (14): 'CallFailed'
#   verbose call end reason (2,208): pdn-ipv4-call-disallowed
#   connection attempt #1 failed: pdn-ipv4-call-disallowed: IPv6 only allowed
#
# Writing the carrier APN into that profile fixes it, even though the APN in
# uci was already correct.  ModemManager's --3gpp-set-initial-eps-bearer-settings
# is meant to do this, but this modem firmware (HM02_MODEM_V1.2) rejects it
# with QMI error 81 ("DS profile error: invalid-parameter-length"), so write
# the profile with qmicli instead.
# Two spellings are in play: uci uses ipv4/ipv6/ipv4v6, --wds-modify-profile
# wants IP/IPV6/IPV4V6, and --wds-get-profile-list answers ipv4/ipv6/
# ipv4-or-ipv6.  Normalise both sides before comparing, or every run rewrites
# the profile.
qmi_pdp_type() {
	case "$1" in
		ipv4|IP) echo IP ;;
		ipv6|IPV6) echo IPV6 ;;
		*) echo IPV4V6 ;;
	esac
}

qmi_pdp_norm() {
	case "$1" in
		ipv4|IP|IPV4) echo ipv4 ;;
		ipv6|IPV6) echo ipv6 ;;
		*) echo ipv4v6 ;;
	esac
}

# qmicli and ModemManager both want the modem's QMI port.  A direct open steals
# the port from ModemManager and leaks client IDs until the pool is exhausted
# and ModemManager gives up with "ClientIdsExhausted", so always go through the
# QMI proxy (-p) the way the watchdog and sim-hotplug do.  Even then a client
# allocation can fail with "endpoint hangup" while ModemManager reprobes, so
# retry instead of failing the whole provision.
qmi_retry() {
	local i=0 out
	while [ "$i" -lt 8 ]; do
		out=$("$@" 2>&1)
		case "$out" in
			*"endpoint hangup"*|*"Transaction timed out"*|*"Resource temporarily unavailable"*)
				i=$((i + 1))
				sleep 3
				;;
			*)
				printf '%s\n' "$out"
				return 0
				;;
		esac
	done
	return 1
}

# The LTE attach context is a separate QMI object from the 3GPP profile.  The
# modem derives it from the default profile when the radio is turned off and
# back on, and it is what decides whether the network accepts an IPv4 PDN.  A
# stale IPv6 context refuses every IPv4 bearer with
# "pdn-ipv4-call-disallowed: IPv6 only allowed" no matter what the profile or
# uci say, which is exactly what happens after a reboot: the profile resets to
# empty, the modem attaches IPv6, and writing the profile afterwards is too
# late.  mmcli's --3gpp-set-initial-eps-bearer-settings is the intended lever
# but this firmware rejects it with QMI error 81.
qmi_attach_context() {
	local qmi="$1" out apn ipt

	out=$(qmi_retry qmicli -p -d "$qmi" --wds-get-lte-attach-parameters) || return 1
	apn=$(printf '%s\n' "$out" | awk -F': ' '/^[[:space:]]*APN:/ {print $2; exit}' | tr -d "'\r" | sed 's/[[:space:]]*$//')
	ipt=$(printf '%s\n' "$out" | awk -F': ' '/IP support type:/ {print $2; exit}' | tr -d "'\r" | sed 's/[[:space:]]*$//')
	printf '%s|%s\n' "$apn" "$ipt"
}

# --wds-get-lte-attach-parameters answers ipv4 / ipv6 / ipv4-or-ipv6.
attach_pdp_norm() {
	case "$1" in
		ipv4|IP|IPV4) echo ipv4 ;;
		ipv6|IPV6) echo ipv6 ;;
		*) echo ipv4v6 ;;
	esac
}

# ModemManager drops the modem object while it reprobes the QMI port, so a
# single "mmcli -L" can legitimately come back empty.
mm_modem_path() {
	local i=0 p
	while [ "$i" -lt 10 ]; do
		p=$(mmcli -L 2>/dev/null | grep -o '/org/freedesktop/ModemManager1/Modem/[0-9]*' | head -n 1)
		[ -n "$p" ] && {
			printf '%s\n' "$p"
			return 0
		}
		i=$((i + 1))
		sleep 2
	done
	return 1
}

cycle_modem_radio() {
	local m_path="$1"
	local qmi="$2"
	local i=0

	log "Cycling the modem radio so the LTE attach context picks up the new profile..."
	mmcli -m "$m_path" --set-power-state-low >/dev/null 2>&1 || {
		log "WARNING: could not power the modem down."
		return 1
	}

	# ModemManager usually brings the radio back on by itself; nudge it if not.
	while [ "$i" -lt 30 ]; do
		sleep 1
		mmcli -m "$m_path" --set-power-state-on >/dev/null 2>&1 && break
		i=$((i + 1))
	done

	# Wait until the modem answers QMI again.
	i=0
	while [ "$i" -lt 20 ]; do
		sleep 3
		qmicli -p -d "$qmi" --wds-get-lte-attach-parameters >/dev/null 2>&1 && return 0
		i=$((i + 1))
	done
	log "WARNING: the modem did not answer QMI after the radio cycle."
	return 1
}

provision_modem_profile() {
	local apn="$1"
	local iptype="$2"
	local qmi prof defnum cur_apn cur_pdp pdp want_ipt
	local m_path ctx ctx_apn ctx_ipt

	[ -n "$apn" ] || return 1

	qmi=$(ls /dev/wwan*qmi* 2>/dev/null | head -n 1)
	[ -n "$qmi" ] || {
		log "No QMI device found; skipping modem profile provisioning."
		return 1
	}

	defnum=$(qmi_retry qmicli -p -d "$qmi" --wds-get-default-profile-number=3gpp 2>/dev/null |
		awk -F"'" '/Default profile number/ {print $2; exit}')
	[ -n "$defnum" ] || defnum=1

	prof=$(qmi_retry qmicli -p -d "$qmi" --wds-get-profile-list=3gpp 2>/dev/null)
	cur_apn=$(printf '%s\n' "$prof" |
		awk -F"'" -v n="$defnum" '$0 ~ "\\[" n "\\] 3gpp" {f=1; next} f && /\[[0-9]+\] 3gpp/ {f=0} f && /APN:/ {print $2; exit}')
	cur_pdp=$(printf '%s\n' "$prof" |
		awk -F"'" -v n="$defnum" '$0 ~ "\\[" n "\\] 3gpp" {f=1; next} f && /\[[0-9]+\] 3gpp/ {f=0} f && /PDP type:/ {print $2; exit}')
	pdp=$(qmi_pdp_type "$iptype")

	if [ "$cur_apn" = "$apn" ] && [ "$(qmi_pdp_norm "$cur_pdp")" = "$(qmi_pdp_norm "$iptype")" ]; then
		log "Modem profile $defnum matches APN '$apn' / PDP type '$(qmi_pdp_norm "$iptype")'."
	else
		log "Modem profile $defnum has APN '${cur_apn:-<empty>}' / PDP type '${cur_pdp:-?}'; provisioning APN '$apn' / PDP type '$pdp'..."
		if qmi_retry qmicli -p -d "$qmi" --wds-modify-profile="3gpp,${defnum},apn=${apn},pdp-type=${pdp}" 2>&1 |
			grep -q "successfully modified"; then
			log "Modem profile $defnum updated."
		else
			log "WARNING: could not update modem profile $defnum; the bearer may fall back to the subscription's default APN."
			return 1
		fi
	fi

	# The profile alone does not change what the modem already attached with.
	want_ipt=$(qmi_pdp_norm "$iptype")
	ctx=$(qmi_attach_context "$qmi" 2>/dev/null) || ctx=""
	ctx_apn=${ctx%%|*}
	ctx_ipt=${ctx#*|}
	[ "$ctx" = "" ] && ctx_ipt=""

	if [ "$ctx_apn" = "$apn" ] && [ "$(attach_pdp_norm "$ctx_ipt")" = "$want_ipt" ]; then
		log "LTE attach context matches APN '$apn' / PDP type '$want_ipt'."
		return 0
	fi

	m_path=$(mm_modem_path)
	[ -n "$m_path" ] || {
		log "WARNING: no ModemManager modem; cannot refresh the LTE attach context."
		return 1
	}

	log "LTE attach context is APN '${ctx_apn:-<empty>}' / PDP type '${ctx_ipt:-?}'; refreshing it."
	cycle_modem_radio "$m_path" "$qmi" || return 1

	ctx=$(qmi_attach_context "$qmi" 2>/dev/null) || ctx=""
	ctx_apn=${ctx%%|*}
	ctx_ipt=${ctx#*|}
	if [ "$ctx_apn" = "$apn" ] && [ "$(attach_pdp_norm "$ctx_ipt")" = "$want_ipt" ]; then
		log "LTE attach context refreshed: APN '$apn' / PDP type '$want_ipt'."
		return 0
	fi
	log "WARNING: LTE attach context is still '${ctx_apn:-?}' / '${ctx_ipt:-?}'; the bearer may be refused."
	return 1
}

flush_bearer_cache() {
	local m_path="$1"
	local target_apn="$2"
	local target_iptype="$3"

	log "Flushing network interface and bearer caches..."

	# 1. Bring down netifd interface
	ifdown modem 2>/dev/null || true

	# 2. Disconnect and delete all existing ModemManager bearers
	if [ -n "$m_path" ]; then
		mmcli -m "$m_path" --simple-disconnect 2>/dev/null || true

		local bearer_list
		bearer_list=$(mmcli -m "$m_path" --output-keyvalue 2>/dev/null | grep -o '/org/freedesktop/ModemManager1/Bearer/[0-9]*' | sort -u)
		for b in $bearer_list; do
			log "Deleting obsolete bearer: $b"
			mmcli -m "$m_path" --delete-bearer="$b" 2>/dev/null || true
		done

		# 3. Write the carrier APN into the modem's default 3GPP profile: that
		#    profile is what the next bearer is started from.
		provision_modem_profile "$target_apn" "$target_iptype"
	fi
}

STORED_IMSI_FILE="/etc/qcom-carrier-autocfg/last_provisioned_imsi"

check_and_flush_radio_cache() {
	local m_path="$1"
	local target_apn="$2"
	local target_iptype="$3"
	local current_imsi="$4"
	local target_op_code="$5"
	local carrier_name="$6"
	local is_jio="${7:-0}"

	local stored_imsi
	stored_imsi=$(cat "$STORED_IMSI_FILE" 2>/dev/null || echo "")

	local cached_uci_apn
	cached_uci_apn=$(uci -q get network.modem.apn || echo "")

	local need_flush=0

	# 1. SIM identity check: Stored IMSI differs from current SIM
	if [ -n "$current_imsi" ] && [ "$stored_imsi" != "$current_imsi" ]; then
		log "SIM swap detected (stored IMSI='${stored_imsi:-none}', current IMSI='$current_imsi'). Radio cache flush required."
		need_flush=1
	fi

	# 2. Network config APN check
	if [ -n "$cached_uci_apn" ] && [ "$cached_uci_apn" != "$target_apn" ]; then
		log "APN mismatch detected in network cache: cached='$cached_uci_apn', SIM requires='$target_apn'"
		need_flush=1
	fi

	if [ -n "$m_path" ]; then
		local modem_kv
		modem_kv=$(mmcli -m "$m_path" -K 2>/dev/null)

		# 3. Baseband Operator ID check: If modem stack still holds another operator's session
		local cached_modem_op
		cached_modem_op=$(echo "$modem_kv" | awk -F': ' '/modem.3gpp.operator-code/ {print $2}' | tr -d ' \r\n')
		if [ -n "$cached_modem_op" ] && [ "$cached_modem_op" != "--" ] && [ -n "$target_op_code" ] && [ "$cached_modem_op" != "$target_op_code" ]; then
			log "Operator mismatch in modem radio cache: modem is on '$cached_modem_op', SIM wants '$target_op_code'"
			need_flush=1
		fi

		# 4. Initial EPS Bearer APN check.  An EMPTY APN is a mismatch, not an
		#    unknown: it is exactly the state in which the network refuses IPv4
		#    (see provision_modem_profile).
		local cached_eps_apn
		cached_eps_apn=$(echo "$modem_kv" | awk -F': ' '/modem.3gpp.eps.initial-bearer.settings.apn/ {print $2}' | tr -d " '\r\n")
		if [ "$cached_eps_apn" != "--" ] && [ -n "$target_apn" ] && [ "$cached_eps_apn" != "$target_apn" ]; then
			log "Initial EPS bearer APN mismatch: radio cache has '${cached_eps_apn:-<empty>}', SIM requires '$target_apn'"
			need_flush=1
		fi

		# 5. Radio Band configuration check (HMUF02-V5-only: older modem OS 1.0 IRAT workaround)
		case "$(cat /tmp/sysinfo/board_name 2>/dev/null)" in
			*hmu05*|*hmuf02v5*)
				local cur_bands
				cur_bands=$(echo "$modem_kv" | awk -F': ' '/modem.generic.current-bands.value/ {print $2}')
				if [ "$is_jio" = "1" ]; then
					if echo "$cur_bands" | grep -qi -E '\<utran|\<geran'; then
						log "HMUF02-V5: Radio band mismatch: Jio requires LTE-only, but radio cache contains 2G/3G bands"
						need_flush=1
					fi
				else
					if [ -n "$cur_bands" ] && ! echo "$cur_bands" | grep -qi -E '\<utran'; then
						log "HMUF02-V5: Radio band mismatch: '$carrier_name' requires 3G/4G multi-mode, but radio cache is locked to LTE-only"
						need_flush=1
					fi
				fi
				;;
		esac

		# 6. Radio Mode check
		local cur_modes
		cur_modes=$(echo "$modem_kv" | awk -F': ' '/modem.generic.current-modes/ {print $2}')
		if [ "$is_jio" = "0" ] && [ -n "$cur_modes" ]; then
			if ! echo "$cur_modes" | grep -qi '3g'; then
				log "Radio mode mismatch: '$carrier_name' requires 3G fallback, but radio cache lacks 3G"
				need_flush=1
			fi
		fi

		# 7. Existing Bearer APN check: Check if any active bearer has a conflicting APN
		local bearer_count b_idx b_path b_apn
		bearer_count=$(echo "$modem_kv" | awk -F': ' '/modem.generic.bearers.length/ {print $2}' | tr -d ' \r\n')
		if [ -n "$bearer_count" ] && [ "$bearer_count" -gt 0 ] 2>/dev/null; then
			b_idx=1
			while [ "$b_idx" -le "$bearer_count" ]; do
				b_path=$(echo "$modem_kv" | awk -F': ' "/modem.generic.bearers.value\\[$b_idx\\]/ {print \$2}" | tr -d ' \r\n')
				if [ -n "$b_path" ]; then
					b_apn=$(mmcli -b "$b_path" -K 2>/dev/null | awk -F': ' '$1 == "bearer.properties.apn" {print $2}' | tr -d " '\r\n-")
					if [ -n "$b_apn" ] && [ "$b_apn" != "$target_apn" ]; then
						log "Bearer APN mismatch in bearer $b_path: cached='$b_apn', SIM requires='$target_apn'"
						need_flush=1
						break
					fi
				fi
				b_idx=$((b_idx + 1))
			done
		fi
	fi

	if [ "$need_flush" = "1" ]; then
		log "Radio cache mismatch confirmed. Flushing bearer cache for '$carrier_name' (APN: $target_apn)..."
		flush_bearer_cache "$m_path" "$target_apn" "$target_iptype"
		local m_state
		m_state=$(mmcli -m "$m_path" -K 2>/dev/null | awk -F': ' '$1 == "modem.generic.state" {print $2}' | tr -d ' \r\n')
		if [ "$m_state" != "connected" ]; then
			reset_baseband_cache "$m_path"
		fi
		return 0
	else
		log "Radio cache fully matches SIM requirements for '$carrier_name'. No cache flush needed."
		return 1
	fi
}

connect_bearer() {
	local apn="$1"
	local iptype="$2"
	local imsi="$3"

	# NOTE: there is deliberately NO QMI-time / ATS_USER gate here.
	#
	# This function used to carry an HMUF02-V5-only block that waited up to 15 s for
	# qcom-time-daemon's /var/run/qcom-time-synced marker and then `return 1` --
	# refusing to bring up the LTE bearer unless the modem had confirmed an
	# ATS_USER SET.  That gate rested on the SCLK / ~900 s calibration-watchdog
	# premise, which is RETRACTED (see qcom-time-daemon.init); the real ~900 s
	# fix is the HiMI_OK guard (ledger §112.170), which is orthogonal to time.
	# Android sends the ATS_USER SET once at boot and never gates LTE attach on
	# it.  Attaching must therefore NOT depend on qcom-time-daemon, so that
	# qcom-carrier-autocfg stays device-agnostic and qcom-time-daemon stays an
	# optional, HMUF02-V5-only extra.

	(
		flock -x 200
		# The profile must be right before the bearer starts; it is what the
		# modem starts the PDN from.
		provision_modem_profile "$apn" "$iptype"
		log "Triggering netifd interface bring-up for modem (APN: '$apn', IP-Type: '$iptype')..."
		ifup modem 2>/dev/null || true
	) 200>/var/lock/modem-bearer.lock

	if [ -n "$imsi" ]; then
		mkdir -p /etc/qcom-carrier-autocfg 2>/dev/null || true
		echo "$imsi" > "$STORED_IMSI_FILE" 2>/dev/null || true
	fi
}

# tests/qcom-carrier-autocfg sources this file to exercise provision_network
# against a stub uci; stop before the daemon loop in that case.
if [ "${QCOM_CARRIER_AUTOCFG_LIB_ONLY:-0}" = "1" ]; then
	return 0 2>/dev/null || exit 0
fi

log "Started MSM8916 SIM Carrier Auto-Provisioning Engine"

INITIAL_BOOT_PROVISION=1
LAST_OPERATOR_CODE=""
LAST_IMSI=""

# Main event loop: monitor SIM state
while true; do
	# Check if ModemManager is running and detected a modem
	MODEM_PATH=$(mmcli -L 2>/dev/null | grep -o '/org/freedesktop/ModemManager1/Modem/[0-9]*' | head -n 1)

	if [ -n "$MODEM_PATH" ]; then
		MODEM_KV=$(mmcli -m "$MODEM_PATH" -K 2>/dev/null)
		SIM_PATH=$(echo "$MODEM_KV" | awk -F': ' '/modem.generic.sim/ {print $2}')
		SIM_IDX=$(echo "$SIM_PATH" | grep -o '[0-9]*$' | head -n 1)

		if [ -n "$SIM_IDX" ]; then
			SIM_KV=$(mmcli -i "$SIM_IDX" -K 2>/dev/null)
			OP_CODE=$(echo "$SIM_KV" | awk -F': ' '/sim.properties.operator-code/ {print $2}' | tr -d ' \r\n')
			OP_NAME=$(echo "$SIM_KV" | awk -F': ' '/sim.properties.operator-name/ {print $2}' | tr -d '\r\n')
			IMSI=$(echo "$SIM_KV" | awk -F': ' '/sim.properties.imsi/ {print $2}' | tr -d ' \r\n')

			if [ -n "$OP_CODE" ] && [ "$OP_CODE" != "--" ]; then
				stored_imsi=$(cat "$STORED_IMSI_FILE" 2>/dev/null || echo "")
				cached_uci_apn=$(uci -q get network.modem.apn || echo "")

				if [ "$OP_CODE" != "$LAST_OPERATOR_CODE" ] || [ "$IMSI" != "$LAST_IMSI" ] || [ "$stored_imsi" != "$IMSI" ] || [ "$INITIAL_BOOT_PROVISION" = "1" ]; then
					lookup_carrier_profile "$OP_CODE" "$OP_NAME" "$IMSI"

					is_jio=0
					case "$CARRIER_NAME" in
						*[Jj]io*) is_jio=1 ;;
					esac
					case "$OP_CODE" in
						4058[4-7][0-9]) is_jio=1 ;;
					esac
					case "$OP_NAME" in
						*[Jj]io*|*[Rr]eliance*|*"IN Loop"*) is_jio=1 ;;
					esac

					# Make sure a profile that carries IMS is active; it
					# is what lets the modem start its IMS stack.
					provision_carrier_mbn "$CARRIER_MBN"

					check_and_flush_radio_cache "$MODEM_PATH" "$CARRIER_APN" "$CARRIER_IPTYPE" "$IMSI" "$OP_CODE" "$CARRIER_NAME" "$is_jio"
					provision_network "$CARRIER_APN" "$CARRIER_IPTYPE" "$CARRIER_MODE" "$is_jio"
					provision_carrier_bands "$MODEM_PATH" "$CARRIER_MODE" "$CARRIER_NAME" "$OP_CODE" "$OP_NAME" "$is_jio"

					LAST_OPERATOR_CODE="$OP_CODE"
					LAST_IMSI="$IMSI"

					if [ "$INITIAL_BOOT_PROVISION" = "1" ]; then
						# Device booted up with this SIM (e.g. swapped when powered off)
						INITIAL_BOOT_PROVISION=0
						connect_bearer "$CARRIER_APN" "$CARRIER_IPTYPE" "$IMSI"
						log "Boot-time carrier provisioning completed successfully. No reboot required."
						log "========================================================"
					elif [ "$OP_CODE" != "$LAST_OPERATOR_CODE" ] || [ "$IMSI" != "$LAST_IMSI" ] || [ "$stored_imsi" != "$IMSI" ]; then
						# Physical SIM was swapped while system was running (HOT-SWAP)
						log "HOT-SWAP: Live APN and baseband caches flushed."
						connect_bearer "$CARRIER_APN" "$CARRIER_IPTYPE" "$IMSI"
						log "Hot-swap handled live without reboot. Connection restored."
						log "========================================================"
					else
						# SIM is unchanged, but APN cache mismatch was detected and needs flush
						log "========================================================"
						log "RADIO CACHE MISMATCH DETECTED FOR CURRENT SIM ($CARRIER_NAME)"
						check_and_flush_radio_cache "$MODEM_PATH" "$CARRIER_APN" "$CARRIER_IPTYPE" "$IMSI" "$OP_CODE" "$CARRIER_NAME" "$is_jio"
						provision_network "$CARRIER_APN" "$CARRIER_IPTYPE" "$CARRIER_MODE" "$is_jio"
						provision_carrier_bands "$MODEM_PATH" "$CARRIER_MODE" "$CARRIER_NAME" "$OP_CODE" "$OP_NAME" "$is_jio"
						connect_bearer "$CARRIER_APN" "$CARRIER_IPTYPE" "$IMSI"
						log "Radio cache refreshed and connection restored."
						log "========================================================"
					fi
				fi
			fi
		fi
	fi

	sleep 10
done
