#include <unistd.h>
#include <sys/syscall.h>
#include <linux/reboot.h>
#include <errno.h>
#include <stdio.h>
#include <string.h>
#include <libgen.h>

static void print_usage(const char *prog)
{
	printf("Qualcomm MSM Reboot Utility\n");
	printf("Usage: %s [mode]\n\n", prog);
	printf("Available modes:\n");
	printf("  edl        - Qualcomm 9008 Emergency Download Mode (PBL)\n");
	printf("  dload      - Qualcomm 9006 Mass Storage / Dump Mode (SBL1)\n");
	printf("  bootloader - Fastboot Mode (aboot)\n");
	printf("  fastboot   - Fastboot Mode (aboot)\n");
	printf("  recovery   - Recovery Mode (aboot)\n");
}

int main(int argc, char *argv[])
{
	const char *cmd = "edl";
	char *bname = basename(argv[0]);

	if (strstr(bname, "dload") || strstr(bname, "9006"))
		cmd = "dload";
	else if (strstr(bname, "bootloader") || strstr(bname, "fastboot"))
		cmd = "bootloader";
	else if (strstr(bname, "recovery"))
		cmd = "recovery";
	else if (strstr(bname, "edl") || strstr(bname, "9008"))
		cmd = "edl";

	if (argc > 1) {
		if (!strcmp(argv[1], "-h") || !strcmp(argv[1], "--help")) {
			print_usage(argv[0]);
			return 0;
		}
		cmd = argv[1];
	}

	printf("Rebooting into %s mode (LINUX_REBOOT_CMD_RESTART2 '%s')...\n", cmd, cmd);
	fflush(stdout);
	sync();

	long ret = syscall(SYS_reboot,
			   LINUX_REBOOT_MAGIC1,
			   LINUX_REBOOT_MAGIC2,
			   LINUX_REBOOT_CMD_RESTART2,
			   cmd);

	fprintf(stderr, "reboot syscall returned: %ld (%s)\n",
		ret, strerror(errno));

	return 1;
}
