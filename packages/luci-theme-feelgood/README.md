# luci-theme-feelgood

The LuCI theme every msm89xx image ships, and the default theme of the
25.12.6 branch.

## Where this comes from

This directory is a **vendored copy** of the standalone package at
<https://github.com/akbar-npj/luci-theme-feelgood> (subdirectory
`luci-theme-feelgood/`).  It is vendored rather than added as a git submodule
because the CI checkout does not pass `submodules: recursive`, so a submodule
would arrive empty and the build would fail on a missing package.

The upstream repo is the source of truth.  After editing the theme there,
re-sync it here:

    rsync -a --delete --exclude='.git' \
        ~/Projects/luci-theme-feelgood/luci-theme-feelgood/ \
        packages/luci-theme-feelgood/

`--delete` matters: a file removed upstream must disappear here too, or it
keeps being installed.

## How it becomes the default

`root/etc/uci-defaults/30_luci-theme-feelgood` registers the theme and takes
`luci.main.mediaurlbase` over from the stock `/luci-static/bootstrap` value.
LuCI resolves its theme from that option (`modules/luci-base/ucode/runtime.uc`),
so it applies to a fresh flash *and* to a config-preserving `sysupgrade`.

Two deliberate properties:

* Only the stock bootstrap default is replaced.  A user who picked another
  theme keeps it.
* `luci-theme-bootstrap` stays in `DEFAULT_PACKAGES`.  If the Feelgood
  templates ever fail to compile, LuCI walks `luci.themes.*` and falls back to
  it instead of returning a 500.

## Layout notes

Two navigation modes (`theme_feelgood.nav` = `horizontal` | `vertical`) and
two palettes (`theme_feelgood.color` = `dark` | `light`) share one stylesheet.
The `@media (max-width: 992px)` block turns either mode into a drawer; several
of its rules need `:root` or `html[data-theme-nav=...]` prefixes because the
desktop rules are declared later in the file at equal specificity.

`htdocs/luci-static/resources/menu-feelgood.js` is the colour engine
(`Colorizer`).  It re-runs on a MutationObserver, so every `setAttribute` in a
pass must be guarded by a comparison - an unconditional write re-triggers the
observer forever.
