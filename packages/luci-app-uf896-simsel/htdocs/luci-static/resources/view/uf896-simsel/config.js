'use strict';
'require view';
'require form';
'require uci';
'require ui';
'require fs';

return view.extend({
    load: function() {
        return Promise.all([
            uci.load('uf896-simsel'),
            L.resolveDefault(fs.exec('/usr/sbin/uf896-simsel', ['status']), null)
        ]);
    },

    render: function(data) {
        var status_raw = data[1] ? data[1].stdout : '';
        var st = {};
        status_raw.split(String.fromCharCode(10)).forEach(function(line) {
            var i = line.indexOf('=');
            if (i > 0) { st[line.substring(0, i)] = line.substring(i + 1); }
        });

        var modemState = st.state || 'unknown';
        var m, s, o;

        m = new form.Map('uf896-simsel',
            _('UF896 SIM Slot'),
            _('Selects the UF896 physical SIM through the modem AT command ' +
              'AT+SIMSEL and shows live modem status. The auto re-apply option ' +
              're-sends the command whenever the modem reconnects after a drop.'));

        var statusText = _('Modem') + ': ';
        if (modemState === 'registered' || modemState === 'connected' || modemState === 'connecting') {
            statusText += '<strong style="color:green">' + _('Up') + ' (' + modemState + ')</strong>';
        } else {
            statusText += '<strong style="color:red">' + _('Down') + ' (' + modemState + ')</strong>';
        }

        var status_section = m.section(form.NamedSection, 'status', '_status');
        s = status_section;
        s.render = function() {
            return E('div', { 'class': 'cbi-section' }, [
                E('h3', {}, _('Current Status')),
                E('div', { 'class': 'cbi-value' }, [
                    E('div', { 'class': 'cbi-value-title' }, _('Modem')),
                    E('div', { 'class': 'cbi-value-field' }, statusText)
                ]),
                E('div', { 'class': 'cbi-value' }, [
                    E('div', { 'class': 'cbi-value-title' }, _('Operator')),
                    E('div', { 'class': 'cbi-value-field' }, (st.operator || '-'))
                ]),
                E('div', { 'class': 'cbi-value' }, [
                    E('div', { 'class': 'cbi-value-title' }, _('SIM')),
                    E('div', { 'class': 'cbi-value-field' }, (st.sim || '-') + '  ICCID: ' + (st.iccid || '-'))
                ]),
                E('div', { 'class': 'cbi-value' }, [
                    E('div', { 'class': 'cbi-value-title' }, _('SIMSEL applied')),
                    E('div', { 'class': 'cbi-value-field' },
                        (st.simsel_applied === '1' ? _('Yes') : _('No')) + '  (' + (st.simsel_cmd || '') + ')')
                ])
            ]);
        };

        s = m.section(form.NamedSection, 'simsel', 'uf896-simsel');
        s.anonymous = true;
        s.addremove = false;

        o = s.option(form.Flag, 'enabled', _('Re-apply SIMSEL if modem reconnects'));
        o.description = _('When enabled, the watchdog re-sends the SIMSEL command ' +
            'every time the modem recovers from a disconnect, so the correct SIM ' +
            'stays bound across modem resets.');
        o.rmempty = false;

        o = s.option(form.Value, 'simsel_cmd', _('SIMSEL command'));
        o.description = _('AT command sent to select the physical SIM. Default ' +
            'AT+SIMSEL=0,1,1,1 binds SIM1 (stock sim1.sh behaviour).');
        o.rmempty = false;

        var btn = s.option(form.Button, '_apply', _('Apply SIMSEL now'));
        btn.inputtitle = _('Apply');
        btn.onclick = function(ev) {
            return fs.exec('/usr/sbin/uf896-simsel', ['apply']).then(function(res) {
                var out = (res && res.stdout ? res.stdout : '') +
                          (res && res.stderr ? res.stderr : '');
                ui.addNotification(null, E('p', _('SIMSEL response: ') + (out || 'ok')), 'info');
            }).catch(function(e) {
                ui.addNotification(null, E('p', _('SIMSEL apply failed: ') + e), 'error');
            });
        };

        m.on_after_commit = function(map) {
            return fs.exec('/etc/init.d/uf896-simsel', ['restart']).catch(function() {
                ui.addNotification(null,
                    E('p', _('Restart requested; check status if anything looks wrong.')), 'warning');
            });
        };

        return m.render();
    }
});
