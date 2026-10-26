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
		var m, s, s2, o;

		// uci is only reliably populated by the time the view renders: the
		// view's load() resolves before the fetched state is applied, so a
		// uci.sections() call in load() sees nothing and would add a row on
		// every visit.  Do the migration and the default row here instead.
		var aps = uci.sections('wifista', 'ap');
		var main = uci.get('wifista', 'main');

		// One-time migration of the legacy single wifista.main.ssid/key pair
		// into a saved-network profile (committed on Save).  uci.add() returns
		// the new section ID (a string) -- set options through uci.set().
		if (!aps.some(function(sec) { return sec.ssid; }) && main && main.ssid) {
			var sid = uci.add('wifista', 'ap');
			uci.set('wifista', sid, 'ssid', main.ssid);
			if (main.key)
				uci.set('wifista', sid, 'key', main.key);
		}

		// Always show one row, so the SSID and password boxes are there
		// without having to press Add network first.  An empty row is
		// harmless: wifi-sta-up skips profiles with an empty ssid, and the
		// section is only committed if the user saves the form.
		if (!uci.sections('wifista', 'ap').length)
			uci.add('wifista', 'ap');

		m = new form.Map('wifista', _('Station Mode'),
			_('A client interface on the same radio as the access point. ' +
			  'The access point is moved to the upstream\u2019s channel automatically, because one radio can only use one channel at a time.'));

		s = m.section(form.NamedSection, 'main', 'sta', _('Station'));
		s.anonymous = true;

		o = s.option(form.Flag, 'enabled', _('Enable station mode'),
			_('Off by default. The interface is created a few seconds after the access point is up.'));
		o.rmempty = false;

		o = s.option(form.Value, 'hostname', _('Hostname to announce'),
			_('Sent to the upstream network as the DHCP hostname (option 12), so the upstream router can resolve this device by name. Leave empty to use the system hostname (%s).').format(st.hostname || 'OpenWrt'));
		o.placeholder = st.hostname || _('(system hostname)');

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

		s2 = m.section(form.TypedSection, 'ap', _('Saved networks'),
			_('Every enabled row is handed to wpa_supplicant; it joins whichever is in range and moves on to the next one when a password is rejected. A rejected password is reported on the Status page.'));
		// form.TypedSection spells these addremove / addbtntitle.  The CBI
		// property names (add / remove / addbtitle) are silently ignored, which
		// left the section with no Add button at all: a fresh config has zero
		// wifista.ap rows, so there was no SSID field to fill and the scan's
		// Use button could only report "add a network row first".
		s2.addremove = true;
		s2.anonymous = true;
		s2.addbtntitle = _('Add network');

		o = s2.option(form.Value, 'ssid', _('SSID'));
		o.rmempty = false;
		o.placeholder = 'MyHomeWiFi';

		o = s2.option(form.Value, 'key', _('Password'));
		o.password = true;
		o.placeholder = _('(leave empty for an open network)');
		// Catch a mistyped PSK before it is applied: wpa_supplicant only
		// reports it later as "wrong password" and the row silently stays
		// unused.  Empty means an open network.
		o.validate = function(section_id, value) {
			if (!value)
				return true;
			if (value.length == 64 && /^[0-9a-fA-F]{64}$/.test(value))
				return true;
			if (value.length >= 8 && value.length <= 63)
				return true;
			return _('A WPA password is 8 to 63 characters (or 64 hex digits).');
		};

		o = s2.option(form.Flag, 'disabled', _('Disabled'),
			_('Keep the row but never try it.'));
		o.default = '0';
		o.editable = true;

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
										// The saved-network rows are <input id="widget.cbid.wifista.<sid>.ssid">.
										var inputs = document.querySelectorAll('input[id$=".ssid"]');
										var input = null, same = null;

										for (var i = 0; i < inputs.length; i++) {
											if (!inputs[i].value) {
												input = inputs[i];
												break;
											}
											if (inputs[i].value == c.ssid)
												same = inputs[i];
										}

										function keyOf(el) {
											return el ? document.getElementById(el.id.replace(/\.ssid$/, '.key')) : null;
										}

										// Already saved: just put the cursor in its password.
										if (!input && same) {
											input = same;
											input.scrollIntoView({ block: 'center' });
											var k0 = keyOf(input);

											if (k0)
												k0.focus();

											ui.addNotification(null, E('p', {}, _('%s is already saved. Change its password above if needed, then Save & Apply.').format(c.ssid)));
											return;
										}

										if (input) {
											var open = /^none$/i.test((c.enc || '').trim());
											var keyInput = keyOf(input);

											input.value = c.ssid;
											input.dispatchEvent(new Event('change'));
											input.scrollIntoView({ block: 'center' });

											if (open) {
												ui.addNotification(null, E('p', {}, _('%s is an open network \u2014 no password needed. Press Save & Apply.').format(c.ssid)));
											}
											else if (keyInput) {
												keyInput.focus();
												ui.addNotification(null, E('p', {}, _('SSID set to %s \u2014 type its password above, then Save & Apply.').format(c.ssid)));
											}

											return;
										}

										// Every row already has a different SSID: add one,
										// exactly like the Add network button, then fill it.
										// handleAdd() saves the form and re-renders, so the new
										// row exists in the DOM when the promise resolves.
										return Promise.resolve(s2.handleAdd(null)).then(function() {
											var all = document.querySelectorAll('input[id$=".ssid"]');
											var last = all[all.length - 1];

											if (!last)
												return;

											last.value = c.ssid;
											last.dispatchEvent(new Event('change'));
											last.scrollIntoView({ block: 'center' });

											var k1 = keyOf(last);

											if (k1)
												k1.focus();

											ui.addNotification(null, E('p', {}, _('Added %s \u2014 type its password above, then Save & Apply.').format(c.ssid)));
										}).catch(function(e) {
											ui.addNotification(null, E('p', {}, _('Could not add %s: %s').format(c.ssid, e)));
										});
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

		// One line that answers "did it connect?" without leaving the tab.
		var statusText = null;
		var statusClass = 'alert-message';

		if (st.wrong_key) {
			statusText = _('Wrong password for %s \u2014 correct it above and save again.').format(st.wrong_key);
			statusClass += ' warning';
		}
		else if (st.enabled != '1') {
			statusText = _('Station mode is disabled.');
		}
		else if (st.sta_connected == '1') {
			statusText = _('Connected to %s').format(st.sta_ssid || '?');
			if (st.sta_ip)
				statusText += ' \u2014 ' + st.sta_ip;
			if (st.sta_signal)
				statusText += ' (' + st.sta_signal + ')';
		}
		else {
			statusText = _('Not connected. A saved network is tried in order; a rejected password is reported here.');
		}

		return m.render().then(function(node) {
			var kids = [ E('div', { 'class': statusClass }, statusText ), node, scanBox ];

			return E('div', {}, kids);
		});
	}
});
