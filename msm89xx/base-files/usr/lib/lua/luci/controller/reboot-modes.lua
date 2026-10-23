-- SPDX-License-Identifier: GPL-2.0-only
--
-- "Reboot to EDL" and "Reboot to Recovery" entries in the System menu.
--
-- Both commands come from the reboot-edl package, which installs
-- /sbin/reboot-edl and /sbin/reboot-recovery as symlinks to reboot-mode;
-- reboot-mode picks the target from its own name, quiesces the services,
-- remounts the filesystems read-only and only then hands the reboot reason to
-- the bootloader.  That teardown is why the command has to run last and why
-- the page is rendered before it starts.

module("luci.controller.reboot-modes", package.seeall)

local MODES = {
    edl = {
        title = "Reboot to EDL",
        what  = "Qualcomm EDL (9008) download mode",
        tool  = "/sbin/reboot-edl",
    },
    recovery = {
        title = "Reboot to Recovery",
        what  = "the recovery system",
        tool  = "/sbin/reboot-recovery",
    },
}

function index()
    entry({"admin", "system", "reboot_edl"},
          call("action_reboot_mode", "edl"),
          _("Reboot to EDL"), 91)

    entry({"admin", "system", "reboot_recovery"},
          call("action_reboot_mode", "recovery"),
          _("Reboot to Recovery"), 92)
end

function action_reboot_mode(mode)
    local http = require "luci.http"
    local sys  = require "luci.sys"
    local fs   = require "nixio.fs"

    local m = MODES[mode]
    if not m then
        http.status(404, "Not Found")
        return
    end

    -- The tool is only present when reboot-edl is installed.  Say so instead of
    -- offering a button that would silently do nothing.
    local available = fs.access(m.tool)
    if available and http.formvalue("confirm") == "1" then
        luci.template.render("reboot_mode_go", { title = m.title })
        -- Detach: reboot-mode kills this CGI along with everything else, and
        -- the two second delay lets the response reach the browser first.
        sys.call("(sleep 2; " .. m.tool .. ") >/dev/null 2>&1 &")
        return
    end

    luci.template.render("reboot_mode", {
        title     = m.title,
        what      = m.what,
        tool      = m.tool,
        available = available,
    })
end
