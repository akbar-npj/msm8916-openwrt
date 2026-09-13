/* Decompiled from: /home/shaanair/Projects/msm8916-openwrt-clean/GitIgnore/MelbonWhiteStock_Dump/system/bin/dpmd */
/* Language: ARM:LE:32:v8 */

/* ---- Function: __libc_init @ 000105e4 ---- */

void __libc_init(void)

{
  (*(code *)PTR___libc_init_00011fd4)();
  return;
}



/* ---- Function: __cxa_atexit @ 000105f0 ---- */

void __cxa_atexit(void)

{
  (*(code *)PTR___cxa_atexit_00011fd8)();
  return;
}



/* ---- Function: Diag_LSM_Init @ 000105fc ---- */

void Diag_LSM_Init(void)

{
  (*(code *)PTR_Diag_LSM_Init_00011fdc)();
  return;
}



/* ---- Function: _ZN6DpmLog16getPropertyValueEv @ 00010608 ---- */

void _ZN6DpmLog16getPropertyValueEv(void)

{
  (*(code *)PTR__ZN6DpmLog16getPropertyValueEv_00011fe0)();
  return;
}



/* ---- Function: dlopen @ 00010614 ---- */

void dlopen(void)

{
  (*(code *)PTR_dlopen_00011fe4)();
  return;
}



/* ---- Function: dlsym @ 00010620 ---- */

void dlsym(void)

{
  (*(code *)PTR_dlsym_00011fe8)();
  return;
}



/* ---- Function: _Znwj @ 0001062c ---- */

void _Znwj(void)

{
  (*(code *)PTR__Znwj_00011fec)();
  return;
}



/* ---- Function: _ZN3DpmC1Ev @ 00010638 ---- */

void _ZN3DpmC1Ev(void)

{
  (*(code *)PTR__ZN3DpmC1Ev_00011ff0)();
  return;
}



/* ---- Function: _ZN3Dpm3runEv @ 00010644 ---- */

void _ZN3Dpm3runEv(void)

{
  (*(code *)PTR__ZN3Dpm3runEv_00011ff4)();
  return;
}



/* ---- Function: Diag_LSM_DeInit @ 00010650 ---- */

void Diag_LSM_DeInit(void)

{
  (*(code *)PTR_Diag_LSM_DeInit_00011ff8)();
  return;
}



/* ---- Function: _ZN3DpmD1Ev @ 0001065c ---- */

void _ZN3DpmD1Ev(void)

{
  (*(code *)PTR__ZN3DpmD1Ev_00011ffc)();
  return;
}



/* ---- Function: entry @ 00010668 ---- */

/* WARNING: Control flow encountered bad instruction data */

void processEntry entry(void)

{
  int iVar1;
  undefined4 local_18;
  undefined4 local_14;
  undefined4 local_10;
  
  iVar1 = iRam000106cc + 0x10680;
  local_18 = *(undefined4 *)(iVar1 + DAT_000106d0);
  local_14 = *(undefined4 *)(iVar1 + DAT_000106d4);
  local_10 = *(undefined4 *)(iVar1 + DAT_000106d8);
  __libc_init(&stack0x00000000,0,*(undefined4 *)(iVar1 + DAT_000106dc),&local_18);
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: FUN_000106e0 @ 000106e0 ---- */

undefined4 FUN_000106e0(undefined4 param_1)

{
  undefined4 uVar1;
  
  uVar1 = __cxa_atexit(param_1,0,DAT_00010718 + 0x10704);
  return uVar1;
}



/* ---- Function: FUN_0001071c @ 0001071c ---- */

void FUN_0001071c(void)

{
  int iVar1;
  int iVar2;
  int *piVar3;
  undefined4 uVar4;
  int iVar5;
  
  Diag_LSM_Init();
  iVar2 = _ZN6DpmLog16getPropertyValueEv();
  iVar5 = DAT_00010800 + 0x1073c;
  **(int **)(iVar5 + DAT_00010804) = iVar2;
  if (iVar2 == 0x1e91) {
    **(undefined4 **)(iVar5 + DAT_00010808) = 0;
  }
  else {
    if (iVar2 == 0xf86) {
      uVar4 = 1;
    }
    else {
      uVar4 = 2;
    }
    **(undefined4 **)(iVar5 + DAT_00010808) = uVar4;
  }
  iVar2 = dlopen(DAT_0001080c + 0x10774,0);
  **(int **)(iVar5 + DAT_00010810) = iVar2;
  if (iVar2 == 0) {
    piVar3 = (int *)_Znwj(4);
    iVar2 = DAT_00010824;
  }
  else {
    iVar2 = dlsym(iVar2,DAT_00010814 + 0x10794);
    **(int **)(iVar5 + DAT_00010818) = iVar2;
    if (iVar2 == 0) {
      piVar3 = (int *)_Znwj(4);
      iVar2 = DAT_00010824;
    }
    else {
      piVar3 = (int *)_Znwj(4);
      iVar2 = DAT_0001081c;
    }
  }
  iVar1 = DAT_00010820;
  *piVar3 = *(int *)(iVar5 + iVar2) + 8;
  **(undefined4 **)(iVar5 + iVar1) = piVar3;
  return;
}



/* ---- Function: FUN_00010828 @ 00010828 ---- */

undefined4 FUN_00010828(void)

{
  int iVar1;
  int *piVar2;
  undefined4 *puVar3;
  undefined1 auStack_188 [380];
  
  iVar1 = DAT_000108b0;
  FUN_0001071c(0);
  puVar3 = *(undefined4 **)(iVar1 + 0x10848 + DAT_000108b4);
  piVar2 = (int *)*puVar3;
  (**(code **)(*piVar2 + 8))(piVar2,2,0x28a1,DAT_000108b8 + 0x1085c);
  _ZN3DpmC1Ev(auStack_188);
  _ZN3Dpm3runEv(auStack_188);
  piVar2 = (int *)*puVar3;
  (**(code **)(*piVar2 + 8))(piVar2,2,0x28a1,DAT_000108bc + 0x10894);
  Diag_LSM_DeInit();
  _ZN3DpmD1Ev(auStack_188);
  return 0;
}



/* ---- Function: __libc_init @ 00013000 ---- */

/* WARNING: Control flow encountered bad instruction data */

void __libc_init(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: __cxa_atexit @ 00013004 ---- */

/* WARNING: Control flow encountered bad instruction data */

void __cxa_atexit(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: Diag_LSM_Init @ 00013008 ---- */

/* WARNING: Control flow encountered bad instruction data */

void Diag_LSM_Init(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: _ZN6DpmLog16getPropertyValueEv @ 0001300c ---- */

/* WARNING: Control flow encountered bad instruction data */

void _ZN6DpmLog16getPropertyValueEv(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: dlopen @ 00013010 ---- */

/* WARNING: Control flow encountered bad instruction data */

void dlopen(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: dlsym @ 00013014 ---- */

/* WARNING: Control flow encountered bad instruction data */

void dlsym(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: _Znwj @ 00013018 ---- */

/* WARNING: Control flow encountered bad instruction data */

void _Znwj(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: __aeabi_unwind_cpp_pr0 @ 00013038 ---- */

/* WARNING: Control flow encountered bad instruction data */

void __aeabi_unwind_cpp_pr0(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: _ZN3DpmC1Ev @ 0001303c ---- */

/* WARNING: Control flow encountered bad instruction data */

void _ZN3DpmC1Ev(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: _ZN3Dpm3runEv @ 00013040 ---- */

/* WARNING: Control flow encountered bad instruction data */

void _ZN3Dpm3runEv(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: Diag_LSM_DeInit @ 00013044 ---- */

/* WARNING: Control flow encountered bad instruction data */

void Diag_LSM_DeInit(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



/* ---- Function: _ZN3DpmD1Ev @ 00013048 ---- */

/* WARNING: Control flow encountered bad instruction data */

void _ZN3DpmD1Ev(void)

{
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}



