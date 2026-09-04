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
			return 0
		else
			log "Active Carrier MBN already matches $mbn_rel."
			return 1
		fi
	else
		log "No specific Carrier MBN found for $mbn_rel; preserving existing firmware config."
		return 1
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
		target_allowed="3g|4g"
		target_preferred="4g"
	fi

	local cur_apn cur_iptype cur_proto cur_allowed cur_preferred cur_eps cur_dns
	cur_apn=$(uci -q get network.modem.apn || echo "")
	cur_iptype=$(uci -q get network.modem.iptype || echo "")
	cur_proto=$(uci -q get network.modem.proto || echo "")
	cur_allowed=$(uci -q get network.modem.allowedmode || echo "")
	cur_preferred=$(uci -q get network.modem.preferredmode || echo "")
	cur_eps=$(uci -q get network.modem.init_epsbearer || echo "")
	cur_dns=$(uci -q get network.modem.dns || echo "")

	if [ "$cur_apn" != "$apn" ] || [ "$cur_iptype" != "$iptype" ] || [ "$cur_proto" != "modemmanager" ] || [ "$cur_allowed" != "$target_allowed" ] || [ "$cur_preferred" != "$target_preferred" ] || [ "$cur_eps" != "default" ] || [ -z "$cur_dns" ]; then
		log "Configuring /etc/config/network: APN='$apn', IP-Type='$iptype', Allowed='$target_allowed', Preferred='${target_preferred:-none}'..."
		uci set network.modem=interface
		uci set network.modem.proto='modemmanager'
		uci set network.modem.device='qcom-soc'
		uci set network.modem.apn="$apn"
		uci set network.modem.iptype="$iptype"
		uci set network.modem.init_epsbearer='default'
		uci set network.modem.peerdns='1'
		uci set network.modem.dns='8.8.8.8 1.1.1.1'
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

reset_baseband_cache() {
	local m_path="$1"
	log "Flushing baseband radio cache and re-reading SIM..."

	# Send AT+CFUN=0 (radio off / flush cell cache) then AT+CFUN=1 (radio on / re-read SIM)
	for at_port in /dev/wwan0at0 /dev/wwan0at1; do
		if [ -c "$at_port" ]; then
			log "Sending AT+CFUN radio reset via $at_port..."
			timeout 2 sh -c "printf 'AT+CFUN=0\r\n' > $at_port" 2>/dev/null || true
			sleep 1
			timeout 2 sh -c "printf 'AT+CFUN=1\r\n' > $at_port" 2>/dev/null || true
			break
		fi
	done

	# Cycle ModemManager power state via QMI DMS to ensure registration states are refreshed
	if [ -n "$m_path" ]; then
		mmcli -m "$m_path" --set-power-state-low 2>/dev/null || true
		sleep 1
		mmcli -m "$m_path" --set-power-state-on 2>/dev/null || true
		sleep 1
		mmcli -m "$m_path" -e 2>/dev/null || true
	fi
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

		# 3. Explicitly update initial EPS bearer settings for LTE network attach
		if [ -n "$target_apn" ]; then
			log "Setting 3GPP initial EPS bearer APN to '$target_apn' (${target_iptype:-ipv4v6})..."
			mmcli -m "$m_path" --3gpp-set-initial-eps-bearer-settings="apn=${target_apn},ip-type=${target_iptype:-ipv4v6}" 2>/dev/null || true
		fi
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

		# 4. Initial EPS Bearer APN check
		local cached_eps_apn
		cached_eps_apn=$(echo "$modem_kv" | awk -F': ' '/modem.3gpp.eps.initial-bearer.settings.apn/ {print $2}' | tr -d " '\r\n")
		if [ -n "$cached_eps_apn" ] && [ "$cached_eps_apn" != "--" ] && [ "$cached_eps_apn" != "$target_apn" ]; then
			log "Initial EPS bearer APN mismatch: radio cache has '$cached_eps_apn', SIM requires '$target_apn'"
			need_flush=1
		fi

		# 5. Radio Band configuration check (HMU05-only: older modem OS 1.0 IRAT workaround)
		case "$(cat /tmp/sysinfo/board_name 2>/dev/null)" in
			*hmu05*)
				local cur_bands
				cur_bands=$(echo "$modem_kv" | awk -F': ' '/modem.generic.current-bands.value/ {print $2}')
				if [ "$is_jio" = "1" ]; then
					if echo "$cur_bands" | grep -qi -E '\<utran|\<geran'; then
						log "HMU05: Radio band mismatch: Jio requires LTE-only, but radio cache contains 2G/3G bands"
						need_flush=1
					fi
				else
					if [ -n "$cur_bands" ] && ! echo "$cur_bands" | grep -qi -E '\<utran'; then
						log "HMU05: Radio band mismatch: '$carrier_name' requires 3G/4G multi-mode, but radio cache is locked to LTE-only"
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
					b_apn=$(mmcli -b "$b_path" -K 2>/dev/null | awk -F': ' '/bearer.properties.apn/ {print $2}' | tr -d " '\r\n")
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
		log "Radio cache mismatch confirmed. Flushing all bearer and baseband radio caches for '$carrier_name' (APN: $target_apn)..."
		flush_bearer_cache "$m_path" "$target_apn" "$target_iptype"
		reset_baseband_cache "$m_path"
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

	log "Requesting ModemManager bearer connection for APN '$apn' ($iptype)..."
	mmcli -m any --simple-connect="apn=${apn},ip-type=${iptype}" 2>/dev/null || true
	sleep 2
	ifup modem 2>/dev/null || true

	if [ -n "$imsi" ]; then
		mkdir -p /etc/qcom-carrier-autocfg 2>/dev/null || true
		echo "$imsi" > "$STORED_IMSI_FILE" 2>/dev/null || true
	fi
}

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

					mbn_updated=0
					if provision_carrier_mbn "$CARRIER_MBN"; then
						mbn_updated=1
					fi

					check_and_flush_radio_cache "$MODEM_PATH" "$CARRIER_APN" "$CARRIER_IPTYPE" "$IMSI" "$OP_CODE" "$CARRIER_NAME" "$is_jio"
					provision_network "$CARRIER_APN" "$CARRIER_IPTYPE" "$CARRIER_MODE" "$is_jio"
					provision_carrier_bands "$MODEM_PATH" "$CARRIER_MODE" "$CARRIER_NAME" "$OP_CODE" "$OP_NAME" "$is_jio"

					LAST_OPERATOR_CODE="$OP_CODE"
					LAST_IMSI="$IMSI"

					if [ "$INITIAL_BOOT_PROVISION" = "1" ]; then
						# Device booted up with this SIM (e.g. swapped when powered off)
						INITIAL_BOOT_PROVISION=0
						if [ "$mbn_updated" = "1" ]; then
							log "Carrier MBN radio firmware updated for '$CARRIER_NAME'. Scheduling automatic reboot in 3 seconds to initialize Hexagon DSP..."
							log "========================================================"
							sync
							sleep 3
							reboot
							exit 0
						else
							connect_bearer "$CARRIER_APN" "$CARRIER_IPTYPE" "$IMSI"
							log "Boot-time carrier provisioning completed successfully. No reboot required."
							log "========================================================"
						fi
					elif [ "$OP_CODE" != "$LAST_OPERATOR_CODE" ] || [ "$IMSI" != "$LAST_IMSI" ] || [ "$stored_imsi" != "$IMSI" ]; then
						# Physical SIM was swapped while system was running (HOT-SWAP)
						if [ "$mbn_updated" = "1" ]; then
							log "HOT-SWAP: Carrier MBN changed for '$CARRIER_NAME'. Hexagon modem DSP requires a reboot to load new MBN into baseband RAM."
							log "Restarting device in 3 seconds..."
							log "========================================================"
							sync
							sleep 3
							reboot
							exit 0
						else
							log "HOT-SWAP: Carrier MBN unchanged ($CARRIER_MBN). Live APN and baseband caches flushed."
							connect_bearer "$CARRIER_APN" "$CARRIER_IPTYPE" "$IMSI"
							log "Hot-swap handled live without reboot. Connection restored."
							log "========================================================"
						fi
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
