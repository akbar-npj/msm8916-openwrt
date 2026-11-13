'use strict';
'require view';
'require form';
'require uci';
'require ui';
'require fs';

function good(text) {
	return E('strong', { 'style': 'color:#2e7d32' }, text);
}

function bad(text) {
	return E('strong', { 'style': 'color:#c62828' }, text);
}

function dim(text) {
	return E('span', { 'style': 'color:#888' }, text);
}

function field(label, value) {
	return E('div', { 'class': 'cbi-value' }, [
		E('div', { 'class': 'cbi-value-title' }, label),
		E('div', { 'class': 'cbi-value-field' }, [ value ])
	]);
}

return view.extend({
	load: function() {
		return Promise.all([
			uci.load('ufivoice'),
			L.resolveDefault(fs.exec('/usr/sbin/ufi-voice-status', []), null)
		]);
	},

	render: function(data) {
		var st = null;

		try {
			if (data[1] && data[1].stdout)
				st = JSON.parse(data[1].stdout);
		}
		catch (e) {
			st = null;
		}

		var m, s, o;

		m = new form.Map('ufivoice', _('Phone'),
			_('Calls are carried by the mobile network (VoLTE) and handed to a Bluetooth ' +
			  'headset and/or SIP phones on this device. This page configures the SIP ' +
			  'accounts, where calls go, and the audio path. Changes take effect as soon ' +
			  'as they are applied; a call in progress is not interrupted.'));

		/* ---------------------------------------------------------------- */
		/* Status                                                           */
		/* ---------------------------------------------------------------- */

		s = m.section(form.NamedSection, 'status', '_status');
		s.render = function() {
			var rows = [];

			if (st == null) {
				rows.push(field(_('Status helper'), bad(_('not available'))));
			}
			else {
				var mstate = st.modem_state || 'unknown';
				var modem_up = (mstate === 'registered' || mstate === 'connected');
				var modem = modem_up ? good(mstate) : bad(mstate);

				if (st.modem_operator)
					modem = E('span', {}, [
						modem, ' \u2014 ', st.modem_operator,
						st.modem_signal ? ' (' + st.modem_signal + '%)' : '',
						st.modem_tech ? ' \u00b7 ' + st.modem_tech : ''
					]);

				rows.push(field(_('Modem'), modem));
				rows.push(field(_('IMS registration'),
					(st.ims === 'registered') ? good(st.ims) : bad(st.ims)));

				var svcs = [];

				[
					['ufi_voiced', _('Call daemon')],
					['imsdcm', _('IMS data service')],
					['asterisk', 'Asterisk'],
					['hfp_ag', _('Bluetooth HFP')],
					['voicebridge', _('Audio bridge')]
				].forEach(function(p) {
					svcs.push(E('span', { 'style': 'margin-right:1.2em;white-space:nowrap' }, [
						p[1] + ': ',
						(st[p[0]] === 'running') ? good(_('running')) : dim(_('stopped'))
					]));
				});

				rows.push(field(_('Services'), E('span', {}, svcs)));
				rows.push(field(_('Active calls'), String(st.calls || 0)));
			}

			return E('div', { 'class': 'cbi-section' }, [
				E('h3', {}, _('Status')),
				E('div', { 'class': 'cbi-section-node' }, rows)
			]);
		};

		/* ---------------------------------------------------------------- */
		/* Service                                                          */
		/* ---------------------------------------------------------------- */

		s = m.section(form.NamedSection, 'main', 'ufivoice', _('Service'));

		o = s.option(form.Flag, 'volte', _('Run the IMS data service'),
			_('Lets the modem register for VoLTE. Without it the modem has no IMS ' +
			  'data connection and calls fall back to the circuit-switched network ' +
			  '(which this device does not have on 4G).'));
		o.default = '1';
		o.rmempty = false;

		o = s.option(form.ListValue, 'mode', _('Where calls go'));
		o.value('both', _('Bluetooth headset and SIP phones'));
		o.value('bt', _('Bluetooth headset only'));
		o.value('asterisk', _('SIP phones only'));
		o.value('none', _('Answer on the device, no audio routing'));
		o.default = 'both';
		o.rmempty = false;

		o = s.option(form.Value, 'autoanswer', _('Auto-answer after (seconds)'),
			_('0 never answers by itself.'));
		o.datatype = 'range(0,60)';
		o.placeholder = '0';

		o = s.option(form.Flag, 'manage_answer', _('Manage auto-answer'),
			_('Stops the ModemManager auto-answer service while this daemon runs, ' +
			  'so the two cannot both answer a call.'));

		/* ---------------------------------------------------------------- */
		/* SIP accounts                                                     */
		/* ---------------------------------------------------------------- */

		s = m.section(form.GridSection, 'phone', _('SIP accounts'),
			_('Phones that register to Asterisk on this device. The dialplan sends ' +
			  'every call to the mobile network, and incoming mobile calls ring all ' +
			  'accounts at once. Leave the password empty to have one generated.'));
		s.anonymous = true;
		s.addremove = true;

		o = s.option(form.Value, 'number', _('Number'));
		o.datatype = 'rangelength(2,20)';
		o.placeholder = '100';
		o.rmempty = false;

		o = s.option(form.Value, 'password', _('Password'));
		o.placeholder = _('generated');
		o.description = _('Also written to /etc/asterisk/ufi-voice-phones.txt on the device.');

		o = s.option(form.DummyValue, '_registered', _('Registered'));
		o.rawhtml = true;
		o.cfgvalue = function(section_id) {
			var num = uci.get('ufivoice', section_id, 'number');
			var list = (st && st.sip) ? st.sip : [];

			for (var i = 0; i < list.length; i++) {
				if (list[i].number !== num)
					continue;

				if (list[i].status === 'down')
					return '<span style="color:#c62828">' + _('no') + '</span>';

				return '<span style="color:#2e7d32">' + _('yes') + '</span>' +
					(list[i].host ? ' <span style="color:#888">' + list[i].host + '</span>' : '');
			}

			return '<span style="color:#888">\u2014</span>';
		};

		/* ---------------------------------------------------------------- */
		/* Audio                                                            */
		/* ---------------------------------------------------------------- */

		s = m.section(form.NamedSection, 'main', 'ufivoice', _('Audio'));

		o = s.option(form.Value, 'jitter_ms', _('Jitter buffer (ms)'),
			_('Audio from the phones is buffered this long before it is played into ' +
			  'the call. Raise it if the far end hears choppy speech over Wi-Fi; ' +
			  'lower it to reduce delay. 40 ms suits a wired or good Wi-Fi link.'));
		o.datatype = 'range(20,500)';
		o.placeholder = '40';

		o = s.option(form.ListValue, 'pcm_rate', _('PCM rate'),
			_('Sample rate of the in-call audio device. 48 kHz gives 10 ms periods, ' +
			  'which is what the DSP expects.'));
		o.value('48000', '48000 Hz');
		o.value('16000', '16000 Hz');
		o.default = '48000';

		o = s.option(form.Flag, 'host_tx_mute', _('Mute the unused microphone port'),
			_('Keeps the internal Bluetooth SCO microphone out of the call, so only ' +
			  'the phones supply uplink audio.'));

		o = s.option(form.Flag, 'bridge_debug', _('Log audio statistics'),
			_('Writes a voicebridge statistics line to the system log every 5 seconds. ' +
			  'Useful when diagnosing choppy audio; leave off otherwise.'));

		/* ---------------------------------------------------------------- */
		/* Bluetooth                                                        */
		/* ---------------------------------------------------------------- */

		s = m.section(form.NamedSection, 'main', 'ufivoice', _('Bluetooth headset'));

		o = s.option(form.Value, 'bt_headset', _('Headset address'),
			_('MAC address of the headset to connect to, e.g. 00:11:22:33:44:55. ' +
			  'Pair it first with bluetoothctl.'));
		o.placeholder = '00:11:22:33:44:55';

		o = s.option(form.Value, 'bt_reconnect_max', _('Reconnect backoff (seconds)'),
			_('How long to keep paging a disconnected headset, doubling from 60 s. ' +
			  'Each page briefly blinds the Wi-Fi station (shared 2.4 GHz radio). ' +
			  '0 never pages.'));
		o.datatype = 'range(0,3600)';
		o.placeholder = '900';

		/* ---------------------------------------------------------------- */
		/* Advanced                                                         */
		/* ---------------------------------------------------------------- */

		s = m.section(form.NamedSection, 'main', 'ufivoice', _('Advanced'));

		o = s.option(form.ListValue, 'session', _('Voice session'),
			_('DSP voice path used for call audio. Auto picks VoLTE while IMS is ' +
			  'registered and the circuit-switched session otherwise.'));
		o.value('auto', _('Auto'));
		o.value('volte', _('VoLTE'));
		o.value('cs', _('Circuit switched'));
		o.default = 'auto';

		o = s.option(form.Value, 'audiosocket_port', _('AudioSocket port'));
		o.datatype = 'port';
		o.placeholder = '9092';

		o = s.option(form.Value, 'ami_user', _('Asterisk AMI user'));
		o.placeholder = 'ufivoice';

		o = s.option(form.Value, 'ami_secret', _('Asterisk AMI password'),
			_('Left empty, a random password is generated and shared with Asterisk.'));
		o.password = true;

		o = s.option(form.Value, 'wan_iface', _('Mobile interface'),
			_('netifd interface of the mobile data connection.'));
		o.placeholder = 'modem';

		o = s.option(form.Value, 'ims_kick_after', _('Restart IMS after (seconds)'),
			_('Restarts the IMS data service when IMS has had no connection for this ' +
			  'long, doubling up to one hour while that does not help. 0 never.'));
		o.datatype = 'range(0,3600)';
		o.placeholder = '180';

		/* ---------------------------------------------------------------- */
		/* Apply                                                            */
		/* ---------------------------------------------------------------- */

		/* The JS form API has no on_after_commit hook, so the page footer
		 * button is overridden: save, commit, regenerate the Asterisk
		 * configuration from the new accounts, then reload the page.
		 *
		 * The commit is a "checked" apply, which rolls back unless it is
		 * confirmed within a few seconds; confirm it before reloading, or
		 * the page reload would cancel the rollback timer's confirmation
		 * and the changes would be reverted behind the user's back. */
		this.handleSaveApply = function(ev, mode) {
			return this.handleSave(ev).then(function() {
				return uci.apply();
			}).then(function() {
				return new Promise(function(resolve) {
					window.setTimeout(resolve, 2000);
				});
			}).then(function() {
				return L.resolveDefault(uci.callConfirm(), null);
			}).then(function() {
				return fs.exec('/usr/sbin/ufi-voice-apply', []);
			}).then(function() {
				window.location.reload();
			}).catch(function(err) {
				ui.addNotification(null,
					E('p', _('Saved, but applying failed: ') + (err || '')),
					'danger');
			});
		};

		return m.render();
	}
});
