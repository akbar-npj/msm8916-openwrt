/* Decompiled from: /home/shaanair/Projects/msm8916-openwrt-clean/GitIgnore/MelbonWhiteStock_Dump/system/vendor/lib/libdpmfdmgr.so */
/* Language: ARM:LE:32:v8 */

/* ---- Function: __cxa_finalize @ 00011bac ---- */

void __cxa_finalize(void)

{
  (*(code *)PTR___cxa_finalize_00017f80)();
  return;
}



/* ---- Function: __cxa_atexit @ 00011bb8 ---- */

void __cxa_atexit(void)

{
  (*(code *)PTR___cxa_atexit_00017f84)();
  return;
}



/* ---- Function: _ZdlPv @ 00011bc4 ---- */

void _ZdlPv(void)

{
  (*(code *)PTR__ZdlPv_00017f88)();
  return;
}



/* ---- Function: strlen @ 00011bd0 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

size_t strlen(char *__s)

{
  size_t sVar1;
  
  sVar1 = (*(code *)PTR_strlen_00017f8c)(__s);
  return sVar1;
}



/* ---- Function: memcpy @ 00011bdc ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

void * memcpy(void *__dest,void *__src,size_t __n)

{
  void *pvVar1;
  
  pvVar1 = (void *)(*(code *)PTR_memcpy_00017f90)(__dest);
  return pvVar1;
}



/* ---- Function: puts @ 00011be8 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int puts(char *__s)

{
  int iVar1;
  
  iVar1 = (*(code *)PTR_puts_00017f94)(__s);
  return iVar1;
}



/* ---- Function: abort @ 00011bf4 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

void abort(void)

{
  (*(code *)PTR_abort_00017f98)();
  return;
}



/* ---- Function: _Znwj @ 00011c00 ---- */

void _Znwj(void)

{
  (*(code *)PTR__Znwj_00017f9c)();
  return;
}



/* ---- Function: exit @ 00011c0c ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

void exit(int __status)

{
  (*(code *)PTR_exit_00017fa0)(__status);
  return;
}



/* ---- Function: memcmp @ 00011c24 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int memcmp(void *__s1,void *__s2,size_t __n)

{
  int iVar1;
  
  iVar1 = (*(code *)PTR_memcmp_00017fa8)(__s1);
  return iVar1;
}



/* ---- Function: _ZN6DpmDsm14getAllWwanInfoEv @ 00011c30 ---- */

void _ZN6DpmDsm14getAllWwanInfoEv(void)

{
  (*(code *)PTR__ZN6DpmDsm14getAllWwanInfoEv_00017fac)();
  return;
}



/* ---- Function: __stack_chk_fail @ 00011c3c ---- */

void __stack_chk_fail(void)

{
  (*(code *)PTR___stack_chk_fail_00017fb0)();
  return;
}



/* ---- Function: __aeabi_atexit @ 00011c48 ---- */

void __aeabi_atexit(void)

{
  (*(code *)PTR___aeabi_atexit_00017fb4)();
  return;
}



/* ---- Function: read @ 00011c54 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

ssize_t read(int __fd,void *__buf,size_t __nbytes)

{
  ssize_t sVar1;
  
  sVar1 = (*(code *)PTR_read_00017fb8)(__fd);
  return sVar1;
}



/* ---- Function: atoi @ 00011c60 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int atoi(char *__nptr)

{
  int iVar1;
  
  iVar1 = (*(code *)PTR_atoi_00017fbc)(__nptr);
  return iVar1;
}



/* ---- Function: lseek @ 00011c6c ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

__off_t lseek(int __fd,__off_t __offset,int __whence)

{
  __off_t _Var1;
  
  _Var1 = (*(code *)PTR_lseek_00017fc0)(__fd);
  return _Var1;
}



/* ---- Function: popen @ 00011c78 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

FILE * popen(char *__command,char *__modes)

{
  FILE *pFVar1;
  
  pFVar1 = (FILE *)(*(code *)PTR_popen_00017fc4)(__command);
  return pFVar1;
}



/* ---- Function: pclose @ 00011c84 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int pclose(FILE *__stream)

{
  int iVar1;
  
  iVar1 = (*(code *)PTR_pclose_00017fc8)(__stream);
  return iVar1;
}



/* ---- Function: __strlen_chk @ 00011c90 ---- */

void __strlen_chk(void)

{
  (*(code *)PTR___strlen_chk_00017fcc)();
  return;
}



/* ---- Function: snprintf @ 00011c9c ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int snprintf(char *__s,size_t __maxlen,char *__format,...)

{
  int iVar1;
  
  iVar1 = (*(code *)PTR_snprintf_00017fd0)(__s);
  return iVar1;
}



/* ---- Function: _ZN6DpmCom21removeComEventHandlerEi @ 00011ca8 ---- */

void _ZN6DpmCom21removeComEventHandlerEi(void)

{
  (*(code *)PTR__ZN6DpmCom21removeComEventHandlerEi_00017fd4)();
  return;
}



/* ---- Function: close @ 00011cb4 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int close(int __fd)

{
  int iVar1;
  
  iVar1 = (*(code *)PTR_close_00017fd8)(__fd);
  return iVar1;
}



/* ---- Function: strlcpy @ 00011cc0 ---- */

void strlcpy(void)

{
  (*(code *)PTR_strlcpy_00017fdc)();
  return;
}



/* ---- Function: strlcat @ 00011ccc ---- */

void strlcat(void)

{
  (*(code *)PTR_strlcat_00017fe0)();
  return;
}



/* ---- Function: open @ 00011cd8 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int open(char *__file,int __oflag,...)

{
  int iVar1;
  
  iVar1 = (*(code *)PTR_open_00017fe4)(__file);
  return iVar1;
}



/* ---- Function: _ZN6DpmCom18addComEventHandlerEiPFviPvES0_S2_i @ 00011ce4 ---- */

void _ZN6DpmCom18addComEventHandlerEiPFviPvES0_S2_i(void)

{
  (*(code *)PTR__ZN6DpmCom18addComEventHandlerEiPFviPvES0_S2_i_00017fe8)();
  return;
}



/* ---- Function: __errno @ 00011cf0 ---- */

void __errno(void)

{
  (*(code *)PTR___errno_00017fec)();
  return;
}



/* ---- Function: _ZN13DpmBaseConfigD2Ev @ 00011cfc ---- */

void _ZN13DpmBaseConfigD2Ev(void)

{
  (*(code *)PTR__ZN13DpmBaseConfigD2Ev_00017ff0)();
  return;
}



/* ---- Function: strtol @ 00011d08 ---- */

/* WARNING: Unknown calling convention -- yet parameter storage is locked */

long strtol(char *__nptr,char **__endptr,int __base)

{
  long lVar1;
  
  lVar1 = (*(code *)PTR_strtol_00017ff4)(__nptr);
  return lVar1;
}



/* ---- Function: _ZN13DpmBaseConfig15parseConfigFileERK21DpmConfigParseControlRSt3mapIiSsSt4lessIiESaISt4pairIKiSsEEE @ 00011d14 ---- */

void _ZN13DpmBaseConfig15parseConfigFileERK21DpmConfigParseControlRSt3mapIiSsSt4lessIiESaISt4pairIKiSsEEE
               (void)

{
  (*(code *)
    PTR__ZN13DpmBaseConfig15parseConfigFileERK21DpmConfigParseControlRSt3mapIiSsSt4lessIiESaISt4pairIKiSsEEE_00017ff8
  )();
  return;
}



/* ---- Function: _ZN13DpmBaseConfigC2Ev @ 00011d20 ---- */

void _ZN13DpmBaseConfigC2Ev(void)

{
  (*(code *)PTR__ZN13DpmBaseConfigC2Ev_00017ffc)();
  return;
}



/* ---- Function: _FINI_0 @ 00011d2c ---- */

void _FINI_0(void)

{
  __cxa_finalize(DAT_00011d48 + 0x11d40);
  return;
}



/* ---- Function: FUN_00011d4c @ 00011d4c ---- */

undefined4 FUN_00011d4c(undefined4 param_1)

{
  undefined4 uVar1;
  
  uVar1 = __cxa_atexit(param_1,0,DAT_00011d84 + 0x11d70);
  return uVar1;
}



/* ---- Function: FUN_00011d88 @ 00011d88 ---- */

void FUN_00011d88(void)

{
  int iVar1;
  int iVar2;
  int iVar3;
  
  iVar3 = DAT_00011dd8 + 0x11d9c;
  iVar1 = DAT_00011dd8 + 0x11e0c;
  do {
    iVar2 = iVar1 + -0x1c;
    iVar1 = *(int *)(iVar1 + -8);
    if ((iVar1 != iVar2) && (iVar1 != 0)) {
      _ZdlPv(iVar1);
    }
    iVar1 = iVar2;
  } while (iVar2 != iVar3);
  return;
}



/* ---- Function: _ZNSsC1EPKcRKSaIcE @ 00011ddc ---- */

undefined4 * _ZNSsC1EPKcRKSaIcE(undefined4 *param_1,char *param_2)

{
  size_t sVar1;
  void *pvVar2;
  undefined4 *__dest;
  size_t __n;
  uint uVar3;
  
  param_1[4] = param_1;
  param_1[5] = param_1;
  sVar1 = strlen(param_2);
  __n = (int)(param_2 + sVar1) - (int)param_2;
  uVar3 = __n + 1;
  if (uVar3 == 0) {
    puts((char *)(DAT_00011e88 + 0x11e50));
                    /* WARNING: Subroutine does not return */
    abort();
  }
  __dest = param_1;
  if (0x10 < uVar3) {
    __dest = (undefined4 *)_Znwj(uVar3);
    if (__dest == (undefined4 *)0x0) {
      puts((char *)(DAT_00011e8c + 0x11e80));
                    /* WARNING: Subroutine does not return */
      exit(1);
    }
    param_1[5] = __dest;
    param_1[4] = __dest;
    *param_1 = (undefined1 *)((int)__dest + uVar3);
  }
  if (param_2 != param_2 + sVar1) {
    pvVar2 = memcpy(__dest,param_2,__n);
    __dest = (undefined4 *)((int)pvVar2 + __n);
  }
  param_1[4] = __dest;
  *(undefined1 *)__dest = 0;
  return param_1;
}



/* ---- Function: _ZNSsC1ERKSs @ 00011e90 ---- */

undefined4 * _ZNSsC1ERKSs(undefined4 *param_1,int param_2)

{
  undefined4 *__dest;
  size_t __n;
  void *pvVar1;
  void *pvVar2;
  uint uVar3;
  
  param_1[4] = param_1;
  param_1[5] = param_1;
  pvVar2 = *(void **)(param_2 + 0x10);
  pvVar1 = *(void **)(param_2 + 0x14);
  __n = (int)pvVar2 - (int)pvVar1;
  uVar3 = __n + 1;
  if (uVar3 == 0) {
    puts((char *)(DAT_00011f2c + 0x11ef4));
                    /* WARNING: Subroutine does not return */
    abort();
  }
  __dest = param_1;
  if (0x10 < uVar3) {
    __dest = (undefined4 *)_Znwj(uVar3);
    if (__dest == (undefined4 *)0x0) {
      puts((char *)(DAT_00011f30 + 0x11f24));
                    /* WARNING: Subroutine does not return */
      exit(1);
    }
    param_1[5] = __dest;
    param_1[4] = __dest;
    *param_1 = (undefined1 *)((int)__dest + uVar3);
  }
  if (pvVar1 != pvVar2) {
    pvVar1 = memcpy(__dest,pvVar1,__n);
    __dest = (undefined4 *)((int)pvVar1 + __n);
  }
  param_1[4] = __dest;
  *(undefined1 *)__dest = 0;
  return param_1;
}



/* ---- Function: _ZNSt4priv10_List_baseI10DpmApnTypeSaIS1_EE5clearEv @ 00011f34 ---- */

void _ZNSt4priv10_List_baseI10DpmApnTypeSaIS1_EE5clearEv(int *param_1)

{
  int *piVar1;
  
  piVar1 = (int *)*param_1;
  while (piVar1 != param_1) {
    piVar1 = (int *)*piVar1;
    _ZdlPv();
  }
  *param_1 = (int)param_1;
  param_1[1] = (int)param_1;
  return;
}



/* ---- Function: _ZNSt4priv8_Rb_treeISsSt4lessISsESt4pairIKSsiENS_10_Select1stIS5_EENS_11_MapTraitsTIS5_EESaIS5_EE8_M_eraseEPNS_18_Rb_tree_node_baseE @ 00011f68 ---- */

void _ZNSt4priv8_Rb_treeISsSt4lessISsESt4pairIKSsiENS_10_Select1stIS5_EENS_11_MapTraitsTIS5_EESaIS5_EE8_M_eraseEPNS_18_Rb_tree_node_baseE
               (undefined4 param_1,int param_2)

{
  int iVar1;
  int iVar2;
  
  if (param_2 == 0) {
    return;
  }
  do {
    _ZNSt4priv8_Rb_treeISsSt4lessISsESt4pairIKSsiENS_10_Select1stIS5_EENS_11_MapTraitsTIS5_EESaIS5_EE8_M_eraseEPNS_18_Rb_tree_node_baseE
              (param_1,*(undefined4 *)(param_2 + 0xc));
    iVar1 = *(int *)(param_2 + 0x24);
    iVar2 = *(int *)(param_2 + 8);
    if ((iVar1 != param_2 + 0x10) && (iVar1 != 0)) {
      _ZdlPv(iVar1);
    }
    _ZdlPv(param_2);
    param_2 = iVar2;
  } while (iVar2 != 0);
  return;
}



/* ---- Function: _ZNSt4priv10_Rb_globalIbE12_M_incrementEPNS_18_Rb_tree_node_baseE @ 00011fbc ---- */

int _ZNSt4priv10_Rb_globalIbE12_M_incrementEPNS_18_Rb_tree_node_baseE(int param_1)

{
  int iVar1;
  int iVar2;
  
  iVar2 = *(int *)(param_1 + 0xc);
  if (*(int *)(param_1 + 0xc) == 0) {
    iVar1 = *(int *)(param_1 + 4);
    if (param_1 == *(int *)(iVar1 + 0xc)) {
      do {
        iVar2 = iVar1;
        iVar1 = *(int *)(iVar2 + 4);
      } while (*(int *)(iVar1 + 0xc) == iVar2);
      if (iVar1 == *(int *)(iVar2 + 0xc)) {
        iVar1 = iVar2;
      }
      return iVar1;
    }
  }
  else {
    do {
      iVar1 = iVar2;
      iVar2 = *(int *)(iVar1 + 8);
    } while (*(int *)(iVar1 + 8) != 0);
  }
  return iVar1;
}



/* ---- Function: _ZN8DpmFdMgr21handleIfIdleStatusChgEv @ 00012028 ---- */

void _ZN8DpmFdMgr21handleIfIdleStatusChgEv(int param_1)

{
  int iVar1;
  undefined4 *puVar2;
  
  puVar2 = *(undefined4 **)(DAT_00012124 + 0x12048 + DAT_00012128);
  (**(code **)(*(int *)*puVar2 + 8))((int *)*puVar2,0,0x28a8,DAT_0001212c + 0x1205c);
  iVar1 = *(int *)(param_1 + 0x2c);
  while( true ) {
    if (iVar1 == param_1 + 0x24) {
      (**(code **)(*(int *)*puVar2 + 8))((int *)*puVar2,1,0x28a8,DAT_00012134 + 0x120e0);
      (*(code *)PTR__ZN6DpmQmi9goDormantEv_00017fa4)(*(undefined4 *)(param_1 + 0x20));
      return;
    }
    if (*(int *)(iVar1 + 0x28) == 0) break;
    if (*(char *)(*(int *)(iVar1 + 0x28) + 0x2c) == '\0') {
      (**(code **)(*(int *)*puVar2 + 8))
                ((int *)*puVar2,1,0x28a8,DAT_00012138 + 0x12114,*(undefined4 *)(iVar1 + 0x24));
      return;
    }
    iVar1 = _ZNSt4priv10_Rb_globalIbE12_M_incrementEPNS_18_Rb_tree_node_baseE();
  }
  (**(code **)(*(int *)*puVar2 + 8))((int *)*puVar2,3,0x28a8,DAT_00012130 + 0x120b8);
  return;
}



/* ---- Function: _ZN8DpmFdMgr22ifIdleStatusChgEvtHdlrEPKvPv @ 0001213c ---- */

void _ZN8DpmFdMgr22ifIdleStatusChgEvtHdlrEPKvPv(undefined1 *param_1,int param_2)

{
  int *piVar1;
  
  piVar1 = (int *)**(undefined4 **)(DAT_000121ac + 0x12150 + DAT_000121b0);
  (**(code **)(*piVar1 + 8))(piVar1,0,0x28a8,DAT_000121b4 + 0x12170,*param_1);
  if (param_2 != 0) {
    if (*(int *)(DAT_000121b8 + 0x12194) == param_2) {
      _ZN8DpmFdMgr21handleIfIdleStatusChgEv();
      return;
    }
  }
  return;
}



/* ---- Function: _ZNSt4priv8_Rb_treeISsSt4lessISsESt4pairIKSs19_DpmDsmWwanInfoTypeENS_10_Select1stIS6_EENS_11_MapTraitsTIS6_EESaIS6_EE8_M_eraseEPNS_18_Rb_tree_node_baseE @ 000121bc ---- */

void _ZNSt4priv8_Rb_treeISsSt4lessISsESt4pairIKSs19_DpmDsmWwanInfoTypeENS_10_Select1stIS6_EENS_11_MapTraitsTIS6_EESaIS6_EE8_M_eraseEPNS_18_Rb_tree_node_baseE
               (undefined4 param_1,int param_2)

{
  int iVar1;
  int iVar2;
  
  if (param_2 == 0) {
    return;
  }
  do {
    _ZNSt4priv8_Rb_treeISsSt4lessISsESt4pairIKSs19_DpmDsmWwanInfoTypeENS_10_Select1stIS6_EENS_11_MapTraitsTIS6_EESaIS6_EE8_M_eraseEPNS_18_Rb_tree_node_baseE
              (param_1,*(undefined4 *)(param_2 + 0xc));
    iVar2 = *(int *)(param_2 + 8);
    _ZNSt4priv10_List_baseI10DpmApnTypeSaIS1_EE5clearEv(param_2 + 0x38);
    iVar1 = *(int *)(param_2 + 0x24);
    if ((iVar1 != param_2 + 0x10) && (iVar1 != 0)) {
      _ZdlPv(iVar1);
    }
    _ZdlPv(param_2);
    param_2 = iVar2;
  } while (iVar2 != 0);
  return;
}



/* ---- Function: _ZNSt4priv10_Rb_globalIbE12_Rotate_leftEPNS_18_Rb_tree_node_baseERS3_ @ 00012218 ---- */

void _ZNSt4priv10_Rb_globalIbE12_Rotate_leftEPNS_18_Rb_tree_node_baseERS3_(int param_1,int *param_2)

{
  int iVar1;
  int iVar2;
  
  iVar2 = *(int *)(param_1 + 0xc);
  iVar1 = *(int *)(iVar2 + 8);
  if (iVar1 != 0) {
    *(int *)(iVar1 + 4) = param_1;
  }
  *(int *)(param_1 + 0xc) = iVar1;
  *(undefined4 *)(iVar2 + 4) = *(undefined4 *)(param_1 + 4);
  if (*param_2 == param_1) {
    *param_2 = iVar2;
  }
  else {
    iVar1 = *(int *)(param_1 + 4);
    if (*(int *)(iVar1 + 8) == param_1) {
      *(int *)(iVar1 + 8) = iVar2;
    }
    else {
      *(int *)(iVar1 + 0xc) = iVar2;
    }
  }
  *(int *)(iVar2 + 8) = param_1;
  *(int *)(param_1 + 4) = iVar2;
  return;
}



/* ---- Function: _ZNSt4priv10_Rb_globalIbE13_Rotate_rightEPNS_18_Rb_tree_node_baseERS3_ @ 00012264 ---- */

void _ZNSt4priv10_Rb_globalIbE13_Rotate_rightEPNS_18_Rb_tree_node_baseERS3_
               (int param_1,int *param_2)

{
  int iVar1;
  int iVar2;
  
  iVar2 = *(int *)(param_1 + 8);
  iVar1 = *(int *)(iVar2 + 0xc);
  if (iVar1 != 0) {
    *(int *)(iVar1 + 4) = param_1;
  }
  *(int *)(param_1 + 8) = iVar1;
  *(undefined4 *)(iVar2 + 4) = *(undefined4 *)(param_1 + 4);
  if (*param_2 == param_1) {
    *param_2 = iVar2;
  }
  else {
    iVar1 = *(int *)(param_1 + 4);
    if (*(int *)(iVar1 + 0xc) == param_1) {
      *(int *)(iVar1 + 0xc) = iVar2;
    }
    else {
      *(int *)(iVar1 + 8) = iVar2;
    }
  }
  *(int *)(iVar2 + 0xc) = param_1;
  *(int *)(param_1 + 4) = iVar2;
  return;
}



/* ---- Function: _ZNSt4priv10_Rb_globalIbE20_Rebalance_for_eraseEPNS_18_Rb_tree_node_baseERS3_S4_S4_ @ 000122b0 ---- */

char * _ZNSt4priv10_Rb_globalIbE20_Rebalance_for_eraseEPNS_18_Rb_tree_node_baseERS3_S4_S4_
                 (char *param_1,undefined4 *param_2,undefined4 *param_3,undefined4 *param_4)

{
  char cVar1;
  char *pcVar2;
  char *pcVar3;
  int iVar4;
  char cVar5;
  char *pcVar6;
  char *pcVar7;
  char *pcVar8;
  
  pcVar8 = *(char **)(param_1 + 8);
  pcVar7 = param_1;
  if (pcVar8 == (char *)0x0) {
    pcVar3 = *(char **)(param_1 + 0xc);
  }
  else {
    pcVar6 = *(char **)(param_1 + 0xc);
    pcVar2 = pcVar6;
    pcVar3 = pcVar8;
    if (pcVar6 != (char *)0x0) {
      do {
        pcVar7 = pcVar2;
        pcVar2 = *(char **)(pcVar7 + 8);
      } while (*(char **)(pcVar7 + 8) != (char *)0x0);
      pcVar3 = *(char **)(pcVar7 + 0xc);
      if (param_1 != pcVar7) {
        *(char **)(pcVar8 + 4) = pcVar7;
        *(char **)(pcVar7 + 8) = pcVar8;
        pcVar8 = pcVar7;
        if (pcVar6 != pcVar7) {
          pcVar8 = *(char **)(pcVar7 + 4);
          *(char **)(pcVar7 + 0xc) = pcVar6;
          iVar4 = *(int *)(param_1 + 0xc);
          pcVar2 = pcVar8;
          if (pcVar3 != (char *)0x0) {
            *(char **)(pcVar3 + 4) = pcVar8;
            pcVar2 = *(char **)(pcVar7 + 4);
          }
          *(char **)(iVar4 + 4) = pcVar7;
          *(char **)(pcVar2 + 8) = pcVar3;
        }
        if ((char *)*param_2 == param_1) {
          *param_2 = pcVar7;
          iVar4 = *(int *)(param_1 + 4);
        }
        else {
          iVar4 = *(int *)(param_1 + 4);
          if (*(char **)(iVar4 + 8) == param_1) {
            *(char **)(iVar4 + 8) = pcVar7;
          }
          else {
            *(char **)(iVar4 + 0xc) = pcVar7;
          }
        }
        cVar1 = *param_1;
        cVar5 = *pcVar7;
        *(int *)(pcVar7 + 4) = iVar4;
        *pcVar7 = cVar1;
        *param_1 = cVar5;
        goto LAB_00012368;
      }
    }
  }
  pcVar8 = *(char **)(pcVar7 + 4);
  if (pcVar3 != (char *)0x0) {
    *(char **)(pcVar3 + 4) = pcVar8;
  }
  if ((char *)*param_2 == param_1) {
    *param_2 = pcVar3;
  }
  else {
    iVar4 = *(int *)(param_1 + 4);
    if (*(char **)(iVar4 + 8) == param_1) {
      *(char **)(iVar4 + 8) = pcVar3;
    }
    else {
      *(char **)(iVar4 + 0xc) = pcVar3;
    }
  }
  if ((char *)*param_3 == param_1) {
    pcVar2 = pcVar3;
    if (*(int *)(param_1 + 0xc) == 0) {
      *param_3 = *(undefined4 *)(param_1 + 4);
    }
    else {
      do {
        pcVar6 = pcVar2;
        pcVar2 = *(char **)(pcVar6 + 8);
      } while (pcVar2 != (char *)0x0);
      *param_3 = pcVar6;
    }
  }
  if ((char *)*param_4 == param_1) {
    pcVar2 = pcVar3;
    if (*(int *)(param_1 + 8) == 0) {
      cVar5 = *pcVar7;
      *param_4 = *(undefined4 *)(param_1 + 4);
      param_1 = pcVar7;
    }
    else {
      do {
        pcVar6 = pcVar2;
        pcVar2 = *(char **)(pcVar6 + 0xc);
      } while (pcVar2 != (char *)0x0);
      cVar5 = *pcVar7;
      *param_4 = pcVar6;
      param_1 = pcVar7;
    }
  }
  else {
    cVar5 = *pcVar7;
    param_1 = pcVar7;
  }
LAB_00012368:
  if (cVar5 == '\0') {
    return param_1;
  }
LAB_00012378:
  do {
    pcVar2 = pcVar8;
    pcVar7 = pcVar3;
    if ((char *)*param_2 == pcVar3) {
LAB_00012544:
      if (pcVar7 == (char *)0x0) {
        return param_1;
      }
LAB_00012398:
      *pcVar7 = '\x01';
      return param_1;
    }
    if ((pcVar3 != (char *)0x0) && (*pcVar3 == '\0')) goto LAB_00012398;
    pcVar7 = *(char **)(pcVar2 + 8);
    if (pcVar7 == pcVar3) {
      pcVar8 = *(char **)(pcVar2 + 0xc);
      if (*pcVar8 == '\0') {
        *pcVar8 = '\x01';
        *pcVar2 = '\0';
        _ZNSt4priv10_Rb_globalIbE12_Rotate_leftEPNS_18_Rb_tree_node_baseERS3_(pcVar2,param_2);
        pcVar8 = *(char **)(pcVar2 + 0xc);
      }
      pcVar3 = *(char **)(pcVar8 + 8);
      if ((pcVar3 == (char *)0x0) || (*pcVar3 != '\0')) {
        pcVar6 = *(char **)(pcVar8 + 0xc);
        if ((pcVar6 == (char *)0x0) || (*pcVar6 != '\0')) {
          *pcVar8 = '\0';
          pcVar8 = *(char **)(pcVar2 + 4);
          pcVar3 = pcVar2;
          goto LAB_00012378;
        }
LAB_000125dc:
        *pcVar8 = *pcVar2;
        *pcVar2 = '\x01';
LAB_000125c0:
        *pcVar6 = '\x01';
      }
      else {
        pcVar6 = *(char **)(pcVar8 + 0xc);
        if ((pcVar6 != (char *)0x0) && (*pcVar6 == '\0')) goto LAB_000125dc;
        *pcVar3 = '\x01';
        *pcVar8 = '\0';
        _ZNSt4priv10_Rb_globalIbE13_Rotate_rightEPNS_18_Rb_tree_node_baseERS3_(pcVar8,param_2);
        pcVar6 = *(char **)(*(char **)(pcVar2 + 0xc) + 0xc);
        **(char **)(pcVar2 + 0xc) = *pcVar2;
        *pcVar2 = '\x01';
        if (pcVar6 != (char *)0x0) goto LAB_000125c0;
      }
      _ZNSt4priv10_Rb_globalIbE12_Rotate_leftEPNS_18_Rb_tree_node_baseERS3_(pcVar2,param_2);
      goto LAB_00012544;
    }
    if (*pcVar7 == '\0') {
      *pcVar7 = '\x01';
      *pcVar2 = '\0';
      _ZNSt4priv10_Rb_globalIbE13_Rotate_rightEPNS_18_Rb_tree_node_baseERS3_(pcVar2,param_2);
      pcVar7 = *(char **)(pcVar2 + 8);
    }
    pcVar8 = *(char **)(pcVar7 + 0xc);
    if ((pcVar8 != (char *)0x0) && (*pcVar8 == '\0')) {
      pcVar6 = *(char **)(pcVar7 + 8);
      if ((pcVar6 == (char *)0x0) || (*pcVar6 != '\0')) {
        *pcVar8 = '\x01';
        *pcVar7 = '\0';
        _ZNSt4priv10_Rb_globalIbE12_Rotate_leftEPNS_18_Rb_tree_node_baseERS3_(pcVar7,param_2);
        pcVar6 = *(char **)(*(char **)(pcVar2 + 8) + 8);
        **(char **)(pcVar2 + 8) = *pcVar2;
        *pcVar2 = '\x01';
        if (pcVar6 != (char *)0x0) goto LAB_00012530;
      }
      else {
LAB_00012550:
        *pcVar7 = *pcVar2;
        *pcVar2 = '\x01';
LAB_00012530:
        *pcVar6 = '\x01';
      }
      _ZNSt4priv10_Rb_globalIbE13_Rotate_rightEPNS_18_Rb_tree_node_baseERS3_(pcVar2,param_2);
      pcVar7 = pcVar3;
      goto LAB_00012544;
    }
    pcVar6 = *(char **)(pcVar7 + 8);
    if ((pcVar6 != (char *)0x0) && (*pcVar6 == '\0')) goto LAB_00012550;
    pcVar8 = *(char **)(pcVar2 + 4);
    *pcVar7 = '\0';
    pcVar3 = pcVar2;
  } while( true );
}



/* ---- Function: _ZN8DpmFdMgr18destroyFdIfTrackerESs @ 00012678 ---- */

void _ZN8DpmFdMgr18destroyFdIfTrackerESs(int param_1,int param_2)

{
  int *piVar1;
  int iVar2;
  int iVar3;
  size_t sVar4;
  size_t __n;
  void *__s2;
  int iVar5;
  int iVar6;
  int iVar7;
  size_t sVar8;
  void *__s1;
  
  iVar7 = param_1 + 0x24;
  piVar1 = (int *)**(undefined4 **)(DAT_00012814 + 0x12694 + DAT_00012818);
  (**(code **)(*piVar1 + 8))(piVar1,1,0x28a8,DAT_0001281c + 0x126b0,*(undefined4 *)(param_2 + 0x14))
  ;
  iVar3 = iVar7;
  if (*(int *)(param_1 + 0x28) != 0) {
    __s2 = *(void **)(param_2 + 0x14);
    __n = *(int *)(param_2 + 0x10) - (int)__s2;
    iVar2 = *(int *)(param_1 + 0x28);
    iVar5 = iVar7;
    do {
      while( true ) {
        iVar6 = iVar2;
        __s1 = *(void **)(iVar6 + 0x24);
        sVar8 = *(int *)(iVar6 + 0x20) - (int)__s1;
        if ((int)__n < (int)sVar8) break;
        iVar2 = memcmp(__s1,__s2,sVar8);
        if (iVar2 == 0) {
          if ((int)__n <= (int)sVar8) goto LAB_000126f4;
        }
        else {
LAB_00012800:
          if (-1 < iVar2) goto LAB_000126f4;
        }
        piVar1 = (int *)(iVar6 + 0xc);
        iVar2 = *piVar1;
        iVar6 = iVar5;
        if (*piVar1 == 0) goto LAB_00012750;
      }
      iVar2 = memcmp(__s1,__s2,__n);
      if (iVar2 != 0) goto LAB_00012800;
LAB_000126f4:
      iVar2 = *(int *)(iVar6 + 8);
      iVar5 = iVar6;
    } while (*(int *)(iVar6 + 8) != 0);
LAB_00012750:
    if (iVar7 != iVar6) {
      sVar4 = *(int *)(iVar6 + 0x20) - (int)*(void **)(iVar6 + 0x24);
      sVar8 = __n;
      if ((int)sVar4 <= (int)__n) {
        sVar8 = sVar4;
      }
      iVar2 = memcmp(__s2,*(void **)(iVar6 + 0x24),sVar8);
      if (iVar2 == 0) {
        if ((int)sVar4 <= (int)__n) {
          iVar3 = iVar6;
        }
      }
      else if (-1 < iVar2) {
        iVar3 = iVar6;
      }
    }
  }
  if (iVar3 != iVar7) {
    iVar7 = *(int *)(iVar3 + 0x28);
    if (iVar7 != 0) {
      _ZN20DpmFdIdleTimeTrackerD2Ev(iVar7);
      _ZdlPv(iVar7);
    }
    iVar3 = _ZNSt4priv10_Rb_globalIbE20_Rebalance_for_eraseEPNS_18_Rb_tree_node_baseERS3_S4_S4_
                      (iVar3,param_1 + 0x28,param_1 + 0x2c,param_1 + 0x30);
    if ((*(int *)(iVar3 + 0x24) != iVar3 + 0x10) && (*(int *)(iVar3 + 0x24) != 0)) {
      _ZdlPv();
    }
    _ZdlPv(iVar3);
    *(int *)(param_1 + 0x34) = *(int *)(param_1 + 0x34) + -1;
  }
  return;
}



/* ---- Function: _ZN8DpmFdMgr16deleteFdTrackersEv @ 00012820 ---- */

void _ZN8DpmFdMgr16deleteFdTrackersEv(int param_1)

{
  undefined1 *puVar1;
  int *piVar2;
  undefined1 auStack_4c [4];
  undefined4 local_48;
  undefined1 *local_44;
  int local_3c;
  undefined1 auStack_34 [20];
  undefined1 *local_20;
  int local_1c;
  
  piVar2 = *(int **)(DAT_000128e8 + 0x12830 + DAT_000128ec);
  local_1c = *piVar2;
  _ZN6DpmDsm14getAllWwanInfoEv(auStack_4c,*(undefined4 *)(param_1 + 0x1c));
  if (local_3c != 0) {
    if (local_44 != auStack_4c) {
      puVar1 = local_44;
      do {
        _ZNSsC1ERKSs(auStack_34,puVar1 + 0x10);
        _ZN8DpmFdMgr18destroyFdIfTrackerESs(param_1,auStack_34);
        if ((local_20 != auStack_34) && (local_20 != (undefined1 *)0x0)) {
          _ZdlPv();
        }
        puVar1 = (undefined1 *)
                 _ZNSt4priv10_Rb_globalIbE12_M_incrementEPNS_18_Rb_tree_node_baseE(puVar1);
      } while (puVar1 != auStack_4c);
    }
    if (local_3c != 0) {
      _ZNSt4priv8_Rb_treeISsSt4lessISsESt4pairIKSs19_DpmDsmWwanInfoTypeENS_10_Select1stIS6_EENS_11_MapTraitsTIS6_EESaIS6_EE8_M_eraseEPNS_18_Rb_tree_node_baseE
                (auStack_4c,local_48);
    }
  }
  if (local_1c != *piVar2) {
                    /* WARNING: Subroutine does not return */
    __stack_chk_fail();
  }
  return;
}



/* ---- Function: _ZN15EventDispatcherI11DpmDsmEventE18deregEventCallbackES0_PFvS0_PKvPvES4_ @ 000128f0 ---- */

undefined4
_ZN15EventDispatcherI11DpmDsmEventE18deregEventCallbackES0_PFvS0_PKvPvES4_
          (int param_1,int param_2,int param_3,int param_4)

{
  int iVar1;
  int *piVar2;
  int iVar3;
  int iVar4;
  int iVar5;
  int iVar6;
  
  iVar5 = *(int *)(param_1 + 4);
  iVar6 = iVar5;
  iVar1 = param_1;
  if (iVar5 != 0) {
    do {
      if (*(int *)(iVar6 + 0x10) < param_2) {
        iVar3 = *(int *)(iVar6 + 0xc);
      }
      else {
        iVar3 = *(int *)(iVar6 + 8);
        iVar1 = iVar6;
      }
      iVar6 = iVar3;
      iVar4 = param_1;
    } while (iVar3 != 0);
    do {
      if (param_2 < *(int *)(iVar5 + 0x10)) {
        iVar6 = *(int *)(iVar5 + 8);
        iVar4 = iVar5;
      }
      else {
        iVar6 = *(int *)(iVar5 + 0xc);
      }
      iVar5 = iVar6;
    } while (iVar6 != 0);
    for (; iVar4 != iVar1;
        iVar1 = _ZNSt4priv10_Rb_globalIbE12_M_incrementEPNS_18_Rb_tree_node_baseE(iVar1)) {
      piVar2 = *(int **)(iVar1 + 0x14);
      if ((*piVar2 == param_3) && (piVar2[1] == param_4)) {
        if (piVar2[2] == 0) {
          _ZdlPv(piVar2);
          iVar1 = _ZNSt4priv10_Rb_globalIbE20_Rebalance_for_eraseEPNS_18_Rb_tree_node_baseERS3_S4_S4_
                            (iVar1,param_1 + 4,param_1 + 8,param_1 + 0xc);
          if (iVar1 != 0) {
            _ZdlPv();
          }
          *(undefined4 *)(param_1 + 0x18) = 1;
          *(int *)(param_1 + 0x10) = *(int *)(param_1 + 0x10) + -1;
          return 1;
        }
        if (piVar2[2] != 1) {
          return 1;
        }
        piVar2[2] = 2;
        return 1;
      }
    }
  }
  return 1;
}



/* ---- Function: _ZN8DpmFdMgrD1Ev @ 000129f8 ---- */

int _ZN8DpmFdMgrD1Ev(int param_1)

{
  int *piVar1;
  int iVar2;
  int iVar3;
  int iVar4;
  int iVar5;
  
  iVar5 = param_1 + 0x24;
  piVar1 = (int *)**(undefined4 **)(DAT_00012b88 + 0x12a14 + DAT_00012b8c);
  (**(code **)(*piVar1 + 8))(piVar1,0,0x28a8,DAT_00012b94 + 0x12a30,DAT_00012b90 + 0x12a2c,0x6d);
  iVar3 = *(int *)(param_1 + 0x2c);
  if (iVar3 == iVar5) {
    iVar4 = *(int *)(param_1 + 0x34);
  }
  else {
    do {
      iVar4 = *(int *)(iVar3 + 0x28);
      if (iVar4 != 0) {
        _ZN20DpmFdIdleTimeTrackerD2Ev(iVar4);
        _ZdlPv(iVar4);
      }
      iVar4 = _ZNSt4priv10_Rb_globalIbE20_Rebalance_for_eraseEPNS_18_Rb_tree_node_baseERS3_S4_S4_
                        (iVar3,param_1 + 0x28,param_1 + 0x2c,param_1 + 0x30);
      iVar2 = *(int *)(iVar4 + 0x24);
      if ((iVar2 != iVar4 + 0x10) && (iVar2 != 0)) {
        _ZdlPv(iVar2);
      }
      _ZdlPv(iVar4);
      iVar4 = *(int *)(param_1 + 0x34) + -1;
      *(int *)(param_1 + 0x34) = iVar4;
      iVar3 = _ZNSt4priv10_Rb_globalIbE12_M_incrementEPNS_18_Rb_tree_node_baseE(iVar3);
    } while (iVar3 != iVar5);
  }
  if (iVar4 != 0) {
    _ZNSt4priv8_Rb_treeISsSt4lessISsESt4pairIKSsiENS_10_Select1stIS5_EENS_11_MapTraitsTIS5_EESaIS5_EE8_M_eraseEPNS_18_Rb_tree_node_baseE
              (iVar5,*(undefined4 *)(param_1 + 0x28));
    *(int *)(param_1 + 0x2c) = iVar5;
    *(undefined4 *)(param_1 + 0x28) = 0;
    *(int *)(param_1 + 0x30) = iVar5;
    *(undefined4 *)(param_1 + 0x34) = 0;
  }
  if (*(int *)(param_1 + 0x1c) != 0) {
    iVar3 = DAT_00012b98 + 0x12b28;
    _ZN15EventDispatcherI11DpmDsmEventE18deregEventCallbackES0_PFvS0_PKvPvES4_
              (*(int *)(param_1 + 0x1c),1,iVar3,param_1);
    _ZN15EventDispatcherI11DpmDsmEventE18deregEventCallbackES0_PFvS0_PKvPvES4_
              (*(undefined4 *)(param_1 + 0x1c),3,iVar3,param_1);
  }
  if (*(int *)(param_1 + 0x34) != 0) {
    _ZNSt4priv8_Rb_treeISsSt4lessISsESt4pairIKSsiENS_10_Select1stIS5_EENS_11_MapTraitsTIS5_EESaIS5_EE8_M_eraseEPNS_18_Rb_tree_node_baseE
              (iVar5,*(undefined4 *)(param_1 + 0x28));
    *(int *)(param_1 + 0x2c) = iVar5;
    *(undefined4 *)(param_1 + 0x28) = 0;
    *(int *)(param_1 + 0x30) = iVar5;
    *(undefined4 *)(param_1 + 0x34) = 0;
  }
  _ZN11DpmFdConfigD2Ev(param_1);
  return param_1;
}



/* ---- Function: deinit_fd_mgr @ 00012b9c ---- */

undefined4 deinit_fd_mgr(void)

{
  int iVar1;
  int *piVar2;
  
  piVar2 = (int *)(DAT_00012bdc + 0x12bac);
  iVar1 = *piVar2;
  if (iVar1 != 0) {
    _ZN8DpmFdMgrD1Ev(iVar1);
    _ZdlPv(iVar1);
    *piVar2 = 0;
    return 0;
  }
  return 0xffffffff;
}



/* ---- Function: _ZNSt4priv10_Rb_globalIbE10_RebalanceEPNS_18_Rb_tree_node_baseERS3_ @ 00012be0 ---- */

void _ZNSt4priv10_Rb_globalIbE10_RebalanceEPNS_18_Rb_tree_node_baseERS3_
               (char *param_1,undefined4 *param_2)

{
  char *pcVar1;
  char *pcVar2;
  char *pcVar3;
  char *pcVar4;
  
  *param_1 = '\0';
  pcVar1 = (char *)*param_2;
  while ((param_1 != pcVar1 && (pcVar4 = *(char **)(param_1 + 4), *pcVar4 == '\0'))) {
    pcVar3 = *(char **)(pcVar4 + 4);
    pcVar2 = *(char **)(pcVar3 + 8);
    if (pcVar4 == pcVar2) {
      pcVar2 = *(char **)(pcVar3 + 0xc);
      if ((pcVar2 == (char *)0x0) || (*pcVar2 != '\0')) {
        pcVar1 = pcVar4;
        if (*(char **)(pcVar4 + 0xc) == param_1) {
          _ZNSt4priv10_Rb_globalIbE12_Rotate_leftEPNS_18_Rb_tree_node_baseERS3_(pcVar4,param_2);
          pcVar1 = *(char **)(pcVar4 + 4);
          pcVar3 = *(char **)(pcVar1 + 4);
          param_1 = pcVar4;
        }
        *pcVar1 = '\x01';
        *pcVar3 = '\0';
        _ZNSt4priv10_Rb_globalIbE13_Rotate_rightEPNS_18_Rb_tree_node_baseERS3_(pcVar3,param_2);
        pcVar1 = (char *)*param_2;
      }
      else {
        *pcVar4 = '\x01';
        *pcVar2 = '\x01';
        *pcVar3 = '\0';
        param_1 = pcVar3;
      }
    }
    else if ((pcVar2 == (char *)0x0) || (*pcVar2 != '\0')) {
      pcVar1 = pcVar4;
      if (*(char **)(pcVar4 + 8) == param_1) {
        _ZNSt4priv10_Rb_globalIbE13_Rotate_rightEPNS_18_Rb_tree_node_baseERS3_(pcVar4,param_2);
        pcVar1 = *(char **)(pcVar4 + 4);
        pcVar3 = *(char **)(pcVar1 + 4);
        param_1 = pcVar4;
      }
      *pcVar1 = '\x01';
      *pcVar3 = '\0';
      _ZNSt4priv10_Rb_globalIbE12_Rotate_leftEPNS_18_Rb_tree_node_baseERS3_(pcVar3,param_2);
      pcVar1 = (char *)*param_2;
    }
    else {
      *pcVar4 = '\x01';
      *pcVar2 = '\x01';
      *pcVar3 = '\0';
      param_1 = pcVar3;
    }
  }
  *pcVar1 = '\x01';
  return;
}



/* ---- Function: _ZNSt4priv8_Rb_treeISsSt4lessISsESt4pairIKSsP20DpmFdIdleTimeTrackerENS_10_Select1stIS7_EENS_11_MapTraitsTIS7_EESaIS7_EE9_M_insertEPNS_18_Rb_tree_node_baseERKS7_SF_SF_ @ 00012d18 ---- */

int * _ZNSt4priv8_Rb_treeISsSt4lessISsESt4pairIKSsP20DpmFdIdleTimeTrackerENS_10_Select1stIS7_EENS_11_MapTraitsTIS7_EESaIS7_EE9_M_insertEPNS_18_Rb_tree_node_baseERKS7_SF_SF_
                (int *param_1,int param_2,int param_3,int param_4,int param_5,int param_6)

{
  int iVar1;
  undefined4 uVar2;
  size_t __n;
  size_t sVar3;
  size_t sVar4;
  
  if (param_2 == param_3) {
    iVar1 = _Znwj(0x2c);
    if (iVar1 == 0) goto LAB_00012e84;
    _ZNSsC1ERKSs(iVar1 + 0x10,param_4);
    uVar2 = *(undefined4 *)(param_4 + 0x18);
    *(undefined4 *)(iVar1 + 8) = 0;
    *(undefined4 *)(iVar1 + 0xc) = 0;
    *(undefined4 *)(iVar1 + 0x28) = uVar2;
    *(int *)(param_2 + 8) = iVar1;
    *(int *)(param_2 + 4) = iVar1;
    *(int *)(param_2 + 0xc) = iVar1;
    goto LAB_00012d80;
  }
  if (param_6 != 0) goto LAB_00012d40;
  if (param_5 == 0) {
    sVar4 = *(int *)(param_4 + 0x10) - (int)*(void **)(param_4 + 0x14);
    sVar3 = *(int *)(param_3 + 0x20) - (int)*(void **)(param_3 + 0x24);
    __n = sVar3;
    if ((int)sVar4 <= (int)sVar3) {
      __n = sVar4;
    }
    iVar1 = memcmp(*(void **)(param_4 + 0x14),*(void **)(param_3 + 0x24),__n);
    if (iVar1 == 0) {
      if ((int)sVar3 <= (int)sVar4) {
LAB_00012d40:
        iVar1 = _Znwj(0x2c);
        if (iVar1 == 0) goto LAB_00012e84;
        _ZNSsC1ERKSs(iVar1 + 0x10,param_4);
        uVar2 = *(undefined4 *)(param_4 + 0x18);
        *(undefined4 *)(iVar1 + 8) = 0;
        *(undefined4 *)(iVar1 + 0xc) = 0;
        *(undefined4 *)(iVar1 + 0x28) = uVar2;
        *(int *)(param_3 + 0xc) = iVar1;
        if (param_3 == *(int *)(param_2 + 0xc)) {
          *(int *)(param_2 + 0xc) = iVar1;
        }
        goto LAB_00012d80;
      }
    }
    else if (-1 < iVar1) goto LAB_00012d40;
  }
  iVar1 = _Znwj(0x2c);
  if (iVar1 == 0) {
LAB_00012e84:
    puts((char *)(DAT_00012e98 + 0x12e90));
                    /* WARNING: Subroutine does not return */
    exit(1);
  }
  _ZNSsC1ERKSs(iVar1 + 0x10,param_4);
  uVar2 = *(undefined4 *)(param_4 + 0x18);
  *(undefined4 *)(iVar1 + 8) = 0;
  *(undefined4 *)(iVar1 + 0xc) = 0;
  *(undefined4 *)(iVar1 + 0x28) = uVar2;
  *(int *)(param_3 + 8) = iVar1;
  if (param_3 == *(int *)(param_2 + 8)) {
    *(int *)(param_2 + 8) = iVar1;
  }
LAB_00012d80:
  *(int *)(iVar1 + 4) = param_3;
  _ZNSt4priv10_Rb_globalIbE10_RebalanceEPNS_18_Rb_tree_node_baseERS3_(iVar1,param_2 + 4);
  *(int *)(param_2 + 0x10) = *(int *)(param_2 + 0x10) + 1;
  *param_1 = iVar1;
  return param_1;
}



/* ---- Function: _ZNSt4priv8_Rb_treeISsSt4lessISsESt4pairIKSsP20DpmFdIdleTimeTrackerENS_10_Select1stIS7_EENS_11_MapTraitsTIS7_EESaIS7_EE13insert_uniqueERKS7_ @ 00012e9c ---- */

undefined4 *
_ZNSt4priv8_Rb_treeISsSt4lessISsESt4pairIKSsP20DpmFdIdleTimeTrackerENS_10_Select1stIS7_EENS_11_MapTraitsTIS7_EESaIS7_EE13insert_uniqueERKS7_
          (undefined4 *param_1,char *param_2,int param_3)

{
  int iVar1;
  size_t __n;
  char *pcVar2;
  char *pcVar3;
  void *__s2;
  size_t __n_00;
  size_t __n_01;
  void *__s1;
  char *pcVar4;
  bool bVar5;
  undefined4 local_2c [2];
  
  pcVar3 = param_2;
  if (*(char **)(param_2 + 4) == (char *)0x0) {
LAB_00012f9c:
    pcVar2 = pcVar3;
    if (pcVar3 != *(char **)(param_2 + 8)) {
      if ((*pcVar3 == '\0') && (*(char **)(*(int *)(pcVar3 + 4) + 4) == pcVar3)) {
        pcVar2 = *(char **)(pcVar3 + 0xc);
      }
      else {
        pcVar4 = *(char **)(pcVar3 + 8);
        if (*(char **)(pcVar3 + 8) == (char *)0x0) {
          pcVar2 = *(char **)(pcVar3 + 4);
          pcVar4 = pcVar2;
          if (pcVar3 == *(char **)(pcVar2 + 8)) {
            do {
              pcVar2 = *(char **)(pcVar4 + 4);
              bVar5 = *(char **)(pcVar2 + 8) == pcVar4;
              pcVar4 = pcVar2;
            } while (bVar5);
          }
        }
        else {
          do {
            pcVar2 = pcVar4;
            pcVar4 = *(char **)(pcVar2 + 0xc);
          } while (*(char **)(pcVar2 + 0xc) != (char *)0x0);
        }
      }
      __s1 = *(void **)(param_3 + 0x14);
      __s2 = *(void **)(pcVar2 + 0x24);
      __n_01 = *(int *)(param_3 + 0x10) - (int)__s1;
      __n_00 = *(int *)(pcVar2 + 0x20) - (int)__s2;
      goto LAB_00012f48;
    }
LAB_0001301c:
    _ZNSt4priv8_Rb_treeISsSt4lessISsESt4pairIKSsP20DpmFdIdleTimeTrackerENS_10_Select1stIS7_EENS_11_MapTraitsTIS7_EESaIS7_EE9_M_insertEPNS_18_Rb_tree_node_baseERKS7_SF_SF_
              (local_2c,param_2,pcVar3,param_3,pcVar2,0);
    *(undefined1 *)(param_1 + 1) = 1;
    *param_1 = local_2c[0];
  }
  else {
    __s1 = *(void **)(param_3 + 0x14);
    __n_01 = *(int *)(param_3 + 0x10) - (int)__s1;
    pcVar2 = *(char **)(param_2 + 4);
    do {
      pcVar3 = pcVar2;
      __s2 = *(void **)(pcVar3 + 0x24);
      __n_00 = *(int *)(pcVar3 + 0x20) - (int)__s2;
      if ((int)__n_00 < (int)__n_01) {
        iVar1 = memcmp(__s1,__s2,__n_00);
        if (iVar1 != 0) goto LAB_00012f90;
LAB_00012ee0:
        pcVar2 = *(char **)(pcVar3 + 0xc);
        bVar5 = false;
      }
      else {
        iVar1 = memcmp(__s1,__s2,__n_01);
        if (iVar1 == 0) {
          if ((int)__n_00 <= (int)__n_01) goto LAB_00012ee0;
        }
        else {
LAB_00012f90:
          if (-1 < iVar1) goto LAB_00012ee0;
        }
        pcVar2 = *(char **)(pcVar3 + 8);
        bVar5 = true;
      }
    } while (pcVar2 != (char *)0x0);
    pcVar2 = pcVar3;
    if (bVar5) goto LAB_00012f9c;
LAB_00012f48:
    __n = __n_00;
    if ((int)__n_01 <= (int)__n_00) {
      __n = __n_01;
    }
    iVar1 = memcmp(__s2,__s1,__n);
    if (iVar1 == 0) {
      if ((int)__n_00 < (int)__n_01) {
LAB_00013008:
        pcVar2 = (char *)0x0;
        goto LAB_0001301c;
      }
    }
    else if (iVar1 < 0) goto LAB_00013008;
    *param_1 = pcVar2;
    *(undefined1 *)(param_1 + 1) = 0;
  }
  return param_1;
}



/* ---- Function: _ZN8DpmFdMgr17createFdIfTrackerESs @ 00013084 ---- */

void _ZN8DpmFdMgr17createFdIfTrackerESs(int param_1,int param_2)

{
  int *piVar1;
  int *piVar2;
  void *__s1;
  int iVar3;
  int iVar4;
  int iVar5;
  size_t __n;
  void *__s2;
  size_t sVar6;
  int iVar7;
  int iVar8;
  size_t sVar9;
  undefined1 auStack_84 [8];
  undefined1 auStack_7c [20];
  undefined1 *local_68;
  undefined1 auStack_64 [20];
  undefined1 *local_50;
  int local_4c;
  undefined1 auStack_48 [20];
  undefined1 *local_34;
  int local_30;
  int local_2c;
  
  piVar1 = *(int **)(DAT_000132bc + 0x13098 + DAT_000132c0);
  iVar8 = param_1 + 0x24;
  local_2c = *piVar1;
  piVar2 = (int *)**(undefined4 **)(DAT_000132bc + 0x13098 + DAT_000132c8);
  (**(code **)(*piVar2 + 8))(piVar2,1,0x28a8,DAT_000132c4 + 0x130c0,*(undefined4 *)(param_2 + 0x14))
  ;
  iVar5 = iVar8;
  if (*(int *)(param_1 + 0x28) != 0) {
    __s2 = *(void **)(param_2 + 0x14);
    __n = *(int *)(param_2 + 0x10) - (int)__s2;
    iVar4 = iVar8;
    iVar3 = *(int *)(param_1 + 0x28);
    do {
      while( true ) {
        iVar7 = iVar3;
        __s1 = *(void **)(iVar7 + 0x24);
        sVar9 = *(int *)(iVar7 + 0x20) - (int)__s1;
        if ((int)__n < (int)sVar9) break;
        iVar3 = memcmp(__s1,__s2,sVar9);
        if (iVar3 == 0) {
          if ((int)__n <= (int)sVar9) goto LAB_0001311c;
        }
        else {
LAB_000131cc:
          if (-1 < iVar3) goto LAB_0001311c;
        }
        piVar2 = (int *)(iVar7 + 0xc);
        iVar7 = iVar4;
        iVar3 = *piVar2;
        if (*piVar2 == 0) goto LAB_00013168;
      }
      iVar3 = memcmp(__s1,__s2,__n);
      if (iVar3 != 0) goto LAB_000131cc;
LAB_0001311c:
      iVar4 = iVar7;
      iVar3 = *(int *)(iVar7 + 8);
    } while (*(int *)(iVar7 + 8) != 0);
LAB_00013168:
    if (iVar8 != iVar7) {
      sVar6 = *(int *)(iVar7 + 0x20) - (int)*(void **)(iVar7 + 0x24);
      sVar9 = __n;
      if ((int)sVar6 <= (int)__n) {
        sVar9 = sVar6;
      }
      iVar4 = memcmp(__s2,*(void **)(iVar7 + 0x24),sVar9);
      if (iVar4 == 0) {
        iVar5 = iVar7;
        if ((int)__n < (int)sVar6) {
          iVar5 = iVar8;
        }
      }
      else if (-1 < iVar4) {
        iVar5 = iVar7;
      }
    }
  }
  if (iVar5 == iVar8) {
    _ZNSsC1ERKSs(auStack_7c,param_2);
    iVar5 = _Znwj(0x40);
    _ZN20DpmFdIdleTimeTrackerC2ER3DpmR11DpmFdConfigSs
              (iVar5,*(undefined4 *)(param_1 + 0x18),param_1,auStack_7c);
    if ((local_68 != auStack_7c) && (local_68 != (undefined1 *)0x0)) {
      _ZdlPv();
    }
    if (iVar5 != 0) {
      _ZNSsC1ERKSs(auStack_64,param_2);
      local_4c = iVar5;
      _ZNSsC1ERKSs(auStack_48,auStack_64);
      local_30 = local_4c;
      _ZNSt4priv8_Rb_treeISsSt4lessISsESt4pairIKSsP20DpmFdIdleTimeTrackerENS_10_Select1stIS7_EENS_11_MapTraitsTIS7_EESaIS7_EE13insert_uniqueERKS7_
                (auStack_84,iVar8,auStack_48);
      if ((local_34 != auStack_48) && (local_34 != (undefined1 *)0x0)) {
        _ZdlPv();
      }
      if ((local_50 != auStack_64) && (local_50 != (undefined1 *)0x0)) {
        _ZdlPv();
      }
      _ZN20DpmFdIdleTimeTracker19regIdleStatusChgEvtEPFvPKvPvES2_
                (iVar5,DAT_000132cc + 0x132b4,param_1);
    }
  }
  if (local_2c != *piVar1) {
                    /* WARNING: Subroutine does not return */
    __stack_chk_fail();
  }
  return;
}



/* ---- Function: _ZN8DpmFdMgr16createFdTrackersEv @ 000132d0 ---- */

void _ZN8DpmFdMgr16createFdTrackersEv(int param_1)

{
  undefined4 *puVar1;
  int *****pppppiVar2;
  undefined1 *puVar3;
  undefined4 uVar4;
  undefined4 uVar5;
  undefined4 uVar6;
  int *piVar7;
  undefined4 *puVar8;
  undefined4 *puVar9;
  undefined4 *puVar10;
  int ****ppppiVar11;
  int *piVar12;
  undefined1 auStack_f8 [4];
  undefined4 local_f4;
  undefined1 *local_f0;
  int local_e8;
  undefined1 auStack_e0 [20];
  undefined1 *local_cc;
  undefined4 local_c8;
  undefined4 local_c4;
  int local_c0;
  undefined4 local_bc;
  int ****local_b8;
  int ****local_b4;
  undefined4 local_b0;
  undefined4 uStack_ac;
  undefined4 uStack_a8;
  undefined4 uStack_a4;
  undefined4 local_a0;
  undefined4 uStack_9c;
  undefined4 uStack_98;
  undefined4 uStack_94;
  undefined4 local_90;
  undefined4 uStack_8c;
  undefined4 uStack_88;
  undefined2 local_84;
  undefined4 local_82;
  undefined4 local_7e;
  undefined4 local_7a;
  undefined4 local_76;
  undefined4 local_72 [7];
  undefined2 local_56 [9];
  undefined4 local_44;
  undefined4 uStack_40;
  undefined4 uStack_3c;
  undefined4 uStack_38;
  undefined4 local_34;
  undefined2 local_30;
  undefined1 local_2e;
  int local_2c;
  
  piVar12 = *(int **)(DAT_0001352c + 0x132e4 + DAT_00013530);
  local_2c = *piVar12;
  _ZN6DpmDsm14getAllWwanInfoEv(auStack_f8,*(undefined4 *)(param_1 + 0x1c));
  if (local_e8 != 0) {
    if (local_f0 != auStack_f8) {
      puVar3 = local_f0;
      do {
        local_c8 = *(undefined4 *)(puVar3 + 0x28);
        piVar7 = *(int **)(puVar3 + 0x38);
        local_c4 = *(undefined4 *)(puVar3 + 0x2c);
        local_c0 = *(int *)(puVar3 + 0x30);
        local_bc = *(undefined4 *)(puVar3 + 0x34);
        local_b4 = (int ****)&local_b8;
        local_b8 = (int ****)&local_b8;
        while (piVar7 != (int *)(puVar3 + 0x38)) {
          pppppiVar2 = (int *****)_Znwj(0xc);
          if (pppppiVar2 == (int *****)0x0) {
            puts((char *)(DAT_00013534 + 0x13520));
                    /* WARNING: Subroutine does not return */
            exit(1);
          }
          ppppiVar11 = (int ****)piVar7[2];
          *pppppiVar2 = (int ****)&local_b8;
          pppppiVar2[2] = ppppiVar11;
          *local_b4 = (int ***)pppppiVar2;
          piVar7 = (int *)*piVar7;
          pppppiVar2[1] = local_b4;
          local_b4 = (int ****)pppppiVar2;
        }
        local_b0 = *(undefined4 *)(puVar3 + 0x40);
        uStack_ac = *(undefined4 *)(puVar3 + 0x44);
        uStack_a8 = *(undefined4 *)(puVar3 + 0x48);
        uStack_a4 = *(undefined4 *)(puVar3 + 0x4c);
        local_82 = *(undefined4 *)(puVar3 + 0x6e);
        local_a0 = *(undefined4 *)(puVar3 + 0x50);
        uStack_9c = *(undefined4 *)(puVar3 + 0x54);
        uStack_98 = *(undefined4 *)(puVar3 + 0x58);
        uStack_94 = *(undefined4 *)(puVar3 + 0x5c);
        local_7e = *(undefined4 *)(puVar3 + 0x72);
        local_7a = *(undefined4 *)(puVar3 + 0x76);
        local_90 = *(undefined4 *)(puVar3 + 0x60);
        uStack_8c = *(undefined4 *)(puVar3 + 100);
        uStack_88 = *(undefined4 *)(puVar3 + 0x68);
        local_76 = *(undefined4 *)(puVar3 + 0x7a);
        local_84 = (undefined2)*(undefined4 *)(puVar3 + 0x6c);
        puVar9 = (undefined4 *)(puVar3 + 0x7e);
        puVar1 = local_72;
        do {
          puVar10 = puVar1;
          puVar8 = puVar9;
          uVar4 = puVar8[1];
          uVar5 = puVar8[2];
          uVar6 = puVar8[3];
          puVar9 = puVar8 + 4;
          *puVar10 = *puVar8;
          puVar10[1] = uVar4;
          puVar10[2] = uVar5;
          puVar10[3] = uVar6;
          puVar1 = puVar10 + 4;
        } while (puVar9 != (undefined4 *)(puVar3 + 0x9e));
        uVar4 = puVar8[6];
        uVar5 = *puVar9;
        puVar10[5] = puVar8[5];
        puVar10[6] = uVar4;
        puVar10[4] = uVar5;
        *(undefined2 *)(puVar10 + 7) = *(undefined2 *)(puVar8 + 7);
        local_44 = *(undefined4 *)(puVar3 + 0xac);
        uStack_40 = *(undefined4 *)(puVar3 + 0xb0);
        uStack_3c = *(undefined4 *)(puVar3 + 0xb4);
        uStack_38 = *(undefined4 *)(puVar3 + 0xb8);
        local_34 = *(undefined4 *)(puVar3 + 0xbc);
        local_2e = (undefined1)((uint)*(undefined4 *)(puVar3 + 0xc0) >> 0x10);
        local_30 = (undefined2)*(undefined4 *)(puVar3 + 0xc0);
        if (local_c0 == 1) {
          _ZNSsC1ERKSs(auStack_e0,puVar3 + 0x10);
          _ZN8DpmFdMgr17createFdIfTrackerESs(param_1,auStack_e0);
          if ((local_cc != auStack_e0) && (local_cc != (undefined1 *)0x0)) {
            _ZdlPv();
          }
        }
        _ZNSt4priv10_List_baseI10DpmApnTypeSaIS1_EE5clearEv(&local_b8);
        puVar3 = (undefined1 *)
                 _ZNSt4priv10_Rb_globalIbE12_M_incrementEPNS_18_Rb_tree_node_baseE(puVar3);
      } while (puVar3 != auStack_f8);
    }
    if (local_e8 != 0) {
      _ZNSt4priv8_Rb_treeISsSt4lessISsESt4pairIKSs19_DpmDsmWwanInfoTypeENS_10_Select1stIS6_EENS_11_MapTraitsTIS6_EESaIS6_EE8_M_eraseEPNS_18_Rb_tree_node_baseE
                (auStack_f8,local_f4);
    }
  }
  if (local_2c == *piVar12) {
    return;
  }
                    /* WARNING: Subroutine does not return */
  __stack_chk_fail();
}



/* ---- Function: _ZN8DpmFdMgr15dsmEventHandlerE11DpmDsmEventPKvPv @ 00013538 ---- */

void _ZN8DpmFdMgr15dsmEventHandlerE11DpmDsmEventPKvPv(int param_1,uint *param_2,int param_3)

{
  int *piVar1;
  int iVar2;
  undefined4 *puVar3;
  uint *puVar4;
  int *piVar5;
  int iVar6;
  undefined1 auStack_60 [4];
  undefined1 auStack_5c [12];
  uint local_50;
  undefined1 auStack_4c [20];
  undefined1 *local_38;
  undefined1 auStack_34 [20];
  undefined1 *local_20;
  int local_1c;
  
  iVar6 = DAT_00013760 + 0x13550;
  piVar1 = *(int **)(iVar6 + DAT_00013764);
  local_1c = *piVar1;
  if (param_2 == (uint *)0x0 || param_3 == 0) {
    iVar2 = *(int *)(DAT_00013774 + 0x135d8);
  }
  else {
    piVar5 = (int *)(DAT_00013768 + 0x1357c);
    iVar2 = *piVar5;
    if (param_3 == iVar2) {
      puVar3 = *(undefined4 **)(iVar6 + DAT_0001376c);
      (**(code **)(*(int *)*puVar3 + 8))((int *)*puVar3,1,0x28a8,DAT_00013778 + 0x135f0,param_1);
      if (param_1 == 1) {
        puVar4 = param_2 + 1;
        (**(code **)(*(int *)*puVar3 + 8))
                  ((int *)*puVar3,1,0x28a8,DAT_00013784 + 0x136b0,*param_2,puVar4);
        if ((char)param_2[1] != '\0') {
          if (*param_2 == 1) {
            iVar6 = *piVar5;
            _ZNSsC1EPKcRKSaIcE(auStack_4c,puVar4,auStack_60);
            _ZN8DpmFdMgr17createFdIfTrackerESs(iVar6,auStack_4c);
            local_20 = local_38;
            if (local_38 == auStack_4c) goto LAB_000135b4;
          }
          else {
            iVar6 = *piVar5;
            _ZNSsC1EPKcRKSaIcE(auStack_34,puVar4,auStack_60);
            _ZN8DpmFdMgr18destroyFdIfTrackerESs(iVar6,auStack_34);
            if (local_20 == auStack_34) goto LAB_000135b4;
          }
          if (local_20 != (undefined1 *)0x0) {
            _ZdlPv();
          }
        }
      }
      else if (param_1 == 3) {
        (**(code **)(*(int *)*puVar3 + 8))((int *)*puVar3,1,0x28a8,DAT_00013780 + 0x1365c,*param_2);
        _ZN11DpmFdConfig14getDpmFdConfigER13DpmFdConfig_s(*piVar5,auStack_5c);
        if ((local_50 & 1 << (*param_2 & 0xff)) == 0) {
          _ZN8DpmFdMgr16deleteFdTrackersEv();
        }
        else {
          _ZN8DpmFdMgr16createFdTrackersEv(*piVar5);
          _ZN8DpmFdMgr21handleIfIdleStatusChgEv(*piVar5);
        }
      }
      else {
        (**(code **)(*(int *)*puVar3 + 8))((int *)*puVar3,1,0x28a8,DAT_0001377c + 0x13630,param_1);
      }
      goto LAB_000135b4;
    }
  }
  (**(code **)(*(int *)**(undefined4 **)(iVar6 + DAT_0001376c) + 8))
            ((int *)**(undefined4 **)(iVar6 + DAT_0001376c),3,0x28a8,DAT_00013770 + 0x135a0,param_2,
             param_3,iVar2);
LAB_000135b4:
  if (local_1c == *piVar1) {
    return;
  }
                    /* WARNING: Subroutine does not return */
  __stack_chk_fail();
}



/* ---- Function: _ZN15EventDispatcherI11DpmDsmEventE16regEventCallbackES0_PFvS0_PKvPvES4_ @ 00013788 ---- */

void _ZN15EventDispatcherI11DpmDsmEventE16regEventCallbackES0_PFvS0_PKvPvES4_
               (int param_1,int param_2,undefined4 param_3,undefined4 param_4)

{
  undefined4 *puVar1;
  int iVar2;
  int iVar3;
  
  puVar1 = (undefined4 *)_Znwj(0xc);
  *puVar1 = param_3;
  puVar1[1] = param_4;
  iVar2 = *(int *)(param_1 + 4);
  puVar1[2] = 0;
  if (iVar2 != 0) {
    do {
      iVar3 = iVar2;
      if (param_2 < *(int *)(iVar3 + 0x10)) {
        iVar2 = *(int *)(iVar3 + 8);
      }
      else {
        iVar2 = *(int *)(iVar3 + 0xc);
      }
    } while (iVar2 != 0);
    if (param_1 != iVar3) {
      if (param_2 < *(int *)(iVar3 + 0x10)) {
        iVar2 = _Znwj(0x18);
        if (iVar2 != 0) {
          *(undefined4 **)(iVar2 + 0x14) = puVar1;
          *(undefined4 *)(iVar2 + 8) = 0;
          *(undefined4 *)(iVar2 + 0xc) = 0;
          *(int *)(iVar2 + 0x10) = param_2;
          *(int *)(iVar3 + 8) = iVar2;
          if (*(int *)(param_1 + 8) == iVar3) {
            *(int *)(param_1 + 8) = iVar2;
          }
          goto LAB_00013820;
        }
        goto LAB_000138b0;
      }
      iVar2 = _Znwj(0x18);
      if (iVar2 == 0) goto LAB_000138b0;
      *(undefined4 **)(iVar2 + 0x14) = puVar1;
      *(undefined4 *)(iVar2 + 8) = 0;
      *(undefined4 *)(iVar2 + 0xc) = 0;
      *(int *)(iVar2 + 0x10) = param_2;
      *(int *)(iVar3 + 0xc) = iVar2;
      if (*(int *)(param_1 + 0xc) == iVar3) {
        *(int *)(param_1 + 0xc) = iVar2;
      }
      goto LAB_00013820;
    }
  }
  iVar2 = _Znwj(0x18);
  if (iVar2 == 0) {
LAB_000138b0:
    puts((char *)(DAT_000138c4 + 0x138bc));
                    /* WARNING: Subroutine does not return */
    exit(1);
  }
  *(int *)(iVar2 + 0x10) = param_2;
  *(undefined4 **)(iVar2 + 0x14) = puVar1;
  *(undefined4 *)(iVar2 + 8) = 0;
  *(undefined4 *)(iVar2 + 0xc) = 0;
  *(int *)(param_1 + 8) = iVar2;
  *(int *)(param_1 + 4) = iVar2;
  *(int *)(param_1 + 0xc) = iVar2;
  iVar3 = param_1;
LAB_00013820:
  *(int *)(iVar2 + 4) = iVar3;
  _ZNSt4priv10_Rb_globalIbE10_RebalanceEPNS_18_Rb_tree_node_baseERS3_(iVar2,param_1 + 4);
  *(undefined4 *)(param_1 + 0x18) = 1;
  *(int *)(param_1 + 0x10) = *(int *)(param_1 + 0x10) + 1;
  return;
}



/* ---- Function: _ZN8DpmFdMgrC1ER3Dpm @ 000138c8 ---- */

int _ZN8DpmFdMgrC1ER3Dpm(int param_1,undefined4 param_2)

{
  int iVar1;
  int iVar2;
  undefined4 *puVar3;
  uint *puVar4;
  undefined4 local_20;
  
  _ZN11DpmFdConfigC2Ev();
  iVar1 = DAT_00013a08;
  local_20 = local_20 & 0xffffff00;
  puVar4 = (uint *)(param_1 + 0x24);
  *(undefined4 *)(param_1 + 0x18) = param_2;
  *puVar4 = local_20;
  *(undefined4 *)(param_1 + 0x28) = 0;
  *(undefined4 *)(param_1 + 0x2c) = 0;
  *(undefined4 *)(param_1 + 0x30) = 0;
  iVar2 = DAT_00013a0c;
  *(undefined4 *)(param_1 + 0x28) = 0;
  *(uint **)(param_1 + 0x2c) = puVar4;
  *(uint **)(param_1 + 0x30) = puVar4;
  *(undefined1 *)(param_1 + 0x24) = 0;
  *(undefined4 *)(param_1 + 0x34) = 0;
  puVar3 = *(undefined4 **)(iVar1 + 0x1390c + iVar2);
  (**(code **)(*(int *)*puVar3 + 8))
            ((int *)*puVar3,0,0x28a8,DAT_00013a10 + 0x13940,DAT_00013a14 + 0x13948,0x4e);
  iVar1 = *(int *)(param_1 + 0x18) + 0xa0;
  *(int *)(param_1 + 0x1c) = iVar1;
  if (iVar1 == 0) {
    (**(code **)(*(int *)*puVar3 + 8))((int *)*puVar3,3,0x28a8,DAT_00013a20 + 0x13a00);
  }
  else {
    iVar2 = *(int *)(param_1 + 0x18) + 0x120;
    *(int *)(param_1 + 0x20) = iVar2;
    if (iVar2 == 0) {
      (**(code **)(*(int *)*puVar3 + 8))((int *)*puVar3,3,0x28a8,DAT_00013a1c + 0x139dc);
    }
    else {
      iVar2 = DAT_00013a18 + 0x13994;
      _ZN15EventDispatcherI11DpmDsmEventE16regEventCallbackES0_PFvS0_PKvPvES4_
                (iVar1,1,iVar2,param_1);
      _ZN15EventDispatcherI11DpmDsmEventE16regEventCallbackES0_PFvS0_PKvPvES4_
                (*(undefined4 *)(param_1 + 0x1c),3,iVar2,param_1);
      _ZN8DpmFdMgr16createFdTrackersEv(param_1);
    }
  }
  return param_1;
}



/* ---- Function: init_fd_mgr @ 00013a24 ---- */

undefined4 init_fd_mgr(int param_1)

{
  int iVar1;
  int *piVar2;
  
  piVar2 = (int *)(DAT_00013a70 + 0x13a38);
  if ((*piVar2 == 0) && (param_1 != 0)) {
    iVar1 = _Znwj(0x3c);
    _ZN8DpmFdMgrC1ER3Dpm(iVar1,param_1);
    *piVar2 = iVar1;
    return 0;
  }
  return 0xffffffff;
}



/* ---- Function: _INIT_0 @ 00013a74 ---- */

void _INIT_0(void)

{
  int iVar1;
  int iVar2;
  int iVar3;
  undefined1 auStack_14 [8];
  
  iVar1 = DAT_00013b14;
  iVar3 = DAT_00013b18 + 0x13a98;
  iVar2 = DAT_00013b14 + 0x13a98;
  *(undefined4 *)(DAT_00013b14 + 0x13a94) = 0;
  _ZNSsC1EPKcRKSaIcE(iVar2,iVar3,auStack_14);
  iVar2 = DAT_00013b1c;
  *(undefined4 *)(iVar1 + 0x13ab0) = 1;
  _ZNSsC1EPKcRKSaIcE(iVar1 + 0x13ab4,iVar2 + 0x13ab8,auStack_14);
  iVar2 = DAT_00013b20;
  *(undefined4 *)(iVar1 + 0x13acc) = 2;
  _ZNSsC1EPKcRKSaIcE(iVar1 + 0x13ad0,iVar2 + 0x13ad4,auStack_14);
  iVar2 = DAT_00013b24 + 0x13aec;
  *(undefined4 *)(iVar1 + 0x13ae8) = 3;
  _ZNSsC1EPKcRKSaIcE(iVar1 + 0x13aec,iVar2,auStack_14);
  __aeabi_atexit(0,DAT_00013b28 + 0x13b08,DAT_00013b2c + 0x13b0c);
  return;
}



/* ---- Function: _ZN20DpmFdIdleTimeTracker22idleTimerExpiryHandlerEiPv @ 00013b30 ---- */

void _ZN20DpmFdIdleTimeTracker22idleTimerExpiryHandlerEiPv(int param_1,int param_2)

{
  ssize_t sVar1;
  int iVar2;
  int iVar3;
  int *piVar4;
  undefined1 local_31;
  char local_30 [12];
  int local_24;
  
  iVar2 = DAT_00013c88 + 0x13b50;
  piVar4 = *(int **)(iVar2 + DAT_00013c8c);
  local_24 = *piVar4;
  if (param_2 == 0) {
    (**(code **)(*(int *)**(undefined4 **)(iVar2 + DAT_00013c94) + 8))
              ((int *)**(undefined4 **)(iVar2 + DAT_00013c94),3,0x28a8,DAT_00013c9c + 0x13c74);
  }
  else {
    local_31 = 0;
    local_30[0] = '\0';
    local_30[1] = '\0';
    local_30[2] = '\0';
    local_30[3] = '\0';
    iVar3 = DAT_00013c90 + 0x13b78;
    local_30[4] = '\0';
    local_30[5] = '\0';
    local_30[6] = '\0';
    local_30[7] = '\0';
    local_30[8] = '\0';
    local_30[9] = '\0';
    while (sVar1 = read(param_1,local_30,10), 0 < sVar1) {
      (**(code **)(*(int *)**(undefined4 **)(iVar2 + DAT_00013c94) + 8))
                ((int *)**(undefined4 **)(iVar2 + DAT_00013c94),0,0x28a8,iVar3,local_30);
    }
    iVar3 = atoi(local_30);
    local_31 = iVar3 == 0;
    if (*(char *)(param_2 + 0x2c) != local_31) {
      (**(code **)(*(int *)**(undefined4 **)(iVar2 + DAT_00013c94) + 8))
                ((int *)**(undefined4 **)(iVar2 + DAT_00013c94),2,0x28a8,DAT_00013c98 + 0x13bfc,
                 local_31);
      *(undefined1 *)(param_2 + 0x2c) = local_31;
      if (*(code **)(param_2 + 0x20) != (code *)0x0) {
        (**(code **)(param_2 + 0x20))(&local_31,*(undefined4 *)(param_2 + 0x24));
      }
    }
    lseek(param_1,0,0);
  }
  if (local_24 != *piVar4) {
                    /* WARNING: Subroutine does not return */
    __stack_chk_fail();
  }
  return;
}



/* ---- Function: FUN_00013ca0 @ 00013ca0 ---- */

void FUN_00013ca0(void)

{
  int iVar1;
  int iVar2;
  int iVar3;
  
  iVar3 = DAT_00013cf0 + 0x13cb4;
  iVar1 = DAT_00013cf0 + 0x13d24;
  do {
    iVar2 = iVar1 + -0x1c;
    iVar1 = *(int *)(iVar1 + -8);
    if ((iVar1 != iVar2) && (iVar1 != 0)) {
      _ZdlPv(iVar1);
    }
    iVar1 = iVar2;
  } while (iVar2 != iVar3);
  return;
}



/* ---- Function: _ZN20DpmFdIdleTimeTracker19regIdleStatusChgEvtEPFvPKvPvES2_ @ 00013cf4 ---- */

undefined4
_ZN20DpmFdIdleTimeTracker19regIdleStatusChgEvtEPFvPKvPvES2_
          (int param_1,int param_2,undefined4 param_3)

{
  undefined4 uVar1;
  
  if (param_2 == 0) {
    uVar1 = 0xffffffff;
  }
  else {
    *(int *)(param_1 + 0x20) = param_2;
    *(undefined4 *)(param_1 + 0x24) = param_3;
    uVar1 = 0;
  }
  return uVar1;
}



/* ---- Function: _ZN20DpmFdIdleTimeTracker19evaluateIdleTimeOutEv @ 00013d10 ---- */

undefined4 _ZN20DpmFdIdleTimeTracker19evaluateIdleTimeOutEv(int param_1)

{
  undefined4 local_18;
  undefined4 local_14;
  undefined4 local_10;
  
  _ZN11DpmFdConfig14getDpmFdConfigER13DpmFdConfig_s(*(undefined4 *)(param_1 + 4),&local_18);
  if ((*(int *)(*(int *)(param_1 + 0x38) + 0x50) != 0) &&
     (local_10 = local_18, *(int *)(*(int *)(param_1 + 0x38) + 0x54) != 0)) {
    local_10 = local_14;
  }
  return local_10;
}



/* ---- Function: _ZN20DpmFdIdleTimeTracker11execCommandEPKcj @ 00013d58 ---- */

undefined4
_ZN20DpmFdIdleTimeTracker11execCommandEPKcj(undefined4 param_1,char *param_2,size_t param_3)

{
  size_t sVar1;
  FILE *__stream;
  int iVar2;
  int *piVar3;
  int iVar4;
  
  iVar4 = DAT_00013e20 + 0x13d70;
  if (param_2 == (char *)0x0) {
    iVar2 = (int)&DAT_00013e20 + DAT_00013e34;
  }
  else {
    sVar1 = strlen(param_2);
    if (param_3 != sVar1) {
      return 0xffffffff;
    }
    __stream = popen(param_2,(char *)(DAT_00013e24 + 0x13d90));
    if (__stream != (FILE *)0x0) {
      iVar2 = pclose(__stream);
      if (iVar2 < 0) {
        piVar3 = (int *)**(undefined4 **)(iVar4 + DAT_00013e2c);
        (**(code **)(*piVar3 + 8))(piVar3,4,0x28a8,DAT_00013e30 + 0x13df8);
        return 0;
      }
      return 0;
    }
    iVar2 = DAT_00013e28 + 0x13dc0;
  }
  piVar3 = (int *)**(undefined4 **)(iVar4 + DAT_00013e2c);
  (**(code **)(*piVar3 + 8))(piVar3,4,0x28a8,iVar2);
  return 0xffffffff;
}



/* ---- Function: _ZN20DpmFdIdleTimeTracker22runIpTableInitCommandsEv @ 00013e38 ---- */

void _ZN20DpmFdIdleTimeTracker22runIpTableInitCommandsEv(undefined4 param_1)

{
  undefined4 uVar1;
  int iVar2;
  int *piVar3;
  bool bVar4;
  undefined4 local_dc;
  undefined4 uStack_d8;
  undefined4 uStack_d4;
  undefined4 uStack_d0;
  undefined4 local_cc;
  undefined4 uStack_c8;
  undefined4 uStack_c4;
  undefined4 uStack_c0;
  undefined4 local_bc;
  undefined4 uStack_b8;
  undefined4 uStack_b4;
  undefined4 uStack_b0;
  undefined4 local_ac;
  undefined4 local_a8;
  undefined2 uStack_a4;
  undefined1 local_a2;
  int local_14;
  
  local_dc = *(undefined4 *)(DAT_00014100 + 0x13e58);
  uStack_d8 = *(undefined4 *)(DAT_00014100 + 0x13e5c);
  uStack_d4 = *(undefined4 *)(DAT_00014100 + 0x13e60);
  uStack_d0 = *(undefined4 *)(DAT_00014100 + 0x13e64);
  piVar3 = *(int **)(DAT_00014104 + 0x13e60 + DAT_00014108);
  local_cc = *(undefined4 *)(DAT_00014100 + 0x13e68);
  uStack_c8 = *(undefined4 *)(DAT_00014100 + 0x13e6c);
  uStack_c4 = *(undefined4 *)(DAT_00014100 + 0x13e70);
  uStack_c0 = *(undefined4 *)(DAT_00014100 + 0x13e74);
  local_14 = *piVar3;
  local_bc = *(undefined4 *)(DAT_00014100 + 0x13e78);
  uStack_b8 = *(undefined4 *)(DAT_00014100 + 0x13e7c);
  uVar1 = __strlen_chk(&local_dc,200);
  iVar2 = _ZN20DpmFdIdleTimeTracker11execCommandEPKcj(param_1,&local_dc,uVar1);
  if (iVar2 != -1) {
    local_dc = *(undefined4 *)(DAT_0001410c + 0x13eb4);
    uStack_d8 = *(undefined4 *)(DAT_0001410c + 0x13eb8);
    uStack_d4 = *(undefined4 *)(DAT_0001410c + 0x13ebc);
    uStack_d0 = *(undefined4 *)(DAT_0001410c + 0x13ec0);
    local_cc = *(undefined4 *)(DAT_0001410c + 0x13ec4);
    uStack_c8 = *(undefined4 *)(DAT_0001410c + 0x13ec8);
    uStack_c4 = *(undefined4 *)(DAT_0001410c + 0x13ecc);
    uStack_c0 = *(undefined4 *)(DAT_0001410c + 0x13ed0);
    local_bc = *(undefined4 *)(DAT_0001410c + 0x13ed4);
    uStack_b8 = *(undefined4 *)(DAT_0001410c + 0x13ed8);
    uStack_b4 = *(undefined4 *)(DAT_0001410c + 0x13edc);
    uVar1 = __strlen_chk(&local_dc,200);
    iVar2 = _ZN20DpmFdIdleTimeTracker11execCommandEPKcj(param_1,&local_dc,uVar1);
    if (iVar2 != -1) {
      local_dc = *(undefined4 *)(DAT_00014110 + 0x13efc);
      uStack_d8 = *(undefined4 *)(DAT_00014110 + 0x13f00);
      uStack_d4 = *(undefined4 *)(DAT_00014110 + 0x13f04);
      uStack_d0 = *(undefined4 *)(DAT_00014110 + 0x13f08);
      local_cc = *(undefined4 *)(DAT_00014110 + 0x13f0c);
      uStack_c8 = *(undefined4 *)(DAT_00014110 + 0x13f10);
      uStack_c4 = *(undefined4 *)(DAT_00014110 + 0x13f14);
      uStack_c0 = *(undefined4 *)(DAT_00014110 + 0x13f18);
      local_bc = *(undefined4 *)(DAT_00014110 + 0x13f1c);
      uStack_b8 = *(undefined4 *)(DAT_00014110 + 0x13f20);
      uVar1 = __strlen_chk(&local_dc,200);
      iVar2 = _ZN20DpmFdIdleTimeTracker11execCommandEPKcj(param_1,&local_dc,uVar1);
      if (iVar2 != -1) {
        local_dc = *(undefined4 *)(DAT_00014114 + 0x13f44);
        uStack_d8 = *(undefined4 *)(DAT_00014114 + 0x13f48);
        uStack_d4 = *(undefined4 *)(DAT_00014114 + 0x13f4c);
        uStack_d0 = *(undefined4 *)(DAT_00014114 + 0x13f50);
        local_cc = *(undefined4 *)(DAT_00014114 + 0x13f54);
        uStack_c8 = *(undefined4 *)(DAT_00014114 + 0x13f58);
        uStack_c4 = *(undefined4 *)(DAT_00014114 + 0x13f5c);
        uStack_c0 = *(undefined4 *)(DAT_00014114 + 0x13f60);
        local_bc = *(undefined4 *)(DAT_00014114 + 0x13f64);
        uStack_b8 = *(undefined4 *)(DAT_00014114 + 0x13f68);
        uStack_b4 = *(undefined4 *)(DAT_00014114 + 0x13f6c);
        uVar1 = __strlen_chk(&local_dc,200);
        iVar2 = _ZN20DpmFdIdleTimeTracker11execCommandEPKcj(param_1,&local_dc,uVar1);
        if (iVar2 != -1) {
          local_dc = *(undefined4 *)(DAT_00014118 + 0x13f8c);
          uStack_d8 = *(undefined4 *)(DAT_00014118 + 0x13f90);
          uStack_d4 = *(undefined4 *)(DAT_00014118 + 0x13f94);
          uStack_d0 = *(undefined4 *)(DAT_00014118 + 0x13f98);
          local_cc = *(undefined4 *)(DAT_00014118 + 0x13f9c);
          uStack_c8 = *(undefined4 *)(DAT_00014118 + 0x13fa0);
          uStack_c4 = *(undefined4 *)(DAT_00014118 + 0x13fa4);
          uStack_c0 = *(undefined4 *)(DAT_00014118 + 0x13fa8);
          local_bc = *(undefined4 *)(DAT_00014118 + 0x13fac);
          uStack_b8 = *(undefined4 *)(DAT_00014118 + 0x13fb0);
          uStack_b4 = *(undefined4 *)(DAT_00014118 + 0x13fb4);
          uStack_b0 = *(undefined4 *)(DAT_00014118 + 0x13fb8);
          local_ac = *(undefined4 *)(DAT_00014118 + 0x13fbc);
          local_a8 = CONCAT22(local_a8._2_2_,(short)*(undefined4 *)(DAT_00014118 + 0x13fc0));
          uVar1 = __strlen_chk(&local_dc,200);
          iVar2 = _ZN20DpmFdIdleTimeTracker11execCommandEPKcj(param_1,&local_dc,uVar1);
          if (iVar2 != -1) {
            local_dc = *(undefined4 *)(DAT_0001411c + 0x13fe0);
            uStack_d8 = *(undefined4 *)(DAT_0001411c + 0x13fe4);
            uStack_d4 = *(undefined4 *)(DAT_0001411c + 0x13fe8);
            uStack_d0 = *(undefined4 *)(DAT_0001411c + 0x13fec);
            local_cc = *(undefined4 *)(DAT_0001411c + 0x13ff0);
            uStack_c8 = *(undefined4 *)(DAT_0001411c + 0x13ff4);
            uStack_c4 = *(undefined4 *)(DAT_0001411c + 0x13ff8);
            uStack_c0 = *(undefined4 *)(DAT_0001411c + 0x13ffc);
            local_bc = *(undefined4 *)(DAT_0001411c + 0x14000);
            uStack_b8 = *(undefined4 *)(DAT_0001411c + 0x14004);
            uStack_b4 = *(undefined4 *)(DAT_0001411c + 0x14008);
            uStack_b0 = *(undefined4 *)(DAT_0001411c + 0x1400c);
            local_ac = *(undefined4 *)(DAT_0001411c + 0x14010);
            local_a8 = *(undefined4 *)(DAT_0001411c + 0x14014);
            uStack_a4 = (undefined2)*(undefined4 *)(DAT_0001411c + 0x14018);
            local_a2 = (undefined1)((uint)*(undefined4 *)(DAT_0001411c + 0x14018) >> 0x10);
            uVar1 = __strlen_chk(&local_dc,200);
            iVar2 = _ZN20DpmFdIdleTimeTracker11execCommandEPKcj(param_1,&local_dc,uVar1);
            if (iVar2 != -1) {
              local_dc = *(undefined4 *)(DAT_00014120 + 0x1403c);
              uStack_d8 = *(undefined4 *)(DAT_00014120 + 0x14040);
              uStack_d4 = *(undefined4 *)(DAT_00014120 + 0x14044);
              uStack_d0 = *(undefined4 *)(DAT_00014120 + 0x14048);
              local_cc = *(undefined4 *)(DAT_00014120 + 0x1404c);
              uStack_c8 = *(undefined4 *)(DAT_00014120 + 82000);
              uStack_c4 = *(undefined4 *)(DAT_00014120 + 0x14054);
              uStack_c0 = *(undefined4 *)(DAT_00014120 + 0x14058);
              local_bc = *(undefined4 *)(DAT_00014120 + 0x1405c);
              uStack_b8 = *(undefined4 *)(DAT_00014120 + 0x14060);
              uStack_b4 = *(undefined4 *)(DAT_00014120 + 0x14064);
              uStack_b0 = *(undefined4 *)(DAT_00014120 + 0x14068);
              local_ac = *(undefined4 *)(DAT_00014120 + 0x1406c);
              local_a8 = CONCAT22(local_a8._2_2_,(short)*(undefined4 *)(DAT_00014120 + 0x14070));
              uVar1 = __strlen_chk(&local_dc,200);
              iVar2 = _ZN20DpmFdIdleTimeTracker11execCommandEPKcj(param_1,&local_dc,uVar1);
              if (iVar2 != -1) {
                local_dc = *(undefined4 *)(DAT_00014124 + 0x14090);
                uStack_d8 = *(undefined4 *)(DAT_00014124 + 0x14094);
                uStack_d4 = *(undefined4 *)(DAT_00014124 + 0x14098);
                uStack_d0 = *(undefined4 *)(DAT_00014124 + 0x1409c);
                local_cc = *(undefined4 *)(DAT_00014124 + 0x140a0);
                uStack_c8 = *(undefined4 *)(DAT_00014124 + 0x140a4);
                uStack_c4 = *(undefined4 *)(DAT_00014124 + 0x140a8);
                uStack_c0 = *(undefined4 *)(DAT_00014124 + 0x140ac);
                local_bc = *(undefined4 *)(DAT_00014124 + 0x140b0);
                uStack_b8 = *(undefined4 *)(DAT_00014124 + 0x140b4);
                uStack_b4 = *(undefined4 *)(DAT_00014124 + 0x140b8);
                uStack_b0 = *(undefined4 *)(DAT_00014124 + 0x140bc);
                local_ac = *(undefined4 *)(DAT_00014124 + 0x140c0);
                local_a8 = *(undefined4 *)(DAT_00014124 + 0x140c4);
                uStack_a4 = (undefined2)*(undefined4 *)(DAT_00014124 + 0x140c8);
                local_a2 = (undefined1)((uint)*(undefined4 *)(DAT_00014124 + 0x140c8) >> 0x10);
                uVar1 = __strlen_chk(&local_dc,200);
                iVar2 = _ZN20DpmFdIdleTimeTracker11execCommandEPKcj(param_1,&local_dc,uVar1);
                bVar4 = iVar2 != -1;
                goto LAB_000140e4;
              }
            }
          }
        }
      }
    }
  }
  bVar4 = false;
LAB_000140e4:
  if (local_14 != *piVar3) {
                    /* WARNING: Subroutine does not return */
    __stack_chk_fail(bVar4);
  }
  return;
}



/* ---- Function: _ZN20DpmFdIdleTimeTracker24runIpTableDeleteCommandsEv @ 00014128 ---- */

void _ZN20DpmFdIdleTimeTracker24runIpTableDeleteCommandsEv(int param_1)

{
  undefined4 uVar1;
  int iVar2;
  int *piVar3;
  char acStack_dc [200];
  int local_14;
  
  piVar3 = *(int **)(DAT_00014204 + 0x1413c + DAT_00014208);
  local_14 = *piVar3;
  snprintf(acStack_dc,200,(char *)(DAT_0001420c + 0x14168),*(undefined4 *)(param_1 + 0x1c),
           *(undefined4 *)(param_1 + 0x28),*(undefined4 *)(param_1 + 0x1c));
  uVar1 = __strlen_chk(acStack_dc,200);
  iVar2 = _ZN20DpmFdIdleTimeTracker11execCommandEPKcj(param_1,acStack_dc,uVar1);
  if (iVar2 == -1) {
    uVar1 = 0;
  }
  else {
    snprintf(acStack_dc,200,(char *)(DAT_00014210 + 0x141bc),*(undefined4 *)(param_1 + 0x1c),
             *(undefined4 *)(param_1 + 0x28),*(undefined4 *)(param_1 + 0x1c));
    uVar1 = __strlen_chk(acStack_dc,200);
    iVar2 = _ZN20DpmFdIdleTimeTracker11execCommandEPKcj(param_1,acStack_dc,uVar1);
    uVar1 = 0;
    if (iVar2 != -1) {
      uVar1 = 1;
    }
  }
  if (local_14 != *piVar3) {
                    /* WARNING: Subroutine does not return */
    __stack_chk_fail(uVar1);
  }
  return;
}



/* ---- Function: _ZN20DpmFdIdleTimeTracker12stopTrackingEv @ 00014214 ---- */

void _ZN20DpmFdIdleTimeTracker12stopTrackingEv
               (int param_1,undefined4 param_2,undefined4 param_3,undefined4 param_4)

{
  int *piVar1;
  int iVar2;
  undefined4 *puVar3;
  
  puVar3 = *(undefined4 **)(DAT_000142a8 + 0x14230 + DAT_000142ac);
  piVar1 = (int *)*puVar3;
  (**(code **)(*piVar1 + 8))(piVar1,0,0x28a8,DAT_000142b0 + 0x14240,param_4);
  if (*(int *)(param_1 + 0x3c) != 0) {
    _ZN6DpmCom21removeComEventHandlerEi(*(int *)(param_1 + 0x3c),*(undefined4 *)(param_1 + 0x30));
  }
  if (*(int *)(param_1 + 0x30) != -1) {
    close(*(int *)(param_1 + 0x30));
    *(undefined4 *)(param_1 + 0x30) = 0xffffffff;
  }
  iVar2 = _ZN20DpmFdIdleTimeTracker24runIpTableDeleteCommandsEv(param_1);
  if (iVar2 != 0) {
    return;
  }
  piVar1 = (int *)*puVar3;
  (**(code **)(*piVar1 + 8))(piVar1,0,0x28a8,DAT_000142b4 + 0x142a0);
  return;
}



/* ---- Function: _ZN20DpmFdIdleTimeTracker21runIpTableAddCommandsEv @ 000142b8 ---- */

void _ZN20DpmFdIdleTimeTracker21runIpTableAddCommandsEv(int param_1)

{
  undefined4 uVar1;
  int iVar2;
  int *piVar3;
  char acStack_dc [200];
  int local_14;
  
  piVar3 = *(int **)(DAT_00014394 + 0x142cc + DAT_00014398);
  local_14 = *piVar3;
  snprintf(acStack_dc,200,(char *)(DAT_0001439c + 0x142f8),*(undefined4 *)(param_1 + 0x1c),
           *(undefined4 *)(param_1 + 0x28),*(undefined4 *)(param_1 + 0x1c));
  uVar1 = __strlen_chk(acStack_dc,200);
  iVar2 = _ZN20DpmFdIdleTimeTracker11execCommandEPKcj(param_1,acStack_dc,uVar1);
  if (iVar2 == -1) {
    uVar1 = 0;
  }
  else {
    snprintf(acStack_dc,200,(char *)(DAT_000143a0 + 0x1434c),*(undefined4 *)(param_1 + 0x1c),
             *(undefined4 *)(param_1 + 0x28),*(undefined4 *)(param_1 + 0x1c));
    uVar1 = __strlen_chk(acStack_dc,200);
    iVar2 = _ZN20DpmFdIdleTimeTracker11execCommandEPKcj(param_1,acStack_dc,uVar1);
    uVar1 = 0;
    if (iVar2 != -1) {
      uVar1 = 1;
    }
  }
  if (local_14 != *piVar3) {
                    /* WARNING: Subroutine does not return */
    __stack_chk_fail(uVar1);
  }
  return;
}



/* ---- Function: _ZN20DpmFdIdleTimeTracker13startTrackingEv @ 000143a4 ---- */

void _ZN20DpmFdIdleTimeTracker13startTrackingEv(int param_1)

{
  int iVar1;
  undefined4 *puVar2;
  int iVar3;
  int *piVar4;
  int *piVar5;
  code *pcVar6;
  char acStack_50 [52];
  int local_1c;
  
  iVar3 = DAT_00014548 + 0x143c0;
  piVar5 = *(int **)(iVar3 + DAT_0001454c);
  local_1c = *piVar5;
  if (*(char *)(param_1 + 0x34) == '\0') {
    iVar1 = _ZN20DpmFdIdleTimeTracker22runIpTableInitCommandsEv();
    if (iVar1 != 0) {
      *(undefined1 *)(param_1 + 0x34) = 1;
      goto LAB_000143d4;
    }
    iVar1 = DAT_00014550 + 0x14420;
  }
  else {
LAB_000143d4:
    if (*(int *)(param_1 + 0x28) == 0) goto LAB_000143e0;
    iVar1 = _ZN20DpmFdIdleTimeTracker21runIpTableAddCommandsEv(param_1);
    if (iVar1 != 0) {
      strlcpy(acStack_50,DAT_00014554 + 0x14444,0x32);
      strlcat(acStack_50,*(undefined4 *)(param_1 + 0x1c),0x32);
      iVar1 = open(acStack_50,0);
      *(int *)(param_1 + 0x30) = iVar1;
      if (iVar1 == -1) {
        piVar4 = (int *)**(undefined4 **)(iVar3 + DAT_00014560);
        pcVar6 = *(code **)(*piVar4 + 8);
        puVar2 = (undefined4 *)__errno(0xffffffff,0xffffffff);
        (*pcVar6)(piVar4,2,0x28a8,DAT_00014568 + 0x14524,*puVar2);
        _ZN20DpmFdIdleTimeTracker12stopTrackingEv(param_1);
      }
      else if (*(int *)(param_1 + 0x3c) == 0) {
        (**(code **)(*(int *)**(undefined4 **)(iVar3 + DAT_00014560) + 8))
                  ((int *)**(undefined4 **)(iVar3 + DAT_00014560),2,0x28a8,DAT_00014564 + 0x144e4);
        _ZN20DpmFdIdleTimeTracker12stopTrackingEv(param_1);
      }
      else {
        _ZN6DpmCom18addComEventHandlerEiPFviPvES0_S2_i
                  (*(int *)(param_1 + 0x3c),iVar1,DAT_00014558 + 0x14494,param_1,0,10);
      }
      goto LAB_000143e0;
    }
    iVar1 = DAT_0001455c + 0x144b0;
  }
  (**(code **)(*(int *)**(undefined4 **)(iVar3 + DAT_00014560) + 8))
            ((int *)**(undefined4 **)(iVar3 + DAT_00014560),2,0x28a8,iVar1);
LAB_000143e0:
  if (local_1c != *piVar5) {
                    /* WARNING: Subroutine does not return */
    __stack_chk_fail();
  }
  return;
}



/* ---- Function: _ZN20DpmFdIdleTimeTracker15dsmEventHandlerE11DpmDsmEventPKvPv @ 0001456c ---- */

void _ZN20DpmFdIdleTimeTracker15dsmEventHandlerE11DpmDsmEventPKvPv
               (int param_1,int param_2,int param_3)

{
  int iVar1;
  int *piVar2;
  undefined4 *puVar3;
  
  if (param_2 == 0 || param_3 == 0) {
    piVar2 = (int *)**(undefined4 **)(DAT_0001468c + 0x14584 + DAT_00014690);
    (**(code **)(*piVar2 + 8))(piVar2,3,0x28a8,DAT_000146a0 + 0x1466c,param_2,param_3);
  }
  else {
    puVar3 = *(undefined4 **)(DAT_0001468c + 0x14584 + DAT_00014690);
    (**(code **)(*(int *)*puVar3 + 8))((int *)*puVar3,1,0x28a8,DAT_00014694 + 0x145b0,param_1);
    if (param_1 - 4U < 2) {
      iVar1 = _ZN20DpmFdIdleTimeTracker19evaluateIdleTimeOutEv(param_3);
      (**(code **)(*(int *)*puVar3 + 8))
                ((int *)*puVar3,1,0x28a8,DAT_0001469c + 0x14614,iVar1,
                 *(undefined4 *)(param_3 + 0x28));
      if (iVar1 != *(int *)(param_3 + 0x28)) {
        _ZN20DpmFdIdleTimeTracker12stopTrackingEv(param_3);
        *(int *)(param_3 + 0x28) = iVar1;
        _ZN20DpmFdIdleTimeTracker13startTrackingEv(param_3);
        return;
      }
    }
    else {
      (**(code **)(*(int *)*puVar3 + 8))((int *)*puVar3,1,0x28a8,DAT_00014698 + 0x145e8,param_1);
    }
  }
  return;
}



/* ---- Function: _ZN20DpmFdIdleTimeTrackerD2Ev @ 000146a4 ---- */

int _ZN20DpmFdIdleTimeTrackerD2Ev(int param_1)

{
  int *piVar1;
  int iVar2;
  
  piVar1 = (int *)**(undefined4 **)(DAT_0001475c + 0x146c0 + DAT_00014760);
  (**(code **)(*piVar1 + 8))(piVar1,0,0x28a8,DAT_00014764 + 0x146d4,DAT_00014768 + 0x146e4,0x6c);
  if (*(int *)(param_1 + 0x38) != 0) {
    iVar2 = DAT_0001476c + 0x14714;
    _ZN15EventDispatcherI11DpmDsmEventE18deregEventCallbackES0_PFvS0_PKvPvES4_
              (*(int *)(param_1 + 0x38),4,iVar2,param_1);
    _ZN15EventDispatcherI11DpmDsmEventE18deregEventCallbackES0_PFvS0_PKvPvES4_
              (*(undefined4 *)(param_1 + 0x38),5,iVar2,param_1);
  }
  _ZN20DpmFdIdleTimeTracker12stopTrackingEv(param_1);
  if ((*(int *)(param_1 + 0x1c) != param_1 + 8) && (*(int *)(param_1 + 0x1c) != 0)) {
    _ZdlPv();
  }
  return param_1;
}



/* ---- Function: _ZN20DpmFdIdleTimeTrackerC2ER3DpmR11DpmFdConfigSs @ 00014770 ---- */

int * _ZN20DpmFdIdleTimeTrackerC2ER3DpmR11DpmFdConfigSs
                (int *param_1,int param_2,int param_3,int param_4)

{
  int *piVar1;
  int iVar2;
  int iVar3;
  size_t __n;
  void *pvVar4;
  undefined4 *puVar5;
  void *pvVar6;
  uint uVar7;
  
  piVar1 = param_1 + 2;
  param_1[6] = (int)piVar1;
  param_1[7] = (int)piVar1;
  iVar2 = DAT_0001494c;
  pvVar6 = *(void **)(param_4 + 0x10);
  pvVar4 = *(void **)(param_4 + 0x14);
  __n = (int)pvVar6 - (int)pvVar4;
  *param_1 = param_2;
  uVar7 = __n + 1;
  param_1[1] = param_3;
  if (uVar7 == 0) {
    puts((char *)(DAT_00014964 + 0x148cc));
                    /* WARNING: Subroutine does not return */
    abort();
  }
  if (0x10 < uVar7) {
    piVar1 = (int *)_Znwj(uVar7);
    if (piVar1 == (int *)0x0) {
      puts((char *)(DAT_00014968 + 0x148fc));
                    /* WARNING: Subroutine does not return */
      exit(1);
    }
    param_1[7] = (int)piVar1;
    param_1[6] = (int)piVar1;
    param_1[2] = (int)((int)piVar1 + uVar7);
  }
  if (pvVar4 != pvVar6) {
    pvVar4 = memcpy(piVar1,pvVar4,__n);
    piVar1 = (int *)((int)pvVar4 + __n);
  }
  iVar3 = DAT_00014950;
  param_1[6] = (int)piVar1;
  *(undefined1 *)piVar1 = 0;
  param_1[8] = 0;
  param_1[9] = 0;
  puVar5 = *(undefined4 **)(iVar2 + 0x147a8 + iVar3);
  (**(code **)(*(int *)*puVar5 + 8))
            ((int *)*puVar5,0,0x28a8,DAT_00014954 + 0x14804,DAT_00014958 + 0x14808,0x47);
  *(undefined1 *)(param_1 + 0xd) = 0;
  iVar2 = *param_1 + 0xa0;
  param_1[0xc] = -1;
  param_1[0xe] = iVar2;
  if (iVar2 == 0) {
    (**(code **)(*(int *)*puVar5 + 8))((int *)*puVar5,3,0x28a8,DAT_00014970 + 0x14944);
  }
  else {
    iVar3 = *param_1 + 0x38;
    param_1[0xf] = iVar3;
    if (iVar3 == 0) {
      (**(code **)(*(int *)*puVar5 + 8))((int *)*puVar5,3,0x28a8,DAT_0001496c + 0x14920);
    }
    else {
      iVar3 = DAT_0001495c + 0x14860;
      _ZN15EventDispatcherI11DpmDsmEventE16regEventCallbackES0_PFvS0_PKvPvES4_
                (iVar2,4,iVar3,param_1);
      _ZN15EventDispatcherI11DpmDsmEventE16regEventCallbackES0_PFvS0_PKvPvES4_
                (param_1[0xe],5,iVar3,param_1);
      iVar2 = _ZN20DpmFdIdleTimeTracker19evaluateIdleTimeOutEv(param_1);
      iVar3 = DAT_00014960 + 0x14894;
      piVar1 = (int *)*puVar5;
      param_1[10] = iVar2;
      (**(code **)(*piVar1 + 8))(piVar1,0,0x28a8,iVar3,iVar2);
      _ZN20DpmFdIdleTimeTracker13startTrackingEv(param_1);
    }
  }
  return param_1;
}



/* ---- Function: _INIT_1 @ 00014974 ---- */

void _INIT_1(void)

{
  int iVar1;
  int iVar2;
  int iVar3;
  undefined1 auStack_14 [8];
  
  iVar1 = DAT_00014a14;
  iVar3 = DAT_00014a18 + 0x14998;
  iVar2 = DAT_00014a14 + 0x14998;
  *(undefined4 *)(DAT_00014a14 + 0x14994) = 0;
  _ZNSsC1EPKcRKSaIcE(iVar2,iVar3,auStack_14);
  iVar2 = DAT_00014a1c;
  *(undefined4 *)(iVar1 + 0x149b0) = 1;
  _ZNSsC1EPKcRKSaIcE(iVar1 + 0x149b4,iVar2 + 0x149b8,auStack_14);
  iVar2 = DAT_00014a20;
  *(undefined4 *)(iVar1 + 0x149cc) = 2;
  _ZNSsC1EPKcRKSaIcE(iVar1 + 0x149d0,iVar2 + 0x149d4,auStack_14);
  iVar2 = DAT_00014a24 + 0x149ec;
  *(undefined4 *)(iVar1 + 0x149e8) = 3;
  _ZNSsC1EPKcRKSaIcE(iVar1 + 0x149ec,iVar2,auStack_14);
  __aeabi_atexit(0,DAT_00014a28 + 0x14a08,DAT_00014a2c + 0x14a0c);
  return;
}



/* ---- Function: _ZN11DpmFdConfigD2Ev @ 00014a30 ---- */

int * _ZN11DpmFdConfigD2Ev(int *param_1)

{
  int iVar1;
  int *piVar2;
  int iVar3;
  
  iVar1 = DAT_00014aac;
  iVar3 = DAT_00014aa8 + 0x14a58;
  *param_1 = DAT_00014aa4 + 0x14a58;
  piVar2 = (int *)**(undefined4 **)(iVar3 + iVar1);
  (**(code **)(*piVar2 + 8))(piVar2,0,0x28a8,DAT_00014ab4 + 0x14a7c,DAT_00014ab0 + 0x14a78,100);
  _ZN13DpmBaseConfigD2Ev(param_1);
  return param_1;
}



/* ---- Function: _ZN11DpmFdConfigD0Ev @ 00014ab8 ---- */

undefined4 _ZN11DpmFdConfigD0Ev(undefined4 param_1)

{
  _ZN11DpmFdConfigD2Ev();
  _ZdlPv(param_1);
  return param_1;
}



/* ---- Function: FUN_00014ad4 @ 00014ad4 ---- */

void FUN_00014ad4(void)

{
  puts((char *)(DAT_00014ae8 + 0x14ae4));
                    /* WARNING: Subroutine does not return */
  abort();
}



/* ---- Function: FUN_00014aec @ 00014aec ---- */

void FUN_00014aec(void)

{
  int iVar1;
  int iVar2;
  int iVar3;
  
  iVar3 = DAT_00014b3c + 0x14b00;
  iVar1 = DAT_00014b3c + 0x14b70;
  do {
    iVar2 = iVar1 + -0x1c;
    iVar1 = *(int *)(iVar1 + -8);
    if ((iVar1 != iVar2) && (iVar1 != 0)) {
      _ZdlPv(iVar1);
    }
    iVar1 = iVar2;
  } while (iVar2 != iVar3);
  return;
}



/* ---- Function: _ZN11DpmFdConfig26validateEnableNetworksMaskESs @ 00014b40 ---- */

void _ZN11DpmFdConfig26validateEnableNetworksMaskESs(undefined4 param_1,int param_2)

{
  undefined4 uVar1;
  byte *pbVar2;
  uint uVar3;
  char *pcVar4;
  int *piVar5;
  int iVar6;
  int iVar7;
  undefined4 local_24;
  undefined4 local_20;
  int local_1c;
  
  iVar6 = DAT_00014c9c + 0x14b5c;
  piVar5 = *(int **)(iVar6 + DAT_00014ca0);
  local_1c = *piVar5;
  if (8 < (uint)(*(int *)(param_2 + 0x10) - *(int *)(param_2 + 0x14))) {
    (**(code **)(*(int *)**(undefined4 **)(iVar6 + DAT_00014ca8) + 8))
              ((int *)**(undefined4 **)(iVar6 + DAT_00014ca8),3,0x28a8,DAT_00014cac + 0x14c30,8);
    uVar1 = 0;
    goto LAB_00014bfc;
  }
  iVar7 = 0;
  local_24 = 0;
  local_20 = 0;
  strlcpy(&local_24,*(int *)(param_2 + 0x14),8);
  uVar3 = local_24 & 0xff;
  if (uVar3 == 0x30) {
    if ((local_24._1_1_ & 0xdf) == 0x58) {
      uVar3 = local_24 >> 0x10 & 0xff;
      iVar7 = 2;
      goto LAB_00014b9c;
    }
  }
  else {
LAB_00014b9c:
    if (uVar3 == 0) {
LAB_00014bf8:
      uVar1 = 1;
      goto LAB_00014bfc;
    }
  }
  pbVar2 = (byte *)(*(int *)(param_2 + 0x14) + iVar7);
  if ((*(byte *)(**(int **)(iVar6 + DAT_00014ca4) +
                 (uint)*(byte *)(*(int *)(param_2 + 0x14) + iVar7) + 1) & 0x44) != 0) {
    pcVar4 = (char *)((int)&local_24 + iVar7);
    do {
      pcVar4 = pcVar4 + 1;
      if (*pcVar4 == '\0') goto LAB_00014bf8;
      pbVar2 = pbVar2 + 1;
    } while ((*(byte *)(**(int **)(iVar6 + DAT_00014ca4) + (uint)*pbVar2 + 1) & 0x44) != 0);
  }
  (**(code **)(*(int *)**(undefined4 **)(iVar6 + DAT_00014ca8) + 8))
            ((int *)**(undefined4 **)(iVar6 + DAT_00014ca8),3,0x28a8,DAT_00014cb0 + 0x14c68);
  uVar1 = 0;
LAB_00014bfc:
  if (local_1c == *piVar5) {
    return;
  }
                    /* WARNING: Subroutine does not return */
  __stack_chk_fail(uVar1);
}



/* ---- Function: _ZNSt4priv12_String_baseIcSaIcEE17_M_allocate_blockEj @ 00014cb4 ---- */

void _ZNSt4priv12_String_baseIcSaIcEE17_M_allocate_blockEj(int *param_1,uint param_2)

{
  int iVar1;
  
  if (param_2 == 0) {
                    /* WARNING: Subroutine does not return */
    FUN_00014ad4();
  }
  if (0x10 < param_2) {
    iVar1 = _Znwj(param_2);
    if (iVar1 == 0) {
      puts((char *)(DAT_00014d04 + 0x14cf8));
                    /* WARNING: Subroutine does not return */
      exit(1);
    }
    param_1[5] = iVar1;
    param_1[4] = iVar1;
    *param_1 = iVar1 + param_2;
    return;
  }
  return;
}



/* ---- Function: _ZN11DpmFdConfig11setFdConfigERKSt3mapIiSsSt4lessIiESaISt4pairIKiSsEEE @ 00014d08 ---- */

void _ZN11DpmFdConfig11setFdConfigERKSt3mapIiSsSt4lessIiESaISt4pairIKiSsEEE(int param_1,int param_2)

{
  int *piVar1;
  int iVar2;
  int iVar3;
  int iVar4;
  int *piVar5;
  int iVar6;
  int iVar7;
  int iVar8;
  bool bVar9;
  int local_54;
  int local_50;
  int local_4c;
  long local_48;
  undefined1 auStack_44 [20];
  undefined1 *local_30;
  int local_2c;
  
  iVar6 = DAT_00014ee8 + 0x14d28;
  iVar3 = *(int *)(param_2 + 8);
  piVar5 = *(int **)(iVar6 + DAT_00014eec);
  local_54 = 0;
  local_50 = 0;
  local_2c = *piVar5;
  local_4c = 0;
  local_48 = 0;
  if (iVar3 == param_2) {
    iVar4 = 1;
  }
  else {
    iVar4 = 1;
    iVar7 = DAT_00014ef0 + 0x14d64;
LAB_00014d60:
    do {
      switch(*(undefined4 *)(iVar3 + 0x10)) {
      case 0:
        local_54 = atoi(*(char **)(iVar3 + 0x28));
        break;
      case 1:
        local_50 = atoi(*(char **)(iVar3 + 0x28));
        break;
      case 2:
        local_4c = atoi(*(char **)(iVar3 + 0x28));
        iVar8 = *(int *)(iVar3 + 0xc);
        goto joined_r0x00014e24;
      case 3:
        _ZNSsC1ERKSs(auStack_44,iVar3 + 0x14);
        iVar4 = _ZN11DpmFdConfig26validateEnableNetworksMaskESs(param_1,auStack_44);
        if ((local_30 != auStack_44) && (local_30 != (undefined1 *)0x0)) {
          _ZdlPv();
        }
        if (iVar4 != 0) {
          local_48 = strtol(*(char **)(iVar3 + 0x28),(char **)0x0,0);
        }
        break;
      default:
        piVar1 = (int *)**(undefined4 **)(iVar6 + DAT_00014ef4);
        (**(code **)(*piVar1 + 8))(piVar1,4,0x28a8,iVar7,*(undefined4 *)(iVar3 + 0x10));
      }
      iVar8 = *(int *)(iVar3 + 0xc);
joined_r0x00014e24:
      if (iVar8 != 0) {
        do {
          iVar3 = iVar8;
          iVar8 = *(int *)(iVar3 + 8);
        } while (*(int *)(iVar3 + 8) != 0);
        if (param_2 == iVar3) break;
        goto LAB_00014d60;
      }
      iVar8 = *(int *)(iVar3 + 4);
      bVar9 = iVar3 == *(int *)(iVar8 + 0xc);
      iVar3 = iVar8;
      if (bVar9) {
        do {
          iVar2 = iVar8;
          iVar8 = *(int *)(iVar2 + 4);
        } while (*(int *)(iVar8 + 0xc) == iVar2);
        iVar3 = iVar8;
        if (iVar8 == *(int *)(iVar2 + 0xc)) {
          iVar3 = iVar2;
        }
      }
    } while (param_2 != iVar3);
    if (iVar4 == 0) goto LAB_00014df4;
  }
  *(int *)(param_1 + 4) = local_54;
  *(int *)(param_1 + 8) = local_50;
  *(int *)(param_1 + 0xc) = local_4c;
  *(long *)(param_1 + 0x10) = local_48;
LAB_00014df4:
  if (local_2c != *piVar5) {
                    /* WARNING: Subroutine does not return */
    __stack_chk_fail(iVar4);
  }
  return;
}



/* ---- Function: _ZNSt4priv8_Rb_treeIiSt4lessIiESt4pairIKiSsENS_10_Select1stIS5_EENS_11_MapTraitsTIS5_EESaIS5_EE8_M_eraseEPNS_18_Rb_tree_node_baseE @ 00014ef8 ---- */

void _ZNSt4priv8_Rb_treeIiSt4lessIiESt4pairIKiSsENS_10_Select1stIS5_EENS_11_MapTraitsTIS5_EESaIS5_EE8_M_eraseEPNS_18_Rb_tree_node_baseE
               (undefined4 param_1,int param_2)

{
  int iVar1;
  int iVar2;
  
  if (param_2 == 0) {
    return;
  }
  do {
    _ZNSt4priv8_Rb_treeIiSt4lessIiESt4pairIKiSsENS_10_Select1stIS5_EENS_11_MapTraitsTIS5_EESaIS5_EE8_M_eraseEPNS_18_Rb_tree_node_baseE
              (param_1,*(undefined4 *)(param_2 + 0xc));
    iVar1 = *(int *)(param_2 + 0x28);
    iVar2 = *(int *)(param_2 + 8);
    if ((iVar1 != param_2 + 0x14) && (iVar1 != 0)) {
      _ZdlPv(iVar1);
    }
    _ZdlPv(param_2);
    param_2 = iVar2;
  } while (iVar2 != 0);
  return;
}



/* ---- Function: _ZNSs9_M_appendEPKcS0_ @ 00014f4c ---- */

int * _ZNSs9_M_appendEPKcS0_(int *param_1,undefined1 *param_2,undefined1 *param_3)

{
  uint uVar1;
  undefined1 *puVar2;
  int *piVar3;
  undefined1 *puVar4;
  undefined1 *puVar5;
  uint uVar6;
  uint uVar7;
  undefined1 *puVar8;
  
  if (param_2 == param_3) {
    return param_1;
  }
  piVar3 = (int *)param_1[5];
  uVar6 = (int)param_3 - (int)param_2;
  if (piVar3 == param_1) {
    puVar8 = (undefined1 *)piVar3[4];
    uVar1 = (int)piVar3 + (0x10 - (int)puVar8);
  }
  else {
    puVar8 = (undefined1 *)param_1[4];
    uVar1 = *param_1 - (int)puVar8;
  }
  if (uVar6 < uVar1) {
    if (0 < (int)param_3 - (int)(param_2 + 1)) {
      puVar4 = param_2;
      do {
        puVar4 = puVar4 + 1;
        puVar8 = puVar8 + 1;
        *puVar8 = *puVar4;
      } while (puVar4 != param_2 + ((int)param_3 - (int)(param_2 + 1)));
      puVar8 = (undefined1 *)param_1[4];
    }
    puVar8[uVar6] = 0;
    *(undefined1 *)param_1[4] = *param_2;
    param_1[4] = param_1[4] + uVar6;
    return param_1;
  }
  uVar1 = (int)puVar8 - (int)piVar3;
  if (-uVar1 - 2 < uVar6) {
                    /* WARNING: Subroutine does not return */
    FUN_00014ad4();
  }
  uVar7 = uVar1;
  if (uVar1 < uVar6) {
    uVar7 = uVar6;
  }
  uVar7 = uVar1 + 1 + uVar7;
  if ((uVar7 == 0xffffffff) || (uVar7 < uVar1)) {
    uVar7 = 0xfffffffe;
  }
  else if (uVar7 == 0) {
    puVar8 = (undefined1 *)0x0;
    goto LAB_00015040;
  }
  puVar8 = (undefined1 *)_Znwj(uVar7);
  if (puVar8 == (undefined1 *)0x0) {
    puts((char *)(DAT_000150f0 + 0x150e4));
                    /* WARNING: Subroutine does not return */
    exit(1);
  }
  piVar3 = (int *)param_1[5];
  uVar1 = param_1[4] - (int)piVar3;
LAB_00015040:
  puVar4 = puVar8;
  if (0 < (int)uVar1) {
    puVar2 = (undefined1 *)((int)piVar3 + -1);
    puVar5 = puVar8;
    do {
      puVar2 = puVar2 + 1;
      puVar4 = puVar5 + 1;
      *puVar5 = *puVar2;
      puVar5 = puVar4;
    } while (puVar4 != puVar8 + uVar1);
  }
  if (0 < (int)uVar6) {
    param_2 = param_2 + -1;
    puVar2 = puVar4 + uVar6;
    puVar5 = puVar4;
    do {
      param_2 = param_2 + 1;
      puVar4 = puVar5 + 1;
      *puVar5 = *param_2;
      puVar5 = puVar4;
    } while (puVar4 != puVar2);
  }
  *puVar4 = 0;
  if ((param_1 != (int *)param_1[5]) && ((int *)param_1[5] != (int *)0x0)) {
    _ZdlPv();
  }
  param_1[4] = (int)puVar4;
  *param_1 = (int)(puVar8 + uVar7);
  param_1[5] = (int)puVar8;
  return param_1;
}



/* ---- Function: _ZNSs9_M_assignEPKcS0_ @ 000150f4 ---- */

int _ZNSs9_M_assignEPKcS0_(int param_1,void *param_2,int param_3,undefined4 param_4)

{
  void *__dest;
  int iVar1;
  size_t __n;
  undefined1 *puVar2;
  size_t __n_00;
  
  puVar2 = *(undefined1 **)(param_1 + 0x10);
  __n_00 = param_3 - (int)param_2;
  __dest = *(void **)(param_1 + 0x14);
  __n = (int)puVar2 - (int)__dest;
  if (__n < __n_00) {
    if (__n == 0) {
      iVar1 = 0;
    }
    else {
      memcpy(__dest,param_2,__n);
      puVar2 = *(undefined1 **)(param_1 + 0x14);
      iVar1 = *(int *)(param_1 + 0x10) - (int)puVar2;
    }
    _ZNSs9_M_appendEPKcS0_(param_1,(int)param_2 + iVar1,param_3,puVar2,param_4);
  }
  else {
    if (__n_00 != 0) {
      memcpy(__dest,param_2,__n_00);
      __dest = *(void **)(param_1 + 0x14);
      puVar2 = *(undefined1 **)(param_1 + 0x10);
    }
    if ((undefined1 *)((int)__dest + __n_00) != puVar2) {
      *(undefined1 *)((int)__dest + __n_00) = *puVar2;
      *(int *)(param_1 + 0x10) =
           *(int *)(param_1 + 0x10) - ((int)puVar2 - (int)((int)__dest + __n_00));
      return param_1;
    }
  }
  return param_1;
}



/* ---- Function: _ZNSt4priv8_Rb_treeISsSt4lessISsESt4pairIKSsiENS_10_Select1stIS5_EENS_11_MapTraitsTIS5_EESaIS5_EE9_M_insertEPNS_18_Rb_tree_node_baseERKS5_SD_SD_ @ 00015194 ---- */

int * _ZNSt4priv8_Rb_treeISsSt4lessISsESt4pairIKSsiENS_10_Select1stIS5_EENS_11_MapTraitsTIS5_EESaIS5_EE9_M_insertEPNS_18_Rb_tree_node_baseERKS5_SD_SD_
                (int *param_1,int param_2,int param_3,int param_4,int param_5,int param_6)

{
  int iVar1;
  undefined4 uVar2;
  size_t __n;
  size_t sVar3;
  size_t sVar4;
  
  if (param_2 == param_3) {
    iVar1 = _Znwj(0x2c);
    if (iVar1 == 0) goto LAB_00015300;
    _ZNSsC1ERKSs(iVar1 + 0x10,param_4);
    uVar2 = *(undefined4 *)(param_4 + 0x18);
    *(undefined4 *)(iVar1 + 8) = 0;
    *(undefined4 *)(iVar1 + 0xc) = 0;
    *(undefined4 *)(iVar1 + 0x28) = uVar2;
    *(int *)(param_2 + 8) = iVar1;
    *(int *)(param_2 + 4) = iVar1;
    *(int *)(param_2 + 0xc) = iVar1;
    goto LAB_000151fc;
  }
  if (param_6 != 0) goto LAB_000151bc;
  if (param_5 == 0) {
    sVar4 = *(int *)(param_4 + 0x10) - (int)*(void **)(param_4 + 0x14);
    sVar3 = *(int *)(param_3 + 0x20) - (int)*(void **)(param_3 + 0x24);
    __n = sVar3;
    if ((int)sVar4 <= (int)sVar3) {
      __n = sVar4;
    }
    iVar1 = memcmp(*(void **)(param_4 + 0x14),*(void **)(param_3 + 0x24),__n);
    if (iVar1 == 0) {
      if ((int)sVar3 <= (int)sVar4) {
LAB_000151bc:
        iVar1 = _Znwj(0x2c);
        if (iVar1 == 0) goto LAB_00015300;
        _ZNSsC1ERKSs(iVar1 + 0x10,param_4);
        uVar2 = *(undefined4 *)(param_4 + 0x18);
        *(undefined4 *)(iVar1 + 8) = 0;
        *(undefined4 *)(iVar1 + 0xc) = 0;
        *(undefined4 *)(iVar1 + 0x28) = uVar2;
        *(int *)(param_3 + 0xc) = iVar1;
        if (param_3 == *(int *)(param_2 + 0xc)) {
          *(int *)(param_2 + 0xc) = iVar1;
        }
        goto LAB_000151fc;
      }
    }
    else if (-1 < iVar1) goto LAB_000151bc;
  }
  iVar1 = _Znwj(0x2c);
  if (iVar1 == 0) {
LAB_00015300:
    puts((char *)(DAT_00015314 + 0x1530c));
                    /* WARNING: Subroutine does not return */
    exit(1);
  }
  _ZNSsC1ERKSs(iVar1 + 0x10,param_4);
  uVar2 = *(undefined4 *)(param_4 + 0x18);
  *(undefined4 *)(iVar1 + 8) = 0;
  *(undefined4 *)(iVar1 + 0xc) = 0;
  *(undefined4 *)(iVar1 + 0x28) = uVar2;
  *(int *)(param_3 + 8) = iVar1;
  if (param_3 == *(int *)(param_2 + 8)) {
    *(int *)(param_2 + 8) = iVar1;
  }
LAB_000151fc:
  *(int *)(iVar1 + 4) = param_3;
  _ZNSt4priv10_Rb_globalIbE10_RebalanceEPNS_18_Rb_tree_node_baseERS3_(iVar1,param_2 + 4);
  *(int *)(param_2 + 0x10) = *(int *)(param_2 + 0x10) + 1;
  *param_1 = iVar1;
  return param_1;
}



/* ---- Function: _ZNSt4priv8_Rb_treeISsSt4lessISsESt4pairIKSsiENS_10_Select1stIS5_EENS_11_MapTraitsTIS5_EESaIS5_EE13insert_uniqueERKS5_ @ 00015318 ---- */

undefined4 *
_ZNSt4priv8_Rb_treeISsSt4lessISsESt4pairIKSsiENS_10_Select1stIS5_EENS_11_MapTraitsTIS5_EESaIS5_EE13insert_uniqueERKS5_
          (undefined4 *param_1,char *param_2,int param_3)

{
  int iVar1;
  size_t __n;
  char *pcVar2;
  char *pcVar3;
  void *__s2;
  size_t __n_00;
  size_t __n_01;
  void *__s1;
  char *pcVar4;
  bool bVar5;
  undefined4 local_2c [2];
  
  pcVar3 = param_2;
  if (*(char **)(param_2 + 4) == (char *)0x0) {
LAB_00015418:
    pcVar2 = pcVar3;
    if (pcVar3 != *(char **)(param_2 + 8)) {
      if ((*pcVar3 == '\0') && (*(char **)(*(int *)(pcVar3 + 4) + 4) == pcVar3)) {
        pcVar2 = *(char **)(pcVar3 + 0xc);
      }
      else {
        pcVar4 = *(char **)(pcVar3 + 8);
        if (*(char **)(pcVar3 + 8) == (char *)0x0) {
          pcVar2 = *(char **)(pcVar3 + 4);
          pcVar4 = pcVar2;
          if (pcVar3 == *(char **)(pcVar2 + 8)) {
            do {
              pcVar2 = *(char **)(pcVar4 + 4);
              bVar5 = *(char **)(pcVar2 + 8) == pcVar4;
              pcVar4 = pcVar2;
            } while (bVar5);
          }
        }
        else {
          do {
            pcVar2 = pcVar4;
            pcVar4 = *(char **)(pcVar2 + 0xc);
          } while (*(char **)(pcVar2 + 0xc) != (char *)0x0);
        }
      }
      __s1 = *(void **)(param_3 + 0x14);
      __s2 = *(void **)(pcVar2 + 0x24);
      __n_01 = *(int *)(param_3 + 0x10) - (int)__s1;
      __n_00 = *(int *)(pcVar2 + 0x20) - (int)__s2;
      goto LAB_000153c4;
    }
LAB_00015498:
    _ZNSt4priv8_Rb_treeISsSt4lessISsESt4pairIKSsiENS_10_Select1stIS5_EENS_11_MapTraitsTIS5_EESaIS5_EE9_M_insertEPNS_18_Rb_tree_node_baseERKS5_SD_SD_
              (local_2c,param_2,pcVar3,param_3,pcVar2,0);
    *(undefined1 *)(param_1 + 1) = 1;
    *param_1 = local_2c[0];
  }
  else {
    __s1 = *(void **)(param_3 + 0x14);
    __n_01 = *(int *)(param_3 + 0x10) - (int)__s1;
    pcVar2 = *(char **)(param_2 + 4);
    do {
      pcVar3 = pcVar2;
      __s2 = *(void **)(pcVar3 + 0x24);
      __n_00 = *(int *)(pcVar3 + 0x20) - (int)__s2;
      if ((int)__n_00 < (int)__n_01) {
        iVar1 = memcmp(__s1,__s2,__n_00);
        if (iVar1 != 0) goto LAB_0001540c;
LAB_0001535c:
        pcVar2 = *(char **)(pcVar3 + 0xc);
        bVar5 = false;
      }
      else {
        iVar1 = memcmp(__s1,__s2,__n_01);
        if (iVar1 == 0) {
          if ((int)__n_00 <= (int)__n_01) goto LAB_0001535c;
        }
        else {
LAB_0001540c:
          if (-1 < iVar1) goto LAB_0001535c;
        }
        pcVar2 = *(char **)(pcVar3 + 8);
        bVar5 = true;
      }
    } while (pcVar2 != (char *)0x0);
    pcVar2 = pcVar3;
    if (bVar5) goto LAB_00015418;
LAB_000153c4:
    __n = __n_00;
    if ((int)__n_01 <= (int)__n_00) {
      __n = __n_01;
    }
    iVar1 = memcmp(__s2,__s1,__n);
    if (iVar1 == 0) {
      if ((int)__n_00 < (int)__n_01) {
LAB_00015484:
        pcVar2 = (char *)0x0;
        goto LAB_00015498;
      }
    }
    else if (iVar1 < 0) goto LAB_00015484;
    *param_1 = pcVar2;
    *(undefined1 *)(param_1 + 1) = 0;
  }
  return param_1;
}



/* ---- Function: _ZN11DpmFdConfig17parseFdConfigFileEv @ 00015500 ---- */

void _ZN11DpmFdConfig17parseFdConfigFileEv(int param_1)

{
  int *piVar1;
  int *piVar2;
  undefined4 uVar3;
  int iVar4;
  int iVar5;
  int iVar6;
  undefined4 *puVar7;
  undefined1 auStack_b4 [8];
  undefined1 local_ac [4];
  undefined4 local_a8;
  undefined1 *local_a4;
  undefined1 *local_a0;
  int local_9c;
  undefined1 auStack_94 [20];
  undefined1 *local_80;
  undefined4 local_7c;
  undefined1 auStack_78 [20];
  undefined1 *local_64;
  undefined4 local_60;
  undefined1 local_5c [16];
  undefined1 *local_4c;
  undefined1 *local_48;
  undefined1 local_44 [4];
  undefined4 local_40;
  undefined1 *local_3c;
  undefined1 *local_38;
  int local_34;
  int local_2c;
  
  piVar1 = *(int **)(DAT_00015908 + 0x1551c + DAT_0001590c);
  local_5c[0] = 0;
  local_40 = 0;
  local_44[0] = 0;
  local_34 = 0;
  local_2c = *piVar1;
  local_ac[0] = 0;
  local_a8 = 0;
  local_9c = 0;
  puVar7 = *(undefined4 **)(DAT_00015908 + 0x1551c + DAT_00015910);
  local_a4 = local_ac;
  local_a0 = local_ac;
  local_4c = local_5c;
  local_48 = local_5c;
  local_3c = local_44;
  local_38 = local_44;
  (**(code **)(*(int *)*puVar7 + 8))((int *)*puVar7,0,0x28a8,DAT_00015914 + 0x15590);
  if (*(char *)(param_1 + 0x14) == '\0') {
    iVar4 = DAT_00015918 + 0x155c8;
    iVar5 = 0;
    iVar6 = 0;
    do {
      uVar3 = *(undefined4 *)(iVar5 + iVar4);
      _ZNSsC1ERKSs(auStack_94,iVar4 + iVar6 * 0x1c + 4);
      local_7c = uVar3;
      _ZNSsC1ERKSs(auStack_78,auStack_94);
      local_60 = local_7c;
      _ZNSt4priv8_Rb_treeISsSt4lessISsESt4pairIKSsiENS_10_Select1stIS5_EENS_11_MapTraitsTIS5_EESaIS5_EE13insert_uniqueERKS5_
                (auStack_b4,local_44,auStack_78);
      if ((local_64 != auStack_78) && (local_64 != (undefined1 *)0x0)) {
        _ZdlPv();
      }
      if ((local_80 != auStack_94) && (local_80 != (undefined1 *)0x0)) {
        _ZdlPv();
      }
      iVar6 = iVar6 + 1;
      iVar5 = iVar5 + 0x1c;
    } while (iVar6 != 4);
    _ZNSs9_M_assignEPKcS0_(local_5c,DAT_0001591c + 0x15660,DAT_0001591c + 0x15677);
    iVar5 = _ZN13DpmBaseConfig15parseConfigFileERK21DpmConfigParseControlRSt3mapIiSsSt4lessIiESaISt4pairIKiSsEEE
                      (param_1,local_5c,local_ac);
    if (iVar5 == 1) {
      (**(code **)(*(int *)*puVar7 + 8))((int *)*puVar7,2,0x28a8,DAT_0001592c + 0x157dc);
      iVar6 = _ZN11DpmFdConfig11setFdConfigERKSt3mapIiSsSt4lessIiESaISt4pairIKiSsEEE
                        (param_1,local_ac);
      iVar5 = DAT_00015938;
      piVar2 = (int *)*puVar7;
      if (iVar6 != 0) {
        iVar6 = *piVar2;
        *(undefined1 *)(param_1 + 0x14) = 1;
        (**(code **)(iVar6 + 8))(piVar2,2,0x28a8,iVar5 + 0x15870);
        iVar5 = 1;
        goto LAB_0001570c;
      }
      iVar5 = -1;
      (**(code **)(*piVar2 + 8))(piVar2,3,0x28a8,DAT_00015930 + 0x1581c);
    }
    (**(code **)(*(int *)*puVar7 + 8))((int *)*puVar7,3,0x28a8,DAT_00015920 + 0x15698,iVar5);
    _ZNSs9_M_assignEPKcS0_(local_5c,DAT_00015924 + 0x156b8,DAT_00015924 + 0x156d5);
    if (local_9c != 0) {
      _ZNSt4priv8_Rb_treeIiSt4lessIiESt4pairIKiSsENS_10_Select1stIS5_EENS_11_MapTraitsTIS5_EESaIS5_EE8_M_eraseEPNS_18_Rb_tree_node_baseE
                (local_ac,local_a8);
      local_a8 = 0;
      local_9c = 0;
      local_a4 = local_ac;
      local_a0 = local_ac;
    }
    iVar5 = _ZN13DpmBaseConfig15parseConfigFileERK21DpmConfigParseControlRSt3mapIiSsSt4lessIiESaISt4pairIKiSsEEE
                      (param_1,local_5c,local_ac);
    if (iVar5 == 1) {
      (**(code **)(*(int *)*puVar7 + 8))((int *)*puVar7,2,0x28a8,DAT_0001593c + 0x15890);
      iVar4 = _ZN11DpmFdConfig11setFdConfigERKSt3mapIiSsSt4lessIiESaISt4pairIKiSsEEE
                        (param_1,local_ac);
      iVar6 = DAT_00015944;
      piVar2 = (int *)*puVar7;
      if (iVar4 != 0) {
        iVar4 = *piVar2;
        *(undefined1 *)(param_1 + 0x14) = 1;
        (**(code **)(iVar4 + 8))(piVar2,2,0x28a8,iVar6 + 0x158fc);
        goto LAB_0001570c;
      }
      iVar5 = -1;
      (**(code **)(*piVar2 + 8))(piVar2,3,0x28a8,DAT_00015940 + 0x158d0);
    }
    (**(code **)(*(int *)*puVar7 + 8))((int *)*puVar7,3,0x28a8,DAT_00015928 + 0x15700,iVar5);
  }
  else {
    iVar5 = 1;
    (**(code **)(*(int *)*puVar7 + 8))((int *)*puVar7,2,0x28a8,DAT_00015934 + 0x1583c);
  }
LAB_0001570c:
  if (local_9c != 0) {
    _ZNSt4priv8_Rb_treeIiSt4lessIiESt4pairIKiSsENS_10_Select1stIS5_EENS_11_MapTraitsTIS5_EESaIS5_EE8_M_eraseEPNS_18_Rb_tree_node_baseE
              (local_ac,local_a8);
    local_a8 = 0;
    local_9c = 0;
    local_a4 = local_ac;
    local_a0 = local_ac;
  }
  if (local_34 != 0) {
    _ZNSt4priv8_Rb_treeISsSt4lessISsESt4pairIKSsiENS_10_Select1stIS5_EENS_11_MapTraitsTIS5_EESaIS5_EE8_M_eraseEPNS_18_Rb_tree_node_baseE
              (local_44,local_40);
    local_40 = 0;
    local_34 = 0;
    local_3c = local_44;
    local_38 = local_44;
  }
  if ((local_48 != local_5c) && (local_48 != (undefined1 *)0x0)) {
    _ZdlPv();
  }
  if (local_2c == *piVar1) {
    return;
  }
                    /* WARNING: Subroutine does not return */
  __stack_chk_fail(iVar5);
}



/* ---- Function: _ZN11DpmFdConfig14getDpmFdConfigER13DpmFdConfig_s @ 00015948 ---- */

int _ZN11DpmFdConfig14getDpmFdConfigER13DpmFdConfig_s(int param_1,undefined4 *param_2)

{
  undefined4 uVar1;
  undefined4 uVar2;
  undefined4 uVar3;
  undefined4 *puVar4;
  int iVar5;
  int *piVar6;
  int iVar7;
  
  puVar4 = *(undefined4 **)(DAT_00015a40 + 0x15964 + DAT_00015a44);
  (**(code **)(*(int *)*puVar4 + 8))((int *)*puVar4,2,0x28a8,DAT_00015a48 + 0x1597c);
  if (*(char *)(param_1 + 0x14) == '\0') {
    (**(code **)(*(int *)*puVar4 + 8))((int *)*puVar4,1,0x28a8,DAT_00015a4c + 0x159c8);
    iVar5 = _ZN11DpmFdConfig17parseFdConfigFileEv(param_1);
    if (iVar5 == 1) {
      uVar1 = *(undefined4 *)(param_1 + 8);
      uVar2 = *(undefined4 *)(param_1 + 0xc);
      uVar3 = *(undefined4 *)(param_1 + 0x10);
      *param_2 = *(undefined4 *)(param_1 + 4);
      param_2[1] = uVar1;
      param_2[2] = uVar2;
      param_2[3] = uVar3;
    }
    else {
      (**(code **)(*(int *)*puVar4 + 8))((int *)*puVar4,3,0x28a8,DAT_00015a50 + 0x159fc,iVar5);
      uVar1 = *(undefined4 *)(param_1 + 8);
      uVar2 = *(undefined4 *)(param_1 + 0xc);
      uVar3 = *(undefined4 *)(param_1 + 0x10);
      piVar6 = (int *)*puVar4;
      iVar7 = *piVar6;
      *param_2 = *(undefined4 *)(param_1 + 4);
      param_2[1] = uVar1;
      param_2[2] = uVar2;
      param_2[3] = uVar3;
      (**(code **)(iVar7 + 8))(piVar6,2,0x28a8,DAT_00015a54 + 0x15a30);
    }
  }
  else {
    uVar1 = *(undefined4 *)(param_1 + 8);
    uVar2 = *(undefined4 *)(param_1 + 0xc);
    uVar3 = *(undefined4 *)(param_1 + 0x10);
    iVar5 = 1;
    *param_2 = *(undefined4 *)(param_1 + 4);
    param_2[1] = uVar1;
    param_2[2] = uVar2;
    param_2[3] = uVar3;
  }
  return iVar5;
}



/* ---- Function: _ZN11DpmFdConfigC2Ev @ 00015a58 ---- */

int * _ZN11DpmFdConfigC2Ev(int *param_1)

{
  int iVar1;
  int iVar2;
  int *piVar3;
  int iVar4;
  
  iVar1 = DAT_00015af0;
  _ZN13DpmBaseConfigC2Ev();
  iVar2 = DAT_00015af8;
  iVar4 = DAT_00015af4 + 0x15a88;
  *(undefined1 *)(param_1 + 5) = 0;
  *param_1 = iVar4;
  piVar3 = (int *)**(undefined4 **)(iVar1 + 0x15a7c + iVar2);
  (**(code **)(*piVar3 + 8))(piVar3,0,0x28a8,DAT_00015afc + 0x15aa8,DAT_00015b00 + 0x15aac,0x3e);
  param_1[3] = 0x1e;
  param_1[1] = 0x1e;
  param_1[2] = 10;
  param_1[4] = 0x28708;
  _ZN11DpmFdConfig17parseFdConfigFileEv(param_1);
  return param_1;
}



/* ---- Function: _INIT_2 @ 00015b04 ---- */

void _INIT_2(void)

{
  int iVar1;
  int iVar2;
  int iVar3;
  undefined1 auStack_14 [8];
  
  iVar1 = DAT_00015ba4;
  iVar3 = DAT_00015ba8 + 0x15b28;
  iVar2 = DAT_00015ba4 + 0x15b28;
  *(undefined4 *)(DAT_00015ba4 + 0x15b24) = 0;
  _ZNSsC1EPKcRKSaIcE(iVar2,iVar3,auStack_14);
  iVar2 = DAT_00015bac;
  *(undefined4 *)(iVar1 + 0x15b40) = 1;
  _ZNSsC1EPKcRKSaIcE(iVar1 + 0x15b44,iVar2 + 0x15b48,auStack_14);
  iVar2 = DAT_00015bb0;
  *(undefined4 *)(iVar1 + 0x15b5c) = 2;
  _ZNSsC1EPKcRKSaIcE(iVar1 + 0x15b60,iVar2 + 0x15b64,auStack_14);
  iVar2 = DAT_00015bb4 + 0x15b7c;
  *(undefined4 *)(iVar1 + 0x15b78) = 3;
  _ZNSsC1EPKcRKSaIcE(iVar1 + 0x15b7c,iVar2,auStack_14);
  __aeabi_atexit(0,DAT_00015bb8 + 0x15b98,DAT_00015bbc + 0x15b9c);
  return;
}



/* ---- Function: __cxa_finalize @ 00019000 ---- */

/* WARNING: Control flow encountered bad instruction data */

void __cxa_finalize(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: __cxa_atexit @ 00019004 ---- */

/* WARNING: Control flow encountered bad instruction data */

void __cxa_atexit(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: _ZdlPv @ 00019008 ---- */

/* WARNING: Control flow encountered bad instruction data */

void _ZdlPv(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: __aeabi_unwind_cpp_pr0 @ 0001900c ---- */

/* WARNING: Control flow encountered bad instruction data */

void __aeabi_unwind_cpp_pr0(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: strlen @ 00019010 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

size_t strlen(char *__s)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: memcpy @ 00019014 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

void * memcpy(void *__dest,void *__src,size_t __n)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: puts @ 00019018 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int puts(char *__s)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: abort @ 0001901c ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

void abort(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: _Znwj @ 00019020 ---- */

/* WARNING: Control flow encountered bad instruction data */

void _Znwj(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: exit @ 00019024 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

void exit(int __status)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: __aeabi_unwind_cpp_pr1 @ 00019028 ---- */

/* WARNING: Control flow encountered bad instruction data */

void __aeabi_unwind_cpp_pr1(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: _ZN6DpmQmi9goDormantEv @ 0001902c ---- */

/* WARNING: Control flow encountered bad instruction data */

void _ZN6DpmQmi9goDormantEv(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: memcmp @ 00019034 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int memcmp(void *__s1,void *__s2,size_t __n)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: _ZN6DpmDsm14getAllWwanInfoEv @ 00019038 ---- */

/* WARNING: Control flow encountered bad instruction data */

void _ZN6DpmDsm14getAllWwanInfoEv(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: __stack_chk_fail @ 0001903c ---- */

/* WARNING: Control flow encountered bad instruction data */

void __stack_chk_fail(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: __aeabi_atexit @ 00019044 ---- */

/* WARNING: Control flow encountered bad instruction data */

void __aeabi_atexit(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: read @ 00019048 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

ssize_t read(int __fd,void *__buf,size_t __nbytes)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: atoi @ 0001904c ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int atoi(char *__nptr)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: lseek @ 00019050 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

__off_t lseek(int __fd,__off_t __offset,int __whence)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: popen @ 00019054 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

FILE * popen(char *__command,char *__modes)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: pclose @ 00019058 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int pclose(FILE *__stream)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: __strlen_chk @ 0001905c ---- */

/* WARNING: Control flow encountered bad instruction data */

void __strlen_chk(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: snprintf @ 00019060 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int snprintf(char *__s,size_t __maxlen,char *__format,...)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: _ZN6DpmCom21removeComEventHandlerEi @ 00019064 ---- */

/* WARNING: Control flow encountered bad instruction data */

void _ZN6DpmCom21removeComEventHandlerEi(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: close @ 00019068 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int close(int __fd)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: strlcpy @ 0001906c ---- */

/* WARNING: Control flow encountered bad instruction data */

void strlcpy(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: strlcat @ 00019070 ---- */

/* WARNING: Control flow encountered bad instruction data */

void strlcat(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: open @ 00019074 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

int open(char *__file,int __oflag,...)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: _ZN6DpmCom18addComEventHandlerEiPFviPvES0_S2_i @ 00019078 ---- */

/* WARNING: Control flow encountered bad instruction data */

void _ZN6DpmCom18addComEventHandlerEiPFviPvES0_S2_i(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: __errno @ 0001907c ---- */

/* WARNING: Control flow encountered bad instruction data */

void __errno(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: _ZN13DpmBaseConfigD2Ev @ 00019080 ---- */

/* WARNING: Control flow encountered bad instruction data */

void _ZN13DpmBaseConfigD2Ev(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: strtol @ 00019088 ---- */

/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Unknown calling convention -- yet parameter storage is locked */

long strtol(char *__nptr,char **__endptr,int __base)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: _ZN13DpmBaseConfig15parseConfigFileERK21DpmConfigParseControlRSt3mapIiSsSt4lessIiESaISt4pairIKiSsEEE @ 0001908c ---- */

/* WARNING: Control flow encountered bad instruction data */

void _ZN13DpmBaseConfig15parseConfigFileERK21DpmConfigParseControlRSt3mapIiSsSt4lessIiESaISt4pairIKiSsEEE
               (void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: _ZN13DpmBaseConfigC2Ev @ 00019090 ---- */

/* WARNING: Control flow encountered bad instruction data */

void _ZN13DpmBaseConfigC2Ev(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



