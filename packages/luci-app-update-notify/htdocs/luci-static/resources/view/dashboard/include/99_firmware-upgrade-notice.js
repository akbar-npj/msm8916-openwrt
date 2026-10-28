'use strict';
'require baseclass';
'require fs';

/*
 * Dashboard notice: "Firmware update available: current -> latest".
 *
 * The state is produced out of band by /usr/sbin/update-notify, which runs
 * firmware-upgrade check and writes /tmp/firmware-upgrade-notify.json.  As with
 * the package-update notice, the file is read through rpcd (the acl grants
 * read on that one path) so the banner needs no ubus object of its own and
 * still renders the last known answer when the daemon is stopped.
 *
 * When the msm89xx-firmware-upgrade package is not installed the daemon writes
 * {"available":false,"installed":false}, and this include renders nothing.
 *
 * The "Upgrade firmware" button runs `firmware-upgrade upgrade` via rpcd file
 * exec; the device reboots into the new image, so the request usually drops.
 */

var STATE_FILE = '/tmp/firmware-upgrade-notify.json';
var UPGRADE_CMD = '/usr/sbin/firmware-upgrade';

return baseclass.extend({
	node: null,
	busy: false,

	load() {
		return L.resolveDefault(fs.read(STATE_FILE), '');
	},

	build() {
		this.label = E('strong');
		this.when = E('span', { 'class': 'update-notice-when' });

		this.btn = E('button', {
			'class': 'btn cbi-button cbi-button-apply',
			'click': this.onUpgrade.bind(this)
		}, [ _('Upgrade firmware') ]);

		return E('div', { 'class': 'alert-message warning firmware-upgrade-notice' }, [
			E('div', {}, [
				this.label,
				' ',
				this.when
			]),
			E('div', {}, [ this.btn ])
		]);
	},

	render(raw) {
		var state = null;

		try {
			state = JSON.parse(raw || '');
		}
		catch (e) {
			state = null;
		}

		if (!state || state.installed === false) {
			/* Feature not installed, or never checked yet: leave untouched. */
			if (this.node) this.node.style.display = 'none';
			return this.node;
		}

		if (!state.available) {
			if (this.node == null)
				return null;
			this.node.style.display = 'none';
			return this.node;
		}

		if (this.node == null)
			this.node = this.build();

		var cur = state.current || '?';
		var latest = state.latest || '?';
		var msg = _('Firmware update available: %s -> %s');
		msg = msg.replace('%s', cur).replace('%s', latest);
		this.label.textContent = msg;

		this.when.textContent = state.checked
			? _('Last checked %s.').replace('%s', new Date(state.checked * 1000).toLocaleString())
			: '';

		this.node.style.display = '';
		return this.node;
	},

	onUpgrade(ev) {
		ev.preventDefault();
		if (this.busy)
			return;
		this.busy = true;
		this.btn.disabled = true;
		this.btn.textContent = _('Upgrading...');

		/* Fire and forget: sysupgrade reboots the device, so the RPC normally
		 * drops. The page will reload or reconnect afterwards. */
		return fs.exec(UPGRADE_CMD, [ 'upgrade' ]).catch(function () {});
	}
});
