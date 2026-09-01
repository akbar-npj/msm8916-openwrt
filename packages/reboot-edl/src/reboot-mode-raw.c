/*
 * SPDX-License-Identifier: GPL-2.0-only
 *
 * reboot-mode-raw.c - Low-level syscall dispatcher for Qualcomm MSM reboot modes
 */

#include <unistd.h>
#include <sys/syscall.h>
#include <linux/reboot.h>
#include <errno.h>
#include <stdio.h>
#include <string.h>

int main(int argc, char *argv[])
{
	const char *cmd = "edl";

	if (argc > 1 && argv[1][0] != '\0')
		cmd = argv[1];

	sync();

	/*
	 * Dispatch Linux RESTART2 with Qualcomm target mode parameter:
	 *   - "edl"        -> 9008 PBL Emergency Download
	 *   - "dload"      -> 9006 SBL1 Mass Storage / Dump
	 *   - "bootloader" -> fastboot
	 *   - "recovery"   -> recovery
	 */
	long ret = syscall(SYS_reboot,
			   LINUX_REBOOT_MAGIC1,
			   LINUX_REBOOT_MAGIC2,
			   LINUX_REBOOT_CMD_RESTART2,
			   cmd);

	fprintf(stderr, "reboot(%s) syscall returned: %ld (%s)\n",
		cmd, ret, strerror(errno));

	return 1;
}
