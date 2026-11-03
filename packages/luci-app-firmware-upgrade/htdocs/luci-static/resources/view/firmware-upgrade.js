'use strict';
'require view';
'require fs';
'require ui';
'require poll';

var BIN = '/usr/sbin/firmware-upgrade';
var FAILED = '/tmp/firmware-upgrade.failed';
var APPLYLOG = '/tmp/firmware-upgrade-apply.log';
var PROGRESS = '/tmp/firmware-upgrade.progress';

function humanSize(n) {
	n = parseInt(n, 10);
	if (isNaN(n) || n < 0)
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

function getHotfix() {
	return fs.exec(BIN, [ 'hotfix', 'check' ]).then(function(res) {
		try { return JSON.parse((res.stdout || '').trim()); }
		catch (e) { return null; }
	}).catch(function() { return null; });
}

// "phase=download bytes=8123456 total=8123456" -> { phase: ..., bytes: ..., total: ... }
function getProgress() {
	return fs.read(PROGRESS).then(function(s) {
		var o = {}, parts = (s || '').trim().split(/\s+/);
		for (var i = 0; i < parts.length; i++) {
			var eq = parts[i].indexOf('=');
			if (eq > 0)
				o[parts[i].substring(0, eq)] = parts[i].substring(eq + 1);
		}
		return o.phase ? o : null;
	}).catch(function() { return null; });
}

function getLog() {
	return fs.read(APPLYLOG).then(function(s) {
		return (s || '').trim();
	}).catch(function() { return ''; });
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
		return Promise.all([ getCheck(), getHotfix(), getProgress(), getLog() ]);
	},

	render: function(data) {
		var info = data ? data[0] : null;
		var hot = data ? data[1] : null;
		var state = { info: info, hotfix: hot, pct: null, active: false };
		var hotfixNode = E('div', {});
		var statusNode = E('div', {});
		var barFill = E('div', { 'style': 'width:0%' });
		var barNode = E('div', { 'class': 'cbi-progressbar', 'title': '0%' }, [ barFill ]);
		var progressText = E('div', { 'class': 'cbi-value-description' }, '');
		var progressSection = E('div', { 'class': 'cbi-section', 'style': 'display:none' }, [
			E('h3', {}, _('Upgrade progress')),
			progressText,
			barNode
		]);
		var logNode = E('pre', {
			'style': 'display:none;max-height:14em;overflow:auto;background:#111;color:#ddd;padding:8px;border-radius:4px;white-space:pre-wrap;font-size:90%;margin-top:8px'
		}, '');
		var keepCb = E('input', { 'type': 'checkbox', 'id': 'fu-keep', 'checked': 'checked' });
		var upBtn = E('button', {
			'class': 'cbi-button cbi-button-apply',
			'click': function() { startUpgrade(); }
		}, _('Upgrade'));

		function setBar(pct, label, level) {
			if (pct != null)
				state.pct = pct;
			var shown = (state.pct == null) ? 0 : state.pct;
			barFill.style.width = shown + '%';
			progressText.textContent = label;
			barNode.setAttribute('title', label + (state.pct == null ? '' : ' (' + shown + '%)'));
			barNode.setAttribute('data-level', level || (shown >= 100 ? 'good' : 'normal'));
		}

		function showProgress(show) {
			progressSection.style.display = show ? '' : 'none';
		}

		function applyProgress(p) {
			if (!p)
				return;

			var total = parseInt(p.total, 10) || 0;
			var bytes = parseInt(p.bytes, 10) || 0;

			showProgress(true);

			switch (p.phase) {
			case 'download':
				// The download is the long phase (a ~8 MB image over Wi-Fi), so it
				// owns the first 80% of the bar; verify and flash share the rest.
				var pct = (total > 0) ? Math.min(80, Math.floor(bytes / total * 80)) : null;
				setBar(pct, _('Downloading %s of %s').format(humanSize(bytes), humanSize(total)));
				break;
			case 'verify':
				setBar(85, _('Verifying the image checksum'));
				break;
			case 'flash':
				setBar(92, _('Flashing — the device will reboot when this finishes'));
				break;
			case 'done':
				setBar(100, _('Finished'));
				break;
			case 'failed':
				setBar(null, _('The upgrade failed. See the log below.'), 'critical');
				break;
			}
		}

		function setLog(text) {
			if (!text)
				return;
			logNode.style.display = '';
			logNode.textContent = text;
			logNode.scrollTop = logNode.scrollHeight;
		}

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
			upBtn.disabled = !(avail && !blocked) || state.active;
		}

		function refresh() {
			return Promise.all([ getCheck(), getHotfix() ]).then(function(r) {
				if (r[0]) state.info = r[0];
				if (r[1]) state.hotfix = r[1];
				renderStatus();
				renderHotfix();
			});
		}

		function renderHotfix() {
			var h = state.hotfix;
			while (hotfixNode.firstChild)
				hotfixNode.removeChild(hotfixNode.firstChild);

			if (!h || !h.count) {
				hotfixNode.appendChild(E('p', { 'class': 'cbi-value-description' },
					_('No fixes pending for this build.')));
				return;
			}

			var rows = [];
			(h.items || []).forEach(function(it) {
				rows.push(row(it.title || it.package, '%s %s'.format(it.package, it.version)));
			});
			hotfixNode.appendChild(E('table', { 'class': 'table' }, rows));
			hotfixNode.appendChild(E('p', { 'class': 'cbi-value-description' },
				_('Installed from the feed of this build. The affected services are restarted; the device does not reboot.')));
			hotfixNode.appendChild(E('div', { 'class': 'cbi-page-actions' }, [
				E('button', { 'class': 'cbi-button cbi-button-apply', 'click': startHotfix }, _('Install fixes'))
			]));
		}

		function startHotfix() {
			if (!state.hotfix || !state.hotfix.count)
				return;

			ui.showModal(_('Install fixes'), [
				E('p', {}, _('Install %d fix(es) for this build?').format(state.hotfix.count)),
				E('div', { 'class': 'right' }, [
					E('button', { 'class': 'btn', 'click': ui.hideModal }, _('Cancel')),
					' ',
					E('button', {
						'class': 'cbi-button cbi-button-apply',
						'click': function() {
							ui.hideModal();
							fs.exec(BIN, [ 'hotfix', 'apply' ]).then(function() {
								ui.addNotification(null, E('p', _('Fixes installed.')), 'info');
								return refresh();
							}).catch(function() {
								ui.addNotification(null, E('p', _('Installing the fixes failed. See the system log.')), 'danger');
							});
						}
					}, _('Install'))
				])
			]);
		}

		function pollApply() {
			return Promise.all([ getProgress(), getLog(), fs.read(FAILED).catch(function() { return null; }) ])
				.then(function(r) {
					var p = r[0], log = r[1], failed = r[2] && (r[2].trim() === 'failed');

					applyProgress(p);
					setLog(log);

					// The wrapper writes /tmp/firmware-upgrade.failed when the
					// detached job dies before it can record a phase (manifest
					// unreachable, version below min_version, ...), so the marker
					// alone must still turn the bar red.
					if (failed && (!p || p.phase !== 'failed')) {
						applyProgress({ phase: 'failed' });
						showProgress(true);
					}

					if (failed || (p && p.phase === 'failed')) {
						// poll.remove() is required: LuCI's poll keeps a callback that
						// returns false, it does not drop it.
						poll.remove(pollApply);
						state.active = false;
						renderStatus();
						return;
					}

					if (p && p.phase === 'done') {
						poll.remove(pollApply);
						state.active = false;
						renderStatus();
					}
				});
		}

		function startUpgrade() {
			if (!state.info || parseInt(state.info.available, 10) !== 1 || state.active)
				return;

			var noKeep = !keepCb.checked;
			var args = noKeep ? [ 'apply', '--no-keep' ] : [ 'apply' ];
			var msg = noKeep
				? _('Download, verify and flash %s, ERASING all settings? The device will reboot to defaults.').format(state.info.latest)
				: _('Download, verify and flash %s? The device will reboot and keep its settings.').format(state.info.latest);

			var body = [ E('p', {}, msg) ];
			if (noKeep)
				body.push(E('p', { 'style': 'color:#b71c1c;font-weight:bold' },
					_('All settings will be erased, including Wi-Fi, Station Mode, network and modem configuration.')));
			body.push(E('div', { 'class': 'right' }, [
				E('button', { 'class': 'btn', 'click': ui.hideModal }, _('Cancel')),
				' ',
				E('button', {
					'class': 'cbi-button cbi-button-apply',
					'click': function() {
						ui.hideModal();

						// Show the console immediately instead of waiting for the
						// first log line: an upgrade that appears to do nothing for
						// several seconds reads as "the button did not work".
						state.active = true;
						state.pct = null;
						logNode.textContent = '';
						logNode.style.display = '';
						setBar(0, _('Starting…'));
						showProgress(true);
						upBtn.disabled = true;

						ui.addNotification(null, E('p', noKeep
							? _('Upgrade started — downloading, verifying and flashing. The device will reboot to defaults.')
							: _('Upgrade started — downloading, verifying and flashing. The device will reboot.')), 'info');

						fs.exec(BIN, args).then(function() {
							poll.add(pollApply, 2);
						}).catch(function() {
							state.active = false;
							renderStatus();
							ui.addNotification(null, E('p', _('Could not start the upgrade. See the system log.')), 'danger');
						});
					}
				}, _('Upgrade'))
			]));

			ui.showModal(_('Firmware upgrade'), body);
		}

		renderStatus();
		renderHotfix();

		// Coming back to the page mid-upgrade (or after a failed one) must show
		// the console too, not just a click in this session.
		var p0 = data ? data[2] : null;
		var l0 = data ? data[3] : '';
		if (p0) {
			applyProgress(p0);
			setLog(l0);
			if (p0.phase !== 'done' && p0.phase !== 'failed') {
				state.active = true;
				renderStatus();
				poll.add(pollApply, 2);
			}
		}

		return E('div', { 'class': 'cbi-map' }, [
			E('h2', {}, _('Firmware Upgrade')),
			E('div', { 'class': 'cbi-map-descr' },
				_('Check for a newer firmware image published for this board and install it.')),
			E('div', { 'class': 'cbi-section' }, [ statusNode ]),
			E('div', { 'class': 'cbi-section' }, [
				E('h3', {}, _('Fixes for this build')),
				hotfixNode
			]),
			E('div', { 'class': 'cbi-section' }, [
				E('div', { 'class': 'cbi-value' }, [
					E('label', { 'class': 'cbi-value-title', 'for': 'fu-keep' }, _('Keep settings')),
					E('div', { 'class': 'cbi-value-field' }, [
						keepCb,
						E('div', { 'class': 'cbi-value-description' },
							_('Uncheck to erase all settings on upgrade (sysupgrade -n). This wipes the overlay and your configuration.'))
					])
				])
			]),
			E('div', { 'class': 'cbi-page-actions' }, [
				E('button', { 'class': 'cbi-button', 'click': function() { refresh(); } }, _('Check again')),
				' ',
				upBtn
			]),
			progressSection,
			logNode
		]);
	},

	handleSaveApply: null,
	handleSave: null,
	handleReset: null
});
