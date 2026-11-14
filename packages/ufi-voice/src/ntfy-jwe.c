/*
 * ntfy-jwe - build an end-to-end encrypted ntfy message.
 *
 * NTFY me (and the ntfy E2E draft, ntfy#69) sends messages as a JWE compact
 * serialization with "dir"/A256GCM: the content encryption key is derived from
 * the topic password, so the server only ever stores ciphertext.
 *
 *   salt = SHA-256(topic URL)
 *   key  = PBKDF2-HMAC-SHA256(password, salt, 50000, 32 bytes)
 *   JWE  = b64url(header) "." "" "." b64url(iv) "." b64url(ct) "." b64url(tag)
 *
 * where header = {"alg":"dir","enc":"A256GCM"} and the base64url header is the
 * AAD. The plaintext is the ntfy message JSON, so the title, tags and priority
 * are inside the ciphertext too.
 *
 * Usage: NTFY_TOPIC_PASSWORD=... ntfy-jwe <topic-url> < payload.json
 * The password is read from the environment, not from argv, so it does not
 * show up in ps.
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <openssl/evp.h>
#include <openssl/rand.h>

#define HEADER_JSON "{\"alg\":\"dir\",\"enc\":\"A256GCM\"}"
#define KEY_LEN     32
#define IV_LEN      12
#define TAG_LEN     16
#define PBKDF2_ITER 50000

static const char b64url_tab[] =
	"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

/* base64url without padding, as the JWE compact form requires */
static char *b64url(const unsigned char *in, size_t len)
{
	size_t out_len = (len + 2) / 3 * 4;
	char *out = malloc(out_len + 1);
	size_t i, o = 0;

	if (!out)
		return NULL;
	for (i = 0; i + 2 < len; i += 3) {
		unsigned v = (in[i] << 16) | (in[i + 1] << 8) | in[i + 2];
		out[o++] = b64url_tab[(v >> 18) & 63];
		out[o++] = b64url_tab[(v >> 12) & 63];
		out[o++] = b64url_tab[(v >> 6) & 63];
		out[o++] = b64url_tab[v & 63];
	}
	if (len - i == 1) {
		unsigned v = in[i] << 16;
		out[o++] = b64url_tab[(v >> 18) & 63];
		out[o++] = b64url_tab[(v >> 12) & 63];
	} else if (len - i == 2) {
		unsigned v = (in[i] << 16) | (in[i + 1] << 8);
		out[o++] = b64url_tab[(v >> 18) & 63];
		out[o++] = b64url_tab[(v >> 12) & 63];
		out[o++] = b64url_tab[(v >> 6) & 63];
	}
	out[o] = '\0';
	return out;
}

static int die(const char *what)
{
	fprintf(stderr, "ntfy-jwe: %s\n", what);
	return 1;
}

int main(int argc, char **argv)
{
	const char *url, *password;
	unsigned char salt[32], key[KEY_LEN], iv[IV_LEN], tag[TAG_LEN];
	unsigned char *plain = NULL, *ct = NULL;
	size_t plain_len = 0, plain_cap = 4096, ct_len = 0, n;
	char *h64, *iv64, *ct64, *tag64;
	EVP_MD_CTX *md = NULL;
	EVP_CIPHER_CTX *c = NULL;
	int outl = 0, rc = 1;

	if (argc != 2)
		return die("usage: ntfy-jwe <topic-url> < payload.json");
	url = argv[1];
	password = getenv("NTFY_TOPIC_PASSWORD");
	if (!password || !*password)
		return die("NTFY_TOPIC_PASSWORD is not set");

	plain = malloc(plain_cap + 1);
	if (!plain)
		return die("out of memory");
	while ((n = fread(plain + plain_len, 1, plain_cap - plain_len, stdin)) > 0) {
		plain_len += n;
		if (plain_len == plain_cap) {
			if (plain_cap > 65536)
				return die("payload too large");
			plain_cap *= 2;
			unsigned char *p = realloc(plain, plain_cap + 1);
			if (!p)
				return die("out of memory");
			plain = p;
		}
	}
	plain[plain_len] = '\0';

	/* salt = SHA-256(topic URL) */
	md = EVP_MD_CTX_new();
	if (!md || EVP_DigestInit_ex(md, EVP_sha256(), NULL) != 1 ||
	    EVP_DigestUpdate(md, url, strlen(url)) != 1 ||
	    EVP_DigestFinal_ex(md, salt, NULL) != 1)
		return die("SHA-256 failed");

	if (PKCS5_PBKDF2_HMAC(password, strlen(password), salt, sizeof(salt),
			      PBKDF2_ITER, EVP_sha256(), KEY_LEN, key) != 1)
		return die("PBKDF2 failed");

	if (RAND_bytes(iv, sizeof(iv)) != 1)
		return die("no random IV");

	h64 = b64url((const unsigned char *)HEADER_JSON, strlen(HEADER_JSON));
	if (!h64)
		return die("out of memory");

	ct = malloc(plain_len + 16);
	if (!ct)
		return die("out of memory");
	c = EVP_CIPHER_CTX_new();
	if (!c || EVP_EncryptInit_ex(c, EVP_aes_256_gcm(), NULL, NULL, NULL) != 1 ||
	    EVP_CIPHER_CTX_ctrl(c, EVP_CTRL_GCM_SET_IVLEN, IV_LEN, NULL) != 1 ||
	    EVP_EncryptInit_ex(c, NULL, NULL, key, iv) != 1)
		return die("AES-256-GCM init failed");
	/* the base64url header is the additional authenticated data */
	if (EVP_EncryptUpdate(c, NULL, &outl, (const unsigned char *)h64,
			      strlen(h64)) != 1)
		return die("AAD failed");
	if (EVP_EncryptUpdate(c, ct, &outl, plain, plain_len) != 1)
		return die("encrypt failed");
	ct_len = outl;
	if (EVP_EncryptFinal_ex(c, ct + ct_len, &outl) != 1)
		return die("encrypt final failed");
	ct_len += outl;
	if (EVP_CIPHER_CTX_ctrl(c, EVP_CTRL_GCM_GET_TAG, TAG_LEN, tag) != 1)
		return die("tag failed");

	iv64 = b64url(iv, sizeof(iv));
	ct64 = b64url(ct, ct_len);
	tag64 = b64url(tag, sizeof(tag));
	if (!iv64 || !ct64 || !tag64)
		return die("out of memory");

	/* compact JWE: header..iv.ct.tag (empty encrypted key for "dir") */
	printf("%s..%s.%s.%s\n", h64, iv64, ct64, tag64);
	rc = 0;
	return rc;
}
