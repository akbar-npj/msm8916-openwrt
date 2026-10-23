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

local fs = require "nixio.fs"

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
        -- Only meaningful where the 16-partition layout was actually written.
        needs_recovery_partition = true,
    },
}

-- Is there a block partition named "recovery"?
--
-- Asking the block layer beats keeping a board list: the entry appears the
-- moment a stick is repartitioned and never offers a button that would reboot
-- into a partition the bootloader cannot find.  The name comes from
-- generate_squashfs_gpt.sh, so it is the same one lk2nd matches on.
--
-- nixio has no opendir(): the directory iterator is fs.dir().  Note that
-- nixio.fs is a Lua table whose metatable is __index = itself, so a misspelt
-- key raises "loop in gettable" rather than "attempt to call a nil value".
local function has_recovery_partition()
    local ok, iter, state, init = pcall(fs.dir, "/sys/class/block")
    if not ok or not iter then
        return false
    end

    for name in iter, state, init do
        local uevent = fs.readfile("/sys/class/block/" .. name .. "/uevent")
        if uevent and uevent:find("PARTNAME=recovery", 1, true) then
            return true
        end
    end

    return false
end

function index()
    entry({"admin", "system", "reboot_edl"},
          call("action_reboot_mode", "edl"),
          _("Reboot to EDL"), 91)

    if has_recovery_partition() then
        entry({"admin", "system", "reboot_recovery"},
              call("action_reboot_mode", "recovery"),
              _("Reboot to Recovery"), 92)
    end
end

function action_reboot_mode(mode)
    local http = require "luci.http"
    local sys  = require "luci.sys"

    local m = MODES[mode]
    if not m then
        http.status(404, "Not Found")
        return
    end

    -- Re-check on POST as well as on menu build: a bookmarked URL, or a stick
    -- repartitioned back to the 15-partition layout, must not get a page that
    -- offers to reboot into nothing.
    local reason
    if not fs.access(m.tool) then
        reason = "The reboot tool is not installed on this device."
    elseif m.needs_recovery_partition and not has_recovery_partition() then
        reason = "This device has no recovery partition, so there is nothing to boot into."
    end

    local available = (reason == nil)

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
        reason    = reason,
    })
end
