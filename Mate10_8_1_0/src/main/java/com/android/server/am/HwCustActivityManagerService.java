package com.android.server.am;

public class HwCustActivityManagerService {
    protected boolean shouldDelaySwitchUserDlg() {
        return false;
    }

    protected boolean isAllowRamCompress() {
        return false;
    }

    protected void setEvent(String event) {
    }

    protected int addProcesstoPersitList(ProcessRecord proc) {
        return proc.maxAdj;
    }

    protected boolean isIQIEnable() {
        return false;
    }
}
