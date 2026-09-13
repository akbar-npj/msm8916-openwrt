/* Decompiled from: /home/shaanair/Projects/msm8916-openwrt-clean/GitIgnore/MelbonWhiteStock_Dump/RE Needed/time_daemon */
/* Language: ARM:LE:32:v8 */

/* ---- Function: __libc_init @ 00010be4 ---- */

void __libc_init(void)

{
  (*(code *)PTR___libc_init_00014f38)();
  return;
}



/* ---- Function: __cxa_atexit @ 00010bf0 ---- */

void __cxa_atexit(void)

{
  (*(code *)PTR___cxa_atexit_00014f3c)();
  return;
}



/* ---- Function: open @ 00010bfc ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int open(char *__file,int __oflag,...)

{
  int iVar1;
  
  iVar1 = (*(code *)PTR_open_00014f40)(__file);
  return iVar1;
}



/* ---- Function: __android_log_print @ 00010c08 ---- */

void __android_log_print(void)

{
  (*(code *)PTR___android_log_print_00014f44)();
  return;
}



/* ---- Function: ioctl @ 00010c14 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int ioctl(int __fd,ulong __request,...)

{
  int iVar1;
  
  iVar1 = (*(code *)PTR_ioctl_00014f48)(__fd);
  return iVar1;
}



/* ---- Function: mktime @ 00010c20 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

time_t mktime(tm *__tp)

{
  time_t tVar1;
  
  tVar1 = (*(code *)PTR_mktime_00014f4c)(__tp);
  return tVar1;
}



/* ---- Function: close @ 00010c2c ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int close(int __fd)

{
  int iVar1;
  
  iVar1 = (*(code *)PTR_close_00014f50)(__fd);
  return iVar1;
}



/* ---- Function: socket @ 00010c38 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int socket(int __domain,int __type,int __protocol)

{
  int iVar1;
  
  iVar1 = (*(code *)PTR_socket_00014f54)(__domain);
  return iVar1;
}



/* ---- Function: pthread_exit @ 00010c44 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

void pthread_exit(void *__retval)

{
  (*(code *)PTR_pthread_exit_00014f58)(__retval);
  return;
}



/* ---- Function: strlcpy @ 00010c50 ---- */

void strlcpy(void)

{
  (*(code *)PTR_strlcpy_00014f5c)();
  return;
}



/* ---- Function: unlink @ 00010c5c ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int unlink(char *__name)

{
  int iVar1;
  
  iVar1 = (*(code *)PTR_unlink_00014f60)(__name);
  return iVar1;
}



/* ---- Function: bind @ 00010c68 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int bind(int __fd,sockaddr *__addr,socklen_t __len)

{
  int iVar1;
  
  iVar1 = (*(code *)PTR_bind_00014f64)(__fd);
  return iVar1;
}



/* ---- Function: listen @ 00010c74 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int listen(int __fd,int __n)

{
  int iVar1;
  
  iVar1 = (*(code *)PTR_listen_00014f68)(__fd);
  return iVar1;
}



/* ---- Function: accept @ 00010c80 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int accept(int __fd,sockaddr *__addr,socklen_t *__addr_len)

{
  int iVar1;
  
  iVar1 = (*(code *)PTR_accept_00014f6c)(__fd);
  return iVar1;
}



/* ---- Function: pthread_create @ 00010c8c ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int pthread_create(pthread_t *__newthread,pthread_attr_t *__attr,__start_routine *__start_routine,
                  void *__arg)

{
  int iVar1;
  
  iVar1 = (*(code *)PTR_pthread_create_00014f70)(__newthread);
  return iVar1;
}



/* ---- Function: pthread_join @ 00010c98 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int pthread_join(pthread_t __th,void **__thread_return)

{
  int iVar1;
  
  iVar1 = (*(code *)PTR_pthread_join_00014f74)(__th);
  return iVar1;
}



/* ---- Function: __stack_chk_fail @ 00010ca4 ---- */

void __stack_chk_fail(void)

{
  (*(code *)PTR___stack_chk_fail_00014f78)();
  return;
}



/* ---- Function: snprintf @ 00010cb0 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int snprintf(char *__s,size_t __maxlen,char *__format,...)

{
  int iVar1;
  
  iVar1 = (*(code *)PTR_snprintf_00014f7c)(__s);
  return iVar1;
}



/* ---- Function: read @ 00010cbc ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

ssize_t read(int __fd,void *__buf,size_t __nbytes)

{
  ssize_t sVar1;
  
  sVar1 = (*(code *)PTR_read_00014f80)(__fd);
  return sVar1;
}



/* ---- Function: qmi_client_notifier_init @ 00010cc8 ---- */

void qmi_client_notifier_init(void)

{
  (*(code *)PTR_qmi_client_notifier_init_00014f84)();
  return;
}



/* ---- Function: qmi_client_get_service_list @ 00010cd4 ---- */

void qmi_client_get_service_list(void)

{
  (*(code *)PTR_qmi_client_get_service_list_00014f88)();
  return;
}



/* ---- Function: sleep @ 00010ce0 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

uint sleep(uint __seconds)

{
  uint uVar1;
  
  uVar1 = (*(code *)PTR_sleep_00014f8c)(__seconds);
  return uVar1;
}



/* ---- Function: gettimeofday @ 00010cec ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int gettimeofday(timeval *__tv,__timezone_ptr_t __tz)

{
  int iVar1;
  
  iVar1 = (*(code *)PTR_gettimeofday_00014f90)(__tv);
  return iVar1;
}



/* ---- Function: __aeabi_idivmod @ 00010cf8 ---- */

void __aeabi_idivmod(void)

{
  (*(code *)PTR___aeabi_idivmod_00014f94)();
  return;
}



/* ---- Function: pthread_mutex_lock @ 00010d04 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int pthread_mutex_lock(pthread_mutex_t *__mutex)

{
  int iVar1;
  
  iVar1 = (*(code *)PTR_pthread_mutex_lock_00014f98)(__mutex);
  return iVar1;
}



/* ---- Function: pthread_cond_timedwait @ 00010d10 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int pthread_cond_timedwait(pthread_cond_t *__cond,pthread_mutex_t *__mutex,timespec *__abstime)

{
  int iVar1;
  
  iVar1 = (*(code *)PTR_pthread_cond_timedwait_00014f9c)(__cond);
  return iVar1;
}



/* ---- Function: pthread_mutex_unlock @ 00010d1c ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int pthread_mutex_unlock(pthread_mutex_t *__mutex)

{
  int iVar1;
  
  iVar1 = (*(code *)PTR_pthread_mutex_unlock_00014fa0)(__mutex);
  return iVar1;
}



/* ---- Function: qmi_client_init @ 00010d28 ---- */

void qmi_client_init(void)

{
  (*(code *)PTR_qmi_client_init_00014fa4)();
  return;
}



/* ---- Function: memset @ 00010d34 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

void * memset(void *__s,int __c,size_t __n)

{
  void *pvVar1;
  
  pvVar1 = (void *)(*(code *)PTR_memset_00014fa8)(__s);
  return pvVar1;
}



/* ---- Function: __aeabi_uldivmod @ 00010d40 ---- */

void __aeabi_uldivmod(void)

{
  (*(code *)PTR___aeabi_uldivmod_00014fac)();
  return;
}



/* ---- Function: qmi_client_send_msg_sync @ 00010d4c ---- */

void qmi_client_send_msg_sync(void)

{
  (*(code *)PTR_qmi_client_send_msg_sync_00014fb0)();
  return;
}



/* ---- Function: qmi_client_release @ 00010d58 ---- */

void qmi_client_release(void)

{
  (*(code *)PTR_qmi_client_release_00014fb4)();
  return;
}



/* ---- Function: qmi_client_message_decode @ 00010d64 ---- */

void qmi_client_message_decode(void)

{
  (*(code *)PTR_qmi_client_message_decode_00014fb8)();
  return;
}



/* ---- Function: pthread_cond_signal @ 00010d70 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int pthread_cond_signal(pthread_cond_t *__cond)

{
  int iVar1;
  
  iVar1 = (*(code *)PTR_pthread_cond_signal_00014fbc)(__cond);
  return iVar1;
}



/* ---- Function: write @ 00010d7c ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

ssize_t write(int __fd,void *__buf,size_t __n)

{
  ssize_t sVar1;
  
  sVar1 = (*(code *)PTR_write_00014fc0)(__fd);
  return sVar1;
}



/* ---- Function: gmtime @ 00010d88 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

tm * gmtime(time_t *__timer)

{
  tm *ptVar1;
  
  ptVar1 = (tm *)(*(code *)PTR_gmtime_00014fc4)(__timer);
  return ptVar1;
}



/* ---- Function: property_get @ 00010d94 ---- */

void property_get(void)

{
  (*(code *)PTR_property_get_00014fc8)();
  return;
}



/* ---- Function: strcmp @ 00010da0 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int strcmp(char *__s1,char *__s2)

{
  int iVar1;
  
  iVar1 = (*(code *)PTR_strcmp_00014fcc)(__s1);
  return iVar1;
}



/* ---- Function: recv @ 00010dac ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

ssize_t recv(int __fd,void *__buf,size_t __n,int __flags)

{
  ssize_t sVar1;
  
  sVar1 = (*(code *)PTR_recv_00014fd0)(__fd);
  return sVar1;
}



/* ---- Function: send @ 00010db8 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

ssize_t send(int __fd,void *__buf,size_t __n,int __flags)

{
  ssize_t sVar1;
  
  sVar1 = (*(code *)PTR_send_00014fd4)(__fd);
  return sVar1;
}



/* ---- Function: __errno @ 00010dc4 ---- */

void __errno(void)

{
  (*(code *)PTR___errno_00014fd8)();
  return;
}



/* ---- Function: pthread_cond_wait @ 00010dd0 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int pthread_cond_wait(pthread_cond_t *__cond,pthread_mutex_t *__mutex)

{
  int iVar1;
  
  iVar1 = (*(code *)PTR_pthread_cond_wait_00014fdc)(__cond);
  return iVar1;
}



/* ---- Function: setgid @ 00010ddc ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int setgid(__gid_t __gid)

{
  int iVar1;
  
  iVar1 = (*(code *)PTR_setgid_00014fe0)(__gid);
  return iVar1;
}



/* ---- Function: setuid @ 00010de8 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int setuid(__uid_t __uid)

{
  int iVar1;
  
  iVar1 = (*(code *)PTR_setuid_00014fe4)(__uid);
  return iVar1;
}



/* ---- Function: exit @ 00010df4 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

void exit(int __status)

{
  (*(code *)PTR_exit_00014fe8)(__status);
  return;
}



/* ---- Function: bsd_signal @ 00010e00 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

__sighandler_t bsd_signal(int __sig,__sighandler_t __handler)

{
  __sighandler_t p_Var1;
  
  p_Var1 = (__sighandler_t)(*(code *)PTR_bsd_signal_00014fec)(__sig);
  return p_Var1;
}



/* ---- Function: pthread_mutex_init @ 00010e0c ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int pthread_mutex_init(pthread_mutex_t *__mutex,pthread_mutexattr_t *__mutexattr)

{
  int iVar1;
  
  iVar1 = (*(code *)PTR_pthread_mutex_init_00014ff0)(__mutex);
  return iVar1;
}



/* ---- Function: __aeabi_idiv @ 00010e18 ---- */

void __aeabi_idiv(void)

{
  (*(code *)PTR___aeabi_idiv_00014ff4)();
  return;
}



/* ---- Function: settimeofday @ 00010e24 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int settimeofday(timeval *__tv,timezone *__tz)

{
  int iVar1;
  
  iVar1 = (*(code *)PTR_settimeofday_00014ff8)(__tv);
  return iVar1;
}



/* ---- Function: pthread_kill @ 00010e30 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int pthread_kill(pthread_t __threadid,int __signo)

{
  int iVar1;
  
  iVar1 = (*(code *)PTR_pthread_kill_00014ffc)(__threadid);
  return iVar1;
}



/* ---- Function: entry @ 00010e40 ---- */

/* WARNING: Control flow encountered bad instruction data */

void processEntry entry(void)

{
  int iVar1;
  undefined4 local_18;
  undefined4 local_14;
  undefined4 local_10;
  
  iVar1 = iRam00010ea4 + 0x10e58;
  local_18 = *(undefined4 *)(iVar1 + DAT_00010ea8);
  local_14 = *(undefined4 *)(iVar1 + DAT_00010eac);
  local_10 = *(undefined4 *)(iVar1 + DAT_00010eb0);
  __libc_init(&stack0x00000000,0,*(undefined4 *)(iVar1 + DAT_00010eb4),&local_18);
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: FUN_00010eb8 @ 00010eb8 ---- */

undefined4 FUN_00010eb8(undefined4 param_1)

{
  undefined4 uVar1;
  
  uVar1 = __cxa_atexit(param_1,0,DAT_00010ef0 + 0x10edc);
  return uVar1;
}



/* ---- Function: FUN_00010ef4 @ 00010ef4 ---- */

undefined4 FUN_00010ef4(longlong *param_1)

{
  int __fd;
  int iVar1;
  time_t tVar2;
  int iVar3;
  int iVar4;
  tm local_3c;
  
  __fd = open((char *)(DAT_00010fb0 + 0x10f02),0);
  if (__fd < 0) {
    __android_log_print(6,DAT_00010fb4 + 0x10f14,DAT_00010fb8 + 0x10f16,DAT_00010fbc + 0x10f18);
  }
  else {
    iVar1 = ioctl(__fd,DAT_00010fac,&local_3c);
    if (iVar1 < 0) {
      iVar3 = DAT_00010fc0 + 0x10f34;
      iVar4 = DAT_00010fc4 + 0x10f36;
      iVar1 = DAT_00010fc8 + 0x10f38;
    }
    else {
      if (*(int *)(DAT_00010fcc + 0x10f3e) == 0) {
        __android_log_print(3,DAT_00010fd0 + 0x10f60,DAT_00010fd4 + 0x10f64,DAT_00010fd8 + 0x10f68,
                            local_3c.tm_mon,local_3c.tm_mday,local_3c.tm_year,local_3c.tm_hour,
                            local_3c.tm_min,local_3c.tm_sec);
      }
      tVar2 = mktime(&local_3c);
      iVar1 = tVar2 + local_3c.tm_gmtoff;
      if (-1 < iVar1) {
        *param_1 = (longlong)iVar1 * 1000;
        close(__fd);
        return 0;
      }
      iVar3 = DAT_00010fdc + 0x10f80;
      iVar4 = DAT_00010fe0 + 0x10f82;
    }
    __android_log_print(6,iVar3,iVar4,iVar1);
    close(__fd);
  }
  return 0xffffffea;
}



/* ---- Function: FUN_00010fe4 @ 00010fe4 ---- */

undefined4 FUN_00010fe4(uint *param_1,undefined4 param_2)

{
  int iVar1;
  uint uVar2;
  undefined4 *puVar3;
  uint uVar4;
  undefined4 uVar5;
  int *piVar6;
  uint *puVar7;
  uint *puVar8;
  uint local_18;
  int iStack_14;
  
  local_18 = 0;
  iStack_14 = 0;
  puVar8 = param_1;
  if (*(int *)(DAT_000110d0 + 0x10ff6) == 0) {
    __android_log_print(3,DAT_000110d4 + 0x11002,DAT_000110d8 + 0x11006,*param_1,param_1,param_2);
  }
  if (*param_1 < 0xf) {
    puVar7 = (uint *)(*param_1 * 0x90 + DAT_000110dc + 0x11016);
    if ((char)puVar7[2] == '\0') {
      __android_log_print(6,DAT_000110e0 + 0x11028,DAT_000110e4 + 0x1102a,DAT_000110e8 + 0x1102c,
                          puVar8,param_2);
    }
    else {
      iVar1 = FUN_00010ef4(&local_18);
      if (iVar1 == 0) {
        if (*(int *)(DAT_000110ec + 0x11040) == 0) {
          iVar1 = DAT_000110f0 + 0x11052;
          __android_log_print(3,iVar1,DAT_000110f4 + 0x11058,iStack_14,local_18,iStack_14);
          __android_log_print(3,iVar1,DAT_000110f8 + 0x11068);
        }
        iVar1 = DAT_000110fc;
        uVar2 = *puVar7;
        uVar4 = puVar7[1];
        piVar6 = (int *)param_1[1];
        *piVar6 = local_18 + uVar2;
        piVar6[1] = iStack_14 + uVar4 + CARRY4(local_18,uVar2);
        if (*(int *)(iVar1 + 0x11088) == 0) {
          uVar5 = ((undefined4 *)param_1[1])[1];
          __android_log_print(3,DAT_00011100 + 0x11098,DAT_00011104 + 0x110a0,uVar5,
                              *(undefined4 *)param_1[1],uVar5);
        }
        return 0;
      }
    }
    puVar3 = (undefined4 *)param_1[1];
    *puVar3 = 0;
    puVar3[1] = 0;
  }
  else {
    __android_log_print(6,DAT_00011108 + 0x110c0,DAT_0001110c + 0x110c2,DAT_00011110 + 0x110c4,
                        puVar8,param_2);
  }
  return 0xffffffea;
}



/* ---- Function: FUN_0001129c @ 0001129c ---- */

void FUN_0001129c(undefined4 *param_1)

{
  int iVar1;
  int *piVar2;
  int iVar3;
  undefined4 uVar4;
  int __fd;
  ssize_t sVar5;
  char acStack_94 [120];
  int local_1c;
  
  piVar2 = *(int **)(DAT_0001138c + 0x112a4 + DAT_00011390);
  local_1c = *piVar2;
  if (((code *)param_1[4] != (code *)0x0) && (iVar3 = (*(code *)param_1[4])(), iVar3 != 0)) {
    __android_log_print(6,DAT_00011394 + 0x112c6,DAT_00011398 + 0x112c8,DAT_0001139c + 0x112ca);
    uVar4 = 0xffffffea;
    goto LAB_00011378;
  }
  iVar1 = DAT_000113ac;
  iVar3 = DAT_000113a8;
  if (*(char *)(param_1 + 8) == '\x01') {
    snprintf(acStack_94,0x78,(char *)(DAT_000113a0 + 0x112ea),DAT_000113a4 + 0x112ee,
             (int)param_1 + 0x21);
    iVar3 = iVar3 + 0x112f8;
    __android_log_print(3,iVar3,DAT_000113b0 + 0x11302,acStack_94);
    __android_log_print(3,iVar3,DAT_000113b4 + 0x11310,iVar1 + 0x11304);
    __fd = open(acStack_94,0);
    if (__fd < 0) {
      __android_log_print(3,iVar3,DAT_000113b8 + 0x1132a);
    }
    else {
      sVar5 = read(__fd,param_1,8);
      if (-1 < sVar5) {
        close(__fd);
        goto LAB_00011372;
      }
      __android_log_print(3,iVar3,DAT_000113bc + 0x11346,iVar1 + 0x11304);
      close(__fd);
    }
    __android_log_print(3,DAT_000113c0 + 0x11364,DAT_000113c4 + 0x11366,DAT_000113c8 + 0x11368);
    *param_1 = 0;
    param_1[1] = 0;
  }
LAB_00011372:
  *(undefined1 *)(param_1 + 2) = 1;
  uVar4 = 0;
LAB_00011378:
  if (local_1c != *piVar2) {
                    /* WARNING: Subroutine does not return */
    __stack_chk_fail(uVar4);
  }
  return;
}



/* ---- Function: FUN_000113d0 @ 000113d0 ---- */

undefined4 FUN_000113d0(void)

{
  longlong lVar1;
  int iVar2;
  int iVar3;
  int iVar4;
  int extraout_r1;
  int iVar5;
  undefined4 *puVar6;
  int iVar7;
  int iVar8;
  int iVar9;
  undefined4 uVar10;
  undefined4 local_128;
  undefined4 local_124;
  undefined4 local_120;
  undefined4 uStack_11c;
  undefined1 auStack_118 [4];
  int local_114;
  timeval local_110;
  timeval local_108;
  longlong local_100;
  int local_f8 [2];
  undefined1 auStack_f0 [8];
  timespec local_e8 [25];
  
  iVar9 = DAT_00011610 + 0x113e6;
  local_120 = 0;
  uStack_11c = 0;
  iVar8 = DAT_00011614 + 0x113ec;
  local_128 = 1;
  __android_log_print(3,iVar9,DAT_00011618 + 0x113f4,iVar8);
  iVar2 = FUN_000127c0(1,1,2);
  iVar5 = DAT_00011624;
  if (iVar2 == 0) {
    iVar5 = DAT_0001161c + 0x11414;
LAB_00011564:
    __android_log_print(6,iVar9,iVar5,iVar8);
  }
  else {
    iVar7 = 6;
    qmi_client_notifier_init(iVar2,local_f8,DAT_00011620 + 0x11420);
    while( true ) {
      iVar3 = qmi_client_get_service_list(iVar2,0,0,&local_124);
      iVar4 = iVar3;
      uVar10 = local_124;
      __android_log_print(6,iVar9,iVar5 + 0x11426,iVar8,iVar3,local_124);
      if (iVar3 == 0) break;
      iVar7 = iVar7 + -1;
      if (iVar7 == 0) {
        __android_log_print(6,iVar9,DAT_00011628 + 0x11458,iVar8,iVar4,uVar10);
        puVar6 = (undefined4 *)(DAT_0001162c + 0x11460);
        goto LAB_000115ec;
      }
      sleep(1);
      local_f8[1] = 0;
      gettimeofday(&local_108,(__timezone_ptr_t)0x0);
      local_e8[0].tv_sec = local_108.tv_sec + 1;
      local_e8[0].tv_nsec = local_108.tv_usec * 1000;
      if (local_e8[0].tv_nsec - DAT_00011608 != 0 && DAT_00011608 <= local_e8[0].tv_nsec) {
        local_e8[0].tv_sec = local_108.tv_sec + 2;
        __aeabi_idivmod(local_e8[0].tv_nsec,DAT_0001160c);
        local_e8[0].tv_nsec = extraout_r1;
      }
      pthread_mutex_lock((pthread_mutex_t *)(auStack_f0 + 4));
      do {
        if (local_f8[0] != 0) goto LAB_000114ba;
        iVar4 = pthread_cond_timedwait
                          ((pthread_cond_t *)auStack_f0,(pthread_mutex_t *)(auStack_f0 + 4),local_e8
                          );
      } while (iVar4 != 0x6e);
      local_f8[1] = 1;
LAB_000114ba:
      pthread_mutex_unlock((pthread_mutex_t *)(auStack_f0 + 4));
    }
    iVar5 = qmi_client_get_service_list(iVar2,local_e8,&local_128,&local_124);
    if (iVar5 == 0) {
      puVar6 = (undefined4 *)(DAT_0001163c + 0x11500);
      iVar5 = qmi_client_init(local_e8,iVar2,DAT_00011640 + 0x1150a,0,DAT_00011638 + 0x114fe,puVar6)
      ;
      if (iVar5 == 0) {
        memset(&local_108,0,0x10);
        gettimeofday(&local_110,(__timezone_ptr_t)0x0);
        lVar1 = __aeabi_uldivmod(local_110.tv_usec,local_110.tv_usec >> 0x1f,1000,0);
        iVar5 = FUN_00010ef4(&local_120);
        if (iVar5 != 0) {
          iVar5 = DAT_0001164c + 0x11566;
          goto LAB_00011564;
        }
        local_100 = ((longlong)local_110.tv_sec * 1000 - CONCAT44(uStack_11c,local_120)) +
                    CONCAT44(DAT_00011604,DAT_00011600) + lVar1;
        local_108.tv_sec = 2;
        iVar5 = qmi_client_send_msg_sync(*puVar6,0x20,&local_108,0x10,auStack_118,8,5000);
        if ((iVar5 == 0) && (local_114 == 0)) {
          __android_log_print(3,iVar9,DAT_00011650 + 0x115be,iVar8);
          *(undefined1 *)(DAT_00011654 + 0x115c8) = 1;
          return 0;
        }
        __android_log_print(6,DAT_00011658 + 0x115d8,DAT_0001165c + 0x115da,DAT_00011660 + 0x115dc);
        qmi_client_release(*(undefined4 *)(DAT_00011664 + 0x115e4));
        puVar6 = (undefined4 *)(DAT_00011668 + 0x115ee);
      }
      else {
        __android_log_print(6,iVar9,DAT_00011644 + 0x1151c,iVar8,iVar5);
        puVar6 = (undefined4 *)(DAT_00011648 + 0x11526);
      }
    }
    else {
      __android_log_print(6,iVar9,DAT_00011630 + 0x114e8,iVar8,iVar5,local_124,local_128);
      puVar6 = (undefined4 *)(DAT_00011634 + 0x114f0);
    }
LAB_000115ec:
    qmi_client_release(*puVar6);
  }
  return 0xffffffea;
}



/* ---- Function: FUN_00011670 @ 00011670 ---- */

void FUN_00011670(undefined4 param_1,int param_2,undefined4 param_3,undefined4 param_4)

{
  int iVar1;
  int iVar2;
  int iVar3;
  uint *puVar4;
  uint local_28 [4];
  
  __android_log_print(6,DAT_0001174c + 0x1168c,DAT_00011750 + 0x1168e,DAT_00011754 + 0x11690,param_2
                     );
  iVar2 = DAT_00011758;
  if ((1 < param_2 - 0x2dU) && (param_2 != 0x29)) {
    return;
  }
  puVar4 = (uint *)(DAT_00011758 + 0x116a4);
  pthread_mutex_lock((pthread_mutex_t *)(DAT_00011758 + 0x116ac));
  pthread_mutex_lock((pthread_mutex_t *)(DAT_0001175c + 0x116b0));
  iVar1 = qmi_client_message_decode(param_1,2,param_2,param_3,param_4,local_28,0x10);
  iVar3 = DAT_00011780;
  if (iVar1 == 0) {
    if (local_28[0] < 0xf) {
      iVar1 = *(int *)(local_28[0] * 0x90 + DAT_0001176c + 0x116f6);
      if (*(int *)(DAT_00011770 + 0x116f2 + iVar1 * 4) == 0) {
        __android_log_print(6,DAT_00011774 + 0x11702,DAT_00011778 + 0x11706,DAT_0001177c + 0x1170a,
                            iVar1);
      }
      else {
        *puVar4 = local_28[0];
        *(undefined4 *)(iVar2 + 0x116a8) = 1;
        pthread_cond_signal((pthread_cond_t *)(iVar3 + 0x11718));
      }
      goto LAB_0001171c;
    }
    iVar2 = DAT_0001178c + 0x1173c;
    iVar3 = DAT_00011790 + 0x1173e;
    iVar1 = DAT_00011794 + 0x11740;
  }
  else {
    iVar2 = DAT_00011760 + 0x116d8;
    iVar3 = DAT_00011764 + 0x116da;
    iVar1 = DAT_00011768 + 0x116dc;
  }
  __android_log_print(6,iVar2,iVar3,iVar1);
LAB_0001171c:
  pthread_mutex_unlock((pthread_mutex_t *)(DAT_00011784 + 0x11722));
  pthread_mutex_unlock((pthread_mutex_t *)(DAT_00011788 + 0x11732));
  return;
}



/* ---- Function: FUN_00011798 @ 00011798 ---- */

void FUN_00011798(undefined4 *param_1)

{
  int __fd;
  ssize_t sVar1;
  int *piVar2;
  int iVar3;
  char acStack_94 [120];
  int local_1c;
  
  piVar2 = *(int **)(DAT_00011894 + 0x117a0 + DAT_00011898);
  local_1c = *piVar2;
  if (*(char *)(param_1 + 8) != '\x01') goto LAB_0001187c;
  iVar3 = DAT_0001189c + 0x117c4;
  __android_log_print(3,iVar3,DAT_000118a0 + 0x117d0,DAT_000118a4 + 0x117d2,*param_1,param_1[1]);
  snprintf(acStack_94,0x78,(char *)(DAT_000118a8 + 0x117e0),DAT_000118ac + 0x117e2,
           (int)param_1 + 0x21);
  __android_log_print(3,iVar3,DAT_000118b0 + 0x117f0,acStack_94);
  __android_log_print(3,iVar3,DAT_000118b4 + 0x11800,DAT_000118b8 + 0x11802);
  __fd = open(acStack_94,DAT_0001188c);
  if (__fd < 0) {
    __android_log_print(3,iVar3,DAT_000118bc + 0x1181a);
    __fd = open(acStack_94,DAT_00011890,0x1a4);
    if (-1 < __fd) goto LAB_0001183a;
    __android_log_print(3,iVar3,DAT_000118c0 + 0x11836);
  }
  else {
LAB_0001183a:
    sVar1 = write(__fd,param_1,8);
    if (-1 < sVar1) {
      close(__fd);
      goto LAB_0001187c;
    }
    __android_log_print(6,DAT_000118c4 + 0x11854,DAT_000118c8 + 0x11856,DAT_000118cc + 0x11858);
    close(__fd);
  }
  __android_log_print(3,DAT_000118d0 + 0x11876,DAT_000118d4 + 0x11878,DAT_000118d8 + 0x1187a);
LAB_0001187c:
  if (local_1c != *piVar2) {
                    /* WARNING: Subroutine does not return */
    __stack_chk_fail();
  }
  return;
}



/* ---- Function: FUN_000118dc @ 000118dc ---- */

undefined4 FUN_000118dc(uint *param_1)

{
  int *piVar1;
  uint uVar2;
  int iVar3;
  undefined4 extraout_r1;
  undefined4 extraout_r1_00;
  int iVar4;
  int iVar5;
  int iVar6;
  int iVar7;
  int *piVar8;
  int iVar9;
  int iVar10;
  int iVar11;
  int iVar12;
  uint local_28;
  int iStack_24;
  
  iVar3 = DAT_000119c4;
  if (*param_1 < 0xf) {
    piVar8 = (int *)(*param_1 * 0x90 + DAT_000119c4 + 0x118f4);
    if ((char)piVar8[2] != '\0') {
      iVar4 = FUN_00010ef4(&local_28);
      if (iVar4 != 0) {
        return 0xffffffea;
      }
      iVar9 = DAT_000119d4 + 0x11926;
      __android_log_print(3,iVar9,DAT_000119d8 + 0x1192c,iStack_24,local_28,iStack_24);
      uVar2 = *(uint *)param_1[1];
      iVar6 = uVar2 - local_28;
      iVar7 = (((uint *)param_1[1])[1] - iStack_24) - (uint)(uVar2 < local_28);
      __android_log_print(3,iVar9,DAT_000119dc + 0x1193e);
      iVar4 = DAT_000119e0;
      *piVar8 = iVar6;
      piVar8[1] = iVar7;
      iVar5 = iVar6;
      iVar10 = iVar7;
      iVar11 = iVar6;
      iVar12 = iVar7;
      __android_log_print(3,iVar9,iVar4 + 0x1195a);
      FUN_00011798(piVar8,extraout_r1,iVar6,iVar7,iVar5,iVar10,iVar11,iVar12);
      if (*(int *)(DAT_000119e4 + 0x11978 + piVar8[3] * 4) == 0) {
        return 0;
      }
      if (piVar8[3] != 1) {
        __android_log_print(3,iVar9,DAT_000119e8 + 0x1198a);
        iVar4 = piVar8[1];
        piVar1 = (int *)(iVar3 + 0x11984);
        *piVar1 = *piVar8;
        *(int *)(iVar3 + 0x11988) = iVar4;
        FUN_00011798(piVar1,extraout_r1_00,iVar6,iVar7,iVar5,iVar10,iVar11,iVar12);
      }
      return 0;
    }
    iVar3 = DAT_000119c8 + 0x11906;
    iVar4 = DAT_000119cc + 0x11908;
    iVar5 = DAT_000119d0 + 0x1190a;
  }
  else {
    iVar3 = DAT_000119ec + 0x119b4;
    iVar4 = DAT_000119f0 + 0x119b6;
    iVar5 = DAT_000119f4 + 0x119b8;
  }
  __android_log_print(6,iVar3,iVar4,iVar5);
  return 0xffffffea;
}



/* ---- Function: FUN_000119f8 @ 000119f8 ---- */

undefined4 FUN_000119f8(uint *param_1)

{
  longlong lVar1;
  undefined4 uVar2;
  tm *ptVar3;
  int iVar4;
  uint uVar5;
  int iVar6;
  int iVar7;
  uint *puVar8;
  undefined4 *puVar9;
  undefined8 *puVar10;
  undefined8 uVar11;
  time_t local_28;
  int local_24;
  uint local_20;
  time_t *local_1c;
  undefined4 local_18;
  uint local_14;
  
  if (*(int *)(DAT_00011b74 + 0x11a00) == 0) {
    __android_log_print(3,DAT_00011b80 + 0x11a28,DAT_00011b7c + 0x11a1e,DAT_00011b78 + 0x11a10,
                        *param_1);
  }
  local_14 = param_1[3];
  if (local_14 == 1) {
    uVar5 = param_1[2];
    if (uVar5 == 2) {
      uVar2 = FUN_00010fe4(param_1);
      puVar10 = (undefined8 *)param_1[1];
      uVar11 = __aeabi_uldivmod(*(undefined4 *)puVar10,*(undefined4 *)((int)puVar10 + 4),1000,0);
      *puVar10 = uVar11;
      return uVar2;
    }
    if (uVar5 == 3) {
      local_20 = *param_1;
      local_18 = 2;
      local_1c = &local_28;
      uVar2 = FUN_00010fe4(&local_20);
      ptVar3 = gmtime(&local_28);
      param_1[1] = (uint)ptVar3;
      return uVar2;
    }
    if (uVar5 == 1) {
      uVar2 = FUN_00010fe4(param_1);
      return uVar2;
    }
    iVar4 = DAT_00011b84 + 0x11a92;
    iVar6 = DAT_00011b88 + 0x11a94;
    iVar7 = DAT_00011b8c + 0x11a96;
LAB_00011b02:
    __android_log_print(6,iVar4,iVar6,iVar7,uVar5);
    return 0xffffffea;
  }
  if (local_14 != 0) {
    if (local_14 != 2) {
      return 0;
    }
    if (0xe < *param_1) {
      __android_log_print(6,DAT_00011ba8 + 0x11b64,DAT_00011bac + 0x11b66,DAT_00011bb0 + 0x11b68);
      return 0xffffffea;
    }
    iVar4 = *param_1 * 0x90;
    puVar8 = (uint *)param_1[1];
    *puVar8 = (uint)(*(int *)(DAT_00011b9c + 0x11b20 + iVar4 + 4) != 0 ||
                    *(int *)(DAT_00011b9c + 0x11b20 + iVar4) != 0);
    puVar8[1] = 0;
    puVar9 = (undefined4 *)param_1[1];
    __android_log_print(3,DAT_00011ba4 + 0x11b50,DAT_00011ba0 + 0x11b42,puVar9,*puVar9,puVar9[1],
                        *param_1);
    return 0;
  }
  uVar5 = param_1[2];
  if (uVar5 == 2) {
    lVar1 = (ulonglong)*(uint *)param_1[1] * 1000;
    local_28 = (time_t)lVar1;
    local_24 = ((uint *)param_1[1])[1] * 1000 + (int)((ulonglong)lVar1 >> 0x20);
    param_1[1] = (uint)&local_28;
  }
  else {
    if (uVar5 == 3) {
      local_28 = mktime((tm *)param_1[1]);
      local_20 = *param_1;
      local_1c = &local_28;
      local_24 = local_28 >> 0x1f;
      local_18 = 2;
      uVar2 = FUN_000119f8(&local_20);
      return uVar2;
    }
    if (uVar5 != 1) {
      iVar4 = DAT_00011b90 + 0x11b00;
      iVar6 = DAT_00011b94 + 0x11b02;
      iVar7 = DAT_00011b98 + 0x11b04;
      goto LAB_00011b02;
    }
  }
  uVar2 = FUN_000118dc(param_1);
  return uVar2;
}



/* ---- Function: FUN_000120d0 @ 000120d0 ---- */

void FUN_000120d0(void)

{
  int iVar1;
  int iVar2;
  int iVar3;
  int iVar4;
  int iVar5;
  int iVar6;
  pthread_mutex_t *ppVar7;
  void *__s;
  int iVar8;
  int iVar9;
  undefined4 *puVar10;
  int iVar11;
  int iVar12;
  pthread_mutex_t *__mutex;
  int iVar13;
  undefined4 local_3c;
  undefined4 local_38;
  int local_34;
  undefined4 local_30;
  int local_2c;
  
  iVar1 = DAT_00012228;
  puVar10 = (undefined4 *)(DAT_0001222c + 0x120ea);
  iVar11 = DAT_00012230 + 0x120ee;
  iVar12 = DAT_00012234 + 0x120f0;
  __mutex = (pthread_mutex_t *)(DAT_00012228 + 0x120ec);
  iVar5 = DAT_00012238 + 0x120f6;
  do {
    while( true ) {
      iVar2 = DAT_0001223c;
      ppVar7 = (pthread_mutex_t *)(DAT_0001223c + 0x12104);
      pthread_mutex_lock(ppVar7);
      if (*(int *)(iVar2 + 0x12100) == 0) {
        pthread_cond_wait((pthread_cond_t *)(DAT_00012240 + 74000),ppVar7);
      }
      iVar2 = DAT_0001224c;
      iVar9 = DAT_00012244 + 0x1211e;
      iVar8 = DAT_00012248 + 0x12120;
      __s = (void *)(DAT_0001224c + 0x1212c);
      __android_log_print(3,iVar9,iVar5,iVar8,*puVar10);
      memset(__s,0,0x18);
      local_3c = *puVar10;
      iVar3 = qmi_client_send_msg_sync
                        (*(undefined4 *)(DAT_00012250 + 0x1214a),0x21,&local_3c,4,__s,0x18,1000);
      if ((iVar3 != 0) || (iVar13 = *(int *)(iVar2 + 0x12130), iVar13 != 0)) break;
      iVar4 = *(uint *)(iVar2 + 0x1213c) + DAT_00012220;
      iVar6 = *(int *)(iVar2 + 0x12140) +
              DAT_00012224 + (uint)CARRY4(*(uint *)(iVar2 + 0x1213c),DAT_00012220);
      *(int *)(iVar2 + 0x1213c) = iVar4;
      *(int *)(iVar2 + 0x12140) = iVar6;
      __android_log_print(3,iVar9,DAT_00012254 + 0x12190,iVar8,iVar4,iVar6);
      pthread_mutex_lock((pthread_mutex_t *)(DAT_00012258 + 0x12198));
      local_38 = *(undefined4 *)(iVar2 + 0x12134);
      local_30 = 1;
      local_34 = iVar2 + 0x1213c;
      local_2c = iVar13;
      iVar3 = FUN_000119f8(&local_38);
      if (iVar3 == 0) {
        __android_log_print(6,iVar9,DAT_00012268 + 0x121fe,iVar8,*(undefined4 *)(iVar2 + 0x12134),
                            iVar6);
      }
      else {
        __android_log_print(6,iVar9,DAT_00012264 + 0x121ec,iVar8,iVar4,iVar6);
      }
      iVar2 = DAT_0001226c;
      ppVar7 = (pthread_mutex_t *)(DAT_00012270 + 0x1220e);
      *(undefined4 *)(DAT_0001226c + 0x12210) = 0;
      pthread_mutex_unlock(ppVar7);
      pthread_mutex_unlock((pthread_mutex_t *)(iVar2 + 0x12214));
    }
    __android_log_print(6,iVar11,DAT_00012260 + 0x121d0,iVar12,iVar3,
                        *(undefined4 *)(DAT_0001225c + 0x121ca));
    pthread_mutex_unlock(__mutex);
    *(undefined4 *)(iVar1 + 0x120e8) = 0;
  } while( true );
}



/* ---- Function: FUN_000127c0 @ 000127c0 ---- */

undefined * FUN_000127c0(int param_1,int param_2,int param_3)

{
  if (((param_1 == 1) && (param_2 == 1)) && (param_3 == 2)) {
    return &UNK_000127d6 + DAT_000127d8;
  }
  return (undefined *)0x0;
}



/* ---- Function: __libc_init @ 00016000 ---- */

/* WARNING: Control flow encountered bad instruction data */

void __libc_init(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: __cxa_atexit @ 00016004 ---- */

/* WARNING: Control flow encountered bad instruction data */

void __cxa_atexit(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: open @ 00016008 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int open(char *__file,int __oflag,...)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: __android_log_print @ 0001600c ---- */

/* WARNING: Control flow encountered bad instruction data */

void __android_log_print(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: ioctl @ 00016010 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int ioctl(int __fd,ulong __request,...)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: mktime @ 00016014 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

time_t mktime(tm *__tp)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: close @ 00016018 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int close(int __fd)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: __aeabi_unwind_cpp_pr0 @ 0001601c ---- */

/* WARNING: Control flow encountered bad instruction data */

void __aeabi_unwind_cpp_pr0(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: socket @ 00016020 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int socket(int __domain,int __type,int __protocol)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: pthread_exit @ 00016024 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

void pthread_exit(void *__retval)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: strlcpy @ 00016028 ---- */

/* WARNING: Control flow encountered bad instruction data */

void strlcpy(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: unlink @ 0001602c ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int unlink(char *__name)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: bind @ 00016030 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int bind(int __fd,sockaddr *__addr,socklen_t __len)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: listen @ 00016034 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int listen(int __fd,int __n)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: accept @ 00016038 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int accept(int __fd,sockaddr *__addr,socklen_t *__addr_len)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: pthread_create @ 0001603c ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int pthread_create(pthread_t *__newthread,pthread_attr_t *__attr,__start_routine *__start_routine,
                  void *__arg)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: pthread_join @ 00016040 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int pthread_join(pthread_t __th,void **__thread_return)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: __stack_chk_fail @ 00016044 ---- */

/* WARNING: Control flow encountered bad instruction data */

void __stack_chk_fail(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: snprintf @ 0001604c ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int snprintf(char *__s,size_t __maxlen,char *__format,...)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: read @ 00016050 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

ssize_t read(int __fd,void *__buf,size_t __nbytes)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: __aeabi_idivmod @ 00016054 ---- */

/* WARNING: Control flow encountered bad instruction data */

void __aeabi_idivmod(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: __aeabi_uldivmod @ 00016058 ---- */

/* WARNING: Control flow encountered bad instruction data */

void __aeabi_uldivmod(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: qmi_client_notifier_init @ 0001605c ---- */

/* WARNING: Control flow encountered bad instruction data */

void qmi_client_notifier_init(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: qmi_client_get_service_list @ 00016060 ---- */

/* WARNING: Control flow encountered bad instruction data */

void qmi_client_get_service_list(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: sleep @ 00016064 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

uint sleep(uint __seconds)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: gettimeofday @ 00016068 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int gettimeofday(timeval *__tv,__timezone_ptr_t __tz)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: pthread_mutex_lock @ 0001606c ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int pthread_mutex_lock(pthread_mutex_t *__mutex)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: pthread_cond_timedwait @ 00016070 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int pthread_cond_timedwait(pthread_cond_t *__cond,pthread_mutex_t *__mutex,timespec *__abstime)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: pthread_mutex_unlock @ 00016074 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int pthread_mutex_unlock(pthread_mutex_t *__mutex)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: qmi_client_init @ 00016078 ---- */

/* WARNING: Control flow encountered bad instruction data */

void qmi_client_init(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: memset @ 0001607c ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

void * memset(void *__s,int __c,size_t __n)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: qmi_client_send_msg_sync @ 00016080 ---- */

/* WARNING: Control flow encountered bad instruction data */

void qmi_client_send_msg_sync(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: qmi_client_release @ 00016084 ---- */

/* WARNING: Control flow encountered bad instruction data */

void qmi_client_release(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: qmi_client_message_decode @ 00016088 ---- */

/* WARNING: Control flow encountered bad instruction data */

void qmi_client_message_decode(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: pthread_cond_signal @ 0001608c ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int pthread_cond_signal(pthread_cond_t *__cond)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: write @ 00016090 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

ssize_t write(int __fd,void *__buf,size_t __n)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: gmtime @ 00016094 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

tm * gmtime(time_t *__timer)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: property_get @ 00016098 ---- */

/* WARNING: Control flow encountered bad instruction data */

void property_get(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: strcmp @ 0001609c ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int strcmp(char *__s1,char *__s2)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: recv @ 000160a0 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

ssize_t recv(int __fd,void *__buf,size_t __n,int __flags)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: send @ 000160a4 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

ssize_t send(int __fd,void *__buf,size_t __n,int __flags)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: __errno @ 000160a8 ---- */

/* WARNING: Control flow encountered bad instruction data */

void __errno(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: pthread_cond_wait @ 000160ac ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int pthread_cond_wait(pthread_cond_t *__cond,pthread_mutex_t *__mutex)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: __aeabi_idiv @ 000160b0 ---- */

/* WARNING: Control flow encountered bad instruction data */

void __aeabi_idiv(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: setgid @ 000160b4 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int setgid(__gid_t __gid)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: setuid @ 000160b8 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int setuid(__uid_t __uid)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: exit @ 000160bc ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

void exit(int __status)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: bsd_signal @ 000160c0 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

__sighandler_t bsd_signal(int __sig,__sighandler_t __handler)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: pthread_mutex_init @ 000160c4 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int pthread_mutex_init(pthread_mutex_t *__mutex,pthread_mutexattr_t *__mutexattr)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: settimeofday @ 000160c8 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int settimeofday(timeval *__tv,timezone *__tz)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: pthread_kill @ 000160cc ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int pthread_kill(pthread_t __threadid,int __signo)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



