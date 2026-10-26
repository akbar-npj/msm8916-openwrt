'use strict';
'require baseclass';
'require ui';

/**
 * Slide animation utilities replacing legacy jQuery slideUp/slideDown
 */
const SlideAnimations = {
	durations: {
		fast: 200,
		normal: 300
	},

	getDuration(d) {
		if (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches)
			return 0;
		return typeof d === 'string' ? (this.durations[d] || this.durations.normal) : (d || this.durations.normal);
	},

	slideDown(element, duration, callback) {
		if (!element) return;
		this.stop(element);
		const animDuration = this.getDuration(duration);

		element.style.display = 'block';
		element.style.overflow = 'hidden';
		element.style.height = '0px';
		element.style.transition = 'height ' + animDuration + 'ms ease-out';
		element.offsetHeight; // force reflow

		const targetHeight = element.scrollHeight;
		element.style.height = targetHeight + 'px';

		const timer = setTimeout(() => {
			element.style.height = '';
			element.style.overflow = '';
			element.style.transition = '';
			delete element._slideTimer;
			if (typeof callback === 'function') callback();
		}, animDuration);
		element._slideTimer = timer;
	},

	slideUp(element, duration, callback) {
		if (!element) return;
		this.stop(element);
		const animDuration = this.getDuration(duration);

		element.style.overflow = 'hidden';
		element.style.height = element.scrollHeight + 'px';
		element.style.transition = 'height ' + animDuration + 'ms ease-out';
		element.offsetHeight; // force reflow

		element.style.height = '0px';

		const timer = setTimeout(() => {
			element.style.display = 'none';
			element.style.height = '';
			element.style.overflow = '';
			element.style.transition = '';
			delete element._slideTimer;
			if (typeof callback === 'function') callback();
		}, animDuration);
		element._slideTimer = timer;
	},

	stop(element) {
		if (element && element._slideTimer) {
			clearTimeout(element._slideTimer);
			delete element._slideTimer;
			element.style.transition = '';
			element.offsetHeight;
		}
	}
};

/**
 * System & Kernel Log Viewer
 * Transforms plain LuCI <textarea id="syslog"> into an interactive,
 * severity-colored log viewer for both System Log and Kernel Log.
 */
const LogViewer = {
	currentFilter: 'all',
	isWrapped: false,

	escapeHtml(str) {
		if (!str) return '';
		return String(str)
			.replace(/&/g, '&amp;')
			.replace(/</g, '&lt;')
			.replace(/>/g, '&gt;')
			.replace(/"/g, '&quot;')
			.replace(/'/g, '&#39;');
	},

	parseLine(line, idx, state) {
		if (!line || !line.trim()) return null;

		let time = '';
		let facility = '';
		let severity = 'info';
		let process = '';
		let msg = '';

		// 1. Standard syslog: Sun Oct 25 22:09:33 2026 kern.err kernel: [ 0.760192] ...
		let m = line.match(/^(\w{3}\s+\w{3}\s+\d+\s+[\d:]+\s+\d{4})\s+([a-zA-Z0-9_]+)\.([a-zA-Z0-9_]+)\s+([^:]+):\s*(.*)$/);
		if (m) {
			time = m[1];
			facility = m[2];
			severity = m[3];
			process = m[4];
			msg = m[5];
		} else {
			// 2. Syslog without process colon: Sun Oct 25 22:09:33 2026 kern.err msg
			m = line.match(/^(\w{3}\s+\w{3}\s+\d+\s+[\d:]+\s+\d{4})\s+([a-zA-Z0-9_]+)\.([a-zA-Z0-9_]+)\s+(.*)$/);
			if (m) {
				time = m[1];
				facility = m[2];
				severity = m[3];
				msg = m[4];
			} else {
				// 3. Intl format: [Oct 25, 2026, 10:09:33 PM GMT+5:45] daemon.info: msg
				m = line.match(/^\[(.*?)\]\s+([a-zA-Z0-9_]+)\.([a-zA-Z0-9_]+):\s*(.*)$/);
				if (m) {
					time = m[1];
					facility = m[2];
					severity = m[3];
					msg = m[4];
				} else {
					// 4. Kernel dmesg format: [ 0.760192] msg
					m = line.match(/^\[\s*(\d+(?:\.\d+)?)\]\s*(.*)$/);
					if (m) {
						time = '[' + parseFloat(m[1]).toFixed(6) + ']';
						facility = 'kern';
						process = 'kernel';
						msg = m[2];
						if (/\b(?:fail|failed|error|fault|denied|corrupt|timed?out|abort|panic|oops|bug)\b/i.test(msg)) {
							severity = 'err';
						} else if (/\b(?:warn|warning|overclocked?|taints?|conflict|dropped|retry)\b/i.test(msg)) {
							severity = 'warn';
						} else if (/\b(?:notice|ready|detected|registered|clean|initialized)\b/i.test(msg)) {
							severity = 'notice';
						} else {
							severity = 'info';
						}
					} else {
						// 5. Continuation / multiline trace
						facility = state.lastFacility || 'sys';
						severity = state.lastSeverity || 'info';
						msg = line;
					}
				}
			}
		}

		// Normalize severity state
		const s = severity.toLowerCase();
		let norm = 'info';
		if (s.startsWith('emerg') || s === 'panic' || s.startsWith('alert') || s.startsWith('crit')) {
			norm = 'crit';
		} else if (s.startsWith('err')) {
			norm = 'err';
		} else if (s.startsWith('warn')) {
			norm = 'warn';
		} else if (s === 'notice') {
			norm = 'notice';
		} else if (s === 'debug') {
			norm = 'debug';
		}

		state.lastFacility = facility;
		state.lastSeverity = norm;

		return {
			idx: idx + 1,
			time,
			facility,
			severity: norm,
			rawSev: severity,
			process,
			msg
		};
	},

	process() {
		const syslog = document.getElementById('syslog');
		if (!syslog) return;

		// Hide the raw plain-text textarea
		if (!syslog.classList.contains('feelgood-hidden-log')) {
			syslog.classList.add('feelgood-hidden-log');
		}

		let viewer = document.getElementById('feelgood-log-viewer');
		if (!viewer) {
			viewer = this.createViewer(syslog);
		}

		this.hookTextarea(syslog, viewer);

		const currentVal = syslog.value || '';
		if (viewer.dataset.lastLogValue !== currentVal) {
			this.render(viewer, currentVal);
		}
	},

	hookTextarea(syslog, viewer) {
		if (syslog._feelgoodHooked) return;
		syslog._feelgoodHooked = true;

		const self = this;
		const originalDesc = Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value');
		if (originalDesc && originalDesc.set) {
			Object.defineProperty(syslog, 'value', {
				get() {
					return originalDesc.get.call(this);
				},
				set(v) {
					originalDesc.set.call(this, v);
					self.render(viewer, v);
				},
				configurable: true
			});
		}
	},

	createViewer(syslog) {
		const viewer = document.createElement('div');
		viewer.id = 'feelgood-log-viewer';
		viewer.className = 'feelgood-log-viewer';

		syslog.parentNode.insertBefore(viewer, syslog.nextSibling);

		// Synchronize LuCI scroll buttons
		const scrollDownBtn = document.getElementById('scrollDownButton');
		const scrollUpBtn = document.getElementById('scrollUpButton');

		if (scrollDownBtn && !scrollDownBtn._feelgoodHooked) {
			scrollDownBtn._feelgoodHooked = true;
			scrollDownBtn.addEventListener('click', () => {
				const body = viewer.querySelector('.feelgood-log-body');
				if (body) body.scrollTop = body.scrollHeight;
			});
		}
		if (scrollUpBtn && !scrollUpBtn._feelgoodHooked) {
			scrollUpBtn._feelgoodHooked = true;
			scrollUpBtn.addEventListener('click', () => {
				const body = viewer.querySelector('.feelgood-log-body');
				if (body) body.scrollTop = 0;
			});
		}

		return viewer;
	},

	render(viewer, rawText) {
		viewer.dataset.lastLogValue = rawText;

		const lines = (rawText || '').split(/\r?\n/);
		const rowsHtml = [];
		const state = { lastFacility: '', lastSeverity: 'info' };
		const counts = { total: 0, crit: 0, err: 0, warn: 0, notice: 0, info: 0, debug: 0 };

		for (let i = 0; i < lines.length; i++) {
			const entry = this.parseLine(lines[i], i, state);
			if (!entry) continue;

			counts.total++;
			if (counts[entry.severity] !== undefined) counts[entry.severity]++;

			rowsHtml.push(
				`<div class="feelgood-log-row log-sev-${entry.severity}" data-severity="${entry.severity}">` +
					`<span class="log-num">${entry.idx}</span>` +
					(entry.time ? `<span class="log-time">${this.escapeHtml(entry.time)}</span>` : '') +
					`<span class="log-badge log-badge-${entry.severity}">` +
						(entry.facility ? `<span class="log-badge-fac">${this.escapeHtml(entry.facility)}</span><span class="log-badge-sep">.</span>` : '') +
						`<span class="log-badge-sev">${this.escapeHtml(entry.rawSev.toUpperCase())}</span>` +
					`</span>` +
					(entry.process ? `<span class="log-proc">${this.escapeHtml(entry.process)}</span>` : '') +
					`<span class="log-msg">${this.escapeHtml(entry.msg)}</span>` +
				`</div>`
			);
		}

		const filterSev = viewer.getAttribute('data-filter-severity') || this.currentFilter;
		const isWrap = viewer.classList.contains('log-wrap') || this.isWrapped;

		// Build toolbar
		let toolbarHtml =
			`<div class="feelgood-log-toolbar">` +
				`<div class="feelgood-log-filters">` +
					`<button type="button" class="feelgood-log-filter-btn ${filterSev === 'all' ? 'active' : ''}" data-sev="all">` +
						`All <span class="pill-cnt">${counts.total}</span>` +
					`</button>` +
					(counts.crit > 0
						? `<button type="button" class="feelgood-log-filter-btn ${filterSev === 'crit' ? 'active' : ''}" data-sev="crit">` +
								`🔴 Critical <span class="pill-cnt">${counts.crit}</span>` +
						  `</button>`
						: '') +
					(counts.err > 0
						? `<button type="button" class="feelgood-log-filter-btn ${filterSev === 'err' ? 'active' : ''}" data-sev="err">` +
								`❌ Errors <span class="pill-cnt">${counts.err}</span>` +
						  `</button>`
						: '') +
					(counts.warn > 0
						? `<button type="button" class="feelgood-log-filter-btn ${filterSev === 'warn' ? 'active' : ''}" data-sev="warn">` +
								`⚠️ Warnings <span class="pill-cnt">${counts.warn}</span>` +
						  `</button>`
						: '') +
					(counts.notice > 0
						? `<button type="button" class="feelgood-log-filter-btn ${filterSev === 'notice' ? 'active' : ''}" data-sev="notice">` +
								`✅ Notices <span class="pill-cnt">${counts.notice}</span>` +
						  `</button>`
						: '') +
					`<button type="button" class="feelgood-log-filter-btn ${filterSev === 'info' ? 'active' : ''}" data-sev="info">` +
						`🟢 Info <span class="pill-cnt">${counts.info}</span>` +
					`</button>` +
				`</div>` +
				`<div class="feelgood-log-actions">` +
					`<button type="button" class="feelgood-log-action-btn log-btn-wrap ${isWrap ? 'active' : ''}" title="Toggle Word Wrap">` +
						`Wrap` +
					`</button>` +
					`<button type="button" class="feelgood-log-action-btn log-btn-copy" title="Copy Raw Logs to Clipboard">` +
						`📋 Copy` +
					`</button>` +
					`<button type="button" class="feelgood-log-action-btn log-btn-tail" title="Scroll to bottom">` +
						`⬇ Tail` +
					`</button>` +
					`<button type="button" class="feelgood-log-action-btn log-btn-head" title="Scroll to top">` +
						`⬆ Head` +
					`</button>` +
				`</div>` +
			`</div>`;

		const bodyHtml = rowsHtml.length > 0
			? `<div class="feelgood-log-body">${rowsHtml.join('')}</div>`
			: `<div class="feelgood-log-body"><div class="feelgood-log-empty">No log entries matching the selected filter.</div></div>`;

		viewer.innerHTML = toolbarHtml + bodyHtml;

		if (filterSev && filterSev !== 'all') {
			viewer.setAttribute('data-filter-severity', filterSev);
		} else {
			viewer.removeAttribute('data-filter-severity');
		}

		if (isWrap) {
			viewer.classList.add('log-wrap');
		} else {
			viewer.classList.remove('log-wrap');
		}

		// Attach toolbar listeners
		const filterBtns = viewer.querySelectorAll('.feelgood-log-filter-btn');
		filterBtns.forEach(btn => {
			btn.addEventListener('click', (e) => {
				e.preventDefault();
				const sev = btn.getAttribute('data-sev');
				this.currentFilter = sev;
				filterBtns.forEach(b => b.classList.remove('active'));
				btn.classList.add('active');

				if (sev === 'all') {
					viewer.removeAttribute('data-filter-severity');
				} else {
					viewer.setAttribute('data-filter-severity', sev);
				}
			});
		});

		const wrapBtn = viewer.querySelector('.log-btn-wrap');
		if (wrapBtn) {
			wrapBtn.addEventListener('click', (e) => {
				e.preventDefault();
				viewer.classList.toggle('log-wrap');
				this.isWrapped = viewer.classList.contains('log-wrap');
				wrapBtn.classList.toggle('active', this.isWrapped);
			});
		}

		const copyBtn = viewer.querySelector('.log-btn-copy');
		if (copyBtn) {
			copyBtn.addEventListener('click', (e) => {
				e.preventDefault();
				const textToCopy = rawText || '';
				if (navigator.clipboard && navigator.clipboard.writeText) {
					navigator.clipboard.writeText(textToCopy).then(() => {
						copyBtn.textContent = '✓ Copied!';
						setTimeout(() => { copyBtn.innerHTML = '📋 Copy'; }, 2000);
					}).catch(() => {
						this.fallbackCopy(textToCopy, copyBtn);
					});
				} else {
					this.fallbackCopy(textToCopy, copyBtn);
				}
			});
		}

		const tailBtn = viewer.querySelector('.log-btn-tail');
		if (tailBtn) {
			tailBtn.addEventListener('click', (e) => {
				e.preventDefault();
				const body = viewer.querySelector('.feelgood-log-body');
				if (body) body.scrollTop = body.scrollHeight;
			});
		}

		const headBtn = viewer.querySelector('.log-btn-head');
		if (headBtn) {
			headBtn.addEventListener('click', (e) => {
				e.preventDefault();
				const body = viewer.querySelector('.feelgood-log-body');
				if (body) body.scrollTop = 0;
			});
		}
	},

	fallbackCopy(text, btn) {
		const ta = document.createElement('textarea');
		ta.value = text;
		ta.style.position = 'fixed';
		ta.style.opacity = '0';
		document.body.appendChild(ta);
		ta.select();
		try {
			document.execCommand('copy');
			btn.textContent = '✓ Copied!';
			setTimeout(() => { btn.innerHTML = '📋 Copy'; }, 2000);
		} catch (err) {
			btn.textContent = 'Failed';
			setTimeout(() => { btn.innerHTML = '📋 Copy'; }, 2000);
		}
		document.body.removeChild(ta);
	}
};

/**
 * Smart Colorizer Engine
 * Enriches LuCI telemetry with contextual semantic tags for:
 * - Signal quality & WiFi connectivity
 * - Data rates and link speeds
 * - Traffic direction (TX/RX counters)
 * - Frequency bands (2.4G, 5G, 6G)
 * - System resource usage levels (Memory, Storage, CPU)
 * - Port statuses
 */
/**
 * Network -> Interfaces: the label of each status row decides its role.
 * ui.itemlist() renders every entry as "<strong>Label: </strong>value".
 */
const IFACE_ROLES = {
	'protocol':    'protocol',
	'device':      'device',
	'carrier':     'carrier',
	'uptime':      'uptime',
	'mac':         'mac',
	'rx':          'rx',
	'tx':          'tx',
	'ipv4':        'ip',
	'ipv6':        'ip',
	'ipv6-pd':     'ip',
	'error':       'error',
	'information': 'info'
};

/**
 * Status -> Processes / DHCP Leases / Routes: table cells carry the column
 * title in data-title (L.ui.Table copies it), so a cell can be coloured by
 * what its column means.  Only the columns that would otherwise read as plain
 * white are listed here - addresses, MACs and durations are already badged by
 * transformText().
 */
const COLUMN_META = {
	'hostname':  'host',
	'pid':       'id',
	'owner':     'id',
	'duid':      'id',
	'iaid':      'id',
	'metric':    'id',
	'table':     'id',
	'priority':  'id',
	'rule':      'id',
	'command':   'code',
	'device':    'code',
	'interface': 'code'
};

/**
 * Classify a protocol name by the role it plays, so the value can be coloured
 * consistently across boards and LuCI versions.
 */
function ifaceProtocolKind(value) {
	const s = String(value || '').toLowerCase();

	if (/qmi|mbim|cellular|modemmanager|3gpp|\b(3g|4g|5g|lte|umts|ncm)\b/.test(s))
		return 'cellular';
	if (/pppoe|\bppp\b|pptp|l2tp|wireguard|openvpn|\bvpn\b|\bgre\b|6in4|6to4|ds-lite|\bmap\b|vxlan|tunnel/.test(s))
		return 'tunnel';
	if (/dhcp/.test(s))
		return 'dynamic';
	if (/static/.test(s))
		return 'static';
	if (/unmanaged|\bnone\b|disabled/.test(s))
		return 'none';

	return 'other';
}

const Colorizer = {
	isBusy: false,
	rafId: null,

	init() {
		this.schedule();
		const target = document.querySelector('#maincontent') || document.body;
		if (target) {
			const observer = new MutationObserver(() => this.schedule());
			observer.observe(target, { childList: true, subtree: true });
		}
	},

	schedule() {
		if (this.rafId !== null) return;
		this.rafId = requestAnimationFrame(() => {
			this.rafId = null;
			this.colorize();
		});
	},

	colorize() {
		if (this.isBusy) return;
		this.isBusy = true;
		try {
			this.colorizeProgressBars();
			this.colorizeElements();
			LogViewer.process();
		} catch (err) {
			console.warn('Feelgood Colorizer error:', err);
		} finally {
			this.isBusy = false;
		}
	},

	colorizeProgressBars() {
		const bars = document.querySelectorAll('.cbi-progressbar');
		for (let i = 0; i < bars.length; i++) {
			const bar = bars[i];
			const child = bar.firstElementChild;
			let pct = null;

			if (child && child.style.width) {
				const m = child.style.width.match(/(\d+(?:\.\d+)?)%/);
				if (m) pct = parseFloat(m[1]);
			}
			if (pct === null && bar.getAttribute('title')) {
				const m = bar.getAttribute('title').match(/(\d+(?:\.\d+)?)%/);
				if (m) pct = parseFloat(m[1]);
			}
			if (pct === null && bar.textContent) {
				const m = bar.textContent.match(/(\d+(?:\.\d+)?)%/);
				if (m) pct = parseFloat(m[1]);
			}

			if (pct !== null) {
				// Detect context to distinguish "Free / Available" metrics from "Used / Consumed" metrics
				let contextText = '';
				const row = bar.closest('tr, .tr');
				if (row) {
					const firstCell = row.querySelector('td:first-child, .td:first-child');
					if (firstCell) contextText += ' ' + firstCell.textContent;
				}
				const cbiValue = bar.closest('.cbi-value');
				if (cbiValue) {
					const title = cbiValue.querySelector('.cbi-value-title');
					if (title) contextText += ' ' + title.textContent;
				}
				if (bar.previousElementSibling) {
					contextText += ' ' + bar.previousElementSibling.textContent;
				}

				const isAvailableOrFree = /(?:free|available|idle|remaining)/i.test(contextText);
				const isCacheOrBuffer = /(?:buffered|cached)/i.test(contextText);

				let level = 'normal';
				if (isAvailableOrFree) {
					// For free/available resources (e.g. "Swap free", "Total Available"):
					// High free percentage (>= 50%) is healthy and optimal -> GREEN ('good')
					// Moderate free percentage (20% - 49%) -> AMBER ('warning')
					// Low free percentage (< 20%) -> RED ('critical' - memory exhaustion)
					if (pct >= 50) {
						level = 'good';
					} else if (pct >= 20) {
						level = 'warning';
					} else {
						level = 'critical';
					}
				} else if (isCacheOrBuffer) {
					// Buffer and cache memory are normal reclaimable resources
					level = 'normal';
				} else {
					// For consumption metrics (e.g. "Used", "Disk space", "Active Connections"):
					// Low usage (< 60%) is healthy -> GREEN ('good')
					// Moderate usage (60% - 84%) -> AMBER ('warning')
					// High usage (>= 85%) -> RED ('critical')
					if (pct >= 85) {
						level = 'critical';
					} else if (pct >= 60) {
						level = 'warning';
					} else {
						level = 'good';
					}
				}

				if (bar.getAttribute('data-level') !== level)
					bar.setAttribute('data-level', level);
			}
		}
	},

	colorizeElements() {
		// 1. Process specialized Port Status boxes first (overview page)
		this.processPortBoxes();

		// 2. Role-tag the interface status rows (Network -> Interfaces)
		this.colorizeIfaceRows();

		// 3. Colour the CPU scaling governors (Services -> CPU Performance)
		this.colorizeGovernors();

		// 4. Utilisation cells (Status -> Processes) and the load average
		this.colorizeUsageCells();
		this.colorizeLoadAverage();
		this.colorizeLabelValueRows();
		this.colorizeTableColumns();

		// 5. Process general table cells, field values, and key-value entries
		const cells = document.querySelectorAll('#maincontent td, #maincontent .td, #maincontent .cbi-value-field, #maincontent .ifacebox-body .nowrap, #maincontent .cpu-perf-label-value');
		for (let i = 0; i < cells.length; i++) {
			const cell = cells[i];

			// Never modify cells containing input controls
			if (cell.querySelector('input, select, textarea, button, form, .cbi-dropdown'))
				continue;

			// Skip if already parsed and content hasn't changed
			if (cell.dataset.colorizedText === cell.textContent)
				continue;

			this.processCell(cell);
		}
	},

	/**
	 * Network -> Interfaces: colour-code the status list by role.
	 *
	 * Tags the row with data-role (and data-state / data-kind where the meaning
	 * depends on the value) so cascade.css can colour the label and the value.
	 * Every write is guarded - an unconditional setAttribute() would retrigger
	 * the MutationObserver forever.
	 */
	colorizeIfaceRows() {
		const rows = document.querySelectorAll('#maincontent .nowrap');

		for (let i = 0; i < rows.length; i++) {
			const row = rows[i];
			const labelEl = row.firstElementChild;

			if (!labelEl || labelEl.tagName !== 'STRONG')
				continue;

			const label = labelEl.textContent.replace(/:\s*$/, '').trim().toLowerCase();
			const role = IFACE_ROLES[label];

			if (!role)
				continue;

			if (row.getAttribute('data-role') !== role)
				row.setAttribute('data-role', role);

			const value = (row.textContent || '').slice(labelEl.textContent.length).trim();

			if (!value)
				continue;

			if (role === 'carrier') {
				const state = /^absent/i.test(value) ? 'bad' : 'ok';

				if (row.getAttribute('data-state') !== state)
					row.setAttribute('data-state', state);
			}
			else if (role === 'protocol') {
				const kind = ifaceProtocolKind(value);

				if (row.getAttribute('data-kind') !== kind)
					row.setAttribute('data-kind', kind);
			}
			else if ((role === 'rx' || role === 'tx') && !row.querySelector('.badge-traffic')) {
				this.wrapTrafficCounter(row, labelEl, role);
			}
		}
	},

	/**
	 * Status -> Processes: colour the "%CPU" / "%MEM" cells by utilisation.
	 * L.ui.Table copies the column title into data-title, so the two columns
	 * are distinguishable without touching the view.
	 */
	colorizeUsageCells() {
		const cells = document.querySelectorAll('#maincontent .td[data-title*="usage"], #maincontent td[data-title*="usage"]');

		for (let i = 0; i < cells.length; i++) {
			const cell = cells[i];
			const v = parseFloat((cell.textContent || '').trim());

			if (isNaN(v) || v <= 0)
				continue;

			// %CPU is per process and can exceed 100 on a 4-core box, so a single
			// busy process is only worth an amber warning.  %MEM is a share of the
			// whole 512 MB, where 10 % is already a large resident set.
			const isMem = /mem/i.test(cell.getAttribute('data-title') || '');
			const mid = isMem ? 10 : 40;
			const high = isMem ? 30 : 80;

			const level = (v >= high) ? 'high' : (v >= mid ? 'mid' : 'low');

			if (cell.getAttribute('data-usage') !== level)
				cell.setAttribute('data-usage', level);
		}
	},

	/**
	 * Status -> Overview: colour the load average by how loaded the box is.
	 * msm8916 is a fixed 4-core part, so 4.00 is the saturation point.
	 */
	colorizeLoadAverage() {
		const tag = (cell) => {
			const m = (cell.textContent || '').match(/([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)/);

			if (!m)
				return;

			const load = parseFloat(m[1]);
			const level = (load >= 4) ? 'high' : (load >= 1.5 ? 'mid' : 'low');

			if (cell.getAttribute('data-load') !== level)
				cell.setAttribute('data-load', level);
		};

		// <span class="nowrap"><strong>Load Average: </strong>0.42, 0.31, 0.20</span>
		const rows = document.querySelectorAll('#maincontent .nowrap');

		for (let i = 0; i < rows.length; i++) {
			const labelEl = rows[i].firstElementChild;

			if (labelEl && labelEl.tagName === 'STRONG' &&
			    /^load average$/i.test(labelEl.textContent.replace(/:\s*$/, '').trim()))
				tag(rows[i]);
		}

		// <tr class="tr"><td>Load Average</td><td>0.42, 0.31, 0.20</td></tr>
		// (Status -> Overview renders the System block as a label/value table.)
		const trs = document.querySelectorAll('#maincontent tr.tr');

		for (let i = 0; i < trs.length; i++) {
			const cells = trs[i].children;

			if (cells.length >= 2 && cells[0].tagName === 'TD' &&
			    /^load average$/i.test((cells[0].textContent || '').trim())) {
				cells[0].setAttribute('data-label', '1');
				tag(cells[1]);
			}
		}
	},

	/**
	 * Status -> Overview renders System / Connections as label+value tables,
	 * so there is no <strong> to key off.  Tag the value cell by its label.
	 */
	colorizeLabelValueRows() {
		const META = {
			'hostname':         'host',
			'model':            'muted',
			'architecture':     'code',
			'target platform':  'code',
			'firmware version': 'version',
			'kernel version':   'version',
			'local time':       'muted'
		};

		const rows = document.querySelectorAll('#maincontent tr.tr');

		for (let i = 0; i < rows.length; i++) {
			const cells = rows[i].children;

			if (cells.length < 2 || cells[0].tagName !== 'TD')
				continue;

			const role = META[(cells[0].textContent || '').trim().toLowerCase()];

			if (!role)
				continue;

			cells[0].setAttribute('data-label', '1');

			if (cells[1].getAttribute('data-meta') !== role)
				cells[1].setAttribute('data-meta', role);
		}
	},

	/**
	 * Status -> Processes / DHCP Leases / Routes: colour the columns that carry
	 * an identifier instead of prose, so the eye can skip them and land on the
	 * values that matter.
	 */
	colorizeTableColumns() {
		const cells = document.querySelectorAll('#maincontent [data-title]');

		for (let i = 0; i < cells.length; i++) {
			const cell = cells[i];

			// %CPU / %MEM and the load average have their own passes.
			if (cell.hasAttribute('data-usage') || cell.hasAttribute('data-load'))
				continue;

			const title = (cell.getAttribute('data-title') || '').trim().toLowerCase();

			if (!title)
				continue;

			if (title === 'protocol') {
				const kind = ifaceProtocolKind(cell.textContent);

				if (cell.getAttribute('data-proto-kind') !== kind)
					cell.setAttribute('data-proto-kind', kind);

				continue;
			}

			const role = COLUMN_META[title];

			if (role && cell.getAttribute('data-meta') !== role)
				cell.setAttribute('data-meta', role);
		}
	},

	/**
	 * Services -> CPU Performance: colour the scaling governor by what it does.
	 * The cells carry ids like "cpu0governor"; the value is the governor name.
	 */
	colorizeGovernors() {
		const cells = document.querySelectorAll('#maincontent [id$="governor"]');

		for (let i = 0; i < cells.length; i++) {
			const cell = cells[i];
			const gov = (cell.textContent || '').trim().toLowerCase();

			if (!gov || gov === '-')
				continue;

			let kind = 'other';

			if (/performance/.test(gov))
				kind = 'performance';
			else if (/powersave/.test(gov))
				kind = 'powersave';
			else if (/ondemand|conservative|schedutil|interactive/.test(gov))
				kind = 'adaptive';
			else if (/userspace/.test(gov))
				kind = 'manual';

			if (cell.getAttribute('data-governor') !== kind)
				cell.setAttribute('data-governor', kind);
		}
	},

	/**
	 * Turn "1.23 GB (456 Pkts.)" into a direction badge plus a quiet packet
	 * count, so the bytes read as the headline and the packets as the detail.
	 */
	wrapTrafficCounter(row, labelEl, role) {
		const node = labelEl.nextSibling;

		if (!node || node.nodeType !== 3)
			return;

		const text = node.nodeValue;
		const m = text.match(/^\s*(.+?)\s*\(([^)]*)\)\s*$/);
		const arrow = (role === 'rx') ? '▼' : '▲';
		let html;

		if (m)
			html = '<span class="badge-colored badge-traffic badge-' + role + '">' +
				'<span class="traffic-icon">' + arrow + '</span> ' + m[1] + '</span> ' +
				'<span class="iface-pkts">(' + m[2] + ')</span>';
		else
			html = '<span class="badge-colored badge-traffic badge-' + role + '">' +
				'<span class="traffic-icon">' + arrow + '</span> ' + text.trim() + '</span>';

		node.parentNode.replaceChild(
			document.createRange().createContextualFragment(html), node);
	},

	/**
	 * Dedicated Port Status Box symmetry & USB Ethernet styling
	 */
	processPortBoxes() {
		const portBoxes = document.querySelectorAll('div[style*="grid-template-columns"] > .ifacebox, .port-status-box');
		if (!portBoxes.length) return;

		// Ensure parent container has symmetrical flex layout
		portBoxes.forEach((box) => {
			const parent = box.parentElement;
			if (parent && !parent.classList.contains('port-status-container')) {
				parent.classList.add('port-status-container');
			}

			box.classList.add('port-card');

			// 1. Port Header (e.g. "usb0")
			const head = box.querySelector('.ifacebox-head');
			let portName = '';
			if (head) {
				portName = head.textContent.trim().toLowerCase();
				if (!head.classList.contains('port-card-head')) {
					head.classList.add('port-card-head');
					head.innerHTML = `<span class="port-name">${portName.toUpperCase()}</span>`;
				}
			}

			// 2. Port Icon & Link State
			const bodies = box.querySelectorAll('.ifacebox-body');
			if (bodies.length >= 1) {
				const body1 = bodies[0];
				if (!body1.classList.contains('port-card-main')) {
					body1.classList.add('port-card-main');

					const img = body1.querySelector('img');
					const isUsb = portName.startsWith('usb');
					const text = body1.textContent.trim();
					const isConnected = /Connected|GbE|M\(|M\b|\d+\s*M/i.test(text);

					// Replace generic RJ-45 icon with dedicated USB Ethernet SVG if usb port
					if (isUsb && !body1.querySelector('.port-icon-usb')) {
						if (img) img.style.display = 'none';
						const usbColor = isConnected ? '#38bdf8' : '#64748b';
						const statusDot = isConnected ? '#34d399' : '#f43f5e';
						const svgHtml = `
							<div class="port-icon-usb-wrapper" title="USB Ethernet (${portName})">
								<svg class="port-icon-usb" viewBox="0 0 40 40" width="36" height="36" fill="none">
									<rect x="4" y="4" width="32" height="32" rx="8" fill="rgba(56, 189, 248, 0.12)" stroke="${usbColor}" stroke-width="1.8"/>
									<path d="M20 10v16M20 10l-2.5 3.5h5L20 10zM20 18l-4 3v3a1.5 1.5 0 1 0 1.5 0v-2l2.5-2M20 20l4 2.5v2a1.5 1.5 0 1 1-1.5 0v-1.2l-2.5-1.8" stroke="${usbColor}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
									<circle cx="16" cy="25" r="1.4" fill="${usbColor}"/>
									<rect x="22.5" y="23.5" width="2.8" height="2.8" fill="${usbColor}"/>
									<circle cx="31" cy="9" r="2.8" fill="${statusDot}"/>
								</svg>
							</div>`;
						body1.insertAdjacentHTML('afterbegin', svgHtml);
					}

					// Format speed / status badge cleanly under the icon
					if (!body1.querySelector('.badge-port-status')) {
						let statusBadge = '';
						if (isConnected) {
							statusBadge = `<span class="badge-colored badge-status-good badge-port-status">${text || 'Connected'}</span>`;
						} else {
							statusBadge = `<span class="badge-colored badge-status-bad badge-port-status">No Link</span>`;
						}
						// Remove raw text nodes while preserving our elements
						Array.from(body1.childNodes).forEach(node => {
							if (node.nodeType === Node.TEXT_NODE || node.nodeName === 'BR') node.remove();
						});
						body1.insertAdjacentHTML('beforeend', statusBadge);
					}
				}
			}

			// 3. Port Traffic Stats (TX & RX)
			if (bodies.length >= 2) {
				const body2 = bodies[1];
				if (!body2.classList.contains('port-card-stats')) {
					body2.classList.add('port-card-stats');
					const text = body2.textContent;
					const txMatch = text.match(/▲\s*([0-9.]+\s*[KMGT]?B)/i);
					const rxMatch = text.match(/▼\s*([0-9.]+\s*[KMGT]?B)/i);

					if (txMatch || rxMatch) {
						const txVal = txMatch ? txMatch[1] : '0 B';
						const rxVal = rxMatch ? rxMatch[1] : '0 B';
						const tooltip = body2.querySelector('.cbi-tooltip');
						const tooltipHtml = tooltip ? tooltip.outerHTML : '';

						body2.innerHTML = `
							<div class="port-stats-grid">
								<span class="badge-colored badge-traffic badge-tx" title="Upload (TX)"><span class="traffic-icon">▲</span> ${txVal}</span>
								<span class="badge-colored badge-traffic badge-rx" title="Download (RX)"><span class="traffic-icon">▼</span> ${rxVal}</span>
							</div>
							${tooltipHtml}`;
					}
				}
			}
		});
	},

	processCell(cell) {
		const rawText = cell.textContent.trim();
		if (!rawText) return;

		// Never modify cells containing interactive input controls
		if (cell.querySelector('input, select, textarea, button, form, .cbi-dropdown'))
			return;

		const prevText = cell.previousElementSibling ? (cell.previousElementSibling.textContent || '') : '';
		const labelText = cell.closest ? (cell.closest('.cbi-value')?.querySelector('.cbi-value-title')?.textContent || '') : '';
		const cellId = cell.id || '';

		const dataTitle = (cell.getAttribute && cell.getAttribute('data-title')) || '';

		const context = {
			rawText: rawText,
			// CPU frequency cells (Services -> CPU Performance) carry ids like
			// cpu0curFreq and a data-title of "Current/Minimum/Maximum frequency".
			isFreq: /freq|frequency/i.test(cellId) || /freq|frequency/i.test(dataTitle) || /frequency/i.test(labelText),
			isFreqLimit: /(?:min|max|minimum|maximum)/i.test(dataTitle) || /minFreq|maxFreq/i.test(cellId),
			isNoise: /Noise/i.test(rawText) || /Noise/i.test(prevText) || /Noise/i.test(labelText) || /noise/i.test(cellId),
			isTxPower: /(?:TX[\s-]*Power|txpower|Current\s*power)/i.test(rawText) ||
			           /(?:TX[\s-]*Power|txpower)/i.test(prevText) ||
			           /(?:TX[\s-]*Power|txpower)/i.test(labelText) ||
			           /(?:txpower|tx_power)/i.test(cellId)
		};

		// Traverse ONLY text nodes so HTML attributes (title, data-signal, etc.) are NEVER matched or corrupted
		const walker = document.createTreeWalker(
			cell,
			NodeFilter.SHOW_TEXT,
			{
				acceptNode: function(n) {
					if (!n.nodeValue || !n.nodeValue.trim())
						return NodeFilter.FILTER_REJECT;
					if (n.parentElement && n.parentElement.closest('.badge-colored, script, style, .port-icon-usb-wrapper, input, select, textarea, button'))
						return NodeFilter.FILTER_REJECT;
					return NodeFilter.FILTER_ACCEPT;
				}
			}
		);

		const textNodes = [];
		let curr;
		while ((curr = walker.nextNode())) {
			textNodes.push(curr);
		}

		let modified = false;
		for (let i = 0; i < textNodes.length; i++) {
			const node = textNodes[i];
			const transformed = this.transformText(node.nodeValue, context);
			if (transformed !== null) {
				const fragment = document.createRange().createContextualFragment(transformed);
				node.parentNode.replaceChild(fragment, node);
				modified = true;
			}
		}

		if (modified) {
			cell.dataset.colorizedText = cell.textContent;
		}
	},

	transformText(text, context) {
		let modified = false;
		let result = text;

		// 0. CPU frequency (e.g. "998.4 MHz", "1.2 GHz", "200 MHz").
		//    MHz is never a WiFi channel, so it is unambiguous; GHz is only
		//    treated as a CPU clock when the cell says it is a frequency,
		//    otherwise rule 4 would paint it as a 2.4 GHz band.
		let cpuFreqDone = false;

		if (context.isFreq || /\bMHz\b/i.test(result)) {
			result = result.replace(/(\d+(?:\.\d+)?)\s*(MHz|GHz)/gi, (match, numStr, unit) => {
				const mhz = parseFloat(numStr) * (unit.toLowerCase() === 'ghz' ? 1000 : 1);
				let level;

				if (context.isFreqLimit)     level = 'limit';
				else if (mhz >= 1200)        level = 'turbo';
				else if (mhz >= 900)         level = 'high';
				else if (mhz >= 600)         level = 'mid';
				else                         level = 'idle';

				return '<span class="badge-colored badge-cpu-freq" data-mhz="' + level + '">' + match + '</span>';
			});
			modified = true;
			cpuFreqDone = true;
		}

		// 1. RF Metrics: TX Power, Noise Floor, and WiFi Signal (dBm)
		if (/-?\d+(?:\.\d+)?\s*dBm/i.test(result)) {
			// A. Compound Signal / Noise (e.g. "-54/-95 dBm" or "-54 / -95 dBm" or "---/-95 dBm")
			if (/(-?\d+(?:\.\d+)?|---)\s*\/\s*(-?\d+(?:\.\d+)?)\s*dBm/i.test(result)) {
				result = result.replace(/(-?\d+(?:\.\d+)?|---)\s*\/\s*(-?\d+(?:\.\d+)?)\s*dBm/gi, (match, sSig, sNoise) => {
					let sigHtml;
					if (sSig !== '---') {
						const fSig = parseFloat(sSig);
						const vSig = Math.abs(fSig);
						let q = 'poor';
						if (fSig !== 0) {
							if (vSig <= 55) q = 'excellent';
							else if (vSig <= 67) q = 'good';
							else if (vSig <= 79) q = 'fair';
						}
						sigHtml = `<span class="badge-colored badge-signal" data-quality="${q}">${sSig} dBm</span>`;
					} else {
						sigHtml = `<span class="badge-colored badge-signal" data-quality="poor">--- dBm</span>`;
					}
					const noiseHtml = `<span class="badge-colored badge-noise"><span class="noise-icon">🔇</span> ${sNoise} dBm</span>`;
					return `${sigHtml} <span class="badge-sep">/</span> ${noiseHtml}`;
				});
				modified = true;
			}
			// B. Radio Transmitter Output Power (e.g. "TX Power: 25.00 dBm" or "25 dBm")
			else if (context.isTxPower) {
				result = result.replace(/(\d+(?:\.\d+)?)\s*dBm/gi, (match) => {
					return `<span class="badge-colored badge-tx-power"><span class="tx-pwr-icon">⚡</span> ${match}</span>`;
				});
				modified = true;
			}
			// C. Dedicated RF Noise Floor (e.g. "Noise: -71.00 dBm" or "-95 dBm")
			else if (context.isNoise) {
				result = result.replace(/(-?\d+(?:\.\d+)?)\s*dBm/gi, (match) => {
					return `<span class="badge-colored badge-noise"><span class="noise-icon">🔇</span> ${match}</span>`;
				});
				modified = true;
			}
			// D. Client Reception Signal (RSSI) (e.g. "Signal: -54 dBm" or "-65 dBm")
			else {
				result = result.replace(/(-?\d+(?:\.\d+)?)\s*dBm/gi, (match, valStr) => {
					const fVal = parseFloat(valStr);
					const val = Math.abs(fVal);
					let q = 'poor';
					if (fVal !== 0) {
						if (val <= 55) q = 'excellent';
						else if (val <= 67) q = 'good';
						else if (val <= 79) q = 'fair';
					}
					return `<span class="badge-colored badge-signal" data-quality="${q}">${match}</span>`;
				});
				modified = true;
			}
		}

		// 2. Data Rates (e.g. "866.7 Mbit/s", "1.2 Gbit/s", "54 Mbit/s")
		if (/\b\d+(?:\.\d+)?\s*(?:Gbit\/s|Mbit\/s|Kbit\/s|Gbps|Mbps|Kbps)\b/i.test(result)) {
			result = result.replace(/(\d+(?:\.\d+)?)\s*(Gbit\/s|Mbit\/s|Kbit\/s|Gbps|Mbps|Kbps)/gi, (match, numStr, unit) => {
				let num = parseFloat(numStr);
				const u = unit.toLowerCase();
				if (u.startsWith('g')) num *= 1000;
				else if (u.startsWith('k')) num /= 1000;

				let tier = 'low';
				if (num >= 1000) tier = 'gigabit';
				else if (num >= 100) tier = 'fast';
				else if (num >= 10) tier = 'normal';

				return `<span class="badge-colored badge-rate" data-speed="${tier}">${match}</span>`;
			});
			modified = true;
		}

		// 2b. Version strings (e.g. "25.12.6", "6.12.112").  Skipped when the
		//     text is an address, otherwise "192.168.1.1" would match.
		if (/\b\d+\.\d+\.\d+\b/.test(result) &&
		    !/\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\b/.test(result)) {
			result = result.replace(/\b\d+\.\d+\.\d+\b/g, match =>
				'<span class="badge-version">' + match + '</span>');
			modified = true;
		}

		// 3. Traffic direction indicators (▲ TX and ▼ RX)
		if (result.includes('▲') || result.includes('▼')) {
			result = result
				.replace(/▲\s*([^<,\n\r]+)/g, '<span class="badge-colored badge-traffic badge-tx"><span class="traffic-icon">▲</span> $1</span>')
				.replace(/▼\s*([^<,\n\r]+)/g, '<span class="badge-colored badge-traffic badge-rx"><span class="traffic-icon">▼</span> $1</span>');
			modified = true;
		}

		// 4. WiFi Frequency Bands (e.g. "5.180 GHz" or "2.437 GHz" or "6 GHz")
		if (!cpuFreqDone && /\b\d+(?:\.\d+)?\s*GHz\b/i.test(result)) {
			result = result.replace(/(\d+(?:\.\d+)?)\s*GHz/gi, (match, freqStr) => {
				const freq = parseFloat(freqStr);
				let band = '2g';
				if (freq >= 5.9) band = '6g';
				else if (freq >= 4.9) band = '5g';

				return `<span class="badge-colored badge-band" data-band="${band}">${match}</span>`;
			});
			modified = true;
		}

		// 5. Port Link Speeds (e.g. "1000baseT/Full", "100baseT/Full", "no link")
		if (/(?:1000base|2500base|100base|10gbase|no link)/i.test(result)) {
			result = result.replace(/\b([0-9]+base[A-Za-z0-9\/]+|no link)\b/gi, (match) => {
				let tier = 'low';
				if (/10000base|10gbase/i.test(match)) tier = 'gigabit';
				else if (/2500base|2\.5g/i.test(match)) tier = 'gigabit';
				else if (/1000base/i.test(match)) tier = 'gigabit';
				else if (/100base/i.test(match)) tier = 'fast';
				else if (/no link/i.test(match)) tier = 'down';

				return `<span class="badge-colored badge-link" data-link-speed="${tier}">${match}</span>`;
			});
			modified = true;
		}

		// 6. Uptime stability tag
		if (/^\s*\d+d\s+\d+h/i.test(result)) {
			result = `<span class="badge-colored badge-uptime" data-uptime="stable">${result}</span>`;
			modified = true;
		} else if (/^\s*\d+h\s+\d+m/i.test(result) && result.length < 24) {
			// A countdown such as a DHCP lease "11h 58m 12s" - not an uptime.
			result = `<span class="badge-colored badge-duration">${result}</span>`;
			modified = true;
		} else if (/^\s*(?:[0-5]?[0-9]m|[0-5]?[0-9]s)/i.test(result) && !/\d+h|\d+d/i.test(result) && result.length < 15) {
			result = `<span class="badge-colored badge-uptime" data-uptime="recent">${result}</span>`;
			modified = true;
		}

		// 7. System Thermal / Temperature (e.g. "48.5 °C", "52°C", "110°F")
		if (/\b\d+(?:\.\d+)?\s*(?:°C|℃|°F|℉)\b/i.test(result)) {
			result = result.replace(/(\d+(?:\.\d+)?)\s*(°C|℃|°F|℉)/gi, (match, valStr, unit) => {
				let tempC = parseFloat(valStr);
				if (unit.includes('F') || unit.includes('℉'))
					tempC = (tempC - 32) * 5 / 9;

				let tier = 'normal';
				if (tempC < 50) tier = 'cool';
				else if (tempC < 70) tier = 'normal';
				else if (tempC < 85) tier = 'hot';
				else tier = 'critical';

				return `<span class="badge-colored badge-thermal" data-temp="${tier}"><span class="thermal-icon">🌡️</span> ${match}</span>`;
			});
			modified = true;
		}

		// 8. IP Addresses (e.g. "192.168.8.1" or "10.0.0.1/24")
		if (/\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)(?:\/\d{1,2})?\b/.test(result)) {
			result = result.replace(/\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)(?:\/\d{1,2})?\b/g, (match) => {
				return `<span class="badge-colored badge-ip"><span class="ip-icon">🌐</span> ${match}</span>`;
			});
			modified = true;
		}

		// 9. MAC Addresses (e.g. "00:1A:2B:3C:4D:5E")
		if (/\b(?:[0-9A-Fa-f]{2}[:-]){5}(?:[0-9A-Fa-f]{2})\b/.test(result)) {
			result = result.replace(/\b(?:[0-9A-Fa-f]{2}[:-]){5}(?:[0-9A-Fa-f]{2})\b/g, (match) => {
				return `<span class="badge-colored badge-mac"><span class="mac-icon">🏷️</span> ${match}</span>`;
			});
			modified = true;
		}

		// 10. Operational Status Keywords (Only when exact short text)
		const trimmed = result.trim();
		if (/^(?:Connected|Associated|Running|Up|Online)$/i.test(trimmed)) {
			result = `<span class="badge-colored badge-status-good">${trimmed}</span>`;
			modified = true;
		} else if (/^(?:Disabled|Stopped|Down|Disconnected|Offline)$/i.test(trimmed)) {
			result = `<span class="badge-colored badge-status-bad">${trimmed}</span>`;
			modified = true;
		} else if (/^(?:Restarting|Pending|Reloading)$/i.test(trimmed)) {
			result = `<span class="badge-colored badge-status-warn">${trimmed}</span>`;
			modified = true;
		}

		return modified ? result : null;
	}
};

/**
 * Feelgood Theme Main Module
 */
return baseclass.extend({
	__init__() {
		this.oScroll = null;
		this.setupThemeSwitcher();
		ui.menu.load().then(this.render.bind(this));
	},

	setupThemeSwitcher() {
		// 1. Navigation Orientation (Horizontal vs Vertical) - Default: horizontal
		const currentNav = document.documentElement.getAttribute('data-theme-nav') ||
			localStorage.getItem('luci-theme-feelgood-nav') || 'horizontal';
		document.documentElement.setAttribute('data-theme-nav', currentNav);

		const navBtn = document.querySelector('#nav_layout_btn');
		if (navBtn) {
			const navLabel = navBtn.querySelector('.nav-label');
			const navIcon = navBtn.querySelector('.toggle-icon');
			const updateNavUI = (nav) => {
				if (navLabel) navLabel.textContent = nav === 'vertical' ? 'Vertical' : 'Horizontal';
				if (navIcon) navIcon.textContent = nav === 'vertical' ? '⬍' : '⬌';
				navBtn.setAttribute('title', nav === 'vertical' ? _('Switch to Horizontal Navigation') : _('Switch to Vertical Navigation'));
			};
			updateNavUI(currentNav);

			navBtn.addEventListener('click', (e) => {
				e.preventDefault();
				const oldNav = document.documentElement.getAttribute('data-theme-nav') || 'horizontal';
				const nextNav = oldNav === 'horizontal' ? 'vertical' : 'horizontal';
				document.documentElement.setAttribute('data-theme-nav', nextNav);
				localStorage.setItem('luci-theme-feelgood-nav', nextNav);
				updateNavUI(nextNav);
			});
		}

		// 2. Lock to Dark Theme
		document.documentElement.setAttribute('data-theme-color', 'dark');
		document.documentElement.setAttribute('data-theme-style', 'modern');
		localStorage.removeItem('luci-theme-feelgood-color');
	},

	render(tree) {
		var node = tree;
		var url = '';

		// 1. Hide loading overlay
		var loading = document.querySelector('.loading');
		if (loading) {
			loading.style.opacity = '0';
			loading.style.visibility = 'hidden';
		}

		// 2. Render mode menu (modemenu in footer)
		this.renderModeMenu(node);

		// 3. Render tab menu (if dispatch path >= 3)
		if (L.env.dispatchpath.length >= 3) {
			for (var i = 0; i < 3 && node; i++) {
				node = node.children[L.env.dispatchpath[i]];
				url = url + (url ? '/' : '') + L.env.dispatchpath[i];
			}

			if (node)
				this.renderTabMenu(node, url);
		}

		// 4. Attach sidebar toggle event listeners
		var showSide = document.querySelector('.showSide');
		var darkMask = document.querySelector('.darkMask');

		if (showSide)
			showSide.addEventListener('click', ui.createHandlerFn(this, 'handleSidebarToggle'));

		if (darkMask)
			darkMask.addEventListener('click', ui.createHandlerFn(this, 'handleSidebarToggle'));

		window.addEventListener('resize', ui.createHandlerFn(this, 'handleResize'), true);

		// 5. Setup password visibility toggle
		this.setupPasswordToggle();

		// 6. Update body classes for view styling
		this.updateBodyClasses();

		// 7. Initialize custom scrollbar on desktop
		this.initScrollBar();

		// 8. Safety net: never leave the page scroll locked by a stray drawer
		this._bindScrollLockGuard();

		// 9. Initialize Smart Colorizer
		Colorizer.init();
	},

	renderMainMenu(tree, url, level) {
		var currentLevel = (level || 0) + 1;
		var children = ui.menu.getChildren(tree);

		if (children.length === 0 || currentLevel > 2)
			return E([]);

		var ul = E('ul', { 'class': level ? 'slide-menu' : 'nav scroll' });

		for (var i = 0; i < children.length; i++) {
			var child = children[i];

			// Handle Logout node specifically
			if (currentLevel === 1 && (child.name === 'logout' || child.name === 'admin/logout')) {
				var logoutBtn = document.querySelector('#logout_btn');
				if (logoutBtn) {
					var logoutA = logoutBtn.querySelector('a');
					if (logoutA) {
						var logoutPath = child.name.indexOf('/') !== -1 ? child.name : (url ? url + '/' + child.name : 'admin/logout');
						logoutA.href = L.url(logoutPath);
						logoutA.textContent = _(child.title);
					}
					logoutBtn.style.display = 'inline-block';
				}
				continue;
			}

			var submenu = this.renderMainMenu(child, url + '/' + child.name, currentLevel);
			var hasChildren = submenu.children.length > 0;

			// If level 1 child has no children and resembles logout
			if (currentLevel === 1 && !hasChildren && child.name.indexOf('logout') !== -1) {
				var logoutBtn = document.querySelector('#logout_btn');
				if (logoutBtn) {
					var logoutA = logoutBtn.querySelector('a');
					if (logoutA) {
						logoutA.href = L.url(url ? url + '/' + child.name : 'admin/logout');
						logoutA.textContent = _(child.title);
					}
					logoutBtn.style.display = 'inline-block';
				}
				continue;
			}

			var isActive = (
				(L.env.dispatchpath[currentLevel] === child.name) &&
				(L.env.dispatchpath[currentLevel - 1] === tree.name)
			);

			if (hasChildren) {
				var liClass = 'slide' + (isActive ? ' active' : '');
				var aClass = 'menu' + (isActive ? ' active' : '');
				if (isActive)
					submenu.style.display = 'block';
				else
					submenu.style.display = 'none';

				var li = E('li', { 'class': liClass }, [
					E('a', {
						'class': aClass,
						'data-title': child.title.replace(/\s+/g, '_'),
						'href': '#',
						'click': ui.createHandlerFn(this, 'handleMenuExpand')
					}, [ _(child.title) ]),
					submenu
				]);
				ul.appendChild(li);
			} else {
				var liClass = isActive ? 'active' : '';
				var li = E('li', { 'class': liClass }, [
					E('a', {
						'data-title': child.title.replace(/\s+/g, '_'),
						'href': L.url(url, child.name),
						'click': function() {
							var loading = document.querySelector('.loading');
							if (loading) {
								loading.style.opacity = '1';
								loading.style.visibility = 'visible';
							}
							var dl = document.querySelector('.main-left');
							if (dl && (dl.classList.contains('open') || dl.style.width === '100%')) {
								var dm = document.querySelector('.darkMask');
								if (dm) dm.style.display = 'none';
								dl.style.width = '';
								dl.classList.remove('open');
								var mr = document.querySelector('.main-right');
								if (mr) mr.style.overflowY = '';
							}
						}
					}, [ _(child.title) ])
				]);
				ul.appendChild(li);
			}
		}

		if (currentLevel === 1) {
			var navBox = document.querySelector('#navBox');
			if (navBox) {
				navBox.innerHTML = '';
				navBox.appendChild(ul);
			}
		}

		return ul;
	},

	renderHorizontalMenu(tree, url) {
		var topNavBox = document.querySelector('#topNavBox');
		if (!topNavBox) return;

		var children = ui.menu.getChildren(tree);
		if (!children || children.length === 0) return;

		var ul = E('ul', { 'class': 'nav-horizontal' });

		for (var i = 0; i < children.length; i++) {
			var child = children[i];
			if (child.name === 'logout' || child.name === 'admin/logout')
				continue;

			var subChildren = ui.menu.getChildren(child);
			var hasChildren = subChildren.length > 0;
			var isTopActive = (L.env.dispatchpath && L.env.dispatchpath[1] === child.name);

			if (hasChildren) {
				var subUl = E('ul', { 'class': 'dropdown-menu' });
				for (var j = 0; j < subChildren.length; j++) {
					var subChild = subChildren[j];
					var isSubActive = isTopActive && (L.env.dispatchpath && L.env.dispatchpath[2] === subChild.name);
					var subLi = E('li', { 'class': 'dropdown-item' + (isSubActive ? ' active' : '') }, [
						E('a', {
							'href': L.url(url, child.name, subChild.name),
							'click': function() {
								var loading = document.querySelector('.loading');
								if (loading) {
									loading.style.opacity = '1';
									loading.style.visibility = 'visible';
								}
							}
						}, [ _(subChild.title) ])
					]);
					subUl.appendChild(subLi);
				}

				var topLi = E('li', {
					'class': 'nav-item has-dropdown' + (isTopActive ? ' active' : ''),
					'mouseenter': function() {
						var d = this.querySelector('.dropdown-menu');
						if (d) d.classList.add('show');
					},
					'mouseleave': function() {
						var d = this.querySelector('.dropdown-menu');
						if (d) d.classList.remove('show');
					}
				}, [
					E('a', {
						'class': 'nav-link' + (isTopActive ? ' active' : ''),
						'href': '#',
						'click': function(e) {
							e.preventDefault();
							e.stopPropagation();
							var d = this.parentNode.querySelector('.dropdown-menu');
							if (d) {
								var wasShow = d.classList.contains('show');
								var all = document.querySelectorAll('.nav-horizontal .dropdown-menu.show');
								for (var k = 0; k < all.length; k++) all[k].classList.remove('show');
								if (!wasShow) d.classList.add('show');
							}
						}
					}, [
						E('span', { 'class': 'nav-title' }, [ _(child.title) ]),
						E('span', { 'class': 'dropdown-caret' }, [ '▾' ])
					]),
					subUl
				]);
				ul.appendChild(topLi);
			} else {
				var isTopActive = (L.env.dispatchpath && L.env.dispatchpath[1] === child.name);
				var topLi = E('li', { 'class': 'nav-item' + (isTopActive ? ' active' : '') }, [
					E('a', {
						'class': 'nav-link' + (isTopActive ? ' active' : ''),
						'href': L.url(url, child.name),
						'click': function() {
							var loading = document.querySelector('.loading');
							if (loading) {
								loading.style.opacity = '1';
								loading.style.visibility = 'visible';
							}
							var dl = document.querySelector('.main-left');
							if (dl && (dl.classList.contains('open') || dl.style.width === '100%')) {
								var dm = document.querySelector('.darkMask');
								if (dm) dm.style.display = 'none';
								dl.style.width = '';
								dl.classList.remove('open');
								var mr = document.querySelector('.main-right');
								if (mr) mr.style.overflowY = '';
							}
						}
					}, [
						E('span', { 'class': 'nav-title' }, [ _(child.title) ])
					])
				]);
				ul.appendChild(topLi);
			}
		}

		topNavBox.innerHTML = '';
		topNavBox.appendChild(ul);

		if (!this._dropdownDismissBound) {
			this._dropdownDismissBound = true;
			document.addEventListener('click', function(e) {
				if (!e.target.closest('.nav-item.has-dropdown')) {
					var openDropdowns = document.querySelectorAll('.nav-horizontal .dropdown-menu.show');
					for (var k = 0; k < openDropdowns.length; k++)
						openDropdowns[k].classList.remove('show');
				}
			});
		}
	},

	renderModeMenu(tree) {
		var menu = document.querySelector('#modemenu');
		if (!menu) return;

		var children = ui.menu.getChildren(tree);
		for (var i = 0; i < children.length; i++) {
			var isActive = (L.env.requestpath.length ? children[i].name == L.env.requestpath[0] : i === 0);
			if (i > 0)
				menu.appendChild(E('span', { 'class': 'divider' }, [' | ']));

			menu.appendChild(E('li', { 'class': isActive ? 'active' : '' }, [
				E('a', { 'href': L.url(children[i].name) }, [ _(children[i].title) ])
			]));

			if (isActive) {
				this.renderMainMenu(children[i], children[i].name);
				this.renderHorizontalMenu(children[i], children[i].name);
			}
		}

		if (children.length > 1)
			menu.style.display = '';
	},

	renderTabMenu(tree, url, level) {
		var container = document.querySelector('#tabmenu');
		if (!container) return E([]);

		var currentLevel = (level || 0) + 1;
		var ul = E('ul', { 'class': 'tabs' });
		var children = ui.menu.getChildren(tree);
		var activeNode = null;

		if (children.length === 0)
			return E([]);

		for (var i = 0; i < children.length; i++) {
			var child = children[i];
			var isActive = (L.env.dispatchpath[currentLevel + 2] === child.name);
			var activeClass = isActive ? ' active' : '';
			var className = 'tabmenu-item-' + child.name + activeClass;

			ul.appendChild(E('li', { 'class': className }, [
				E('a', { 'href': L.url(url, child.name) }, [ _(child.title) ])
			]));

			if (isActive)
				activeNode = child;
		}

		container.appendChild(ul);
		container.style.display = '';

		if (activeNode)
			this.renderTabMenu(activeNode, url + '/' + activeNode.name, currentLevel);

		return ul;
	},

	handleMenuExpand(ev) {
		var a = ev.currentTarget;
		var slide = a.parentNode;
		var slideMenu = a.nextElementSibling;
		if (!slideMenu) return;

		var isOpen = slideMenu.style.display === 'block' || a.classList.contains('active');

		// Close other open submenus
		var allActiveMenus = document.querySelectorAll('.main-left .nav > .slide > .slide-menu');
		for (var i = 0; i < allActiveMenus.length; i++) {
			var menu = allActiveMenus[i];
			if (menu !== slideMenu) {
				SlideAnimations.stop(menu);
				SlideAnimations.slideUp(menu, 'fast');
				menu.classList.remove('active');
				if (menu.previousElementSibling)
					menu.previousElementSibling.classList.remove('active');
				if (menu.parentNode)
					menu.parentNode.classList.remove('active');
			}
		}

		if (isOpen) {
			SlideAnimations.slideUp(slideMenu, 'fast', () => {
				a.classList.remove('active');
				slideMenu.classList.remove('active');
				slide.classList.remove('active');
			});
		} else {
			a.classList.add('active');
			slideMenu.classList.add('active');
			slide.classList.add('active');
			SlideAnimations.slideDown(slideMenu, 'fast');
		}

		if (this.oScroll)
			this.oScroll.setSize(200);

		// On mobile the menu is an overlay drawer. Tapping a category there must
		// dismiss the drawer (and jump to its first sub-page) so the content
		// behind becomes reachable and scrollable again; otherwise the page
		// stays locked behind the dark mask.
		var drawerLeft = document.querySelector('.main-left');
		var drawerOpen = drawerLeft && (drawerLeft.classList.contains('open') || drawerLeft.style.width === '100%');
		if (drawerOpen) {
			var firstChild = slideMenu.querySelector('a');
			if (firstChild && firstChild.getAttribute('href') && firstChild.getAttribute('href') !== '#') {
				this._closeDrawer();
				ev.preventDefault();
				ev.stopPropagation();
				window.location = firstChild.getAttribute('href');
				return;
			}
			this._closeDrawer();
		}

		ev.preventDefault();
		ev.stopPropagation();
	},

	handleSidebarToggle(ev) {
		var mainLeft = document.querySelector('.main-left');
		var darkMask = document.querySelector('.darkMask');
		var mainRight = document.querySelector('.main-right');
		if (!mainLeft) return;

		var isOpen = mainLeft.classList.contains('open') || mainLeft.style.width === '100%';

		if (isOpen) {
			if (darkMask) darkMask.style.display = 'none';
			mainLeft.style.width = '0';
			mainLeft.classList.remove('open');
			if (mainRight) mainRight.style.overflowY = '';
		} else {
			if (darkMask) darkMask.style.display = 'block';
			mainLeft.style.width = '100%';
			mainLeft.classList.add('open');
			if (mainRight) mainRight.style.overflowY = 'hidden';
		}

		ev.preventDefault();
		ev.stopPropagation();
	},

	_closeDrawer() {
		var mainLeft = document.querySelector('.main-left');
		var darkMask = document.querySelector('.darkMask');
		var mainRight = document.querySelector('.main-right');
		if (mainLeft) {
			mainLeft.style.width = '';
			mainLeft.classList.remove('open');
		}
		if (darkMask) darkMask.style.display = 'none';
		if (mainRight) mainRight.style.overflowY = '';
	},

		handleResize(ev) {
		if (window.innerWidth > 992) {
			this._closeDrawer();
		}
	},

	_bindScrollLockGuard() {
		if (this._scrollLockBound) return;
		this._scrollLockBound = true;

		// The mobile drawer (handleSidebarToggle) sets .main-right overflow:hidden to
		// freeze page scroll while the drawer is open. If the user navigates by
		// clicking a link inside the drawer, that lock is never cleared and the
		// page can no longer scroll. Release it on any real navigation and
		// defensively on every page load.
		var release = function() {
			var mr = document.querySelector('.main-right');
			if (mr && mr.style.overflowY === 'hidden') mr.style.overflowY = '';
		};

		document.addEventListener('click', function(e) {
			var t = e.target;
			while (t && t !== document) {
				if (t.tagName === 'A' && t.getAttribute('href') && t.getAttribute('href') !== '#') {
					release();
					break;
				}
				t = t.parentNode;
			}
		});

		window.addEventListener('load', release);
		release();
	},

	setupPasswordToggle() {
		var eyeBtns = document.querySelectorAll('.pwd-eye-icon, .login > .main .cbi-value-last .cbi-value-field > div');
		if (eyeBtns.length === 0) {
			var pwdField = document.querySelector('.login input[name="luci_password"]');
			if (pwdField && pwdField.parentNode && !pwdField.parentNode.querySelector('.pwd-eye-icon')) {
				var eye = E('div', { 'class': 'pwd-eye-icon' });
				pwdField.parentNode.appendChild(eye);
				eyeBtns = [eye];
			}
		}

		for (var i = 0; i < eyeBtns.length; i++) {
			var btn = eyeBtns[i];
			btn.addEventListener('click', function(e) {
				var parent = this.parentNode;
				var input = parent.querySelector('input[type="password"], input[type="text"]');
				if (!input) return;

				if (parent.classList.contains('op-eye')) {
					parent.classList.remove('op-eye');
					input.type = 'password';
				} else {
					parent.classList.add('op-eye');
					input.type = 'text';
				}
			});
		}
	},

	updateBodyClasses() {
		var path = L.env.dispatchpath;
		if (!path || !path.length) return;

		// Clear previous dynamic node- classes
		var classes = document.body.className.split(/\s+/);
		for (var i = 0; i < classes.length; i++) {
			if (classes[i].indexOf('node-') === 0 && classes[i] !== 'node-main-login') {
				document.body.classList.remove(classes[i]);
			}
		}

		if (path[0] === 'admin' && path.length > 2) {
			document.body.classList.add('node-' + path[1] + '-' + path[2]);
		}
		document.body.classList.add('node-' + path.join('-'));
	},

	initScrollBar() {
		if (typeof window.MyScrollBar === 'function' && window.innerWidth > 992) {
			var navBox = document.getElementById('navBox');
			if (navBox && navBox.querySelector('.nav')) {
				try {
					this.oScroll = new window.MyScrollBar({
						selId: 'navBox',
						time: 100,
						bgColor: 'transparent',
						barColor: 'rgba(131, 157, 214, 0.48)',
						enterColor: 'rgba(131, 157, 214, 0.8)',
						enterShow: false
					});
				} catch (e) {
					console.warn('MyScrollBar init failed:', e);
				}
			}
		}
	}
});
