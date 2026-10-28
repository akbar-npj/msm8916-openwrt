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
 * Flow on click: confirm modal -> run `firmware-upgrade download` (poll the
 * downloaded file size for a real progress bar) -> run `firmware-upgrade
 * upgrade` (sha256-verified image is flashed and the device reboots).
 */

var STATE_FILE = '/tmp/firmware-upgrade-notify.json';
var UPGRADE_CMD = '/usr/sbin/firmware-upgrade';
var DL_PATH = '/tmp/firmware-upgrade.bin';

return baseclass.extend({
	node: null,
	busy: false,
	fwSize: null,

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

		this.progress = E('progress', {
			'class': 'firmware-upgrade-progress',
			'max': '100',
			'value': '0',
			'style': 'width:100%;display:none'
		});

		return E('div', { 'class': 'alert-message warning firmware-upgrade-notice' }, [
			E('div', {}, [
				this.label,
				' ',
				this.when
			]),
			E('div', {}, [ this.btn ]),
			E('div', {}, [ this.progress ])
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

		this.fwSize = (state.size && !isNaN(state.size)) ? state.size : null;

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

		var self = this;
		var msg = E('p', {}, [
			_('This downloads the new firmware and reboots the device. ' +
			  'Your settings are kept. The image checksum is verified before flashing. Continue?')
		]);
		var cancelBtn = E('button', {
			'class': 'btn',
			'click': function () { L.hideModal(); }
		}, [ _('Cancel') ]);
		var okBtn = E('button', {
			'class': 'btn cbi-button cbi-button-apply',
			'click': function () { L.hideModal(); self.startUpgrade(); }
		}, [ _('Upgrade') ]);
		var row = E('div', { 'class': 'right' }, [ cancelBtn, okBtn ]);

		L.showModal(_('Confirm firmware upgrade'), [ msg, row ]);
	},

	startUpgrade() {
		if (this.busy)
			return;
		this.busy = true;
		this.btn.disabled = true;
		this.btn.textContent = _('Upgrading...');
		this.progress.style.display = '';
		this.setProgress(0);
		this.label.textContent = _('Downloading firmware and verifying checksum...');

		var self = this;
		this.poll = setInterval(function () { self.pollProgress(); }, 1000);

		fs.exec(UPGRADE_CMD, [ 'download' ]).then(function () {
			clearInterval(self.poll);
			self.setProgress(100);
			self.label.textContent = _('Checksum verified. Rebooting to apply...');
			/* The upgrade flashes the image and reboots, so this call normally
			 * does not return; a dropped connection here is expected. */
			fs.exec(UPGRADE_CMD, [ 'upgrade' ]).catch(function () {});
		}).catch(function () {
			clearInterval(self.poll);
			self.progress.style.display = 'none';
			self.label.textContent = _('Download failed - check network and system log.');
			self.busy = false;
			self.btn.disabled = false;
			self.btn.textContent = _('Retry upgrade');
		});
	},

	pollProgress() {
		var self = this;
		fs.stat(DL_PATH).then(function (st) {
			if (self.fwSize && st && st.size) {
				var pct = Math.min(100, Math.round(100 * st.size / self.fwSize));
				self.setProgress(pct);
			}
		}).catch(function () {});
	},

	setProgress(pct) {
		if (this.progress) {
			this.progress.value = String(pct);
			this.progress.textContent = pct + '%';
		}
	}
});
