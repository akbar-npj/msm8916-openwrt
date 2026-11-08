'use strict';
'require view';
'require fs';
'require ui';

var BIN = '/usr/sbin/led-probe';

var ROLES = [
	{ id: 'wifi',       title: _('Wi-Fi status lamp') },
	{ id: 'wifi_fault', title: _('Wi-Fi fault (red half)') },
	{ id: 'wan',        title: _('LTE / WAN signal lamp') },
	{ id: 'wan_fault',  title: _('Modem fault (red)') },
	{ id: 'data',       title: _('Data activity (blue)') },
	{ id: 'bat_green',  title: _('Battery charge (green)') },
	{ id: 'bat_red',    title: _('Battery fault (red)') }
];

var STATES = {
	wifi:       [ 'down', 'ap_ready', 'client', 'ap_sta' ],
	wifi_fault: [ 'ok', 'fault', 'radio_off' ],
	wan:        [ 'down', 'search', 'idle', 'active', 'nosim' ],
	wan_fault:  [ 'ok', 'down', 'search' ],
	data:       [ 'beacon', 'idle', 'active', 'off' ],
	bat_green:  [ 'charging', 'complete', 'off' ],
	bat_red:    [ 'fault', 'off' ]
};

function getList() {
	return fs.exec(BIN, [ '--list' ]).then(function(res) {
		return (res.stdout || '').trim().split('\n').filter(function(l) { return l.length; });
	}).catch(function() { return []; });
}

function getShow() {
	return fs.exec(BIN, [ '--show' ]).then(function(res) {
		var out = { roles: {}, patterns: {} };

		(res.stdout || '').trim().split('\n').forEach(function(line) {
			var p = line.split('\t');

			if (p[0] === 'role' && p[1])
				out.roles[p[1]] = { val: p[2] || 'auto', eff: p[3] || '' };
			else if (p[0] === 'pattern' && p[1]) {
				out.patterns[p[1]] = out.patterns[p[1]] || {};
				out.patterns[p[1]][p[2]] = { val: p[3] || '', src: p[4] || 'default' };
			}
		});

		return out;
	}).catch(function() { return { roles: {}, patterns: {} }; });
}

return view.extend({
	load: function() {
		return Promise.all([ getList(), getShow() ]);
	},

	render: function(data) {
		var lamps = (data && data[0]) || [];
		var cur = (data && data[1]) || { roles: {}, patterns: {} };
		var roleSelects = {};
		var patFields = [];

		function lampSelect(role) {
			var c = cur.roles[role] || { val: 'auto', eff: '' };
			var sel = E('select', { 'class': 'cbi-input-select' });
			var autoLabel = c.eff
				? _('auto (board default: %s)').format(c.eff)
				: _('auto (board default)');

			sel.appendChild(E('option', { 'value': 'auto' }, autoLabel));
			lamps.forEach(function(l) {
				sel.appendChild(E('option', { 'value': l }, l));
			});

			// A configured label that is not present on this unit would
			// otherwise fall off the list and be silently reset on save.
			if (c.val !== 'auto' && lamps.indexOf(c.val) === -1)
				sel.appendChild(E('option', { 'value': c.val },
					_('%s (not present)').format(c.val)));

			sel.value = c.val;
			sel.dataset.role = role;
			roleSelects[role] = sel;

			return sel;
		}

		function blinkButton(role) {
			return E('button', {
				'class': 'cbi-button',
				'click': function(ev) {
					ev.preventDefault();
					var sel = roleSelects[role];
					var v = sel ? sel.value : 'auto';

					if (v === 'auto') {
						ui.addNotification(null,
							E('p', {}, _('Pick a lamp for this role first.')), 'warning');
						return;
					}

					fs.exec(BIN, [ '--blink', v, '3' ]).catch(function() {});
				}
			}, _('Blink'));
		}

		var roleRows = ROLES.map(function(r) {
			return E('tr', { 'class': 'tr' }, [
				E('td', { 'class': 'td left' }, r.title),
				E('td', { 'class': 'td left' }, lampSelect(r.id)),
				E('td', { 'class': 'td left' }, blinkButton(r.id))
			]);
		});

		var patRows = [];

		var PRESETS = [
			{ v: 'off',             t: _('off') },
			{ v: 'on',              t: _('on (steady)') },
			{ v: 'blink:100:100',   t: _('blink fast (100 / 100 ms)') },
			{ v: 'blink:150:150',   t: _('blink rapid (150 / 150 ms)') },
			{ v: 'blink:250:250',   t: _('blink (250 / 250 ms)') },
			{ v: 'blink:500:500',   t: _('blink slow (500 / 500 ms)') },
			{ v: 'blink:1000:1000', t: _('blink very slow (1 / 1 s)') },
			{ v: 'blink:500:9500',  t: _('beacon (0.5 s every 10 s)') }
		];

		function isPreset(v) {
			return PRESETS.some(function(p) { return p.v === v; });
		}

		function patternField(role, state) {
			// 'eff' is the pattern in use, 'src' says whether it comes
			// from /etc/config/leds or from the built-in table.  Only a
			// 'set' value is an override worth writing back.
			var c = (cur.patterns[role] && cur.patterns[role][state]) || { eff: '', src: 'default' };
			var override = (c.src === 'set') ? c.eff : '';
			var sel = E('select', { 'class': 'cbi-input-select' });
			var inp = E('input', {
				'type': 'text',
				'class': 'cbi-input-text',
				'placeholder': 'blink:300:700',
				'value': override || c.eff || '',
				'style': 'display:none; margin-left:.5em'
			});

			sel.appendChild(E('option', { 'value': '' },
				c.eff ? _('default (%s)').format(c.eff) : _('default')));

			PRESETS.forEach(function(p) {
				sel.appendChild(E('option', { 'value': p.v }, p.t));
			});

			// A timing that is not one of the presets must not be
			// silently dropped when the page is saved.
			if (override && !isPreset(override))
				sel.appendChild(E('option', { 'value': override },
					_('%s (custom)').format(override)));

			sel.appendChild(E('option', { 'value': '__custom__' }, _('custom…')));
			sel.value = override;

			sel.addEventListener('change', function() {
				inp.style.display = (sel.value === '__custom__') ? '' : 'none';
			});

			patFields.push({ role: role, state: state, sel: sel, inp: inp, orig: override });

			return E('td', { 'class': 'td left' }, [ sel, inp ]);
		}

		ROLES.forEach(function(r) {
			(STATES[r.id] || []).forEach(function(s) {
				patRows.push(E('tr', { 'class': 'tr' }, [
					E('td', { 'class': 'td left' }, r.id),
					E('td', { 'class': 'td left' }, s),
					patternField(r.id, s)
				]));
			});
		});

		function save() {
			var args = [ '--set' ];

			ROLES.forEach(function(r) {
				var sel = roleSelects[r.id];

				if (sel)
					args.push(r.id + '=' + sel.value);
			});

			var bad = null;

			patFields.forEach(function(f) {
				var v = (f.sel.value === '__custom__') ? f.inp.value.trim() : f.sel.value;

				if (v === f.orig)
					return;

				if (v !== '' && !/^(off|on|blink:[0-9]+:[0-9]+)$/.test(v)) {
					bad = bad || (f.role + '.' + f.state);
					return;
				}

				// An empty value drops the override and restores the
				// built-in pattern.
				args.push(f.role + '.' + f.state + '=' + v);
			});

			if (bad) {
				ui.addNotification(null,
					E('p', {}, _('Pattern for %s must be off, on or blink:ON_MS:OFF_MS.').format(bad)),
					'danger');
				return;
			}

			fs.exec(BIN, args).then(function(res) {
				if (res && res.code === 0)
					ui.addNotification(null,
						E('p', {}, _('LED mapping saved and the monitors restarted.')), 'info');
				else
					ui.addNotification(null,
						E('p', {}, _('Saving failed: ') + ((res && res.stderr) || '')), 'danger');
			}).catch(function(e) {
				ui.addNotification(null, E('p', {}, _('Saving failed: ') + e), 'danger');
			});
		}

		function reset() {
			fs.exec(BIN, [ '--reset' ]).then(function() {
				ui.addNotification(null,
					E('p', {}, _('Every role lamp reset to auto.')), 'info');
			}).catch(function() {});
		}

		return E('div', {}, [
			E('h2', {}, _('LED Setup')),
			E('div', { 'class': 'cbi-section' }, [
				E('p', { 'class': 'cbi-value-description' },
					_('This is the configuration in effect now. If a unit has its Wi-Fi and LTE lamps swapped, point the roles at the correct labels; "auto" follows the board default, shown next to it. "Blink" lights the selected lamp for three seconds so you can see which one it is. Leave everything as it is and nothing changes.')),

				E('table', { 'class': 'table' }, [
					E('tr', { 'class': 'tr table-titles' }, [
						E('th', { 'class': 'th' }, _('Role')),
						E('th', { 'class': 'th' }, _('Lamp')),
						E('th', { 'class': 'th' }, '')
					])
				].concat(roleRows)),

				E('div', { 'class': 'cbi-page-actions' }, [
					E('button', { 'class': 'cbi-button', 'click': reset }, _('Reset lamps to auto')),
					' ',
					E('button', { 'class': 'cbi-button cbi-button-apply', 'click': save }, _('Save & apply'))
				])
			]),

			E('div', { 'class': 'cbi-section' }, [
				E('h3', {}, _('Patterns')),
				E('p', { 'class': 'cbi-value-description' },
					_('Pick a pattern for each scenario. "default" keeps the built-in timing shown next to it; pick a preset to pin it, or "custom…" to type your own blink:ON_MS:OFF_MS.')),
				E('table', { 'class': 'table' }, [
					E('tr', { 'class': 'tr table-titles' }, [
						E('th', { 'class': 'th' }, _('Role')),
						E('th', { 'class': 'th' }, _('Scenario')),
						E('th', { 'class': 'th' }, _('Pattern'))
					])
				].concat(patRows))
			])
		]);
	},

	handleSaveApply: null,
	handleSave: null,
	handleReset: null
});
