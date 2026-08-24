#include <unistd.h>
#include <sys/syscall.h>
#include <linux/reboot.h>
#include <errno.h>
#include <stdio.h>
#include <string.h>

int main(void)
{
        const char *cmd = "dload";

        printf("Calling LINUX_REBOOT_CMD_RESTART2 with command: %s\n", cmd);
        fflush(stdout);

        sync();

        /*
         * Linux reboot(2):
         *
         * reboot(LINUX_REBOOT_MAGIC1,
         *        LINUX_REBOOT_MAGIC2,
         *        LINUX_REBOOT_CMD_RESTART2,
         *        cmd)
         */
        long ret = syscall(SYS_reboot,
                           LINUX_REBOOT_MAGIC1,
                           LINUX_REBOOT_MAGIC2,
                           LINUX_REBOOT_CMD_RESTART2,
                           cmd);

        fprintf(stderr, "reboot syscall returned: %ld (%s)\n",
                ret, strerror(errno));

        return 1;
}
