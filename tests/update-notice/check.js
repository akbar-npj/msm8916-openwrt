'use strict';

/*
 * Drives 99_update-notice.js outside LuCI.
 *
 * The include is a plain CommonJS-style module: a few `'require x';` string
 * statements (no-ops in node) and a top-level `return baseclass.extend({...})`.
 * Wrapping the source in a function gives us the object, and the stubs below
 * stand in for LuCI's globals.  Nothing here touches a device.
 *
 *   node tests/update-notice/check.js [--verbose]
 *
 * Exit 0 when every assertion passes.
 */

const fs = require('fs');
const path = require('path');

const SRC = process.env.UPDATE_NOTICE_JS ||
	path.join(__dirname, '..', '..', 'packages', 'luci-app-update-notify',
		'htdocs', 'luci-static', 'resources', 'view', 'status', 'include',
		'99_update-notice.js');

let pass = 0, fail = 0;

function ok(msg)  { pass++; console.log('  ok   ' + msg); }
function bad(msg, why) { fail++; console.log('  FAIL ' + msg + (why ? '\n       ' + why : '')); }

function check(cond, msg, why) { cond ? ok(msg) : bad(msg, why); }

/* --- stubs ----------------------------------------------------------------- */

function load(state) {
	let source = fs.readFileSync(SRC, 'utf8');

	const uciValues = state.uci || {};
	const calls = { set: [], save: 0, apply: 0 };

	const baseclass = {
		extend: function(proto) { return proto; }
	};

	const uci = {
		load: function() { return Promise.resolve([ 'update-notify' ]); },
		get: function(conf, sec, opt) {
			const v = uciValues[conf + '.' + sec + '.' + opt];
			return (v === undefined) ? null : v;
		},
		set: function(conf, sec, opt, val) {
			/* Stages the change, like LuCI: uci.get() keeps returning the old
			 * value until save()/apply() and a reload. */
			calls.set.push(conf + '.' + sec + '.' + opt + '=' + val);
		},
		save: function() { calls.save++; return Promise.resolve([]); },
		apply: function() { calls.apply++; return Promise.resolve(0); },
		unload: function() {}
	};

	const ctx = {
		baseclass: baseclass,
		uci: uci,
		fs: {
			read: function() {
				return Promise.resolve(state.json === undefined ? '' : state.json);
			}
		},
		L: {
			resolveDefault: function(p, d) {
				return Promise.resolve(p).catch(function() { return d; });
			},
			url: function(u) { return '/' + u; },
			bind: function(fn, self) { return fn.bind(self); }
		},
		E: function(tag, attrs, kids) {
			return { tag: tag, attrs: attrs || {}, kids: kids || [], style: {} };
		},
		_: function(s) { return s; },
		ui: { addNotification: function() {} },
		document: { querySelector: function() { return null; } },
		Promise: Promise,
		Math: Math,
		JSON: JSON,
		isNaN: isNaN,
		parseInt: parseInt,
		String: String,
		Date: Date
	};

	const factory = new Function(
		'baseclass', 'uci', 'fs', 'L', 'E', '_', 'ui', 'document',
		source
	);

	const proto = factory(ctx.baseclass, ctx.uci, ctx.fs, ctx.L, ctx.E,
		ctx._, ctx.ui, ctx.document);

	return { proto: proto, calls: calls, uciValues: uciValues };
}

function instance(state) {
	const env = load(state);
	const inst = Object.create(env.proto);
	inst.node = null;
	return { inst: inst, calls: env.calls, uciValues: env.uciValues };
}

function json(count, checked) {
	return JSON.stringify({ count: count, checked: checked || 1700000000 });
}

function renders(state, count, dismissed) {
	const uci = {};
	if (dismissed !== undefined)
		uci['update-notify.settings.dismissed_count'] = String(dismissed);

	const env = instance({ json: json(count), uci: uci });
	return env.inst.render(json(count)) !== null;
}

/* --- assertions ------------------------------------------------------------ */

console.log('== update-notice ==');

check(renders({}, 42) === true, 'an undismissed count renders the banner');
check(renders({}, 42, 0) === true, 'dismissed_count 0 renders the banner');
check(renders({}, 42, 42) === false, 'the same count is dismissed');
check(renders({}, 45, 42) === true, 'a larger count speaks up again');
check(renders({}, 42, 50) === false, 'a smaller count stays dismissed');
check(renders({}, 0, 0) === false, 'a zero count renders nothing');
check(renders({}, 42, 'not-a-number') === true, 'a garbage count is treated as 0');

/* The dismiss handler must hide the banner on the same page load: uci.load()
 * caches, so waiting for a reload would bring it back on the next 5 s poll. */
(function() {
	const env = instance({
		json: json(42),
		uci: { 'update-notify.settings.dismissed_count': '0' }
	});
	const inst = env.inst;

	inst.render(json(42));
	check(inst.node !== null, 'the banner is built before dismissing');

	const ev = { preventDefault: function() {} };

	return inst.handleDismiss(ev).then(function() {
		check(env.calls.set.length === 1,
			'the dismissed count is written once',
			JSON.stringify(env.calls.set));
		check(env.calls.set[0] === 'update-notify.settings.dismissed_count=42',
			'the count on screen is the one stored', env.calls.set[0]);
		check(env.calls.save === 1 && env.calls.apply === 1,
			'the change is saved and committed',
			'save=' + env.calls.save + ' apply=' + env.calls.apply);

		/* Same cached uci value, no reload: the local override must win. */
		check(inst.render(json(42)) === null,
			'the banner stays hidden before uci is reloaded');

		console.log('');
		console.log(pass + ' passed, ' + fail + ' failed');
		process.exit(fail === 0 ? 0 : 1);
	}).catch(function(e) {
		bad('the dismiss handler resolves', String(e));
		console.log('');
		console.log(pass + ' passed, ' + fail + ' failed');
		process.exit(1);
	});
})();
