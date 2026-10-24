'use strict';
'require view';
'require fs';
'require uci';
'require ui';
'require poll';

var STA_BIN = '/usr/sbin/wifi-sta-up';
var INITD = '/etc/init.d/wifi-sta-late';

function parseKV(out) {
	var r = {};

	(out || '').trim().split('\n').forEach(function(line) {
		var i = line.indexOf('=');

		if (i > 0)
			r[line.slice(0, i)] = line.slice(i + 1);
	});

	return r;
}

function getStatus() {
	return fs.exec(STA_BIN, [ 'status' ]).then(function(res) {
		return parseKV(res.stdout);
	}).catch(function() {
		return {};
	});
}

function pill(on, text) {
	return E('span', {
		'style': 'display:inline-block;padding:2px 8px;border-radius:10px;color:#fff;font-size:90%;background:' +
			(on ? '#4caf50' : '#b71c1c')
	}, text);
}

function row(label, value) {
	return E('tr', { 'class': 'tr' }, [
		E('td', { 'class': 'td left', 'width': '35%' }, label),
		E('td', { 'class': 'td left' }, value || '\u2014')
	]);
}

function statusTable(st) {
	var ap = st.ap_iface ? [
		row(_('Access point'), st.ap_iface + ' \u2014 ' + (st.ap_ssid || '?')),
		row(_('State'), st.ap_state ? pill(st.ap_state === 'ENABLED', st.ap_state) : '\u2014'),
		row(_('Channel'), st.ap_channel),
		row(_('Clients'), st.ap_clients)
	] : [ row(_('Access point'), _('not running')) ];

	var sta = st.sta_iface ? [
		row(_('Station interface'), st.sta_iface),
		row(_('State'), st.sta_connected == '1' ? pill(true, _('connected')) : pill(false, _('not connected'))),
		row(_('Upstream SSID'), st.sta_ssid),
		row(_('Upstream BSSID'), st.sta_bssid),
		row(_('Channel'), st.sta_channel),
		row(_('Signal'), st.sta_signal),
		row(_('Tx bitrate'), st.sta_bitrate),
		row(_('Address'), st.sta_ip)
	] : [ row(_('Station interface'), _('not present')) ];

	return E('div', {}, [
		E('table', { 'class': 'table' }, [ E('tbody', {}, ap) ]),
		E('h4', {}, _('Station')),
		E('table', { 'class': 'table' }, [ E('tbody', {}, sta) ])
	]);
}

function warnBox(st) {
	if (st.ap_channel && st.sta_channel && st.ap_channel != st.sta_channel)
		return E('div', { 'class': 'alert-message warning' }, [
			E('strong', {}, _('Channel mismatch. ')),
			_('The access point is on channel %s but the station is on %s. One radio can only use one channel \u2014 restart the service to make the AP follow the upstream.').format(st.ap_channel, st.sta_channel)
		]);

	if (st.enabled == '1' && st.sta_connected != '1')
		return E('div', { 'class': 'alert-message warning' }, [
			_('Station mode is enabled but the station is not connected. The watchdog retries every %s s; see the log below for the reason.').format(st.recover_after || '60')
		]);

	return null;
}

function wirelessConflict() {
	var found = [];

	uci.sections('wireless', 'wifi-iface', function(s) {
		if (s.mode == 'sta')
			found.push(s['.name'] || s['.name']);
	});

	if (found.length)
		return E('div', { 'class': 'alert-message error' }, [
			E('strong', {}, _('Crash risk: ')),
			_('the wireless config still has a station interface (%s). A radio restart with that configured crashes the WCNSS firmware \u2014 disable it in Network \u2192 Wireless and use this page instead.').format(found.join(', '))
		]);

	return null;
}

return view.extend({
	load: function() {
		return Promise.all([
			getStatus(),
			uci.load('wireless').catch(function() {})
		]);
	},

	render: function(data) {
		var st = data[0];
		var body = E('div', { 'id': 'wifi-sta-status' });
		var warn = E('div', { 'id': 'wifi-sta-warn' });

		function paint(s) {
			while (body.firstChild) body.removeChild(body.firstChild);
			while (warn.firstChild) warn.removeChild(warn.firstChild);
			body.appendChild(statusTable(s));

			var w = wirelessConflict();
			if (w) warn.appendChild(w);

			var m = warnBox(s);
			if (m) warn.appendChild(m);
		}

		function run(args, msg) {
			return fs.exec(INITD, args).then(function() {
				ui.addNotification(null, E('p', {}, msg));
				return getStatus().then(paint);
			}).catch(function(e) {
				ui.addNotification(null, E('p', {}, _('Command failed: %s').format(e)), 'error');
			});
		}

		function showLog() {
			return fs.exec('/sbin/logread', [ '-e', 'wifi-sta' ]).then(function(res) {
				ui.showModal(_('Station mode log'), [
					E('pre', { 'style': 'max-height:60vh;overflow:auto;white-space:pre-wrap' },
						(res.stdout || _('(empty)')).split('\n').slice(-80).join('\n')),
					E('div', { 'class': 'right' }, E('button', {
						'class': 'btn',
						'click': ui.hideModal
					}, _('Close')))
				]);
			});
		}

		paint(st);

		poll.add(function() {
			return getStatus().then(paint);
		}, 5);

		return E('div', {}, [
			E('h2', {}, _('Station Mode')),
			E('div', { 'class': 'cbi-map-descr' }, [
				_('A second, client-side Wi-Fi interface on the same radio as the access point. '),
				_('It is created by a service after the AP is up \u2014 it is deliberately not part of the wireless config, because a radio restart with a station interface configured crashes the Wi-Fi firmware.')
			]),
			warn,
			E('div', { 'class': 'cbi-section' }, [
				E('h3', {}, [
					_('Status '),
					st.enabled == '1' ? pill(true, _('enabled')) : pill(false, _('disabled'))
				]),
				body,
				E('div', { 'style': 'margin-top:1em' }, [
					E('button', {
						'class': 'cbi-button cbi-button-apply',
						'click': ui.createHandlerFn(this, function() { return run([ 'restart' ], _('Service restarted.')); })
					}, _('Restart service')),
					' ',
					E('button', {
						'class': 'cbi-button',
						'click': ui.createHandlerFn(this, function() { return run([ 'stop' ], _('Service stopped.')); })
					}, _('Stop')),
					' ',
					E('button', {
						'class': 'cbi-button',
						'click': ui.createHandlerFn(this, function() { return run([ 'start' ], _('Service started.')); })
					}, _('Start')),
					' ',
					E('button', {
						'class': 'cbi-button',
						'click': ui.createHandlerFn(this, showLog)
					}, _('Show log'))
				])
			])
		]);
	},

	handleSaveApply: null,
	handleSave: null,
	handleReset: null
});
