/* Decompiled from: /home/shaanair/Projects/msm8916-openwrt-clean/GitIgnore/MelbonWhiteStock_Dump/system/bin/rild */
/* Language: ARM:LE:32:v8 */

/* ---- Function: __libc_init @ 00010a68 ---- */

void __libc_init(void)

{
  (*(code *)PTR___libc_init_00012f5c)();
  return;
}



/* ---- Function: __cxa_atexit @ 00010a74 ---- */

void __cxa_atexit(void)

{
  (*(code *)PTR___cxa_atexit_00012f60)();
  return;
}



/* ---- Function: prctl @ 00010a80 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int prctl(int __option,...)

{
  int iVar1;
  
  iVar1 = (*(code *)PTR_prctl_00012f64)(__option);
  return iVar1;
}



/* ---- Function: setuid @ 00010a8c ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int setuid(__uid_t __uid)

{
  int iVar1;
  
  iVar1 = (*(code *)PTR_setuid_00012f68)(__uid);
  return iVar1;
}



/* ---- Function: capset @ 00010a98 ---- */

void capset(void)

{
  (*(code *)PTR_capset_00012f6c)();
  return;
}



/* ---- Function: __android_log_buf_print @ 00010aa4 ---- */

void __android_log_buf_print(void)

{
  (*(code *)PTR___android_log_buf_print_00012f70)();
  return;
}



/* ---- Function: umask @ 00010ab0 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

__mode_t umask(__mode_t __mask)

{
  __mode_t _Var1;
  
  _Var1 = (*(code *)PTR_umask_00012f74)(__mask);
  return _Var1;
}



/* ---- Function: strcmp @ 00010abc ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int strcmp(char *__s1,char *__s2)

{
  int iVar1;
  
  iVar1 = (*(code *)PTR_strcmp_00012f78)(__s1);
  return iVar1;
}



/* ---- Function: fprintf @ 00010ac8 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int fprintf(FILE *__stream,char *__format,...)

{
  int iVar1;
  
  iVar1 = (*(code *)PTR_fprintf_00012f7c)(__stream);
  return iVar1;
}



/* ---- Function: atoi @ 00010ad4 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int atoi(char *__nptr)

{
  int iVar1;
  
  iVar1 = (*(code *)PTR_atoi_00012f80)(__nptr);
  return iVar1;
}



/* ---- Function: exit @ 00010ae0 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

void exit(int __status)

{
  (*(code *)PTR_exit_00012f84)(__status);
  return;
}



/* ---- Function: strncmp @ 00010aec ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int strncmp(char *__s1,char *__s2,size_t __n)

{
  int iVar1;
  
  iVar1 = (*(code *)PTR_strncmp_00012f88)(__s1);
  return iVar1;
}



/* ---- Function: __strncat_chk @ 00010af8 ---- */

void __strncat_chk(void)

{
  (*(code *)PTR___strncat_chk_00012f8c)();
  return;
}



/* ---- Function: RIL_setRilSocketName @ 00010b04 ---- */

void RIL_setRilSocketName(void)

{
  (*(code *)PTR_RIL_setRilSocketName_00012f90)();
  return;
}



/* ---- Function: property_get @ 00010b10 ---- */

void property_get(void)

{
  (*(code *)PTR_property_get_00012f94)();
  return;
}



/* ---- Function: memset @ 00010b1c ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

void * memset(void *__s,int __c,size_t __n)

{
  void *pvVar1;
  
  pvVar1 = (void *)(*(code *)PTR_memset_00012f98)(__s);
  return pvVar1;
}



/* ---- Function: open @ 00010b28 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int open(char *__file,int __oflag,...)

{
  int iVar1;
  
  iVar1 = (*(code *)PTR_open_00012f9c)(__file);
  return iVar1;
}



/* ---- Function: __errno @ 00010b34 ---- */

void __errno(void)

{
  (*(code *)PTR___errno_00012fa0)();
  return;
}



/* ---- Function: strerror @ 00010b40 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

char * strerror(int __errnum)

{
  char *pcVar1;
  
  pcVar1 = (char *)(*(code *)PTR_strerror_00012fa4)(__errnum);
  return pcVar1;
}



/* ---- Function: read @ 00010b4c ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

ssize_t read(int __fd,void *__buf,size_t __nbytes)

{
  ssize_t sVar1;
  
  sVar1 = (*(code *)PTR_read_00012fa8)(__fd);
  return sVar1;
}



/* ---- Function: close @ 00010b58 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int close(int __fd)

{
  int iVar1;
  
  iVar1 = (*(code *)PTR_close_00012fac)(__fd);
  return iVar1;
}



/* ---- Function: strstr @ 00010b64 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

char * strstr(char *__haystack,char *__needle)

{
  char *pcVar1;
  
  pcVar1 = (char *)(*(code *)PTR_strstr_00012fb0)(__haystack);
  return pcVar1;
}



/* ---- Function: sleep @ 00010b70 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

uint sleep(uint __seconds)

{
  uint uVar1;
  
  uVar1 = (*(code *)PTR_sleep_00012fb4)(__seconds);
  return uVar1;
}



/* ---- Function: snprintf @ 00010b7c ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int snprintf(char *__s,size_t __maxlen,char *__format,...)

{
  int iVar1;
  
  iVar1 = (*(code *)PTR_snprintf_00012fb8)(__s);
  return iVar1;
}



/* ---- Function: __strlen_chk @ 00010b88 ---- */

void __strlen_chk(void)

{
  (*(code *)PTR___strlen_chk_00012fbc)();
  return;
}



/* ---- Function: write @ 00010b94 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

ssize_t write(int __fd,void *__buf,size_t __n)

{
  ssize_t sVar1;
  
  sVar1 = (*(code *)PTR_write_00012fc0)(__fd);
  return sVar1;
}



/* ---- Function: socket_local_client @ 00010ba0 ---- */

void socket_local_client(void)

{
  (*(code *)PTR_socket_local_client_00012fc4)();
  return;
}



/* ---- Function: strpbrk @ 00010bac ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

char * strpbrk(char *__s,char *__accept)

{
  char *pcVar1;
  
  pcVar1 = (char *)(*(code *)PTR_strpbrk_00012fc8)(__s);
  return pcVar1;
}



/* ---- Function: dlopen @ 00010bb8 ---- */

void dlopen(void)

{
  (*(code *)PTR_dlopen_00012fcc)();
  return;
}



/* ---- Function: dlerror @ 00010bc4 ---- */

void dlerror(void)

{
  (*(code *)PTR_dlerror_00012fd0)();
  return;
}



/* ---- Function: RIL_startEventLoop @ 00010bd0 ---- */

void RIL_startEventLoop(void)

{
  (*(code *)PTR_RIL_startEventLoop_00012fd4)();
  return;
}



/* ---- Function: dlsym @ 00010bdc ---- */

void dlsym(void)

{
  (*(code *)PTR_dlsym_00012fd8)();
  return;
}



/* ---- Function: strtok @ 00010be8 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

char * strtok(char *__s,char *__delim)

{
  char *pcVar1;
  
  pcVar1 = (char *)(*(code *)PTR_strtok_00012fdc)(__s);
  return pcVar1;
}



/* ---- Function: RIL_register @ 00010bf4 ---- */

void RIL_register(void)

{
  (*(code *)PTR_RIL_register_00012fe0)();
  return;
}



/* ---- Function: RIL_onRequestComplete @ 00010c00 ---- */

void RIL_onRequestComplete(void)

{
                    /* WARNING: Could not recover jumptable at 0x00010c08. Too many branches */
                    /* WARNING: Treating indirect jump as call */
  (*(code *)PTR_00012fe4)();
  return;
}



/* ---- Function: RIL_onUnsolicitedResponse @ 00010c0c ---- */

void RIL_onUnsolicitedResponse(void)

{
                    /* WARNING: Could not recover jumptable at 0x00010c14. Too many branches */
                    /* WARNING: Treating indirect jump as call */
  (*(code *)PTR_00012fe8)();
  return;
}



/* ---- Function: RIL_requestTimedCallback @ 00010c18 ---- */

void RIL_requestTimedCallback(void)

{
                    /* WARNING: Could not recover jumptable at 0x00010c20. Too many branches */
                    /* WARNING: Treating indirect jump as call */
  (*(code *)PTR_00012fec)();
  return;
}



/* ---- Function: calloc @ 00010c24 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

void * calloc(size_t __nmemb,size_t __size)

{
  void *pvVar1;
  
  pvVar1 = (void *)(*(code *)PTR_calloc_00012ff0)(__nmemb);
  return pvVar1;
}



/* ---- Function: malloc @ 00010c30 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

void * malloc(size_t __size)

{
  void *pvVar1;
  
  pvVar1 = (void *)(*(code *)PTR_malloc_00012ff4)(__size);
  return pvVar1;
}



/* ---- Function: free @ 00010c3c ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

void free(void *__ptr)

{
  (*(code *)PTR_free_00012ff8)(__ptr);
  return;
}



/* ---- Function: memmove @ 00010c48 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

void * memmove(void *__dest,void *__src,size_t __n)

{
  void *pvVar1;
  
  pvVar1 = (void *)(*(code *)PTR_memmove_00012ffc)(__dest);
  return pvVar1;
}



/* ---- Function: entry @ 00010c54 ---- */

/* WARNING: Control flow encountered bad instruction data */

void processEntry entry(void)

{
  int iVar1;
  undefined4 local_18;
  undefined4 local_14;
  undefined4 local_10;
  
  iVar1 = iRam00010cb8 + 0x10c6c;
  local_18 = *(undefined4 *)(iVar1 + DAT_00010cbc);
  local_14 = *(undefined4 *)(iVar1 + DAT_00010cc0);
  local_10 = *(undefined4 *)(iVar1 + DAT_00010cc4);
  __libc_init(&stack0x00000000,0,*(undefined4 *)(iVar1 + DAT_00010cc8),&local_18);
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: FUN_00010ccc @ 00010ccc ---- */

undefined4 FUN_00010ccc(undefined4 param_1)

{
  undefined4 uVar1;
  
  uVar1 = __cxa_atexit(param_1,0,DAT_00010d04 + 0x10cf0);
  return uVar1;
}



/* ---- Function: FUN_00010d08 @ 00010d08 ---- */

void FUN_00010d08(void)

{
  undefined4 local_28;
  undefined4 local_24;
  undefined4 local_20;
  undefined4 local_1c;
  undefined4 local_18;
  undefined4 local_14;
  undefined4 local_10;
  undefined4 local_c;
  
  prctl(8,1,0,0,0);
  setuid(0x3e9);
  local_10 = 0x10;
  local_1c = 0x3000;
  local_20 = 0x3000;
  local_14 = 0x10;
  local_28 = DAT_00010d48;
  local_24 = 0;
  local_18 = 0;
  local_c = 0;
  capset(&local_28,&local_20);
  return;
}



/* ---- Function: FUN_00010d4c @ 00010d4c ---- */

void FUN_00010d4c(int param_1,undefined4 *param_2)

{
  int iVar1;
  int *piVar2;
  ssize_t sVar3;
  size_t sVar4;
  code *pcVar5;
  char *pcVar6;
  char *pcVar7;
  int iVar8;
  char *__nptr;
  undefined1 *puVar9;
  undefined4 uVar10;
  undefined4 *puVar11;
  char *__s2;
  char *__s1;
  int iVar12;
  int iVar13;
  undefined1 auStack_588 [92];
  char acStack_52c [256];
  char acStack_42c [1024];
  undefined4 local_2c;
  
  iVar12 = DAT_00011184 + 0x10d64;
  iVar8 = DAT_0001118c + 0x10d74;
  __s2 = (char *)(DAT_00011190 + 0x10d7a);
  local_2c = **(undefined4 **)(iVar12 + DAT_00011188);
  __android_log_buf_print(1,3,iVar8,DAT_00011194 + 0x10d84);
  __nptr = (char *)0x0;
  __android_log_buf_print(1,3,iVar8,DAT_00011198 + 0x10d9a,param_1);
  umask(0x3f);
  puVar9 = (undefined1 *)0x0;
  pcVar6 = (char *)(DAT_0001119c + 0x10db2);
  pcVar7 = (char *)(DAT_000111a0 + 0x10db6);
  puVar11 = param_2;
  for (iVar8 = 1; iVar8 < param_1; iVar8 = iVar8 + 2) {
    __s1 = (char *)puVar11[1];
    iVar13 = strcmp(__s1,pcVar6);
    if ((iVar13 == 0) && (1 < param_1 - iVar8)) {
      puVar9 = (undefined1 *)puVar11[2];
    }
    else {
      iVar13 = strcmp(__s1,__s2);
      if (iVar13 == 0) {
        iVar8 = iVar8 + 1;
        iVar13 = 1;
        goto LAB_00010e2a;
      }
      iVar13 = strcmp(__s1,pcVar7);
      if ((iVar13 != 0) || (param_1 - iVar8 < 2)) {
        fprintf((FILE *)(*(int *)(iVar12 + DAT_000111a4) + 0xa8),(char *)(DAT_000111a8 + 0x10e12),
                *param_2);
        goto LAB_0001108a;
      }
      __nptr = (char *)puVar11[2];
    }
    puVar11 = puVar11 + 2;
  }
  iVar13 = 0;
LAB_00010e2a:
  if (__nptr == (char *)0x0) {
    __nptr = (char *)(DAT_000111b4 + 0x10e56);
  }
  else {
    iVar1 = atoi(__nptr);
    if (2 < iVar1) {
      __android_log_buf_print(1,6,DAT_000111ac + 0x10e46,DAT_000111b0 + 0x10e48,3);
      iVar8 = 0;
      goto LAB_00010e4c;
    }
  }
  iVar1 = strncmp(__nptr,(char *)(DAT_000111b8 + 0x10e5e),2);
  if (iVar1 != 0) {
    __strncat_chk(*(undefined4 *)(iVar12 + DAT_000111bc),__nptr,6,6);
    RIL_setRilSocketName();
  }
  if (puVar9 == (undefined1 *)0x0) {
    iVar12 = property_get(DAT_000111c0 + 0x10e82,auStack_588,0);
    if (iVar12 == 0) goto LAB_00011116;
    puVar9 = auStack_588;
  }
  memset(acStack_42c,0,0x400);
  iVar12 = open((char *)(DAT_000111c4 + 0x10ea0),0);
  if (iVar12 < 0) {
    piVar2 = (int *)__errno();
    pcVar6 = strerror(*piVar2);
    __android_log_buf_print(1,3,DAT_000111c8 + 0x10ebc,DAT_000111cc + 0x10ebe,pcVar6);
  }
  else {
    do {
      sVar3 = read(iVar12,acStack_42c,0x400);
      if (sVar3 != -1) {
        if (-1 < sVar3) {
          close(iVar12);
          pcVar6 = strstr(acStack_42c,(char *)(DAT_000111d8 + 0x10f16));
          if (pcVar6 != (char *)0x0) {
            iVar8 = 5;
            pcVar6 = (char *)(DAT_000111dc + 0x10f2e);
            iVar12 = DAT_000111e0 + 0x10f30;
            goto LAB_00010f2e;
          }
          pcVar7 = strstr(acStack_42c,(char *)(DAT_00011260 + 0x11146));
          if (pcVar7 == (char *)0x0) goto LAB_00011042;
          pcVar6 = strpbrk(pcVar7 + 0xc,(char *)(DAT_0001120c + 0x11016));
          if (pcVar6 != (char *)0x0) {
            *pcVar6 = '\0';
          }
          iVar13 = DAT_00011210;
          pcVar6 = (char *)(DAT_00011210 + 0x1102a);
          snprintf(pcVar6,0x20,(char *)(DAT_00011214 + 0x1102c),pcVar7 + 0xc);
          iVar8 = DAT_0001121c;
          iVar12 = DAT_00011218 + 0x1103a;
          *(undefined1 *)(iVar13 + 0x11049) = 0;
          iVar8 = iVar8 + 0x1103e;
          goto LAB_0001103c;
        }
        break;
      }
      piVar2 = (int *)__errno();
    } while (*piVar2 == 4);
    piVar2 = (int *)__errno();
    pcVar6 = strerror(*piVar2);
    __android_log_buf_print(1,3,DAT_000111d0 + 0x10ef4,DAT_000111d4 + 0x10ef6,pcVar6);
    close(iVar12);
  }
LAB_00011042:
  FUN_00010d08();
  iVar12 = dlopen(puVar9,0);
  if (iVar12 == 0) {
    puVar9 = (undefined1 *)dlerror();
    iVar8 = DAT_00011220 + 0x11060;
    iVar12 = DAT_00011224 + 0x11062;
  }
  else {
    RIL_startEventLoop();
    pcVar5 = (code *)dlsym(iVar12,DAT_00011228 + 0x11072);
    iVar12 = DAT_00011244;
    if (pcVar5 != (code *)0x0) {
      if (iVar13 == 0) {
        iVar13 = 1;
        pcVar6 = (char *)(DAT_00011234 + 0x110ae);
        pcVar7 = (char *)(DAT_00011240 + 0x110bc);
        property_get(DAT_00011238 + 0x110b4,pcVar6,DAT_0001123c + 0x110ba);
        while (pcVar6 = strtok(pcVar6,pcVar7), pcVar6 != (char *)0x0) {
          *(char **)(iVar12 + 0x110c2 + iVar13 * 4) = pcVar6;
          iVar13 = iVar13 + 1;
          pcVar6 = (char *)0x0;
        }
        puVar11 = (undefined4 *)(DAT_00011248 + 0x110dc);
      }
      else {
        iVar13 = (param_1 - iVar8) + 1;
        puVar11 = param_2 + iVar8 + -1;
      }
      iVar8 = DAT_00011250;
      puVar11[iVar13] = DAT_0001124c + 0x110e6;
      puVar11[iVar13 + 1] = __nptr;
      __android_log_buf_print
                (1,3,DAT_00011254 + 0x11100,iVar8 + 0x110fa,iVar13 + 2,puVar11[iVar13 + 1]);
      iVar8 = DAT_00011258;
      *puVar11 = *param_2;
      (*pcVar5)(iVar8 + 0x11112,iVar13 + 2,puVar11);
      RIL_register();
LAB_00011116:
      do {
        sleep(0xffffff);
      } while( true );
    }
    iVar8 = DAT_0001122c + 0x11084;
    iVar12 = DAT_00011230 + 0x11088;
  }
  __android_log_buf_print(1,6,iVar8,iVar12,puVar9);
LAB_0001108a:
  iVar8 = -1;
LAB_00010e4c:
                    /* WARNING: Subroutine does not return */
  exit(iVar8);
LAB_00010f2e:
  sleep(1);
  snprintf(acStack_52c,0x100,pcVar6,iVar12);
  iVar13 = open((char *)(DAT_000111e4 + 0x10f4a),2);
  if (-1 < iVar13) {
    iVar1 = __strlen_chk(acStack_52c,0x100);
    do {
      sVar4 = write(iVar13,acStack_52c,iVar1 + 1U);
      if (sVar4 != 0xffffffff) {
        if (sVar4 == iVar1 + 1U) goto LAB_00010f94;
        if (sVar4 == 0) {
          puVar11 = (undefined4 *)__errno();
          uVar10 = 0x68;
        }
        else {
          if ((int)sVar4 < 1) goto LAB_00010f9a;
          puVar11 = (undefined4 *)__errno();
          uVar10 = 0x16;
        }
        *puVar11 = uVar10;
        goto LAB_00010f9a;
      }
      piVar2 = (int *)__errno();
    } while (*piVar2 == 4);
    if (iVar1 != -2) goto LAB_00010f9a;
LAB_00010f94:
    if (iVar13 == -1) goto LAB_00010f9a;
LAB_00010fae:
    iVar8 = DAT_000111ec;
    close(iVar13);
    pcVar6 = (char *)(iVar8 + 0x10fbc);
    snprintf(pcVar6,0x20,(char *)(DAT_000111f4 + 0x10fca),DAT_000111f8 + 0x10fcc,
             DAT_000111f0 + 0x10fc0);
    iVar12 = DAT_000111fc + 0x10fd6;
    iVar8 = DAT_00011200 + 0x10fd8;
LAB_0001103c:
    *(int *)(iVar12 + 4) = iVar8;
    *(char **)(iVar12 + 8) = pcVar6;
    iVar12 = DAT_00011274;
    iVar8 = 1;
    param_2 = (undefined4 *)(DAT_00011264 + 0x1115a);
    __android_log_buf_print
              (1,3,DAT_0001126c + 0x11164,DAT_00011270 + 0x11168,
               *(undefined4 *)(DAT_00011264 + 0x1115e),DAT_00011268 + 0x11160);
    puVar9 = (undefined1 *)(iVar12 + 0x1117a);
    param_1 = 3;
    iVar13 = iVar8;
    goto LAB_00011042;
  }
LAB_00010f9a:
  iVar1 = DAT_000111e8 + 0x10fa2;
  iVar13 = socket_local_client(iVar1,1,1);
  if (-1 < iVar13) goto LAB_00010fae;
  piVar2 = (int *)__errno();
  iVar13 = DAT_00011204 + 0x10fe4;
  pcVar7 = strerror(*piVar2);
  __android_log_buf_print(1,3,iVar13,DAT_00011208 + 0x10ff4,iVar1,pcVar7);
  iVar8 = iVar8 + -1;
  if (iVar8 == 0) {
    piVar2 = (int *)__errno();
    pcVar6 = strerror(*piVar2);
    __android_log_buf_print(1,6,iVar13,DAT_0001125c + 0x11136,iVar1,pcVar6);
    do {
      sleep(0xffffff);
    } while( true );
  }
  goto LAB_00010f2e;
}



/* ---- Function: FUN_00011278 @ 00011278 ---- */

uint * FUN_00011278(uint *param_1,uint *param_2)

{
  uint uVar1;
  
  if (param_1 + 1 <= param_2) {
    uVar1 = *param_1;
    param_1 = (uint *)((int)param_1 +
                      (uVar1 << 0x18 | (uVar1 >> 8 & 0xff) << 0x10 | (uVar1 >> 0x10 & 0xff) << 8 |
                      uVar1 >> 0x18) + 4);
    if (param_2 < param_1) {
      param_1 = (uint *)0x0;
    }
    return param_1;
  }
  return (uint *)0x0;
}



/* ---- Function: record_stream_new @ 00011292 ---- */

undefined4 * record_stream_new(undefined4 param_1,int param_2)

{
  undefined4 *puVar1;
  void *pvVar2;
  
  puVar1 = calloc(1,0x18);
  puVar1[1] = param_2;
  *puVar1 = param_1;
  pvVar2 = malloc(param_2 + 4U);
  puVar1[2] = pvVar2;
  puVar1[3] = pvVar2;
  puVar1[4] = pvVar2;
  puVar1[5] = (int)pvVar2 + param_2 + 4U;
  return puVar1;
}



/* ---- Function: record_stream_free @ 000112bc ---- */

void record_stream_free(int param_1)

{
  free(*(void **)(param_1 + 8));
                    /* WARNING: Could not recover jumptable at 0x00011368. Too many branches */
                    /* WARNING: Treating indirect jump as call */
  (*(code *)(&DAT_00011370 + DAT_0001136c))(param_1);
  return;
}



/* ---- Function: record_stream_get_next @ 000112d0 ---- */

ssize_t record_stream_get_next(int *param_1,int *param_2,int *param_3,undefined4 param_4)

{
  int iVar1;
  undefined4 *puVar2;
  ssize_t sVar3;
  void *__src;
  undefined4 uVar4;
  int iVar5;
  size_t __n;
  
  iVar1 = FUN_00011278(param_1[3],param_1[4],param_3,param_4,param_4);
  if (iVar1 != 0) {
    iVar5 = param_1[3];
    param_1[3] = iVar1;
    iVar5 = iVar5 + 4;
    *param_3 = iVar1 - iVar5;
    if (iVar5 != 0) goto LAB_0001135a;
  }
  __src = (void *)param_1[3];
  if (__src == (void *)param_1[2]) {
    if (param_1[4] == param_1[5]) {
      puVar2 = (undefined4 *)__errno();
      uVar4 = 0x1b;
      goto LAB_00011344;
    }
  }
  else {
    __n = param_1[4] - (int)__src;
    if (__n != 0) {
      memmove((void *)param_1[2],__src,__n);
    }
    param_1[4] = param_1[2] + __n;
    param_1[3] = param_1[2];
  }
  sVar3 = read(*param_1,(void *)param_1[4],param_1[5] - param_1[4]);
  if (sVar3 < 1) {
    *param_2 = 0;
    return sVar3;
  }
  param_1[4] = param_1[4] + sVar3;
  iVar1 = FUN_00011278(param_1[3]);
  if (iVar1 != 0) {
    iVar5 = param_1[3];
    param_1[3] = iVar1;
    iVar5 = iVar5 + 4;
    *param_3 = iVar1 - iVar5;
    if (iVar5 != 0) {
LAB_0001135a:
      *param_2 = iVar5;
      return 0;
    }
  }
  puVar2 = (undefined4 *)__errno();
  uVar4 = 0xb;
LAB_00011344:
  *puVar2 = uVar4;
  return -1;
}



/* ---- Function: __libc_init @ 00014000 ---- */

/* WARNING: Control flow encountered bad instruction data */

void __libc_init(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: __cxa_atexit @ 00014004 ---- */

/* WARNING: Control flow encountered bad instruction data */

void __cxa_atexit(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: prctl @ 00014008 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int prctl(int __option,...)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: setuid @ 0001400c ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int setuid(__uid_t __uid)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: capset @ 00014010 ---- */

/* WARNING: Control flow encountered bad instruction data */

void capset(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: __aeabi_unwind_cpp_pr0 @ 00014014 ---- */

/* WARNING: Control flow encountered bad instruction data */

void __aeabi_unwind_cpp_pr0(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: __android_log_buf_print @ 00014018 ---- */

/* WARNING: Control flow encountered bad instruction data */

void __android_log_buf_print(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: umask @ 0001401c ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

__mode_t umask(__mode_t __mask)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: strcmp @ 00014020 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int strcmp(char *__s1,char *__s2)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: fprintf @ 00014024 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int fprintf(FILE *__stream,char *__format,...)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: atoi @ 00014028 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int atoi(char *__nptr)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: exit @ 0001402c ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

void exit(int __status)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: strncmp @ 00014030 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int strncmp(char *__s1,char *__s2,size_t __n)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: __strncat_chk @ 00014034 ---- */

/* WARNING: Control flow encountered bad instruction data */

void __strncat_chk(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: RIL_setRilSocketName @ 00014038 ---- */

/* WARNING: Control flow encountered bad instruction data */

void RIL_setRilSocketName(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: property_get @ 0001403c ---- */

/* WARNING: Control flow encountered bad instruction data */

void property_get(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: memset @ 00014040 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

void * memset(void *__s,int __c,size_t __n)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: open @ 00014044 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int open(char *__file,int __oflag,...)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: __errno @ 00014048 ---- */

/* WARNING: Control flow encountered bad instruction data */

void __errno(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: strerror @ 0001404c ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

char * strerror(int __errnum)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: read @ 00014050 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

ssize_t read(int __fd,void *__buf,size_t __nbytes)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: close @ 00014054 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int close(int __fd)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: strstr @ 00014058 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

char * strstr(char *__haystack,char *__needle)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: sleep @ 0001405c ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

uint sleep(uint __seconds)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: snprintf @ 00014060 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int snprintf(char *__s,size_t __maxlen,char *__format,...)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: __strlen_chk @ 00014064 ---- */

/* WARNING: Control flow encountered bad instruction data */

void __strlen_chk(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: write @ 00014068 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

ssize_t write(int __fd,void *__buf,size_t __n)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: socket_local_client @ 0001406c ---- */

/* WARNING: Control flow encountered bad instruction data */

void socket_local_client(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: strpbrk @ 00014070 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

char * strpbrk(char *__s,char *__accept)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: dlopen @ 00014074 ---- */

/* WARNING: Control flow encountered bad instruction data */

void dlopen(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: dlerror @ 00014078 ---- */

/* WARNING: Control flow encountered bad instruction data */

void dlerror(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: RIL_startEventLoop @ 0001407c ---- */

/* WARNING: Control flow encountered bad instruction data */

void RIL_startEventLoop(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: dlsym @ 00014080 ---- */

/* WARNING: Control flow encountered bad instruction data */

void dlsym(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: strtok @ 00014084 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

char * strtok(char *__s,char *__delim)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: RIL_register @ 00014088 ---- */

/* WARNING: Control flow encountered bad instruction data */

void RIL_register(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: __aeabi_unwind_cpp_pr1 @ 00014098 ---- */

/* WARNING: Control flow encountered bad instruction data */

void __aeabi_unwind_cpp_pr1(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: clock_gettime @ 0001409c ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int clock_gettime(clockid_t __clock_id,timespec *__tp)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: calloc @ 000140a0 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

void * calloc(size_t __nmemb,size_t __size)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: malloc @ 000140a4 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

void * malloc(size_t __size)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: free @ 000140a8 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

void free(void *__ptr)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: memmove @ 000140ac ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

void * memmove(void *__dest,void *__src,size_t __n)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



