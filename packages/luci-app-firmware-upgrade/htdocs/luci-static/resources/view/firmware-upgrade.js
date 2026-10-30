'use strict';
'require view';
'require fs';
'require ui';
'require poll';

var BIN = '/usr/sbin/firmware-upgrade';
var FAILED = '/tmp/firmware-upgrade.failed';
var APPLYLOG = '/tmp/firmware-upgrade-apply.log';

function humanSize(n) {
	n = parseInt(n, 10);
	if (!n || n < 0)
		return '—';
	var u = [ 'B', 'KB', 'MB', 'GB' ], i = 0;
	while (n >= 1024 && i < u.length - 1) { n /= 1024; i++; }
	return (i === 0 ? n : n.toFixed(1)) + ' ' + u[i];
}

function getCheck() {
	return fs.exec(BIN, [ 'check' ]).then(function(res) {
		try { return JSON.parse((res.stdout || '').trim()); }
		catch (e) { return null; }
	}).catch(function() { return null; });
}

function pill(color, text) {
	return E('span', {
		'style': 'display:inline-block;padding:2px 8px;border-radius:10px;color:#fff;font-size:90%;background:' + color
	}, text);
}

function row(label, value) {
	return E('tr', { 'class': 'tr' }, [
		E('td', { 'class': 'td left', 'width': '30%' }, label),
		E('td', { 'class': 'td left' }, value)
	]);
}

return view.extend({
	load: function() {
		return getCheck();
	},

	render: function(info) {
		var state = { info: info };
		var statusNode = E('div', {});
		var logNode = E('pre', {
			'style': 'max-height:14em;overflow:auto;background:#111;color:#ddd;padding:8px;border-radius:4px;display:none;white-space:pre-wrap;font-size:90%'
		}, '');
		var upBtn = E('button', {
			'class': 'cbi-button cbi-button-apply',
			'click': function() { startUpgrade(); }
		}, _('Upgrade'));

		function renderStatus() {
			var i = state.info;
			while (statusNode.firstChild)
				statusNode.removeChild(statusNode.firstChild);

			if (!i) {
				statusNode.appendChild(E('p', { 'class': 'cbi-value-description' },
					_('Could not read update information. Check the network connection and press "Check again".')));
				upBtn.disabled = true;
				return;
			}

			var avail = parseInt(i.available, 10) === 1;
			var blocked = parseInt(i.blocked, 10) === 1;
			var badge = blocked ? pill('#ff9800', _('manual flash required'))
				: (avail ? pill('#4caf50', _('update available')) : pill('#4caf50', _('up to date')));

			statusNode.appendChild(E('table', { 'class': 'table' }, [
				row(_('Current version'), i.current || '—'),
				row(_('Latest version'), i.latest || '—'),
				row(_('Board'), i.board || '—'),
				row(_('Status'), badge),
				row(_('Image size'), humanSize(i.size)),
				row(_('Notes'), i.notes || '—')
			]));
			upBtn.disabled = !(avail && !blocked);
		}

		function refresh() {
			return getCheck().then(function(i) {
				if (i) state.info = i;
				renderStatus();
			});
		}

		function pollApply() {
			return Promise.all([
				fs.read(FAILED).catch(function() { return null; }),
				fs.read(APPLYLOG).catch(function() { return null; })
			]).then(function(r) {
				var failed = r[0] && (r[0].trim() === 'failed');
				var log = (r[1] || '').trim();
				if (log) {
					logNode.style.display = '';
					logNode.textContent = log;
				}
				if (failed) {
					ui.addNotification(null, E('p', _('The upgrade failed. See the log below.')), 'danger');
					return false;
				}
				return true;
			});
		}

		function startUpgrade() {
			if (!state.info || parseInt(state.info.available, 10) !== 1)
				return;
			ui.showModal(_('Firmware upgrade'), [
				E('p', _('Download, verify and flash %s? The device will reboot and keep its settings.').format(state.info.latest)),
				E('div', { 'class': 'right' }, [
					E('button', { 'class': 'btn', 'click': ui.hideModal }, _('Cancel')),
					' ',
					E('button', {
						'class': 'cbi-button cbi-button-apply',
						'click': function() {
							ui.hideModal();
							ui.addNotification(null, E('p', _('Upgrade started — downloading, verifying and flashing. The device will reboot.')), 'info');
							fs.exec(BIN, [ 'apply' ]).then(function() {
								poll.add(pollApply, 3);
							});
						}
					}, _('Upgrade'))
				])
			]);
		}

		renderStatus();

		return E('div', { 'class': 'cbi-map' }, [
			E('h2', {}, _('Firmware Upgrade')),
			E('div', { 'class': 'cbi-map-descr' },
				_('Check for a newer firmware image published for this board and install it. Your settings are preserved.')),
			E('div', { 'class': 'cbi-section' }, [ statusNode ]),
			E('div', { 'class': 'cbi-page-actions' }, [
				E('button', { 'class': 'cbi-button', 'click': function() { refresh(); } }, _('Check again')),
				' ',
				upBtn
			]),
			logNode
		]);
	},

	handleSaveApply: null,
	handleSave: null,
	handleReset: null
});
