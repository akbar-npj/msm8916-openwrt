module("luci.controller.battery", package.seeall)

function index()
    entry({"admin", "status", "battery"}, call("action_status"),
          _("Battery & Charge Limit"), 90)
end

local function rd(p)
    local f = io.open(p, "r")
    if not f then return "-" end
    local v = f:read("*a")
    f:close()
    return (v or ""):gsub("%s+$", "")
end

function action_status()
    local http = require "luci.http"
    local uci  = require "luci.model.uci".cursor()
    local sys  = require "luci.sys"

    local chg  = "/sys/class/power_supply/pm8916-lbc-chgr"
    local batt = "/sys/class/power_supply/pm8916-bms-vm"

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
                 " > " .. chg .. "/charge_enabled")
    end

    local d = {
        online  = rd(chg .. "/online"),
        ce      = rd(chg .. "/charge_enabled"),
        curr    = rd(chg .. "/constant_charge_current"),
        cvmax   = rd(chg .. "/constant_charge_voltage_max"),
        cap     = rd(batt .. "/capacity"),
        vnow    = rd(batt .. "/voltage_now"),
        temp    = rd(batt .. "/temp"),
        health  = rd(batt .. "/health"),
        status  = rd(batt .. "/status"),
        enabled = uci:get("battery", "@charge[0]", "enabled") or "1",
        limit   = uci:get("battery", "@charge[0]", "limit")  or "85",
        resume  = uci:get("battery", "@charge[0]", "resume") or "80",
    }
    luci.template.render("battery/status", { d = d })
end
