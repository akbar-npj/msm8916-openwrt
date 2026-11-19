'use strict';
'require view';
'require fs';
'require poll';
'require ui';

/*
 * Read-only list of associated Wi-Fi clients.
 *
 * Everything comes from /usr/sbin/sta-list, which parses `iw station dump` and
 * adds the power-save state from mac80211 debugfs.  iw alone reports neither
 * the state nor, in its tiny variant, anything about power save at all.
 */

function fetch_stations() {
	return fs.exec('/usr/sbin/sta-list').then(function(res) {
		return JSON.parse((res && res.stdout) || '{"stations":[]}');
	}).catch(function() {
		return { stations: [] };
	});
}

function fmt_duration(secs) {
	var d, h, m, s;

	if (secs == null)
		return '\u2014';

	d = Math.floor(secs / 86400);
	h = Math.floor(secs % 86400 / 3600);
	m = Math.floor(secs % 3600 / 60);
	s = secs % 60;

	if (d > 0) return d + 'd ' + h + 'h';
	if (h > 0) return h + 'h ' + m + 'm';
	if (m > 0) return m + 'm ' + s + 's';
	return s + 's';
}

function fmt_dbm(v) {
	return (v == null) ? '\u2014' : v + ' dBm';
}

function fmt_rate(v) {
	return (v == null || v === '') ? '\u2014' : v;
}

function ps_label(ps) {
	switch (ps) {
	case 'off':    return _('Off');
	case 'on':     return _('On');
	case 'wmm':    return _('WMM');
	case 'legacy': return _('Legacy PS-Poll');
	default:       return _('Unknown');
	}
}

function ps_style(ps) {
	switch (ps) {
	case 'off':    return 'color:#2e7d32';
	case 'legacy': return 'color:#c62828;font-weight:bold';
	default:       return '';
	}
}

function render_row(sta) {
	return E('tr', { 'class': 'tr' }, [
		E('td', { 'class': 'td left' }, sta.mac || '\u2014'),
		E('td', { 'class': 'td left' }, sta.iface || '\u2014'),
		E('td', { 'class': 'td left' }, fmt_dbm(sta.signal)),
		E('td', { 'class': 'td left' }, fmt_dbm(sta.signal_avg)),
		E('td', { 'class': 'td left' }, fmt_rate(sta.tx_rate)),
		E('td', { 'class': 'td left' }, fmt_rate(sta.rx_rate)),
		E('td', { 'class': 'td left' }, fmt_duration(sta.connected)),
		E('td', { 'class': 'td left' }, [
			E('span', { 'style': ps_style(sta.ps) }, ps_label(sta.ps))
		])
	]);
}

return view.extend({
	load: function() {
		return fetch_stations();
	},

	render: function(data) {
		var tbody = E('tbody', {});
		var count = E('span', { 'class': 'label' }, '');

		function update(doc) {
			var stations = (doc && doc.stations) || [];

			while (tbody.firstChild)
				tbody.removeChild(tbody.firstChild);

			if (stations.length === 0) {
				tbody.appendChild(E('tr', { 'class': 'tr' }, [
					E('td', { 'class': 'td left', 'colspan': 8 },
						_('No clients are associated.'))
				]));
			}
			else {
				stations.forEach(function(sta) {
					tbody.appendChild(render_row(sta));
				});
			}

			count.textContent = stations.length + ' ' +
				(stations.length === 1 ? _('client') : _('clients'));
		}

		update(data);

		poll.add(function() {
			return fetch_stations().then(update);
		}, 5);

		return E('div', { 'class': 'cbi-map' }, [
			E('h2', {}, [ _('Connected Clients'), ' ', count ]),
			E('div', { 'class': 'cbi-map-descr' },
				_('Associated Wi-Fi clients, refreshed every 5 seconds. Read-only.')),

			E('div', { 'class': 'cbi-section' }, [
				E('table', { 'class': 'table' }, [
					E('tr', { 'class': 'tr table-titles' }, [
						E('th', { 'class': 'th' }, _('MAC address')),
						E('th', { 'class': 'th' }, _('Interface')),
						E('th', { 'class': 'th' }, _('Signal')),
						E('th', { 'class': 'th' }, _('Signal avg')),
						E('th', { 'class': 'th' }, _('TX rate')),
						E('th', { 'class': 'th' }, _('RX rate')),
						E('th', { 'class': 'th' }, _('Connected')),
						E('th', { 'class': 'th' }, _('Power save'))
					]),
					tbody
				])
			]),

			E('div', { 'class': 'cbi-section' }, [
				E('p', {}, _('A client showing Legacy PS-Poll is dozing between beacons. ' +
					'That state is not visible in retry or drop counters, which stay at zero ' +
					'even when the link is stalling.'))
			])
		]);
	},

	handleSaveApply: null,
	handleSave: null,
	handleReset: null
});
