'use strict';

/*
 * Drives 98_wpa3-notice.js outside LuCI, the same way tests/update-notice
 * drives the update banner.  No device, no network.
 *
 *   node tests/wpa3-notice/check.js
 */

const fs = require('fs');
const path = require('path');

const SRC = process.env.WPA3_NOTICE_JS ||
	path.join(__dirname, '..', '..', 'packages', 'luci-app-update-notify',
		'htdocs', 'luci-static', 'resources', 'view', 'status', 'include',
		'98_wpa3-notice.js');

let pass = 0, fail = 0;
function check(cond, msg) {
	if (cond) { pass++; console.log('  ok   ' + msg); }
	else { fail++; console.log('  FAIL ' + msg); }
}

function makeInstance(interfaces) {
	const source = fs.readFileSync(SRC, 'utf8');
	const store = {};

	const uci = {
		load: function() { return Promise.resolve({}); },
		sections: function(conf, type) {
			if (conf !== 'wireless' || type !== 'wifi-iface')
				return [];
			return Object.keys(interfaces).map(function(k) {
				return Object.assign({ '.name': k }, interfaces[k]);
			});
		},
		get: function(conf, sid) { return interfaces[sid] || {}; }
	};

	const baseclass = { extend: function(proto) { return proto; } };
	const E = function(tag, attrs, kids) {
		return { tag: tag, attrs: attrs || {}, kids: kids || [], style: {} };
	};

	const factory = new Function(
		'baseclass', 'uci', 'window', 'document', 'E', '_', source
	);
	const proto = factory(baseclass, uci,
		{ localStorage: {
			getItem: function(k) { return Object.prototype.hasOwnProperty.call(store, k) ? store[k] : null; },
			setItem: function(k, v) { store[k] = String(v); }
		} },
		{ querySelector: function() { return null; } },
		E, function(s) { return s; });

	const inst = Object.create(proto);
	inst.node = null;
	return { inst: inst, store: store };
}

function warns(interfaces) {
	const env = makeInstance(interfaces);
	const node = env.inst.render();
	return { shown: node !== null, env: env };
}

console.log('== wpa3-notice ==');

check(warns({ ap: { mode: 'ap', encryption: 'none', ieee80211w: '0' } }).shown === false,
	'factory open AP renders nothing');
check(warns({ ap: { mode: 'ap', encryption: 'psk2', key: 'secret', ieee80211w: '0' } }).shown === false,
	'WPA2 with PMF off renders nothing');
check(warns({ ap: { mode: 'ap', encryption: 'sae-mixed', key: 'secret', ieee80211w: '1' } }).shown === true,
	'sae-mixed with PMF warns');
check(warns({ ap: { mode: 'ap', encryption: 'psk2', key: 'secret', ieee80211w: '1' } }).shown === true,
	'WPA2 with optional PMF warns');
check(warns({ sta: { mode: 'sta', encryption: 'sae-mixed', ieee80211w: '1' } }).shown === false,
	'station mode renders nothing');
check(warns({
	ap: { mode: 'ap', encryption: 'psk2', key: 'secret', ieee80211w: '0' },
	sta: { mode: 'sta', encryption: 'sae-mixed', ieee80211w: '1' }
}).shown === false, 'a WPA3 station next to a WPA2 AP renders nothing');

/* Dismissing must hide the banner on the next 5 s dashboard poll, and a
 * different config must speak up again. */
(function() {
	const ifaces = { ap: { mode: 'ap', ssid: 'Home', encryption: 'sae-mixed', ieee80211w: '1' } };
	const env = makeInstance(ifaces);
	const inst = env.inst;

	check(inst.render() !== null, 'the WPA3 banner is built');
	inst.onDismiss({ preventDefault: function() {} });
	check(inst.render() === null, 'dismiss keeps the banner hidden on the next poll');

	ifaces.ap.ssid = 'Guest';
	check(inst.render() !== null, 'a different SSID with WPA3 warns again');
})();

console.log('');
console.log(pass + ' passed, ' + fail + ' failed');
process.exit(fail === 0 ? 0 : 1);
