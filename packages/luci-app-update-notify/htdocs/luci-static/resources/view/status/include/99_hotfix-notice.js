'use strict';
'require baseclass';
'require fs';

/*
 * Dashboard notice: "A fix for your build is available".
 *
 * Fed by /usr/sbin/update-notify, which runs `firmware-upgrade hotfix check`
 * and writes /tmp/hotfix-notify.json.  Kept separate from the package-update
 * notice on purpose: a package update is something the Software page can
 * install, while a hotfix is a version-pinned package (base-files) that only
 * the firmware-upgrade tool may touch -- publishing it as a normal repo entry
 * would put a button in Software that cannot work.
 *
 * The manifest is per build (releases/<exact version>/hotfix.json), so a
 * device can never be offered a fix built for a different image.
 */

var STATE_FILE = '/tmp/hotfix-notify.json';
var PAGE_URL = 'admin/system/firmware-upgrade';

return baseclass.extend({
	node: null,

	load() {
		return L.resolveDefault(fs.read(STATE_FILE), '');
	},

	build() {
		this.label = E('strong');
		this.when = E('span', { 'class': 'hotfix-notice-when' });

		return E('div', { 'class': 'alert-message warning hotfix-notice' }, [
			E('div', {}, [ this.label, ' ', this.when ]),
			E('a', {
				'class': 'btn cbi-button cbi-button-apply',
				'href': L.url(PAGE_URL)
			}, [ _('Install') ])
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
			/* Nothing to show: remove any banner we placed earlier. */
			if (this.node && this.node.parentNode)
				this.node.parentNode.removeChild(this.node);
			return null;
		}

		if (this.node == null)
			this.node = this.build();

		var title = '';
		if (state.items && state.items.length)
			title = state.items[0].title || state.items[0].package || '';

		/* Plain string replace, not String.format(): the latter is defined in
		 * cbi.js, which the dashboard never loads. */
		this.label.textContent = (count == 1)
			? _('A fix for your build is available: %s.').replace('%s', title)
			: _('%d fixes for your build are available.').replace('%d', count);

		this.when.textContent = state.checked
			? _('Last checked %s.').replace('%s', new Date(state.checked * 1000).toLocaleString())
			: '';

		/* Same placement as the update notice: top of the page content. */
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
