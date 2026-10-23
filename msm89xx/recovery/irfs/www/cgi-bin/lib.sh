#!/bin/busybox sh
# Shared helpers for the recovery web UI CGIs.  Sourced, never executed.

# busybox httpd copies the CGI's stdout to the socket verbatim.
hdr() {
	printf 'Content-Type: %s\r\nCache-Control: no-store\r\n\r\n' "${1:-text/plain}"
}

# find_part NAME -> device node.
# GPT partition names are exported by the kernel as PARTNAME= in each block
# device's uevent, so this works on a bare initramfs (no udev, no
# /dev/disk/by-partlabel).
find_part() {
	for d in /dev/mmcblk*p*; do
		[ -b "$d" ] || continue
		p=$(grep -i '^PARTNAME=' "/sys/class/block/${d##*/}/uevent" 2>/dev/null | cut -d= -f2)
		[ "$p" = "$1" ] && { echo "$d"; return 0; }
	done
	return 1
}

# part_size DEVICE -> size in bytes
part_size() {
	s=$(cat "/sys/class/block/${1##*/}/size" 2>/dev/null) || s=0
	echo $(( s * 512 ))
}

# qs_get KEY -> value from QUERY_STRING (no urldecode: keys are plain words)
qs_get() {
	printf '%s\n' "$QUERY_STRING" | tr '&' '\n' | sed -n "s|^$1=||p" | head -1
}
