'use strict';
'require view';
'require form';
'require fs';
'require uci';
'require ui';

function parseScan(out) {
	var cells = [], cur = null;

	(out || '').split('\n').forEach(function(line) {
		var m;

		if (/^Cell \d+/.test(line)) {
			cur = { ssid: '', channel: '', signal: '', enc: '' };
			cells.push(cur);
			return;
		}

		if (!cur)
			return;

		if ((m = line.match(/ESSID: "(.*)"/)))
			cur.ssid = m[1];
		else if ((m = line.match(/Channel: (\d+)/)))
			cur.channel = m[1];
		else if ((m = line.match(/Signal: (-?\d+) dBm/)))
			cur.signal = m[1] + ' dBm';
		else if ((m = line.match(/Encryption: (.*)/)))
			cur.enc = m[1];
	});

	return cells.filter(function(c) { return c.ssid; });
}

return view.extend({
	load: function() {
		return Promise.all([
			uci.load('wifista'),
			uci.load('wireless'),
			fs.exec('/usr/sbin/wifi-sta-up', [ 'status' ]).then(function(r) {
				var kv = {};

				(r.stdout || '').split('\n').forEach(function(l) {
					var i = l.indexOf('=');
					if (i > 0) kv[l.slice(0, i)] = l.slice(i + 1);
				});

				return kv;
			}).catch(function() { return {}; })
		]);
	},

	render: function(data) {
		var st = data[2];
		var m, s, o;

		m = new form.Map('wifista', _('Station Mode'),
			_('A client interface on the same radio as the access point. ' +
			  'The access point is moved to the upstream\u2019s channel automatically, because one radio can only use one channel at a time.'));

		s = m.section(form.NamedSection, 'main', 'sta', _('Station'));
		s.anonymous = true;

		o = s.option(form.Flag, 'enabled', _('Enable station mode'),
			_('Off by default. The interface is created a few seconds after the access point is up.'));
		o.rmempty = false;

		o = s.option(form.Value, 'ssid', _('Upstream SSID'));
		o.placeholder = 'MyHomeWiFi';
		// Deliberately optional: with no SSID the station just stays idle, so
		// the form can be saved before the user knows what to join -- pick a
		// network from the scan below, then Save & Apply.

		o = s.option(form.Value, 'hostname', _('Hostname to announce'),
			_('Sent to the upstream network as the DHCP hostname (option 12), so the upstream router can resolve this device by name. Leave empty to use the system hostname (%s).').format(st.hostname || 'OpenWrt'));
		o.placeholder = st.hostname || _('(system hostname)');

		o = s.option(form.Value, 'key', _('Password'));
		o.password = true;
		o.placeholder = _('(leave empty for an open network)');

		o = s.option(form.Value, 'macaddr', _('Station MAC address'),
			_('Must differ from the access point\u2019s address, otherwise the interface cannot be brought up.'));
		o.placeholder = '00:11:22:33:44:77';
		o.validate = function(section_id, value) {
			if (!value)
				return true;
			if (!/^([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}$/.test(value))
				return _('Expected six colon-separated hex octets');
			return true;
		};

		o = s.option(form.Flag, 'dhcp', _('Request an address (DHCP)'),
			_('The lease is installed without a default route \u2014 the mobile link stays the default path.'));
		o.default = '1';
		o.rmempty = false;

		o = s.option(form.ListValue, 'radio', _('Radio whose channel follows the upstream'));
		uci.sections('wireless', 'wifi-device', function(dev) {
			o.value(dev['.name'], dev['.name']);
		});
		o.default = 'radio0';

		o = s.option(form.Value, 'recover_after', _('Watchdog timeout (seconds)'),
			_('How long the station may stay down before it is recycled. 0 disables the check.'));
		o.datatype = 'uinteger';
		o.placeholder = '60';

		o = s.option(form.Value, 'wait_ap_timeout', _('Wait for the AP at start (seconds)'));
		o.datatype = 'uinteger';
		o.placeholder = '60';

		var scanBox = E('div', { 'class': 'cbi-section' }, [
			E('h3', {}, _('Nearby networks')),
			E('div', { 'id': 'wifi-sta-scan', 'class': 'cbi-section-node' },
				E('em', {}, _('Press Scan to list what this radio can see.'))),
			E('button', {
				'class': 'cbi-button cbi-button-action',
				'click': ui.createHandlerFn(this, function() {
					var out = document.getElementById('wifi-sta-scan');

					// Scan the AP interface reported by wifi-sta-up status, and only
					// when it exists -- a blind 'phy0-ap0' fallback would exec iwinfo
					// against a possibly non-existent interface while the radio is
					// still coming up.  The scan itself is read-only (a netlink
					// hardware scan); it never touches the wireless config.
					var iface = st.ap_iface;

					if (!iface) {
						while (out.firstChild) out.removeChild(out.firstChild);
						out.appendChild(E('em', {}, _('The access point interface was not found \u2014 is Wi-Fi up? Try again in a moment.')));
						return;
					}

					out.textContent = _('Scanning\u2026');

					return fs.exec('/usr/bin/iwinfo', [ iface, 'scan' ]).then(function(res) {
						var cells = parseScan(res.stdout);

						while (out.firstChild) out.removeChild(out.firstChild);

						if (!cells.length) {
							out.appendChild(E('em', {}, _('No networks found.')));
							return;
						}

						var rows = cells.map(function(c) {
							return E('tr', { 'class': 'tr' }, [
								E('td', { 'class': 'td' }, c.ssid),
								E('td', { 'class': 'td' }, c.channel),
								E('td', { 'class': 'td' }, c.signal),
								E('td', { 'class': 'td' }, c.enc),
								E('td', { 'class': 'td' }, E('button', {
									'class': 'cbi-button cbi-button-action',
									'click': function() {
										var input = document.querySelector('input[id$="wifista.main.ssid"]');

										if (input) {
											input.value = c.ssid;
											input.dispatchEvent(new Event('change'));
											ui.addNotification(null, E('p', {}, _('SSID set to %s \u2014 remember to Save & Apply.').format(c.ssid)));
										}
										else {
											ui.addNotification(null, E('p', {}, _('SSID: %s').format(c.ssid)));
										}
									}
								}, _('Use')))
							]);
						});

						out.appendChild(E('table', { 'class': 'table' }, [
							E('thead', { 'class': 'tr table-titles' }, [
								E('th', { 'class': 'th' }, _('SSID')),
								E('th', { 'class': 'th' }, _('Channel')),
								E('th', { 'class': 'th' }, _('Signal')),
								E('th', { 'class': 'th' }, _('Encryption')),
								E('th', { 'class': 'th' }, '')
							]),
							E('tbody', {}, rows)
						]));
					}).catch(function(e) {
						while (out.firstChild) out.removeChild(out.firstChild);
						out.appendChild(E('em', {}, _('Scan failed: %s').format(e)));
					});
				})
			}, _('Scan'))
		]);

		return m.render().then(function(node) {
			return E('div', {}, [ node, scanBox ]);
		});
	}
});
