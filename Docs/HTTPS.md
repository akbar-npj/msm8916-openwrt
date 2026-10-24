# HTTPS on the web admin panel

Every image serves LuCI over **both** HTTP and HTTPS from first boot.  Nothing
has to be enabled and there is no redirect: `http://<device>/` keeps working
exactly as before, and `https://<device>/` works as well.

---

## What ships

| Piece | Where |
| :---- | :---- |
| `px5g-mbedtls` | every board — `DEFAULT_PACKAGES` in `msm89xx/Makefile` |
| `listen_https 0.0.0.0:443` and `[::]:443` | uhttpd default config |
| `redirect_https 0` | uhttpd default config — **deliberate** |
| `cert /etc/uhttpd.crt`, `key /etc/uhttpd.key` | uhttpd default config |

`px5g` is the certificate generator: `uhttpd` calls it on first start when the
certificate files are missing, so the key pair is created **on the device**,
unique per stick, and never leaves it.

### What the generated certificate looks like

```
subject   CN=<hostname>            (msm8916-openwrt unless you renamed the device)
key       EC P-256                 (587-byte cert, 121-byte key)
validity  397 days                (the CA/Browser Forum maximum)
country   ZZ, state Somewhere, location Unknown
```

These are the upstream `config cert defaults`; override them in
`/etc/config/uhttpd` if you want a different common name or key type.

---

## Why there is no redirect

`redirect_https=1` would break every plain-HTTP client — scripts, `curl` from a
host, the recovery image, the first-boot instructions that tell you to open
`http://192.168.8.1/`.  A self-signed certificate also produces a browser
warning, and a redirect turns that warning into a hard failure for anything
non-interactive.

So both listeners stay up.  If you *want* the redirect, it is one uci value:

```sh
uci set uhttpd.main.redirect_https='1'
uci commit uhttpd
/etc/init.d/uhttpd restart
```

> **Trap:** that value lives in `/etc/config/uhttpd`, i.e. in the **overlay**.
> A config-preserving `sysupgrade` keeps it, so a stick you once set to
> redirect keeps redirecting after the upgrade even though the image ships
> `0`.  Check with `uci get uhttpd.main.redirect_https` and compare against
> `/rom/etc/config/uhttpd` when HTTP unexpectedly stops working.

---

## Using your own certificate

Drop the pair in place and restart uhttpd:

```sh
cp my.crt /etc/uhttpd.crt
cp my.key /etc/uhttpd.key
chmod 600 /etc/uhttpd.key
/etc/init.d/uhttpd restart
```

For a certificate that browsers will accept, the common name / SAN must match
the name you actually use to reach the stick (its IP, or a DNS name you point
at it).  A public CA cannot issue for a private address, so on a LAN you either
use a local CA or accept the warning once per browser.

---

## Checking it

```sh
# from a host
curl -k -I https://192.168.8.1/          # HTTP/1.1 200 OK
curl -I  http://192.168.8.1/             # still works, no 301

# on the device
ls -l /etc/uhttpd.crt /etc/uhttpd.key
netstat -ltn | grep -E ':80 |:443 '
logread | grep -i uhttpd
```

If `:443` is missing, `px5g-mbedtls` is not installed — check
`DEFAULT_PACKAGES` in `msm89xx/Makefile` (it ships on every board).

---

## Notes

* The certificate is **not** shared between devices, so each stick shows its own
  warning.  That is expected, not a bug.
* Regenerating it is just `rm /etc/uhttpd.crt /etc/uhttpd.key` followed by an
  `/etc/init.d/uhttpd restart`.
* `sysupgrade -n` wipes the overlay, so the certificate is regenerated with a
  new key on the next boot.