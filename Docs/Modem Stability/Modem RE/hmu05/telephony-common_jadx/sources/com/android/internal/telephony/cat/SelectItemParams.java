package com.android.internal.telephony.cat;

import android.graphics.Bitmap;

/* JADX INFO: compiled from: CommandParams.java */
/* JADX INFO: loaded from: classes.dex */
class SelectItemParams extends CommandParams {
    boolean mLoadTitleIcon;
    Menu mMenu;

    SelectItemParams(CommandDetails cmdDet, Menu menu, boolean loadTitleIcon) {
        super(cmdDet);
        this.mMenu = null;
        this.mLoadTitleIcon = false;
        this.mMenu = menu;
        this.mLoadTitleIcon = loadTitleIcon;
    }

    @Override // com.android.internal.telephony.cat.CommandParams
    boolean setIcon(Bitmap icon) {
        if (icon != null && this.mMenu != null) {
            if (this.mLoadTitleIcon && this.mMenu.titleIcon == null) {
                this.mMenu.titleIcon = icon;
            } else {
                for (Item item : this.mMenu.items) {
                    if (item.icon == null) {
                        item.icon = icon;
                        break;
                    }
                }
            }
            return true;
        }
        return false;
    }
}
