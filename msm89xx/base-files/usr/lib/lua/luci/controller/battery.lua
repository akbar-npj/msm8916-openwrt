module("luci.controller.battery", package.seeall)

function index()
    entry({"admin", "status", "battery"}, call("action_status"),
          _("Battery & Charge Limit"), 90)
end

local CHG     = "/sys/class/power_supply/pm8916-lbc-chgr"
local BATT    = "/sys/class/power_supply/pm8916-bms-vm"
local SESSION = "/etc/battery-session"

local function rd(p)
    local f = io.open(p, "r")
    if not f then return "-" end
    local v = f:read("*a")
    f:close()
    -- NOTE: the extra parentheses truncate gsub()'s second return value;
    -- without them callers like tonumber(rd(p)) receive (value, count).
    return ((v or ""):gsub("%s+$", ""))
end

-- Human-friendly units: sysfs gives uV and uA; the BMS temp is tenths of C.
local function fmt_v(ustr)
    local n = tonumber(ustr)
    if not n then return ustr end
    return string.format("%.3f V", n / 1000000)
end

local function fmt_temp(raw)
    local n = tonumber(raw)
    if not n then return raw end
    return string.format("%.1f C", n / 10)
end

local function fmt_cur(ustr)
    local n = tonumber(ustr)
    if not n then return ustr end
    local ma = n / 1000
    if ma >= 1000 then return string.format("%.2f A", ma / 1000) end
    return string.format("%.0f mA", ma)
end

local function read_kv(path)
    local t = {}
    local f = io.open(path, "r")
    if not f then return t end
    for line in f:lines() do
        local k, v = line:match("^([%w_]+)=(.*)$")
        if k then t[k] = v end
    end
    f:close()
    return t
end

local function fmt_dur(sec)
    sec = tonumber(sec)
    if not sec or sec < 0 then return "-" end
    local h = math.floor(sec / 3600)
    local m = math.floor((sec % 3600) / 60)
    if h > 0 then return string.format("%dh %02dm", h, m) end
    if m > 0 then return string.format("%d min", m) end
    return "under 1 min"
end

function action_status()
    local http = require "luci.http"
    local uci  = require "luci.model.uci".cursor()
    local sys  = require "luci.sys"

    local act = http.formvalue("act")
    if act == "apply" then
        local limit   = http.formvalue("limit")
        local resume  = http.formvalue("resume")
        local enabled = http.formvalue("enabled")
        if limit and #limit > 0 then
            uci:set("battery", "@charge[0]", "limit", limit)
        end
        if resume and #resume > 0 then
            uci:set("battery", "@charge[0]", "resume", resume)
        end
        uci:set("battery", "@charge[0]", "enabled",
                (enabled == "1") and "1" or "0")
        uci:commit("battery")
        sys.call("/etc/init.d/battery-charge-limit restart >/dev/null 2>&1")
    elseif act == "charge" then
        local v = http.formvalue("val")
        sys.call("echo " .. ((v == "1") and "1" or "0") ..
                 " > " .. CHG .. "/charge_enabled")
    end

    local capraw = rd(BATT .. "/capacity")
    local tempraw = rd(BATT .. "/temp")
    local capn = tonumber(capraw)
    local tempn = tonumber(tempraw)

    -- The BMS driver keeps a 25.0 C placeholder (bat->temp = 25000) until its
    -- batt-therm read succeeds, and that read currently never lands.  The VADC
    -- channel itself is readable, so fall back to it while the value is the
    -- placeholder.  Once the driver is fixed the two agree and this is a no-op.
    local tsensor = "bms"
    if tempn == 250 then
        local out = sys.exec("for f in /sys/bus/iio/devices/iio:device*/in_temp*_label; do " ..
                             "echo \"$f $(cat $f 2>/dev/null)\"; done 2>/dev/null")
        for path, label in (out or ""):gmatch("(%S+)%s+([^\n]+)") do
            if label:find("BAT_THERM") then
                local v = tonumber(rd((path:gsub("_label$", "_input"))))
                if v then
                    tempn = math.floor(v / 100)   -- millidegrees -> tenths of C
                    tsensor = "vadc"
                end
                break
            end
        end
    end

    local d = {
        online  = rd(CHG .. "/online"),
        ce      = rd(CHG .. "/charge_enabled"),
        curr    = fmt_cur(rd(CHG .. "/constant_charge_current")),
        cvmax   = fmt_v(rd(CHG .. "/constant_charge_voltage_max")),
        cap     = capraw,
        vnow    = fmt_v(rd(BATT .. "/voltage_now")),
        temp    = fmt_temp(tempn),
        tsensor = tsensor,
        health  = rd(BATT .. "/health"),
        status  = rd(BATT .. "/status"),
        design  = rd(BATT .. "/charge_full_design"),
        enabled = uci:get("battery", "@charge[0]", "enabled") or "1",
        limit   = uci:get("battery", "@charge[0]", "limit")  or "85",
        resume  = uci:get("battery", "@charge[0]", "resume") or "80",
    }

    -- ---- runtime on battery -------------------------------------------------
    -- The gauge exposes no current_now/power_now, so the discharge rate is
    -- derived from the capacity drop across the session recorded by
    -- /usr/sbin/battery-session (fed by battery-led-monitor).
    local ss   = read_kv(SESSION)
    local now  = os.time()
    local onbat = (d.online == "0")
    local elapsed = 0

    if onbat and tonumber(ss.start) then
        elapsed = now - tonumber(ss.start)
        if elapsed < 0 then elapsed = 0 end
    end

    local scap = tonumber(ss.start_cap)
    local rate, remain, full

    if onbat and elapsed >= 300 and capn and scap and scap > capn then
        rate = (scap - capn) / (elapsed / 3600)
        if rate >= 0.2 then
            remain = capn / rate * 3600
            full   = 100 / rate * 3600
        end
    end

    d.onbat    = onbat
    d.uptime   = onbat and fmt_dur(elapsed) or nil
    d.rate     = rate and string.format("%.1f %%/h", rate) or nil
    d.remain   = remain and fmt_dur(remain) or nil
    d.full     = full and fmt_dur(full) or nil
    d.lastsess = tonumber(ss.last) and fmt_dur(ss.last) or nil
    d.startcap = scap

    if capn and capn < 15 then d.capclass = "bat-crit"
    elseif capn and capn < 40 then d.capclass = "bat-warn"
    else d.capclass = "bat-ok" end

    if capn then d.cappct = math.floor(capn) else d.cappct = 0 end

    if tempn and tempn >= 550 then d.tempclass = "bat-crit"
    elseif tempn and tempn >= 450 then d.tempclass = "bat-warn"
    else d.tempclass = "bat-ok" end

    d.chgclass = (d.online == "1") and "bat-ok" or "bat-warn"
    d.ceclass  = (d.ce == "1") and "bat-ok" or "bat-warn"

    if d.status == "Charging" then d.stclass = "bat-ok"
    elseif d.status == "Full" then d.stclass = "bat-info"
    else d.stclass = "bat-warn" end

    luci.template.render("battery/status", { d = d })
end
