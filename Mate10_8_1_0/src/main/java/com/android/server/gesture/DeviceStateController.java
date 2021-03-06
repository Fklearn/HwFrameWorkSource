package com.android.server.gesture;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings.Global;
import android.provider.Settings.Secure;
import android.util.Log;
import android.view.WindowManagerPolicy;
import android.view.WindowManagerPolicy.WindowState;
import com.android.server.LocalServices;
import com.android.server.policy.HwPhoneWindowManager;
import huawei.com.android.server.policy.HwGlobalActionsData;
import java.util.ArrayList;
import vendor.huawei.hardware.hwdisplay.displayengine.V1_0.HighBitsCompModeID;

public class DeviceStateController {
    private static final int DEFAULT_VALUE_GUIDE_OTA_FINISHED = 1;
    private static final String KEY_GUIDE_OTA_FINISHED = "is_ota_finished";
    private static final String TAG = "DeviceStateController";
    private static DeviceStateController sInstance;
    private final ContentResolver mContentResolver;
    private final Context mContext;
    private int mCurrentUserId;
    private final Uri mDeviceProvisionedUri;
    private final Uri mGuideOtaFinishUri;
    private final ArrayList<DeviceChangedListener> mListeners = new ArrayList();
    private WindowManagerPolicy mPolicy;
    private final BroadcastReceiver mPreferChangedReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            boolean isPrefer;
            if (!"android.intent.action.ACTION_PREFERRED_ACTIVITY_CHANGED".equals(intent.getAction())) {
                isPrefer = false;
            } else if (intent.getIntExtra("android.intent.extra.user_handle", -10000) != -10000) {
                isPrefer = true;
            } else {
                return;
            }
            DeviceStateController.this.notifyPreferredActivityChanged(isPrefer);
        }
    };
    protected final ContentObserver mSettingsObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
        public void onChange(boolean selfChange, Uri uri, int userId) {
            if (DeviceStateController.this.mDeviceProvisionedUri.equals(uri)) {
                DeviceStateController.this.notifyProvisionedChanged(DeviceStateController.this.isDeviceProvisioned());
            } else if (DeviceStateController.this.mUserSetupUri.equals(uri)) {
                DeviceStateController.this.notifySetupChanged(DeviceStateController.this.isCurrentUserSetup());
            } else if (DeviceStateController.this.mGuideOtaFinishUri.equals(uri)) {
                DeviceStateController.this.notifyGuideOtaStateChanged();
            }
        }
    };
    private final Uri mUserSetupUri;
    private final BroadcastReceiver mUserSwitchedReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            DeviceStateController.this.mCurrentUserId = intent.getIntExtra("android.intent.extra.user_handle", 0);
            DeviceStateController.this.onUserSwitched(DeviceStateController.this.mCurrentUserId);
        }
    };

    public static abstract class DeviceChangedListener {
        void onDeviceProvisionedChanged(boolean provisioned) {
        }

        void onUserSwitched(int newUserId) {
        }

        void onUserSetupChanged(boolean setup) {
        }

        void onConfigurationChanged() {
        }

        void onPreferredActivityChanged(boolean isPrefer) {
        }

        void onGuideOtaStateChanged() {
        }
    }

    private DeviceStateController(Context context) {
        this.mContext = context;
        this.mContentResolver = this.mContext.getContentResolver();
        this.mDeviceProvisionedUri = Global.getUriFor("device_provisioned");
        this.mUserSetupUri = Secure.getUriFor("user_setup_complete");
        this.mGuideOtaFinishUri = Secure.getUriFor(KEY_GUIDE_OTA_FINISHED);
        this.mCurrentUserId = ActivityManager.getCurrentUser();
        this.mPolicy = (WindowManagerPolicy) LocalServices.getService(WindowManagerPolicy.class);
    }

    public static DeviceStateController getInstance(Context context) {
        DeviceStateController deviceStateController;
        synchronized (DeviceStateController.class) {
            if (sInstance == null) {
                sInstance = new DeviceStateController(context);
            }
            deviceStateController = sInstance;
        }
        return deviceStateController;
    }

    public boolean isDeviceProvisioned() {
        return Global.getInt(this.mContentResolver, "device_provisioned", 0) != 0;
    }

    public boolean isCurrentUserSetup() {
        return isUserSetup(getCurrentUser());
    }

    public boolean isUserSetup(int currentUser) {
        return Secure.getIntForUser(this.mContentResolver, "user_setup_complete", 0, currentUser) != 0;
    }

    public boolean isGuideOtaFinished() {
        return Secure.getIntForUser(this.mContentResolver, KEY_GUIDE_OTA_FINISHED, 1, -2) != 0;
    }

    public int getCurrentUser() {
        return ActivityManager.getCurrentUser();
    }

    public boolean isKeyguardOccluded() {
        return this.mPolicy.isKeyguardOccluded();
    }

    public boolean isKeyguardSecure() {
        return this.mPolicy.isKeyguardSecure(0);
    }

    public boolean isKeyguardShowingOrOccluded() {
        return this.mPolicy.isKeyguardShowingOrOccluded();
    }

    public boolean isStatusBarKeyguardShowing() {
        return this.mPolicy.isStatusBarKeyguardShowing();
    }

    public boolean isKeyguardLocked() {
        return this.mPolicy.isKeyguardLocked();
    }

    public boolean isKeyguardShowingAndNotOccluded() {
        return this.mPolicy.isKeyguardShowingAndNotOccluded();
    }

    public WindowState getFocusWindow() {
        if (this.mPolicy instanceof HwPhoneWindowManager) {
            return this.mPolicy.getFocusedWindow();
        }
        return null;
    }

    public String getFocusWindowName() {
        WindowState focusWindowState = getFocusWindow();
        if (focusWindowState == null || focusWindowState.getAttrs() == null) {
            return null;
        }
        return focusWindowState.getAttrs().getTitle().toString();
    }

    public String getFocusPackageName() {
        WindowState focusWindowState = getFocusWindow();
        if (focusWindowState == null || focusWindowState.getAttrs() == null) {
            return null;
        }
        return focusWindowState.getAttrs().packageName;
    }

    public boolean isNavBarAtBottom() {
        return this.mPolicy.getNavBarPosition() == 4;
    }

    public int getSystemUIFlag() {
        if (this.mPolicy instanceof HwPhoneWindowManager) {
            return this.mPolicy.getLastSystemUiFlags();
        }
        return 0;
    }

    public boolean isWindowBackDisabled() {
        return (getSystemUIFlag() & 4194304) != 0;
    }

    public boolean isWindowHomeDisabled() {
        return (getSystemUIFlag() & HighBitsCompModeID.MODE_EYE_PROTECT) != 0;
    }

    public boolean isWindowRecentDisabled() {
        return (getSystemUIFlag() & HwGlobalActionsData.FLAG_SHUTDOWN) != 0;
    }

    public boolean startHome() {
        if (!(this.mPolicy instanceof HwPhoneWindowManager)) {
            return false;
        }
        this.mPolicy.launchHome(true, false);
        return true;
    }

    public String getCurrentHomeActivity() {
        ResolveInfo resolveInfo = this.mContext.getPackageManager().resolveActivityAsUser(getHomeIntent(), 786432, this.mCurrentUserId);
        if (resolveInfo == null || resolveInfo.activityInfo == null) {
            return null;
        }
        return resolveInfo.activityInfo.packageName + "/" + resolveInfo.activityInfo.name;
    }

    public void onConfigurationChanged() {
        notifyConfigurationChanged();
    }

    public void addCallback(DeviceChangedListener listener) {
        this.mListeners.add(listener);
        if (this.mListeners.size() == 1) {
            startListening(getCurrentUser());
        }
        listener.onDeviceProvisionedChanged(isDeviceProvisioned());
        listener.onUserSetupChanged(isCurrentUserSetup());
    }

    public void removeCallback(DeviceChangedListener listener) {
        this.mListeners.remove(listener);
        if (this.mListeners.size() == 0) {
            stopListening();
        }
    }

    private void registerObserver(int userId) {
        this.mContentResolver.registerContentObserver(this.mDeviceProvisionedUri, true, this.mSettingsObserver, 0);
        this.mContentResolver.registerContentObserver(this.mUserSetupUri, true, this.mSettingsObserver, userId);
        this.mContentResolver.registerContentObserver(this.mGuideOtaFinishUri, true, this.mSettingsObserver, userId);
    }

    private void startListening(int userId) {
        Log.i(TAG, "start listening.");
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.intent.action.USER_SWITCHED");
        this.mContext.registerReceiver(this.mUserSwitchedReceiver, filter, null, null);
        filter = new IntentFilter("android.intent.action.ACTION_PREFERRED_ACTIVITY_CHANGED");
        filter.setPriority(1000);
        this.mContext.registerReceiver(this.mPreferChangedReceiver, filter, null, null);
        IntentFilter packageFilter = new IntentFilter("android.intent.action.PACKAGE_CHANGED");
        packageFilter.addDataScheme("package");
        this.mContext.registerReceiver(this.mPreferChangedReceiver, packageFilter, null, null);
        registerObserver(userId);
    }

    private void stopListening() {
        this.mContext.unregisterReceiver(this.mUserSwitchedReceiver);
        this.mContext.unregisterReceiver(this.mPreferChangedReceiver);
        this.mContentResolver.unregisterContentObserver(this.mSettingsObserver);
        Log.i(TAG, "stop listening.");
    }

    private void onUserSwitched(int newUserId) {
        this.mContentResolver.unregisterContentObserver(this.mSettingsObserver);
        registerObserver(newUserId);
        notifyUserChanged(newUserId);
    }

    private void notifyUserChanged(int newUserId) {
        for (int i = this.mListeners.size() - 1; i >= 0; i--) {
            ((DeviceChangedListener) this.mListeners.get(i)).onUserSwitched(newUserId);
        }
    }

    private void notifyProvisionedChanged(boolean provisioned) {
        for (int i = this.mListeners.size() - 1; i >= 0; i--) {
            ((DeviceChangedListener) this.mListeners.get(i)).onDeviceProvisionedChanged(provisioned);
        }
    }

    private void notifySetupChanged(boolean setup) {
        for (int i = this.mListeners.size() - 1; i >= 0; i--) {
            ((DeviceChangedListener) this.mListeners.get(i)).onUserSetupChanged(setup);
        }
    }

    private void notifyGuideOtaStateChanged() {
        for (int i = this.mListeners.size() - 1; i >= 0; i--) {
            ((DeviceChangedListener) this.mListeners.get(i)).onGuideOtaStateChanged();
        }
    }

    private void notifyConfigurationChanged() {
        for (int i = this.mListeners.size() - 1; i >= 0; i--) {
            ((DeviceChangedListener) this.mListeners.get(i)).onConfigurationChanged();
        }
    }

    private void notifyPreferredActivityChanged(boolean isPrefer) {
        for (int i = this.mListeners.size() - 1; i >= 0; i--) {
            ((DeviceChangedListener) this.mListeners.get(i)).onPreferredActivityChanged(isPrefer);
        }
    }

    private Intent getHomeIntent() {
        Intent intent = new Intent("android.intent.action.MAIN");
        intent.addCategory("android.intent.category.HOME");
        intent.addCategory("android.intent.category.DEFAULT");
        return intent;
    }
}
