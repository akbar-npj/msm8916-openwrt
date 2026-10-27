'use strict';
'require baseclass';
'require fs';

/*
 * Dashboard notice: "N package updates available".
 *
 * The count is produced out of band by /usr/sbin/update-notify, which refreshes
 * the apk index and writes /tmp/update-notify.json.  Doing the check here would
 * mean an `apk update` on every dashboard poll (the dashboard re-runs load()
 * every 5 s), and it would block the page on a slow or dead uplink.
 *
 * The file is read through rpcd (luci-app-update-notify.json grants read on
 * that one path), so no ubus object of our own is needed and the notice still
 * renders the last known answer when the daemon is stopped.
 *
 * Nothing is rendered while the device is up to date, so the dashboard looks
 * exactly as before until there is something to install.
 */

var STATE_FILE = '/tmp/update-notify.json';
var MANAGER_URL = 'admin/system/package-manager';

return baseclass.extend({
	node: null,

	load() {
		return L.resolveDefault(fs.read(STATE_FILE), '');
	},

	build() {
		this.label = E('strong');
		this.when = E('span', { 'class': 'update-notice-when' });

		return E('div', { 'class': 'alert-message warning update-notice' }, [
			E('div', {}, [
				this.label,
				' ',
				this.when
			]),
			E('a', {
				'class': 'btn cbi-button cbi-button-apply',
				'href': L.url(MANAGER_URL)
			}, [ _('Review and install') ])
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

		var count = (state && typeof(state.count) == 'number') ? state.count : 0;

		if (count < 1) {
			/* Never rendered yet: leave the dashboard untouched. */
			if (this.node == null)
				return null;

			/* Rendered before but now current: hide instead of removing, so the
			 * node keeps its place when the count comes back. */
			this.node.style.display = 'none';
			return this.node;
		}

		if (this.node == null)
			this.node = this.build();

		/* Plain string replace, not String.format(): the latter is defined in
		 * cbi.js, which the dashboard never loads. */
		this.label.textContent = (count == 1)
			? _('1 package update is available.')
			: _('%d package updates are available.').replace('%d', count);

		this.when.textContent = state.checked
			? _('Last checked %s.').replace('%s', new Date(state.checked * 1000).toLocaleString())
			: '';

		this.node.style.display = '';

		return this.node;
	}
});
