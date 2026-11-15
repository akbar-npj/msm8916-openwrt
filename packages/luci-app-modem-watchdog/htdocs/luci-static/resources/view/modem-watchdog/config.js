'use strict';
'require view';
'require form';
'require uci';
'require ui';
'require fs';

return view.extend({
    load: function() {
        return Promise.all([
            uci.load('modem-watchdog'),
            L.resolveDefault(fs.exec('/etc/init.d/modem-bearer-watchdog', ['status']), null)
        ]);
    },

    render: function(data) {
        var watchdog_status = data[1] ? data[1].stdout.trim() : 'unknown';
        var m, s, o;

        m = new form.Map('modem-watchdog',
            _('Modem Watchdog'),
            _('Detects stalled LTE bearers and recovers the interface automatically. '
              + 'Changes take effect after saving and restarting the daemon.'));

        // =================================================================
        // Status banner
        // =================================================================
        var statusText = _('Daemon status') + ': ';
        if (watchdog_status === 'running') {
            statusText += '<strong style="color:green">' + _('Running') + '</strong>';
        } else {
            statusText += '<strong style="color:red">' + _('Stopped / not installed') + '</strong>';
        }

        var status_section = m.section(form.NamedSection, 'status', '_status');
        s = status_section;
        s.render = function() {
            return E('div', { 'class': 'cbi-section' }, [
                E('h3', {}, _('Current Status')),
                E('div', { 'class': 'cbi-value' }, [
                    E('div', { 'class': 'cbi-value-title' }, _('Service')),
                    E('div', { 'class': 'cbi-value-field' }, statusText)
                ])
            ]);
        };

        // =================================================================
        // Core watchdog options (general)
        // =================================================================
        // UCI: config watchdog 'general'  -> TypedSection maps by TYPE ('watchdog')
        s = m.section(form.TypedSection, 'watchdog', _('Watchdog Behaviour'));
        s.anonymous = false;
        s.addremove = false;

        o = s.option(form.Flag, 'enabled', _('Enable watchdog'));
        o.description = _('Turn the stall-detection watchdog on or off entirely.');
        o.default = '1';
        o.rmempty = false;

        o = s.option(form.Flag, 'auto_mobile', _('Auto mobile (detect movement)'));
        o.description = _('Automatically switch to the 30s/5s handover-safe profile '
            + 'when the serving cell changes (the device is moving), and revert to '
            + 'the stationary profile after a period of stillness. Boots into the '
            + 'stationary profile, so a parked device is unaffected. ON by default.');
        o.default = '1';
        o.rmempty = false;

        o = s.option(form.Flag, 'mobile_optimized', _('Mobile mode (force, manual)'));
        o.description = _('Force the 30s/5s stall-detection profile at all times '
            + '(car, travel router) instead of auto-detecting movement. Only shown '
            + 'when Auto mobile is OFF; with Auto mobile ON this is unnecessary.');
        o.default = '0';
        o.rmempty = false;
        o.depends('auto_mobile', '0');

        o = s.option(form.Value, 'mobile_grace_secs', _('Mobile grace (seconds)'));
        o.datatype = 'range(30,1800)';
        o.placeholder = '180';
        o.depends('auto_mobile', '1');
        o.description = _('Keep the mobile profile this long after the last detected '
            + 'cell change before reverting to stationary.');

        o = s.option(form.Value, 'cell_poll_secs', _('Cell poll (seconds)'));
        o.datatype = 'range(5,120)';
        o.placeholder = '15';
        o.depends('auto_mobile', '1');
        o.description = _('How often the watchdog samples the serving cell over '
            + 'ModemManager D-Bus to detect movement. Read-only; never wedges the modem.');

        o = s.option(form.Value, 'stall_timeout', _('Stall timeout (seconds)'));
        o.datatype = 'range(30,600)';
        o.placeholder = '60';
        o.description = _('How long the bearer is quiet before the watchdog '
            + 'declares a stall.  Range 30–600; the manual_override config '
            + 'can set values below 30 but the script will clamp to 30.');
        o.depends('mobile_optimized', '0');

        o = s.option(form.Value, 'check_interval', _('Check interval (seconds)'));
        o.datatype = 'range(5,60)';
        o.placeholder = '10';
        o.description = _('How often the watchdog checks RX counters.');
        o.depends('mobile_optimized', '0');

        o = s.option(form.Value, 'cooldown_seconds', _('Recovery cooldown (seconds)'));
        o.datatype = 'range(30,3600)';
        o.placeholder = '60';
        o.description = _('Minimum gap between consecutive recovery attempts, '
            + 'so a flaky tower cannot flap the interface back-to-back.');

        // =================================================================
        // Keepalive (optional, usually left off)
        // =================================================================
        // UCI: config keepalive 'keepalive'
        s = m.section(form.TypedSection, 'keepalive', _('Keepalive (normally off)'));
        s.anonymous = false;
        s.addremove = false;

        o = s.option(form.Flag, 'enabled', _('DNS keepalive'));
        o.description = _('Sends a tiny DNS query every few minutes while idle '
            + 'to keep the modem’s RRC layer in RRC_CONNECTED.  Stock Android '
            + 'does NOT do this — leave disabled unless you have a specific '
            + 'network that drops silent bearers.');
        o.default = '0';
        o.rmempty = false;

        o = s.option(form.Value, 'interval', _('Keepalive interval (seconds)'));
        o.datatype = 'range(30,3600)';
        o.placeholder = '60';
        o.depends('enabled', '1');

        o = s.option(form.Value, 'target', _('Keepalive target'));
        o.placeholder = 'openwrt.org';
        o.depends('enabled', '1');
        o.description = _('Hostname that get a DNS lookup each interval.');

        // =================================================================
        // Recovery escalation
        // =================================================================
        // UCI: config recovery 'recovery'
        s = m.section(form.TypedSection, 'recovery', _('Recovery escalation'));
        s.anonymous = false;
        s.addremove = false;

        o = s.option(form.Flag, 'ssr_enabled', _('Allow SSR restart (modem subsystem reset)'));
        o.description = _('Stage-3 ladder: restart the modem remoteproc. '
            + 'Android’s stock bam_dmux calls subsystem_restart("modem") on the '
            + 'same A2 timeout sites, so this is parity.  Disable ONLY if you '
            + 'want the modem state frozen for post-mortem analysis.');
        o.default = '1';
        o.rmempty = false;

        o = s.option(form.Value, 'bearer_down_recover_secs',
                     _('Bearer-down redrive (seconds)'));
        o.datatype = 'range(5,600)';
        o.placeholder = '30';
        o.description = _('A netifd "down" interface still down this long after a '
            + 'rebuild is stuck — the watchdog retriggers `ifup` and re-arms the proto.');

        o = s.option(form.Value, 'zombie_nudge_secs',
                     _('Zombie-modem nudge (seconds)'));
        o.datatype = 'range(0,300)';
        o.placeholder = '60';
        o.description = _('After a modem crash the baseband can land in a '
            + 'low-power zombie state (no modem object, no registration). '
            + 'This many seconds without a modem object triggers one '
            + 'qmicli --dms-set-operating-mode=online poke.  Set to 0 to disable.');

        o = s.option(form.Flag, 'voice_alive', _('Voice liveness hold'));
        o.description = _('When the data bearer has no downlink, skip the recovery '
            + 'ladder (no interface rebuild, no SSR) while a call is in progress, or, '
            + 'for a SIM without data service, while the modem is alive for voice '
            + '(IMS registered, or registered with live serving-cell measurements). '
            + 'Restarting the modem cannot bring data to a voice-only SIM.');
        o.default = '0';
        o.rmempty = false;

        o = s.option(form.ListValue, 'data_plan', _('SIM data service'));
        o.value('auto', _('Auto (no downlink with this SIM since boot = no data service)'));
        o.value('1', _('Data SIM (only a call holds the ladder)'));
        o.value('0', _('Voice-only SIM'));
        o.default = '1';
        o.depends('voice_alive', '1');
        o.description = _('Auto tracks the SIM by ICCID: a SIM swap starts over, a '
            + 'modem restart does not. Any downlink packet ends the hold.');

        o = s.option(form.Value, 'voice_hold_secs', _('Voice hold recheck (seconds)'));
        o.datatype = 'range(60,3600)';
        o.default = '300';
        o.placeholder = '300';
        o.depends('voice_alive', '1');
        o.description = _('How long a voice hold lasts before the watchdog checks '
            + 'again whether the modem is still alive for voice.');

        o = s.option(form.Flag, 'drop_default_route', _('Drop modem default route during hold'));
        o.description = _('While a SIM without data service is held, remove the '
            + 'modem\'s default routes so traffic never falls back to the empty '
            + 'bearer (e.g. while the Wi-Fi station reconnects). Only while another '
            + 'uplink has a default route or had one in the last 5 minutes; with the '
            + 'modem as the only uplink the routes stay. They are restored when the '
            + 'hold ends. A data SIM keeps its routes.');
        o.default = '0';
        o.rmempty = false;
        o.depends('voice_alive', '1');

        // auto-restart after apply
        m.on_after_commit = function(map) {
            return fs.exec('/etc/init.d/modem-bearer-watchdog', ['restart']).catch(function() {
                ui.addNotification(null, E('p', _('Watchdog restart requested; check status if anything looks wrong.')), 'warning');
            });
        };

        return m.render();
    }
});
