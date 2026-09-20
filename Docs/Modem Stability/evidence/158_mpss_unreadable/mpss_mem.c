// SPDX-License-Identifier: GPL-2.0
/*
 * mpss_mem -- a read-only debugfs window onto the modem (mpss) reserved region.
 *
 * WHY THIS EXISTS
 * ---------------
 * On the HMU05 (MSM8916) the modem firmware's memory is declared `no-map` in
 * the device tree:
 *
 *     /sys/firmware/devicetree/base/reserved-memory/mpss@86800000
 *         reg = <0x0 0x86800000 0x0 0x05500000>   (85 MiB)
 *         no-map
 *
 * A `no-map` region is deliberately kept out of the kernel's linear map AND
 * out of the memmap, so userspace cannot reach it by any route: /dev/mem
 * read() returns EFAULT and /dev/mem mmap() returns SIGBUS.  That is a kernel
 * POLICY refusal, not a hardware one -- the AP interconnect is not blocked
 * from mpss -- so a kernel module can ioremap the region and hand the bytes
 * back out.  That is all this module does.
 *
 * (This is why `rpmring` works in userspace: the RPM log ring is in SRAM,
 *  which is a normal reserved region, not a no-map one.)
 *
 * USAGE
 * -----
 *   insmod mpss_mem.ko
 *   dd if=/sys/kernel/debug/mpss_mem bs=4096 skip=<n> count=<m> | hexdump -C
 *   rmmod mpss_mem
 *
 * The file offset is an offset WITHIN the mpss region, so
 *
 *     AP physical = base + offset
 *
 * The coredump ELF's p_vaddr are MODEM virtual addresses.  For mpss on this
 * device the two spaces differ by a constant:
 *
 *     AP physical = elf_va - 0x39800000
 *     offset      = elf_va - 0xC0000000        (== phys - base)
 *
 * Verified against the captured coredumps: the ERR_FATAL "last fatal" record
 * at ELF va 0xC35B1280 maps to phys 0x89DB1280, i.e. offset 0x035B1280 --
 * inside the 0x05500000-byte region.  (0x39800000 + 0x86800000 = 0xC0000000,
 * which is why the two constants differ.)
 *
 * So offset 0 is modem va 0xC0000000, not 0.
 *
 * WHAT IT IS NOT
 * --------------
 *  * Not a writer.  Mode 0444, no .write handler, no ioctl.
 *  * Not coherent.  Reading mpss while the modem is RUNNING races with the
 *    modem writing it, so a wide dump can be internally inconsistent (and a
 *    counter can be caught mid-update).  Read a small window repeatedly
 *    instead of one huge dump if you care about consistency.
 *  * Not a substitute for the coredump.  The dump is taken with the modem
 *    halted, so it is a consistent snapshot; this is for watching the
 *    TRANSITION, which the dump structurally cannot show.
 *
 * MAPPING MODE
 * ------------
 * The `wc` module parameter selects between the two mappings the abort could
 * plausibly be about, so that the experiment can tell them apart in one build:
 *
 *   wc=1 (default)  memremap(base, size, MEMREMAP_WC)   Normal-NonCacheable
 *   wc=0            ioremap(base, size)                 Device-nGnRE
 *
 * The driver itself reads mpss with memremap(..., MEMREMAP_WC) once it has
 * taken ownership back from the modem (qcom_q6v5_mss.c:1440, :1555).  So:
 *
 *   wc=0 aborts, wc=1 works  -> a memory-type mismatch, not a permission
 *                               problem, and the live reader IS feasible
 *   both abort               -> the region is denied to the AP outright
 *
 * wc=1 is the default because it matches what the driver does.
 */

#include <linux/module.h>
#include <linux/kernel.h>
#include <linux/init.h>
#include <linux/io.h>
#include <linux/debugfs.h>
#include <linux/fs.h>
#include <linux/uaccess.h>
#include <linux/math.h>

static unsigned long base = 0x86800000;
module_param(base, ulong, 0444);
MODULE_PARM_DESC(base, "physical base of the mpss region (default 0x86800000)");

static unsigned long size = 0x05500000;
module_param(size, ulong, 0444);
MODULE_PARM_DESC(size, "size of the mpss region in bytes (default 0x5500000)");

static char *fname = "mpss_mem";
module_param_named(name, fname, charp, 0444);
MODULE_PARM_DESC(name, "debugfs file name (default mpss_mem)");

static void *mpss;
static struct dentry *dent;
static bool use_wc;

/*
 * Mapping mode.  This exists because the abort we are chasing has TWO
 * candidate causes and they must be told apart:
 *
 *   wc=0  ioremap()            -> Device-nGnRE, accessed with readl()
 *   wc=1  memremap(MEMREMAP_WC)-> Normal-NonCacheable, accessed with memcpy()
 *
 * The driver itself reads mpss with memremap(..., MEMREMAP_WC) after taking
 * ownership back from the modem (qcom_q6v5_mss.c:1440, :1555).  If wc=0 aborts
 * and wc=1 works, the cause is a memory-type mismatch, NOT TrustZone, and the
 * live reader is feasible after all.  If both abort, the region is genuinely
 * denied to the AP while the modem owns it.
 */
static int wc = 1;
module_param(wc, int, 0444);
MODULE_PARM_DESC(wc, "1 = memremap(MEMREMAP_WC) [Normal-NC, default]; 0 = ioremap() [Device]");

/*
 * Bounce buffer size.  kmalloc'd in .open() rather than put on the stack:
 * the kernel builds this tree with -Werror=frame-larger-than=2048, and a
 * useful window is bigger than that.
 */
#define BOUNCE_BYTES (64 * 1024)

static int mpss_open(struct inode *inode, struct file *f)
{
	f->private_data = kmalloc(BOUNCE_BYTES, GFP_KERNEL);
	return f->private_data ? 0 : -ENOMEM;
}

static int mpss_release(struct inode *inode, struct file *f)
{
	kfree(f->private_data);
	f->private_data = NULL;
	return 0;
}

static ssize_t mpss_read(struct file *f, char __user *ubuf, size_t count,
			 loff_t *ppos)
{
	u8 *bounce = f->private_data;
	loff_t off = *ppos;
	size_t done = 0;

	if (off < 0 || (unsigned long)off >= size)
		return 0;

	while (done < count) {
		size_t n = min_t(size_t, count - done, BOUNCE_BYTES);
		loff_t o = off + done;
		size_t words, i;

		/* never read past the end of the region */
		if (n > size - o)
			n = size - o;
		if (!n)
			break;

		if (use_wc) {
			memcpy(bounce, mpss + o, n);
		} else {
			words = DIV_ROUND_UP(n, 4);
			if (o + (loff_t)words * 4 > size)
				words = (size - o) / 4;	/* 4-aligned tail only */

			for (i = 0; i < words; i++)
				((u32 *)bounce)[i] = readl(mpss + o + i * 4);

			/* at most 3 leftover bytes, inside the region */
			for (i = words * 4; i < n; i++)
				bounce[i] = readb(mpss + o + i);
		}

		if (copy_to_user(ubuf + done, bounce, n))
			return done ? (ssize_t)done : -EFAULT;

		done += n;
	}

	*ppos = off + done;
	return done;
}

static const struct file_operations mpss_fops = {
	.owner		= THIS_MODULE,
	.open		= mpss_open,
	.release	= mpss_release,
	.read		= mpss_read,
	.llseek		= default_llseek,
};

static int __init mpss_mem_init(void)
{
	if (!size || (size & 3)) {
		pr_err("mpss_mem: size %#lx must be non-zero and 4-byte aligned\n",
		       size);
		return -EINVAL;
	}

	use_wc = !!wc;
	if (use_wc)
		mpss = memremap(base, size, MEMREMAP_WC);
	else
		mpss = ioremap(base, size);

	if (!mpss) {
		pr_err("mpss_mem: %s(%#lx, %#lx) failed\n",
		       use_wc ? "memremap" : "ioremap", base, size);
		return -ENOMEM;
	}

	dent = debugfs_create_file(fname, 0444, NULL, NULL, &mpss_fops);
	if (IS_ERR_OR_NULL(dent)) {
		int ret = dent ? PTR_ERR(dent) : -ENODEV;

		pr_err("mpss_mem: debugfs_create_file(%s) failed: %d\n",
		       fname, ret);
		goto unmap;
	}

	pr_info("mpss_mem: window %#lx..%#lx (offset 0 == modem va %#lx) at /sys/kernel/debug/%s [%s]\n",
		base, base + size - 1, base + 0x39800000UL, fname,
		use_wc ? "memremap WC" : "ioremap Device");
	return 0;

unmap:
	if (use_wc)
		memunmap(mpss);
	else
		iounmap(mpss);
	mpss = NULL;
	return -ENODEV;
}

static void __exit mpss_mem_exit(void)
{
	debugfs_remove(dent);
	if (use_wc)
		memunmap(mpss);
	else
		iounmap(mpss);
	mpss = NULL;
	pr_info("mpss_mem: unloaded\n");
}

module_init(mpss_mem_init);
module_exit(mpss_mem_exit);

MODULE_LICENSE("GPL");
MODULE_DESCRIPTION("Read-only debugfs window onto the no-map mpss reserved region");
