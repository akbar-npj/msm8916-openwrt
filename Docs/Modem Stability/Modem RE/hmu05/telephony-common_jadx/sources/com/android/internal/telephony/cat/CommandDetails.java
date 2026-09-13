package com.android.internal.telephony.cat;

import android.os.Parcel;
import android.os.Parcelable;

/* JADX INFO: loaded from: classes.dex */
class CommandDetails extends ValueObject implements Parcelable {
    public static final Parcelable.Creator<CommandDetails> CREATOR = new Parcelable.Creator<CommandDetails>() { // from class: com.android.internal.telephony.cat.CommandDetails.1
        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public CommandDetails createFromParcel(Parcel in) {
            return new CommandDetails(in);
        }

        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public CommandDetails[] newArray(int size) {
            return new CommandDetails[size];
        }
    };
    public int commandNumber;
    public int commandQualifier;
    public boolean compRequired;
    public int typeOfCommand;

    @Override // com.android.internal.telephony.cat.ValueObject
    public ComprehensionTlvTag getTag() {
        return ComprehensionTlvTag.COMMAND_DETAILS;
    }

    CommandDetails() {
    }

    public boolean compareTo(CommandDetails other) {
        return this.compRequired == other.compRequired && this.commandNumber == other.commandNumber && this.commandQualifier == other.commandQualifier && this.typeOfCommand == other.typeOfCommand;
    }

    public CommandDetails(Parcel in) {
        this.compRequired = in.readInt() != 0;
        this.commandNumber = in.readInt();
        this.typeOfCommand = in.readInt();
        this.commandQualifier = in.readInt();
    }

    @Override // android.os.Parcelable
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.compRequired ? 1 : 0);
        dest.writeInt(this.commandNumber);
        dest.writeInt(this.typeOfCommand);
        dest.writeInt(this.commandQualifier);
    }

    @Override // android.os.Parcelable
    public int describeContents() {
        return 0;
    }

    public String toString() {
        return "CmdDetails: compRequired=" + this.compRequired + " commandNumber=" + this.commandNumber + " typeOfCommand=" + this.typeOfCommand + " commandQualifier=" + this.commandQualifier;
    }
}
