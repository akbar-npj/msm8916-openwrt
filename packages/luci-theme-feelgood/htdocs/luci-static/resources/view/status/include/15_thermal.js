'use strict';
'require baseclass';
'require fs';

return baseclass.extend({
	title: _('Thermal Status'),

	formatSensorName(rawType, zoneName) {
		if (!rawType) return zoneName ? zoneName.replace('thermal_', 'Zone ') : _('Sensor');
		const t = rawType.trim();
		const map = {
			'cpu0-1-thermal': _('CPU Cores 0-1'),
			'cpu2-3-thermal': _('CPU Cores 2-3'),
			'cpu-thermal': _('CPU / SoC'),
			'cpu0_1_thermal': _('CPU Cores 0-1'),
			'cpu2_3_thermal': _('CPU Cores 2-3'),
			'modem-thermal': _('Cellular Modem (LTE)'),
			'modem_thermal': _('Cellular Modem (LTE)'),
			'gpu-thermal': _('GPU (Graphics)'),
			'gpu_thermal': _('GPU (Graphics)'),
			'pm8916-thermal': _('PMIC (Power Management)'),
			'pm8916_thermal': _('PMIC (Power Management)'),
			'camera-thermal': _('Camera Sensor'),
			'camera_thermal': _('Camera Sensor'),
			'board-thermal': _('System Board'),
			'wifi-thermal': _('Wireless (Wi-Fi)')
		};
		if (map[t.toLowerCase()]) return map[t.toLowerCase()];
		return t.replace(/[-_]thermal/gi, '').replace(/[-_]/g, ' ').replace(/\b\w/g, l => l.toUpperCase());
	},

	load() {
		return L.resolveDefault(fs.list('/sys/class/thermal'), []).then((entries) => {
			const zones = entries
				.filter(e => /^thermal_zone\d+$/.test(e.name))
				.sort((a, b) => {
					const numA = parseInt(a.name.replace('thermal_zone', ''), 10);
					const numB = parseInt(b.name.replace('thermal_zone', ''), 10);
					return numA - numB;
				});

			if (zones.length === 0) {
				// Fallback to zones 0-5 if fs.list was restricted
				return Promise.all([0, 1, 2, 3, 4, 5].map(i => {
					const z = 'thermal_zone' + i;
					return Promise.all([
						L.resolveDefault(fs.read('/sys/class/thermal/' + z + '/type'), null),
						L.resolveDefault(fs.read('/sys/class/thermal/' + z + '/temp'), null),
						z
					]);
				}));
			}

			return Promise.all(zones.map(z => {
				return Promise.all([
					L.resolveDefault(fs.read('/sys/class/thermal/' + z.name + '/type'), null),
					L.resolveDefault(fs.read('/sys/class/thermal/' + z.name + '/temp'), null),
					z.name
				]);
			}));
		});
	},

	render(data) {
		if (!Array.isArray(data)) return null;

		const sensors = [];

		for (let i = 0; i < data.length; i++) {
			const typeRaw = data[i][0];
			const tempRaw = data[i][1];
			const zoneName = data[i][2];

			if (!tempRaw) continue;
			let t = parseFloat(tempRaw.trim());
			if (isNaN(t)) continue;
			if (t > 1000) t = t / 1000;

			// Filter out invalid or inactive sensors
			if (t <= 0 || t > 150) continue;

			sensors.push({
				name: this.formatSensorName(typeRaw, zoneName),
				temp: t
			});
		}

		if (sensors.length === 0)
			return null;

		const table = E('table', { 'class': 'table' });

		sensors.forEach(s => {
			let tier = 'normal';
			if (s.temp < 50) tier = 'cool';
			else if (s.temp < 70) tier = 'normal';
			else if (s.temp < 85) tier = 'hot';
			else tier = 'critical';

			table.appendChild(E('tr', { 'class': 'tr' }, [
				E('td', { 'class': 'td left', 'width': '33%' }, [ s.name ]),
				E('td', { 'class': 'td left' }, [
					E('span', {
						'class': 'badge-colored badge-thermal',
						'data-temp': tier
					}, [
						E('span', { 'class': 'thermal-icon' }, ['🌡️ ']),
						'%.1f °C'.format(s.temp)
					])
				])
			]));
		});

		return table;
	}
});
