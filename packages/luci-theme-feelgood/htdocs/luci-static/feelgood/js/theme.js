/**
 *  Feelgood — palette switch
 *
 *  The header template applies data-theme-style before first paint; this file
 *  keeps it in sync and handles the header control.
 *
 *  The palette is stored under its own key, so it is independent of the
 *  navigation layout (luci-theme-feelgood-nav): switching dark -> light keeps
 *  whatever horizontal/vertical choice the user made, and vice versa.
 *
 *  luci-theme-feelgood — Apache-2.0
 */
(function () {
	'use strict';

	var KEY = 'luci-theme-feelgood-style';
	var FALLBACK = 'dark';

	function current() {
		var v = null;
		try { v = localStorage.getItem(KEY); } catch (e) {}
		return (v === 'light' || v === 'colourful') ? v : FALLBACK;
	}

	function apply(style) {
		var root = document.documentElement;
		root.setAttribute('data-theme-style', style);
		root.setAttribute('data-theme-color', style);
		try { localStorage.setItem(KEY, style); } catch (e) {}

		var buttons = document.querySelectorAll('#theme_switch [data-style]');
		for (var i = 0; i < buttons.length; i++) {
			var on = buttons[i].getAttribute('data-style') === style;
			buttons[i].setAttribute('aria-pressed', on ? 'true' : 'false');
			if (on) buttons[i].classList.add('active');
			else buttons[i].classList.remove('active');
		}
	}

	function wire() {
		var box = document.getElementById('theme_switch');
		if (!box) return;
		if (box.dataset.wired === '1') { apply(current()); return; }
		box.dataset.wired = '1';
		box.addEventListener('click', function (ev) {
			var btn = ev.target.closest('[data-style]');
			if (!btn) return;
			ev.preventDefault();
			apply(btn.getAttribute('data-style'));
		});
		apply(current());
	}

	apply(current());
	if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', wire);
	else wire();
})();
