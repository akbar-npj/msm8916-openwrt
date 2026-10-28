'use strict';
'require baseclass';
'require fs';

/*
 * Dashboard notice for firmware updates.
 *
 * Two independent signals feed this include:
 *
 *  1. "Update available"  - produced by /usr/sbin/update-notify, which runs
 *     `firmware-upgrade check` and writes /tmp/firmware-upgrade-notify.json.
 *     Renders a warning banner with a one-click upgrade button (confirm modal
 *     -> download with progress -> sha256-verified sysupgrade + reboot).
 *
 *  2. "Upgrade complete"  - a post-reboot confirmation.  firmware-upgrade
 *     writes a marker just before sysupgrade and preserves it across the
 *     reboot; update-notify turns it into /tmp/firmware-upgrade-just-upgraded.json
 *     on the next boot.  As a fallback that also covers manual/EDL upgrades,
 *     this include compares the live version in /etc/openwrt_release against
 *     the last version it saw (remembered in localStorage) - so when the page
 *     reconnects after a reboot to a new version, it shows the success banner.
 *
 * Both banners render nothing when there is nothing to say, and both keep
 * working (showing the last known answer) when the daemon is stopped.
 */

var STATE_FILE = '/tmp/firmware-upgrade-notify.json';
var JUST_FILE  = '/tmp/firmware-upgrade-just-upgraded.json';
var RELEASE_FILE = '/etc/openwrt_release';
var UPGRADE_CMD = '/usr/sbin/firmware-upgrade';
var DL_PATH = '/tmp/firmware-upgrade.bin';

var LS_LAST = 'msm89xx.fw.last';
var LS_ACK  = 'msm89xx.fw.ack';

function lsGet(k) { try { return window.localStorage.getItem(k); } catch (e) { return null; } }
function lsSet(k, v) { try { window.localStorage.setItem(k, v); } catch (e) {} }

return baseclass.extend({
	node: null,
	busy: false,
	fwSize: null,
	doneTo: null,

	load() {
		return Promise.all([
			L.resolveDefault(fs.read(STATE_FILE), ''),
			L.resolveDefault(fs.read(JUST_FILE), ''),
			L.resolveDefault(fs.read(RELEASE_FILE), '')
		]);
	},

	build() {
		/* --- "update available" (warning) --- */
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
		this.availNode = E('div', { 'class': 'alert-message warning firmware-upgrade-notice', 'style': 'display:none' }, [
			E('div', {}, [ this.label, ' ', this.when ]),
			E('div', {}, [ this.btn ]),
			E('div', {}, [ this.progress ])
		]);

		/* --- "upgrade complete" (success) --- */
		this.doneX = E('button', {
			'class': 'btn',
			'style': 'float:right;margin-left:8px',
			'click': this.dismissDone.bind(this)
		}, [ _('Dismiss') ]);
		this.doneLabel = E('span');
		this.doneNode = E('div', { 'class': 'alert-message success firmware-upgrade-done', 'style': 'display:none' }, [
			E('div', {}, [ this.doneX, this.doneLabel ])
		]);

		return E('div', { 'class': 'firmware-upgrade-wrap' }, [ this.availNode, this.doneNode ]);
	},

	render(raw) {
		var notifyRaw, justRaw, releaseRaw;
		if (Array.isArray(raw)) {
			notifyRaw = raw[0]; justRaw = raw[1]; releaseRaw = raw[2];
		} else {
			notifyRaw = raw; justRaw = ''; releaseRaw = '';
		}

		var liveCur = null;
		try {
			var mm = /DISTRIB_RELEASE='([^']+)'/.exec(releaseRaw || '');
			if (mm) liveCur = mm[1];
		} catch (e) {}

		var just = null;
		try { just = JSON.parse(justRaw || ''); } catch (e) { just = null; }
		var justUpgraded = !!(just && just.upgraded);

		var state = null;
		try { state = JSON.parse(notifyRaw || ''); } catch (e) { state = null; }

		/* ---- "upgrade complete" banner ---- */
		var showDone = false, doneFrom = null, doneTo = null;
		if (justUpgraded) {
			showDone = true;
			doneFrom = (just.from && just.from !== 'unknown') ? just.from : null;
			doneTo = just.to || liveCur || '?';
		} else if (liveCur) {
			var last = lsGet(LS_LAST);
			if (last && last !== liveCur) {
				showDone = true;
				doneFrom = (last !== 'unknown') ? last : null;
				doneTo = liveCur;
			}
		}
		if (liveCur)
			lsSet(LS_LAST, liveCur);
		if (showDone && lsGet(LS_ACK) === doneTo)
			showDone = false;

		if (showDone) {
			this.doneTo = doneTo;
			var dmsg = (doneFrom)
				? _('Firmware upgrade complete: %s -> %s. Your settings were kept.').replace('%s', doneFrom).replace('%s', doneTo)
				: _('Firmware upgrade complete. You are now running %s.').replace('%s', doneTo);
			this.doneLabel.textContent = dmsg;
		}

		/* ---- "update available" banner ---- */
		var showAvail;
		if (justUpgraded) {
			/* Just upgraded: the device is on the latest version, so suppress
			 * any stale "available" banner until the daemon re-checks. */
			showAvail = false;
		} else if (!state || state.installed === false) {
			showAvail = false;
		} else if (!state.available) {
			showAvail = false;
		} else {
			showAvail = true;
		}

		if (!this.node && !showAvail && !showDone)
			return null;
		if (!this.node)
			this.node = this.build();

		if (showDone) {
			this.doneNode.style.display = '';
		} else {
			this.doneNode.style.display = 'none';
		}

		if (showAvail) {
			this.fwSize = (state.size && !isNaN(state.size)) ? state.size : null;
			var cur = state.current || '?';
			var latest = state.latest || '?';
			var amsg = _('Firmware update available: %s -> %s');
			amsg = amsg.replace('%s', cur).replace('%s', latest);
			this.label.textContent = amsg;
			this.when.textContent = state.checked
				? _('Last checked %s.').replace('%s', new Date(state.checked * 1000).toLocaleString())
				: '';
			this.availNode.style.display = '';
		} else {
			this.availNode.style.display = 'none';
		}

		return this.node;
	},

	dismissDone(ev) {
		if (ev) ev.preventDefault();
		if (this.doneTo)
			lsSet(LS_ACK, this.doneTo);
		if (this.doneNode)
			this.doneNode.style.display = 'none';
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
