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

        o = s.option(form.Flag, 'mobile_optimized', _('Mobile mode (in-vehicle)'));
        o.description = _('Enable the 30s/5s stall-detection profile for moving '
            + 'deployments (car, travel router). Each cell handover can otherwise '
            + 'cause up to a minute of "no internet". '
            + 'Leave OFF for stationary use — the 60s default is Android-parity.');
        o.default = '0';
        o.rmempty = false;

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

        // auto-restart after apply
        m.on_after_commit = function(map) {
            return fs.exec('/etc/init.d/modem-bearer-watchdog', ['restart']).catch(function() {
                ui.addNotification(null, E('p', _('Watchdog restart requested; check status if anything looks wrong.')), 'warning');
            });
        };

        return m.render();
    }
});
