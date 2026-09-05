/*
 * Device-specific No-Sleep modem firmware patcher for Generic HMU05
 *
 * Disables LTE ML1 sleep manager in modem.b16 and updates
 * the SHA-256 digest in modem.mdt and modem.b01.
 */

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <unistd.h>
#include <sys/stat.h>

/* --- Minimal Self-Contained SHA-256 Implementation --- */
typedef struct {
    uint32_t state[8];
    uint64_t count;
    uint8_t buffer[64];
} sha256_ctx;

#define ROTR(x, n) (((x) >> (n)) | ((x) << (32 - (n))))
#define CH(x, y, z) (((x) & (y)) ^ (~(x) & (z)))
#define MAJ(x, y, z) (((x) & (y)) ^ ((x) & (z)) ^ ((y) & (z)))
#define EP0(x) (ROTR(x, 2) ^ ROTR(x, 13) ^ ROTR(x, 22))
#define EP1(x) (ROTR(x, 6) ^ ROTR(x, 11) ^ ROTR(x, 25))
#define SIG0(x) (ROTR(x, 7) ^ ROTR(x, 18) ^ ((x) >> 3))
#define SIG1(x) (ROTR(x, 17) ^ ROTR(x, 19) ^ ((x) >> 10))

static const uint32_t sha256_k[64] = {
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
    0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
    0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
    0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
};

static void sha256_transform(sha256_ctx *ctx, const uint8_t data[64]) {
    uint32_t a, b, c, d, e, f, g, h, i, j, t1, t2, m[64];
    for (i = 0, j = 0; i < 16; ++i, j += 4)
        m[i] = (data[j] << 24) | (data[j + 1] << 16) | (data[j + 2] << 8) | (data[j + 3]);
    for (; i < 64; ++i)
        m[i] = SIG1(m[i - 2]) + m[i - 7] + SIG0(m[i - 15]) + m[i - 16];

    a = ctx->state[0]; b = ctx->state[1]; c = ctx->state[2]; d = ctx->state[3];
    e = ctx->state[4]; f = ctx->state[5]; g = ctx->state[6]; h = ctx->state[7];

    for (i = 0; i < 64; ++i) {
        t1 = h + EP1(e) + CH(e, f, g) + sha256_k[i] + m[i];
        t2 = EP0(a) + MAJ(a, b, c);
        h = g; g = f; f = e; e = d + t1;
        d = c; c = b; b = a; a = t1 + t2;
    }

    ctx->state[0] += a; ctx->state[1] += b; ctx->state[2] += c; ctx->state[3] += d;
    ctx->state[4] += e; ctx->state[5] += f; ctx->state[6] += g; ctx->state[7] += h;
}

static void sha256_init(sha256_ctx *ctx) {
    ctx->state[0] = 0x6a09e667; ctx->state[1] = 0xbb67ae85;
    ctx->state[2] = 0x3c6ef372; ctx->state[3] = 0xa54ff53a;
    ctx->state[4] = 0x510e527f; ctx->state[5] = 0x9b05688c;
    ctx->state[6] = 0x1f83d9ab; ctx->state[7] = 0x5be0cd19;
    ctx->count = 0;
}

static void sha256_update(sha256_ctx *ctx, const uint8_t *data, size_t len) {
    size_t i;
    for (i = 0; i < len; ++i) {
        ctx->buffer[ctx->count % 64] = data[i];
        ctx->count++;
        if ((ctx->count % 64) == 0)
            sha256_transform(ctx, ctx->buffer);
    }
}

static void sha256_final(sha256_ctx *ctx, uint8_t hash[32]) {
    uint32_t i = ctx->count % 64;
    ctx->buffer[i++] = 0x80;
    if (i > 56) {
        while (i < 64) ctx->buffer[i++] = 0x00;
        sha256_transform(ctx, ctx->buffer);
        memset(ctx->buffer, 0, 56);
    } else {
        while (i < 56) ctx->buffer[i++] = 0x00;
    }
    uint64_t total_bits = ctx->count * 8;
    for (i = 0; i < 8; ++i)
        ctx->buffer[63 - i] = (uint8_t)(total_bits >> (i * 8));
    sha256_transform(ctx, ctx->buffer);
    for (i = 0; i < 8; ++i) {
        hash[i * 4]     = (uint8_t)(ctx->state[i] >> 24);
        hash[i * 4 + 1] = (uint8_t)(ctx->state[i] >> 16);
        hash[i * 4 + 2] = (uint8_t)(ctx->state[i] >> 8);
        hash[i * 4 + 3] = (uint8_t)(ctx->state[i]);
    }
}

/* --- Patching Logic --- */

static int is_hmu05_board(void) {
    char buf[256];
    FILE *f;

    f = fopen("/tmp/sysinfo/board_name", "r");
    if (f) {
        if (fgets(buf, sizeof(buf), f)) {
            fclose(f);
            if (strstr(buf, "hmu05") || strstr(buf, "HMU05"))
                return 1;
        } else {
            fclose(f);
        }
    }

    f = fopen("/proc/device-tree/model", "r");
    if (f) {
        if (fgets(buf, sizeof(buf), f)) {
            fclose(f);
            if (strstr(buf, "hmu05") || strstr(buf, "HMU05"))
                return 1;
        } else {
            fclose(f);
        }
    }

    f = fopen("/proc/device-tree/compatible", "r");
    if (f) {
        size_t n = fread(buf, 1, sizeof(buf) - 1, f);
        fclose(f);
        if (n > 0) {
            buf[n] = '\0';
            if (strstr(buf, "hmu05") || strstr(buf, "HMU05"))
                return 1;
        }
    }

    return 0;
}

int main(int argc, char *argv[]) {
    const char *fw_dir = (argc > 1) ? argv[1] : "/lib/firmware";
    char b16_path[512], mdt_path[512], b01_path[512];
    uint8_t *b16_data = NULL;
    size_t b16_size;
    uint8_t digest[32];
    FILE *f;

    if (!is_hmu05_board()) {
        /* Not an HMU05 board, exit cleanly */
        return 0;
    }

    snprintf(b16_path, sizeof(b16_path), "%s/modem.b16", fw_dir);
    snprintf(mdt_path, sizeof(mdt_path), "%s/modem.mdt", fw_dir);
    snprintf(b01_path, sizeof(b01_path), "%s/modem.b01", fw_dir);

    if (access(b16_path, F_OK) != 0 || access(mdt_path, F_OK) != 0 || access(b01_path, F_OK) != 0) {
        /* Firmware files not present, exit cleanly */
        return 0;
    }

    /* Open and read modem.b16 */
    f = fopen(b16_path, "rb");
    if (!f) return 1;
    fseek(f, 0, SEEK_END);
    b16_size = ftell(f);
    fseek(f, 0, SEEK_SET);

    if (b16_size < 0x001117e8) {
        fclose(f);
        return 0;
    }

    b16_data = malloc(b16_size);
    if (!b16_data) { fclose(f); return 1; }
    if (fread(b16_data, 1, b16_size, f) != b16_size) {
        free(b16_data);
        fclose(f);
        return 1;
    }
    fclose(f);

    /* Check if already patched at 0x001117e0 (00 c4 00 78) */
    static const uint8_t patch_sleepmgr[8] = { 0x00, 0xc4, 0x00, 0x78, 0x00, 0xc0, 0x9f, 0x52 };

    if (memcmp(&b16_data[0x001117e0], patch_sleepmgr, 4) == 0) {
        /* Already patched */
        free(b16_data);
        return 0;
    }

    /* Apply patch in b16: disable 900s DRX sleep timer in lte_ml1_sleepmgr_cfg */
    memcpy(&b16_data[0x001117e0], patch_sleepmgr, 8);

    /* Write back modem.b16 */
    f = fopen(b16_path, "wb");
    if (!f) { free(b16_data); return 1; }
    if (fwrite(b16_data, 1, b16_size, f) != b16_size) {
        free(b16_data);
        fclose(f);
        return 1;
    }
    fclose(f);

    /* Compute SHA-256 of patched modem.b16 */
    sha256_ctx ctx;
    sha256_init(&ctx);
    sha256_update(&ctx, b16_data, b16_size);
    sha256_final(&ctx, digest);
    free(b16_data);

    /* Update modem.mdt digest at offset 0x05bc */
    f = fopen(mdt_path, "r+b");
    if (f) {
        fseek(f, 0, SEEK_END);
        size_t mdt_size = ftell(f);
        if (mdt_size >= 0x05bc + 32) {
            fseek(f, 0x05bc, SEEK_SET);
            fwrite(digest, 1, 32, f);
        }
        fclose(f);
    }

    /* Update modem.b01 digest at offset 0x0228 */
    f = fopen(b01_path, "r+b");
    if (f) {
        fseek(f, 0, SEEK_END);
        size_t b01_size = ftell(f);
        if (b01_size >= 0x0228 + 32) {
            fseek(f, 0x0228, SEEK_SET);
            fwrite(digest, 1, 32, f);
        }
        fclose(f);
    }

    printf("Applied No-Sleep patch to HMU05 modem firmware\n");
    return 0;
}
