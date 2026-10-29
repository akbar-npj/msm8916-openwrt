'use strict';
'require view';
'require fs';
'require form';
'require ui';
'require poll';

var STATUS_FILE = '/tmp/uplink.json';

function getStatus() {
	return fs.read(STATUS_FILE).then(function (t) {
		try { return JSON.parse(t); } catch (e) { return null; }
	}).catch(function () { return null; });
}

function pill(on, text) {
	return E('span', {
		'style': 'display:inline-block;padding:2px 8px;border-radius:10px;color:#fff;font-size:90%;background:' +
			(on ? '#4caf50' : '#b71c1c')
	}, text);
}

function linkRow(name, l) {
	var present = l && l.present == 1;
	var healthy = l && l.healthy == 1;
	var cells = [
		E('td', { 'class': 'td left', 'width': '22%' }, name),
		E('td', {}, present ? pill(healthy, healthy ? _('healthy') : _('present, probing')) : pill(false, _('down')))
	];
	if (l) {
		cells.push(E('td', {}, l.device || '—'));
		cells.push(E('td', {}, l.gateway || '—'));
		cells.push(E('td', {}, l.dns || '—'));
	}
	return E('tr', {}, cells);
}

function statusTable(j) {
	var rows = [
		linkRow(_('Modem (LTE)'), j ? j.modem : null),
		linkRow(_('Wi-Fi station'), j ? j.sta : null)
	];
	var a = j ? (j.active || 'none') : '?';
	var c = a === 'sta' ? '#4caf50' : (a === 'modem' ? '#2196f3' : '#b71c1c');
	return E('div', { 'class': 'cbi-section' }, [
		E('h3', {}, [_('Active uplink: '), E('span', { 'style': 'color:' + c }, a)]),
		E('table', { 'class': 'table' }, [
			E('thead', {}, E('tr', {}, [
				E('th', {}, _('Link')), E('th', {}, _('State')),
				E('th', {}, _('Device')), E('th', {}, _('Gateway')), E('th', {}, _('DNS'))
			])),
			E('tbody', {}, rows)
		])
	]);
}

return view.extend({
	load: function () {
		return getStatus();
	},

	render: function (data) {
		var m = new form.Map('uplink', _('Uplink policy'),
			_('Choose which path to the internet is preferred and how a failed link is detected. ') +
			_('With policy "Auto" the modem wins while it is healthy and the Wi-Fi station takes over ') +
			_('when the modem has no route or fails its health probe. The station DNS defaults to 8.8.8.8 8.8.4.4.'));

		var s = m.section(form.NamedSection, 'general', 'uplink');
		s.anonymous = false;
		s.addremove = false;

		var policy = s.option(form.ListValue, 'policy', _('Policy'));
		policy.value('auto', _('Auto (modem first, station fallback)'));
		policy.value('modem', _('Modem only'));
		policy.value('sta', _('Station preferred'));
		policy.default = 'auto';

		var sd = s.option(form.Value, 'sta_dns', _('Station DNS'),
			_('DNS servers advertised on the station uplink (space separated). Leave empty to use the DHCP-provided DNS.'));
		sd.datatype = 'string';
		sd.placeholder = '8.8.8.8 8.8.4.4';

		var pt = s.option(form.Value, 'probe_target', _('Probe target'));
		pt.datatype = 'host'; pt.default = '1.1.1.1';
		var pi = s.option(form.Value, 'probe_interval', _('Probe interval (s)'));
		pi.datatype = 'uinteger'; pi.default = '30';
		var pto = s.option(form.Value, 'probe_timeout', _('Probe timeout (s)'));
		pto.datatype = 'uinteger'; pto.default = '3';
		var ft = s.option(form.Value, 'fail_threshold', _('Failures to mark down'));
		ft.datatype = 'uinteger'; ft.default = '3';
		var rt = s.option(form.Value, 'recover_threshold', _('Successes to mark up'));
		rt.datatype = 'uinteger'; rt.default = '2';

		var status = E('div', { 'id': 'uplink-status' });
		function paint(j) {
			while (status.firstChild) status.removeChild(status.firstChild);
			status.appendChild(statusTable(j));
		}
		paint(data);
		poll.add(function () { return getStatus().then(paint); }, 5);

		return m.render().then(function (node) {
			return E('div', {}, [
				E('h2', {}, _('Uplink')),
				E('div', { 'class': 'cbi-map-descr' }, [
					_('The dual-uplink manager selects the default route between the LTE modem and the ') +
					_('Wi-Fi station, with health probes and a user policy.')
				]),
				status,
				node
			]);
		});
	},

	/* Saving is handled by the default view implementation; the manager
	 * re-reads /etc/config/uplink when procd signals a config change. */
});
