'use strict';
'require baseclass';
'require uci';

/*
 * Dashboard warning for WPA3 / 802.11w on the WCN3620.
 *
 * Measured on a mifib-v12 (Docs/WiFi/WPA3_PMF_AND_AGGREGATION.md):
 *
 *   sae-mixed + ieee80211w=1  ->  6.6 Mbit/s, no A-MPDU / Block Ack
 *   psk2      + ieee80211w=0  -> 31.8 Mbit/s, A-MPDU on TID 0 and TID 3
 *
 * The firmware refuses to transmit AP Action frames to a keyed peer, so
 * mac80211's ADDBA request never reaches the air and rate adaptation stays
 * at MCS 0-1.  There is no driver fix; WPA3 is a deliberate opt-in.
 *
 * Reads only UCI wireless state (granted in luci-app-update-notify.json).
 * Renders nothing on a default WPA2 or open AP.  Dismissing remembers the
 * exact offending config, so changing back to WPA3 shows the warning again.
 */

var WPA3_MODES = [ 'sae', 'sae-mixed', 'wpa3', 'wpa3-mixed', 'wpa3-192', 'owe' ];
var LS_ACK = 'msm89xx.wpa3-notice.ack';

function lsGet(k) { try { return window.localStorage.getItem(k); } catch (e) { return null; } }
function lsSet(k, v) { try { window.localStorage.setItem(k, v); } catch (e) {} }

function offendingAP() {
	/* uci.sections() yields section *objects*, not section ids. */
	var sections = uci.sections('wireless', 'wifi-iface') || [];

	for (var i = 0; i < sections.length; i++) {
		var iface = sections[i] || {};

		if (iface.mode !== 'ap')
			continue;

		var enc = iface.encryption || 'none';
		var pmf = (iface.ieee80211w === '1' || iface.ieee80211w === '2' ||
			  iface.ieee80211w === 1 || iface.ieee80211w === 2);

		if (WPA3_MODES.indexOf(enc) >= 0 || (enc !== 'none' && pmf))
			return {
				sid: iface['.name'] || '',
				ssid: iface.ssid || '',
				enc: enc,
				pmf: pmf
			};
	}

	return null;
}

return baseclass.extend({
	node: null,
	signature: null,

	load() {
		return uci.load('wireless');
	},

	build() {
		this.label = E('span');
		this.dismiss = E('button', {
			'class': 'btn',
			'style': 'float:right;margin-left:8px',
			'click': this.onDismiss.bind(this)
		}, [ _('Dismiss') ]);

		return E('div', { 'class': 'alert-message warning wpa3-notice' }, [
			E('div', {}, [ this.dismiss, E('strong', {}, [
				_('WPA3/PMF reduces Wi-Fi speed by about 5x')
			]) ]),
			E('div', {}, [ this.label ])
		]);
	},

	render() {
		var bad = offendingAP();

		if (!bad) {
			if (this.node && this.node.parentNode)
				this.node.parentNode.removeChild(this.node);
			return null;
		}

		this.signature = bad.sid + '|' + bad.ssid + '|' + bad.enc + '|' + bad.pmf;
		if (lsGet(LS_ACK) === this.signature) {
			if (this.node && this.node.parentNode)
				this.node.parentNode.removeChild(this.node);
			return null;
		}

		if (!this.node)
			this.node = this.build();

		this.label.textContent = _(
			'The WCN3620 firmware cannot send AP Action frames with PMF enabled, ' +
			'so Block Ack never forms and downlink drops from ~32 Mbit/s to ~6 Mbit/s. ' +
			'Use WPA2-PSK (encryption psk2, ieee80211w 0) for full speed; WPA3 remains ' +
			'available if you need it. See Docs/WiFi/WPA3_PMF_AND_AGGREGATION.md.'
		);

		/* Keep it at the top of the overview, like the firmware-update notice. */
		var top = document.querySelector('#maincontent');
		if (top) {
			if (top.firstChild !== this.node)
				top.insertBefore(this.node, top.firstChild);
			this.node.style.display = '';
			return null;
		}

		return this.node;
	},

	onDismiss(ev) {
		if (ev) ev.preventDefault();
		if (this.signature)
			lsSet(LS_ACK, this.signature);
		if (this.node && this.node.parentNode)
			this.node.parentNode.removeChild(this.node);
	}
});
