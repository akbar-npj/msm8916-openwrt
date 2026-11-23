'use strict';
'require view';
'require fs';
'require ui';

var BIN = '/usr/sbin/lcd-dashboard-ctl';

/* Sources accepted by lcd-dashboard-ctl --set for each of the eight zones. */
var SOURCES = [
	{ id: 'none',    title: _('blank') },
	{ id: 'signal',  title: _('signal bars') },
	{ id: 'operator',title: _('operator / NO SIM') },
	{ id: 'sim',     title: _('SIM status') },
	{ id: 'wifi',    title: _('Wi-Fi mode') },
	{ id: 'traffic', title: _('data activity arrows') },
	{ id: 'battery', title: _('battery level / charging') },
	{ id: 'sms',     title: _('unread SMS count') },
	{ id: 'missed',  title: _('missed call count') },
	{ id: 'ssid',    title: _('Wi-Fi SSID') },
	{ id: 'key',     title: _('Wi-Fi password') },
	{ id: 'ip',      title: _('IP address') },
	{ id: 'custom1', title: _('custom line 1') },
	{ id: 'custom2', title: _('custom line 2') }
];

var COLOURS = [
	{ id: 'color_top_bg',    title: _('Top band background') },
	{ id: 'color_top_fg',    title: _('Top band text') },
	{ id: 'color_mid_bg',    title: _('Middle band background') },
	{ id: 'color_bottom_bg', title: _('Bottom band background') },
	{ id: 'color_bottom_fg', title: _('Bottom band text') },
	{ id: 'color_tile_on',   title: _('Active tile') },
	{ id: 'color_tile_off',  title: _('Inactive tile') },
	{ id: 'color_popup_bg',  title: _('Notification background') },
	{ id: 'color_popup_fg',  title: _('Notification text') }
];

var ZONES = [
	{ id: 'top_left',  title: _('Top band, left') },
	{ id: 'top_right', title: _('Top band, right') },
	{ id: 'tile1',     title: _('Tile 1') },
	{ id: 'tile2',     title: _('Tile 2') },
	{ id: 'tile3',     title: _('Tile 3') },
	{ id: 'tile4',     title: _('Tile 4') },
	{ id: 'bottom1',   title: _('Bottom band, line 1') },
	{ id: 'bottom2',   title: _('Bottom band, line 2') }
];

var DEFAULT_COLOUR = '#3fb34f';
var HEX = /^#[0-9a-fA-F]{6}$/;

function getShow() {
	return fs.exec(BIN, [ '--show' ]).then(function(res) {
		var out = {};

		(res.stdout || '').trim().split('\n').forEach(function(line) {
			var i = line.indexOf('\t');

			if (i > 0)
				out[line.slice(0, i)] = line.slice(i + 1);
		});

		return out;
	}).catch(function() { return {}; });
}

function getStatus() {
	return fs.exec(BIN, [ '--status' ]).then(function(res) {
		return (res.stdout || '').trim();
	}).catch(function() { return ''; });
}

function notify(msg, type) {
	ui.addNotification(null, E('p', {}, msg), type || 'info');
}

return view.extend({
	load: function() {
		return Promise.all([ getShow(), getStatus() ]);
	},

	render: function(data) {
		var cur = (data && data[0]) || {};
		var status = (data && data[1]) || '';

		var colourFields = [];
		var zoneFields = [];
		var textFields = {};

		function boolCheck(key, title, desc) {
			var checked = (cur[key] === '1' || cur[key] === 'true' || cur[key] === 'on');
			var box = E('input', { 'type': 'checkbox', 'class': 'cbi-input-checkbox' });

			box.checked = checked;
			textFields[key] = box;

			return E('div', { 'class': 'cbi-value' }, [
				E('label', { 'class': 'cbi-value-title' }, title),
				E('div', { 'class': 'cbi-value-field' }, [
					box,
					desc ? E('span', { 'class': 'cbi-value-description' }, ' ' + desc) : null
				])
			]);
		}

		function numberField(key, title, def) {
			var inp = E('input', {
				'type': 'number',
				'min': '1',
				'class': 'cbi-input-text',
				'value': cur[key] || def
			});

			textFields[key] = inp;

			return E('div', { 'class': 'cbi-value' }, [
				E('label', { 'class': 'cbi-value-title' }, title),
				E('div', { 'class': 'cbi-value-field' }, inp)
			]);
		}

		function colourField(id, title) {
			var raw = cur[id];
			var hex = HEX.test(raw || '') ? raw : DEFAULT_COLOUR;
			var swatch = E('input', { 'type': 'color', 'value': hex, 'style': 'width:3em; padding:0; border:0; background:none' });
			var input = E('input', {
				'type': 'text',
				'class': 'cbi-input-text',
				'value': hex,
				'style': 'margin-left:.5em; width:8em'
			});

			swatch.addEventListener('change', function() { input.value = swatch.value; });
			input.addEventListener('change', function() {
				if (HEX.test(input.value))
					swatch.value = input.value;
			});

			colourFields.push({ id: id, input: input });
			[ swatch, input ].forEach(function(e) { e.dataset.key = id; });

			return E('tr', { 'class': 'tr' }, [
				E('td', { 'class': 'td left' }, title),
				E('td', { 'class': 'td left' }, [ swatch, input ])
			]);
		}

		function textInput(key, title, desc) {
			var inp = E('input', {
				'type': 'text',
				'class': 'cbi-input-text',
				'value': cur[key] || '',
				'placeholder': desc || ''
			});

			textFields[key] = inp;

			return E('div', { 'class': 'cbi-value' }, [
				E('label', { 'class': 'cbi-value-title' }, title),
				E('div', { 'class': 'cbi-value-field' }, inp)
			]);
		}

		function statusPanel() {
			var meta = [];
			var rows = [];

			status.split('\n').forEach(function(line) {
				if (!line)
					return;

				if (line.indexOf(': ') > 0)
					meta.push(E('p', { 'class': 'cbi-value-description' }, line));
				else if (line.indexOf('=') > 0) {
					var i = line.indexOf('=');

					rows.push(E('tr', { 'class': 'tr' }, [
						E('td', { 'class': 'td left' }, line.slice(0, i)),
						E('td', { 'class': 'td left' }, line.slice(i + 1))
					]));
				}
			});

			return E('div', { 'class': 'cbi-section' }, [
				E('h3', {}, _('Live state')),
				E('p', { 'class': 'cbi-value-description' },
					_('Values the dashboard is showing right now. They refresh every few seconds.')),
				meta.length ? meta : E('p', {}, _('No status available; is the collector running?')),
				rows.length ? E('table', { 'class': 'table' }, rows) : E('div')
			]);
		}

		function save() {
			var args = [ '--set' ];

			Object.keys(textFields).forEach(function(key) {
				var e = textFields[key];
				var v = (e.type === 'checkbox') ? (e.checked ? '1' : '0') : e.value;

				args.push(key + '=' + v);
			});

			colourFields.forEach(function(f) { args.push(f.id + '=' + f.input.value.trim()); });
			zoneFields.forEach(function(f) { args.push(f.id + '=' + f.sel.value); });

			fs.exec(BIN, args).then(function(res) {
				if (res && res.code === 0)
					notify(_('Dashboard settings saved and redrawn.'), 'info');
				else
					notify(_('Saving failed: ') + ((res && res.stderr) || ''), 'danger');
			}).catch(function(e) {
				notify(_('Saving failed: ') + e, 'danger');
			});
		}

		function reset() {
			fs.exec(BIN, [ '--reset' ]).then(function() {
				notify(_('Dashboard reset to the Android-style defaults. Reloading…'), 'info');
				window.setTimeout(function() { window.location.reload(); }, 800);
			}).catch(function(e) {
				notify(_('Reset failed: ') + e, 'danger');
			});
		}

		function redraw() {
			fs.exec(BIN, [ '--redraw' ]).then(function() {
				notify(_('Dashboard refreshed.'), 'info');
			}).catch(function(e) {
				notify(_('Refresh failed: ') + e, 'danger');
			});
		}

		return E('div', {}, [
			E('h2', {}, _('LCD Dashboard')),
			E('div', { 'class': 'cbi-section' }, [
				E('p', { 'class': 'cbi-value-description' },
					_('Choose what the 128x128 LCD shows and in which colours. Each tile can display any source; two custom lines are free text. The panel redraws automatically every few seconds.')),
				boolCheck('enabled', _('Enable dashboard'), _('Turn the renderer on or off. Power-button LCD control still works either way.')),
				numberField('refresh', _('Refresh interval (seconds)'), '5'),
				E('h3', {}, _('Notifications')),
				boolCheck('notification.enabled', _('Show new SMS / missed call popups'), ''),
				numberField('notification.duration', _('Popup duration (seconds)'), '6')
			]),

			E('div', { 'class': 'cbi-section' }, [
				E('h3', {}, _('Zones')),
				E('p', { 'class': 'cbi-value-description' },
					_('Top band and bottom band are text; the four middle tiles are icons plus a short value. "blank" hides a zone.')),
				E('table', { 'class': 'table' }, ZONES.map(function(z) {
					var sel = E('select', { 'class': 'cbi-input-select' });

					SOURCES.forEach(function(s) {
						sel.appendChild(E('option', { 'value': s.id }, s.title));
					});

					var val = cur[z.id] || 'none';

					if (!SOURCES.some(function(s) { return s.id === val; }))
						sel.appendChild(E('option', { 'value': val },
							val + ' (' + _('unknown') + ')'));

					sel.value = val;
					zoneFields.push({ id: z.id, sel: sel });

					return E('tr', { 'class': 'tr' }, [
						E('td', { 'class': 'td left' }, z.title),
						E('td', { 'class': 'td left' }, sel)
					]);
				})),
				textInput('custom1', _('Custom line 1'), _('Shown when a zone is set to custom line 1')),
				textInput('custom2', _('Custom line 2'), _('Shown when a zone is set to custom line 2'))
			]),

			E('div', { 'class': 'cbi-section' }, [
				E('h3', {}, _('Colours')),
				E('p', { 'class': 'cbi-value-description' },
					_('Pick a swatch or type a #RRGGBB value.')),
				E('table', { 'class': 'table' }, COLOURS.map(function(c) {
					return colourField(c.id, c.title);
				}))
			]),

			statusPanel(),

			E('div', { 'class': 'cbi-page-actions' }, [
				E('button', { 'class': 'cbi-button', 'click': redraw }, _('Refresh now')),
				' ',
				E('button', { 'class': 'cbi-button', 'click': reset }, _('Restore defaults')),
				' ',
				E('button', { 'class': 'cbi-button cbi-button-apply', 'click': save }, _('Save & apply'))
			])
		]);
	},

	handleSaveApply: null,
	handleSave: null,
	handleReset: null
});
