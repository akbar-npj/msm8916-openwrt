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
        "$HOME/Projects/Openwrt Custom Packages/luci-theme-feelgood/luci-theme-feelgood/" \
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

Two navigation modes (`horizontal` | `vertical`, stored in `localStorage` as
`luci-theme-feelgood-nav`) share one stylesheet.  The
`@media (max-width: 992px)` block turns either mode into a drawer; several
of its rules need `:root` or `html[data-theme-nav=...]` prefixes because the
desktop rules are declared later in the file at equal specificity.

`htdocs/luci-static/resources/menu-feelgood.js` is the colour engine
(`Colorizer`).  It re-runs on a MutationObserver, so every `setAttribute` in a
pass must be guarded by a comparison — an unconditional write re-triggers the
observer forever.

The custom sidebar scrollbar (`ScrollY.js`) clamps its offset to
`iScrollH - iWrapperH` with **no floor**, so a menu that collapses below the
box height leaves `iScrollTop` negative; `setTransLate()` then emits
`translate(-0px, --53px)`, which the browser rejects, and the sidebar freezes at
the previous offset while the bar hides itself.  `initScrollBar()`'s resync
therefore clamps the offset to `[0, max]` and re-applies the transform after
every re-measure — keep that, or switching sections while scrolled up leaves the
menu scrolled past its end with no scrollbar to bring it back.

The sidebar opens a section on hover (`bindMenuHover()`) in a panel **beside**
it, not under the tab.  Expanding in place pushes every tab below the open one
down, so reaching the sixth tab means crossing five open submenus (or
scrolling); the flyout keeps the sidebar at a constant height, so every tab
stays exactly where it was.  On a wide screen nothing expands in place at all
— the sidebar is a flat list of tabs, the same shape as the horizontal bar.
Only the touch drawer keeps the inline accordion, because it has no hover.

The panel is a child of `.main-left`, not of the scrolling box:
`.navbar-container` clips its content and the sidebar scrolls by transform, so
a panel inside it would be cut off at the sidebar edge.  Moving from the tab
into the panel crosses a gap, so the close is deferred and cancelled by
entering the panel; a click pins the panel, Escape closes it, and scrolling the
sidebar closes it rather than letting it drift.  Whether an event is a real
hover is decided per event from `pointerType` — touch taps keep the inline
accordion — not from a `(hover: hover)` media query: that query describes the
*primary* input, so a tablet with a mouse or a touchscreen laptop in tablet
mode would report `none` and the sidebar would look inert.

## Visual themes

`data-theme-style` on `<html>` selects the palette: **`dark`** (default),
**`light`**, or **`colourful`**.  The header template sets the attribute
before first paint from `localStorage`, so every page — including the login
screen — renders in the chosen palette with no flash.

The header carries a three-button switch (Dark / Light / Colourful).  The choice is stored
under `luci-theme-feelgood-style`, a **separate key** from the navigation
layout (`luci-theme-feelgood-nav`), so the two settings never reset each
other: switching to light keeps horizontal/vertical as it was, and changing
the layout keeps the palette.

The layer is a CSS-only restyle: glassy cards, a segmented tab bar, pill
buttons, and on **Network → Interfaces** a compact list: identity on the left,
one value per line in the middle (labels in a fixed column, so values line up),
actions on the right.  It adds no markup and no extra data — every page keeps
exactly the information it had, it is only presented differently.

One type size per line: a label and the value it names are the same size, on
every interface detail line (network list, wireless rows, overview cards).
Rank comes from weight and colour — the label is 600-weight and faint, the
value full contrast.  cascade.css sizes `.ifacebox-body .nowrap > strong` at
.84rem while the value beside it inherits the container's smaller size, and the
interface blocks used to go the other way with a .6rem micro-label under a .9rem
value; both read as accidents, so `.nowrap > strong { font-size: inherit }`
neutralises the first and `--iface-fs` sets one size for labels, values and
pills alike in the second.  Column headers (`th`) keep the uppercase
micro-label style — they are not paired with a value.

Files:

* `htdocs/luci-static/feelgood/theme.css` — every structural rule, scoped to
  `html[data-theme-style]` (any palette).  Colour comes only from tokens.
* `htdocs/luci-static/feelgood/light.css` — the daylight palette: cream canvas
  (`#f6efe1`) with warm-ink text, not a blue-white.  Redefines the tokens in §1
  plus the cascade.css token set, and restates the handful of rules
  cascade.css hardcodes for a dark canvas (sidebar link colour, active nav
  pills, alert text).
* `htdocs/luci-static/feelgood/colourful.css` — violet canvas, cyan primary,
  fuchsia secondary.  Same contract; its accent is deliberately cyan so the
  red of a fault never reads as decoration.  Alerts keep the violet panel and
  move the meaning to the border and text, because a translucent amber wash
  composites to brown on violet.
* `htdocs/luci-static/feelgood/js/theme.js` — applies the stored palette,
  handles the switch, and never touches page content.

A further palette is one more file scoped to its own value that redefines the
same tokens plus one button in the header switch and one entry in the
`colourful`/`light` allowlist of `js/theme.js`; `theme.css` needs no change.

### Meaning-aware colour

Colour carries meaning, not decoration.  Every status surface resolves through
five token sets defined in §1 of `theme.css`:

| Token | Meaning | Example |
| --- | --- | --- |
| `--state-ok-*` | healthy | carrier present, lease active, service running |
| `--state-warn-*` | degraded, still working | high temperature, throttling, restart pending |
| `--state-error-*` | fault | carrier absent, no internet, failed service |
| `--state-info-*` | neutral fact | protocol name, frequency band |
| `--state-idle-*` | unknown / n/a | interface down by design |

Wired today: the carrier pill (`data-state="ok|bad"`), interface error lines
(`data-role="error"` — a modem reporting `MM_FAILED_REASON_SIM_MISSING` is
red in both palettes), informational lines (`data-role="info"`), the LuCI
alerts, and the thermal/progress tokens cascade.css already exposes
(`--temp-cool|normal|hot|critical`, `--bar-*`).  A palette must keep the hue
roles — red = fault, amber = degradation, green = healthy — and may only move
lightness/saturation so the meaning survives on its own background.

Each set has `-fg` (text and icon), `-bg` (wash) and `-line` (ring or bar).
Markup hooks the theme reads: `[data-state="ok|bad"]`, `[data-role="error"]`,
`[data-role="info"]`, and LuCI's `.alert-message.error|warning|notice`.

Notes for maintainers:

* `menu-feelgood.js` must not write `data-theme-style`; `js/theme.js` owns it,
  otherwise the switch resets on every page load.
* `theme.css` must not contain a colour literal — every value comes from a
  token, or the light palette silently inherits dark pixels.
* `cascade.css` sizes the `_ifacestat` labels with `flex-basis: 88px`.  Inside
  a column flex that basis becomes a *height*, so `theme.css` resets it — drop
  that rule and every interface card grows to ~850 px.
* Interface glyphs are painted by `theme.css` as CSS masks over LuCI's device
  icons: the `img` is hidden and `.cbi-tooltip-container::before` draws a
  26px chip glyph chosen by the icon LuCI renders (`switch`/`bridge`,
  `ethernet`, `wifi`, `tunnel`).  Category tints only — state colours stay
  reserved for meaning.  Without `:has()` the icons simply grow to 20px.
* LuCI replaces the interface table's `tbody` on every poll, so the list is
  pure CSS on the existing `tr`/`td` elements; anything injected into those
  rows would be thrown away.
* The zone colour arrives as an inline `background-color` on `.ifacebox-head`.
  Used as a fill it put near-white text on pale green (1.3:1), so cascade.css
  turns it into a left accent bar and a faint wash instead — keep it that way
  if you restyle the head.
* The selected tab must be a *flat* tint.  A left-to-right gradient faded to
  nothing on the right half of the pill, which made the selection look
  half-painted; `.cbi-tabmenu li.cbi-tab` therefore sets a solid
  `rgba(56,189,248,.22)` fill plus an inset ring.
* `:root .cbi-value-field > div` in cascade.css paints its target in
  `--data-num` (the accent).  `.cbi-value-description` is also a direct child
  div, so the selector carries `:not(.cbi-value-description)` — without it
  every help paragraph on **Global network options** renders as bright cyan
  body copy.
* Help text uses `--text-description`, not `--text-muted`.  `--text-muted` is
  tuned for one-line metadata; at 13px across a paragraph (the IPv6 ULA prefix
  note) it read as grey mush even at 6.9:1.
* `.cbi-button-reset` belongs to the save/add group, not the remove/negative
  group: a red tint made the page-action Reset look disabled next to Save.
* The status tables (`.leases`, `.leases6`, `.assoclist`) carry nowrap
  pills, badges and buttons, and one unbreakable token (a DUID, an IPv6
  address) can pin a column at ~200px.  Their min-content width stays above the
  card below ~1300px, so the last column — STATIC LEASE, the Disconnect
  button — painted outside the rounded card.  `theme.css` lets long values
  break (`overflow-wrap: anywhere`), floors the hostname column at 8rem, and
  scrolls the table's own wrapper via `:has()` — with a plain
  `overflow-x: auto` on the card for browsers without `:has()`.
* Value lines are ordered with `order`, so every span carries a default
  (`order: 8`) and only the known roles get 1-7.  Without the default, a
  row-specific line such as `error` or `information` defaults to 0 and jumps
  to the top of its row, pushing the shared fields out of line with the
  neighbouring interfaces.
* The same table treatment is shared by every page under
  `[data-page^="admin-network" | "admin-status" | "admin-system" |
  "admin-modem" | "admin-services"]` — Interfaces, Devices, Wireless, DHCP,
  Firewall, Routing, DNS, Diagnostics, Station Mode, the Status/System/Modem/
  Services tabs, nftables and the plain data tables (leases, processes, init
  scripts, SMS, …) all get hairline rows, micro-label headers, badge pills and
  pill actions instead of the stock filled header band and zebra striping.
* Two table shapes are handled: the standard `.cbi-section-table` (with
  `<thead>`/`<tbody>`) and the grid-table variant LuCI uses for nftables
  (`<tr class="tr table-titles">` as a direct child, no wrapper), plus plain
  `<table>` elements that are neither.
* Those overrides need `#maincontent` in the selector.  cascade.css paints
  table headers and rows from rules that outrank a plain class chain (and
  several use `!important`), so a rule that loses the cascade looks like a
  broken feature rather than a specificity problem.
* Station Mode writes its status pills inline (`color:#fff` on `#4caf50`,
  2.8:1) and nests a LuCI badge inside them, which drew green on green.  Only
  an `!important` author rule can outrank an inline declaration, so the pill
  is darkened to `#2e7d32` and the nested badge goes white-on-green.
* Zone badges on Firewall are filled with an inline zone colour (or a light
  hatch when empty), so every label sitting *on* that fill — `<strong>`, the
  `<em>` of "unspecified" — is forced dark.  Only the direct children: the
  `.ifacebadge` chips inside keep their own dark card background and must
  keep the palette's light text.  The selector carries `#maincontent` /
  `#modal_overlay` because the `--data-num` rule paints every `table td
  strong` in the accent and outranks any class-only selector; the
  interface-edit modal is mounted in the overlay, outside `#maincontent`.
* `.zone-forwards` sizes its two sides by content (`flex: 1 1 auto` plus
  `min-width: max-content`), not by the 45% share it used to take: the
  forward target "REJECT all others" is wider than half of the ~290px the
  column gets, so a percentage basis wrapped it onto two lines.
* The zone table's Input / Output selects are `width: 100%` of a cell the
  table may squeeze; with the vertical sidebar they collapsed to 56px and
  clipped "accept" to "acc".  `min-width: max-content` floors each select at
  its own widest option.  That widens the table past its card below ~1200px
  (the Edit/Delete buttons left the screen), so the two long headers are
  allowed to wrap at that width, which gives back ~80px and makes it fit.
* `Configuration / Changes` paints a pending uci change as `<ins>`/`<del>`/
  `<var>` rows.  cascade.css gives them the stock pale fills while the text
  keeps the palette's near-white (~1.1:1), so `theme.css` repaints them with
  the state tokens: added = ok green, removed = error pink, changed key =
  idle slate.  The 10px legend chips stay solid rather than washed.
* The carrier state is a pill, so its `<strong>` label is lifted out with
  `position: absolute` and `left: calc(-1 * (label + gap))`; the pill itself
  is pushed into the value column with the matching `margin-left`.  Both
  lengths come from `--iface-label-w` / `--iface-label-gap` on the cell.
* Disabled `.cbi-button`s fade to `opacity: .75`, not `.60`: at .60 the label
  lands at ~3.8:1 and stops being legible on the dark card.
* `theme.css`, `light.css`, `colourful.css` and `js/theme.js` are requested
  with `?v=` from `header.ut`/`header.htm`.  uhttpd sends no `Cache-Control`
  for `/luci-static`, so that query string is the only cache key — bump it
  together with `PKG_RELEASE` whenever one of those files changes, otherwise
  an upgraded theme keeps rendering from the copy the browser already has
  (this is what made a fixed separator look unfixed after an upgrade).
