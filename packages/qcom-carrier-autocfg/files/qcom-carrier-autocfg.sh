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

	case "$op_code" in
		# Reliance Jio (India) - MCC 405, MNC 840..874
		4058[4-7][0-9]|405861|405840|405854|405855|405856|405857|405858|405859|405860|405862|405863|405864|405865|405866|405867|405868|405869|405870|405871|405872|405873|405874)
			CARRIER_NAME="Reliance Jio"
			CARRIER_APN="jionet"
			CARRIER_IPTYPE="ipv4v6"
			CARRIER_MODE="4g"
			CARRIER_MBN="generic/apac/reliance/commerci/mcfg_sw.mbn"
			;;
		# Bharti Airtel (India) - MCC 404/405
		404[0-9][0-9]|405[0-9][0-9])
			if echo "$op_name" | grep -qi "airtel"; then
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
			elif echo "$op_name" | grep -qi "jio"; then
				CARRIER_NAME="Reliance Jio"
				CARRIER_APN="jionet"
				CARRIER_IPTYPE="ipv4v6"
				CARRIER_MODE="4g"
				CARRIER_MBN="generic/apac/reliance/commerci/mcfg_sw.mbn"
			fi
			;;
		# China Mobile (CMCC)
		46000|46002|46007|46008)
			CARRIER_NAME="China Mobile"
			CARRIER_APN="cmnet"
			CARRIER_IPTYPE="ipv4v6"
			CARRIER_MBN="generic/china/cmcc/csfb/ss/commerci/mcfg_sw.mbn"
			;;
		# China Unicom (CU)
		46001|46006|46009)
			CARRIER_NAME="China Unicom"
			CARRIER_APN="3gnet"
			CARRIER_IPTYPE="ipv4v6"
			CARRIER_MBN="generic/china/cu/csfb/ss/commerci/mcfg_sw.mbn"
			;;
		# China Telecom (CT)
		46003|46005|46011)
			CARRIER_NAME="China Telecom"
			CARRIER_APN="ctnet"
			CARRIER_IPTYPE="ipv4v6"
			CARRIER_MBN="generic/china/ct/srlte/ss/commerci/mcfg_sw.mbn"
			;;
		# AT&T (USA)
		310410|310280|310150|310030|310070|310560|310680)
			CARRIER_NAME="AT&T"
			CARRIER_APN="broadband"
			CARRIER_IPTYPE="ipv4v6"
			CARRIER_MBN="generic/na/att/volte/mcfg_sw.mbn"
			;;
		# Verizon (USA)
		311480|311270|311280|311481|311482|311483|311484|311485|311486|311487|311488|311489)
			CARRIER_NAME="Verizon"
			CARRIER_APN="vzwinternet"
			CARRIER_IPTYPE="ipv4v6"
			CARRIER_MBN="generic/na/verizon/hvolte/mcfg_sw.mbn"
			;;
		# T-Mobile (USA)
		310260|310160|310200|310210|310220|310230|310240|310250|310270|310310|310660|310800)
			CARRIER_NAME="T-Mobile"
			CARRIER_APN="fast.t-mobile.com"
			CARRIER_IPTYPE="ipv4v6"
			CARRIER_MBN="generic/na/tmo/volte_co/mcfg_sw.mbn"
			;;
		*)
			if echo "$op_name" | grep -qi "jio"; then
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
			fi
			;;
	esac
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

	local cur_apn cur_iptype cur_proto
	cur_apn=$(uci -q get network.modem.apn || echo "")
	cur_iptype=$(uci -q get network.modem.iptype || echo "")
	cur_proto=$(uci -q get network.modem.proto || echo "")

	if [ "$cur_apn" != "$apn" ] || [ "$cur_iptype" != "$iptype" ] || [ "$cur_proto" != "modemmanager" ]; then
		log "Configuring /etc/config/network: APN='$apn', IP-Type='$iptype', Mode='$mode'..."
		uci set network.modem=interface
		uci set network.modem.proto='modemmanager'
		uci set network.modem.device='qcom-soc'
		uci set network.modem.apn="$apn"
		uci set network.modem.iptype="$iptype"
		uci set network.modem.allowedmode="$mode"
		uci set network.modem.defaultroute='1'
		uci set network.modem.metric='10'
		uci commit network
		log "Network configuration updated. Reloading netifd..."
		/etc/init.d/network reload
		sleep 3
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
					
					provision_carrier_mbn "$CARRIER_MBN"
					provision_network "$CARRIER_APN" "$CARRIER_IPTYPE" "$CARRIER_MODE"
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
