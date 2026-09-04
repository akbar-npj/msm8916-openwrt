#!/bin/sh
#
# Qualcomm MSM8916 SIM Carrier Auto-Provisioning Engine
# Automatically detects SIM card insertion/change, configures optimal APN,
# IP stack (IPv4/IPv6), and establishes high-stability connection.
#

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
		CARRIER_IPTYPE="ipv4v6"
		CARRIER_MODE="4g"
		CARRIER_MBN="generic/common/row/gen_3gpp/mcfg_sw.mbn"
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
	fi
}

provision_carrier_mbn() {
	local mbn_rel="$1"
	local mcfg_src=""

	# Priority 1: Check if device has its own native carrier tree in /lib/firmware/modem_pr
	if [ -f "/lib/firmware/modem_pr/mcfg/configs/mcfg_sw/$mbn_rel" ]; then
		mcfg_src="/lib/firmware/modem_pr/mcfg/configs/mcfg_sw/$mbn_rel"
		log "Found device-native Carrier MBN at: $mcfg_src"
	elif [ -f "/lib/firmware/modem_pr/$mbn_rel" ]; then
		mcfg_src="/lib/firmware/modem_pr/$mbn_rel"
		log "Found device-native Carrier MBN at: $mcfg_src"
	# Priority 2: Fall back to bundled global carrier database
	elif [ -f "/usr/share/qcom-carrier-autocfg/mcfg/$mbn_rel" ]; then
		mcfg_src="/usr/share/qcom-carrier-autocfg/mcfg/$mbn_rel"
		log "Using bundled Carrier MBN at: $mcfg_src"
	fi

	if [ -n "$mcfg_src" ]; then
		if [ ! -f /lib/firmware/MCFG_SW.MBN ] || ! cmp -s "$mcfg_src" /lib/firmware/MCFG_SW.MBN 2>/dev/null; then
			log "Deploying Carrier MBN '$mbn_rel' into /lib/firmware/MCFG_SW.MBN..."
			cp -af "$mcfg_src" /lib/firmware/MCFG_SW.MBN
			cp -af "$mcfg_src" /lib/firmware/mcfg_sw.mbn
			sync
			log "Carrier MBN deployed successfully."
		else
			log "Active Carrier MBN already matches $mbn_rel."
		fi
	else
		log "No specific Carrier MBN found for $mbn_rel; preserving existing firmware config."
	fi
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
		target_allowed="3g, 4g"
		target_preferred="4g"
	fi

	if [ "$cur_apn" != "$apn" ] || [ "$cur_iptype" != "$iptype" ] || [ "$cur_proto" != "modemmanager" ] || [ "$cur_allowed" != "$target_allowed" ] || [ "$cur_preferred" != "$target_preferred" ]; then
		log "Configuring /etc/config/network: APN='$apn', IP-Type='$iptype', Allowed='$target_allowed', Preferred='${target_preferred:-none}'..."
		uci set network.modem=interface
		uci set network.modem.proto='modemmanager'
		uci set network.modem.device='qcom-soc'
		uci set network.modem.apn="$apn"
		uci set network.modem.iptype="$iptype"
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
		/etc/init.d/network reload
		sleep 3
	fi
}

provision_carrier_bands() {
	local m_path="$1"
	local mode="$2"
	local carrier="$3"
	local op_code="$4"
	local op_name="$5"
	local is_jio="${6:-0}"

	# HMU05-only guard: prevent affecting other board targets
	case "$(cat /tmp/sysinfo/board_name 2>/dev/null)" in
		*hmu05*) ;;
		*) return 0 ;;
	esac

	if [ "$is_jio" = "1" ] && [ -n "$m_path" ]; then
		local sup_bands cur_bands lte_bands=""
		sup_bands=$(mmcli -m "$m_path" --output-keyvalue 2>/dev/null | awk -F': ' '/modem.generic.supported-bands.value/ {print $2}')
		cur_bands=$(mmcli -m "$m_path" --output-keyvalue 2>/dev/null | awk -F': ' '/modem.generic.current-bands.value/ {print $2}')

		for b in $(echo "$sup_bands" | tr -s ', ' '\n'); do
			case "$b" in
				eutran-*)
					lte_bands="${lte_bands:+${lte_bands}|}$b"
					;;
			esac
		done

		if [ -n "$lte_bands" ] && echo "$cur_bands" | grep -qi -E '\<utran|\<geran'; then
			log "HMU05 (Jio): Restricting modem bands to LTE-only ($lte_bands) to eliminate 2G/3G IRAT measurement gap crashes..."
			mmcli -m "$m_path" --set-current-bands="$lte_bands" 2>/dev/null || true
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
			log "HMU05 (Non-Jio Carrier '$carrier'): Restoring full multi-mode bands ($all_bands) for 2G/3G/4G support..."
			mmcli -m "$m_path" --set-current-bands="$all_bands" 2>/dev/null || true
		fi
	fi
}

connect_bearer() {
	local apn="$1"
	local iptype="$2"

	log "Requesting ModemManager bearer connection for APN '$apn' ($iptype)..."
	mmcli -m any --simple-connect="apn=${apn},ip-type=${iptype}" 2>/dev/null || true
	sleep 2
	ifup modem 2>/dev/null || true
}

log "Started MSM8916 SIM Carrier Auto-Provisioning Engine"

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
				if [ "$OP_CODE" != "$LAST_OPERATOR_CODE" ] || [ "$IMSI" != "$LAST_IMSI" ]; then
					log "========================================================"
					log "SIM CARD DETECTED / CHANGED!"
					log "Operator Code: $OP_CODE | Operator Name: $OP_NAME | IMSI: $IMSI"
					
					lookup_carrier_profile "$OP_CODE" "$OP_NAME" "$IMSI"
					log "Identified Carrier: $CARRIER_NAME"
					log "Optimal Settings: APN='$CARRIER_APN', IP-Type='$CARRIER_IPTYPE', Mode='$CARRIER_MODE', MBN='$CARRIER_MBN'"
					
					local is_jio=0
					case "$CARRIER_NAME" in
						*[Jj]io*) is_jio=1 ;;
					esac
					case "$OP_CODE" in
						4058[4-7][0-9]) is_jio=1 ;;
					esac
					case "$OP_NAME" in
						*[Jj]io*|*[Rr]eliance*|*"IN Loop"*) is_jio=1 ;;
					esac

					provision_carrier_mbn "$CARRIER_MBN"
					provision_network "$CARRIER_APN" "$CARRIER_IPTYPE" "$CARRIER_MODE" "$is_jio"
					provision_carrier_bands "$MODEM_PATH" "$CARRIER_MODE" "$CARRIER_NAME" "$OP_CODE" "$OP_NAME" "$is_jio"
					connect_bearer "$CARRIER_APN" "$CARRIER_IPTYPE"
					
					LAST_OPERATOR_CODE="$OP_CODE"
					LAST_IMSI="$IMSI"
					log "Carrier auto-provisioning completed successfully."
					log "========================================================"
				fi
			fi
		fi
	fi

	sleep 10
done
