'use strict';
'require baseclass';
'require fs';
'require uci';
'require ui';

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
 *
 * Dismissing stores the count that was on screen in update-notify.settings.
 * dismissed_count; the banner stays away until a different number of updates
 * is pending.  That is the useful half of "do not show again" on a metered
 * SIM: silence for this set of packages, but a new batch still speaks up.
 */

var STATE_FILE = '/tmp/update-notify.json';
var CONFIG = 'update-notify';
var MANAGER_URL = 'admin/system/package-manager';

return baseclass.extend({
	node: null,
	count: 0,
	dismissedCount: null,

	load() {
		return Promise.all([
			L.resolveDefault(fs.read(STATE_FILE), ''),
			L.resolveDefault(uci.load(CONFIG), null)
		]).then(function(rv) {
			return rv[0];
		});
	},

	/* The uci value, unless this page has already dismissed a higher count:
	 * uci.load() caches, so without the local copy the banner would come back
	 * on the next poll, before the cache is refreshed. */
	dismissed() {
		var n = parseInt(uci.get(CONFIG, 'settings', 'dismissed_count'), 10);

		if (isNaN(n))
			n = 0;

		return Math.max(n, this.dismissedCount || 0);
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
			E('button', {
				'class': 'btn cbi-button',
				'click': L.bind(this.handleDismiss, this)
			}, [ _('Dismiss') ]),
			' ',
			E('a', {
				'class': 'btn cbi-button cbi-button-apply',
				'href': L.url(MANAGER_URL)
			}, [ _('Review and install') ])
		]);
	},

	handleDismiss(ev) {
		var count = this.count;

		ev.preventDefault();

		uci.set(CONFIG, 'settings', 'dismissed_count', String(count));

		return uci.save().then(function() {
			return uci.apply();
		}).then(L.bind(function() {
			this.dismissedCount = count;

			if (this.node && this.node.parentNode)
				this.node.parentNode.removeChild(this.node);
		}, this)).catch(function(e) {
			ui.addNotification(null, E('p', {}, [
				_('Could not save the setting: %s').replace('%s', e)
			]), 'error');
		});
	},

	render(raw) {
		var state = null;

		try {
			state = JSON.parse(raw || '');
		}
		catch (e) {
			state = null;
		}

		this.count = (state && typeof(state.count) == 'number') ? state.count : 0;

		if (this.count < 1 || this.count <= this.dismissed()) {
			/* Nothing to show: remove any banner we placed earlier. */
			if (this.node && this.node.parentNode)
				this.node.parentNode.removeChild(this.node);
			return null;
		}

		if (this.node == null)
			this.node = this.build();

		/* Plain string replace, not String.format(): the latter is defined in
		 * cbi.js, which the dashboard never loads. */
		this.label.textContent = (this.count == 1)
			? _('1 package update is available.')
			: _('%d package updates are available.').replace('%d', this.count);

		this.when.textContent = state.checked
			? _('Last checked %s.').replace('%s', new Date(state.checked * 1000).toLocaleString())
			: '';

		/* Place the banner at the TOP of the page content, like the firmware
		 * notice, instead of as a section at the bottom.  Returning null keeps
		 * the framework from wrapping it in a titled cbi-section. */
		var top = document.querySelector('#maincontent');
		if (top) {
			if (top.firstChild !== this.node)
				top.insertBefore(this.node, top.firstChild);
			this.node.style.display = '';
			return null;
		}

		this.node.style.display = '';
		return this.node;
	}
});
