package com.wix.reactnativenotifications.core.notification;

import android.os.Bundle;

public class PushNotificationProps {

    protected Bundle mBundle;

    public PushNotificationProps(Bundle bundle) {
        mBundle = bundle;
    }

    public String getTitle() {
        return getBundleStringFirstNotNull("gcm.notification.title", "title");
    }

    public String getBody() {
        return getBundleStringFirstNotNull("gcm.notification.body", "body");
    }

    public String getChannelId() {
        return getBundleStringFirstNotNull("gcm.notification.android_channel_id", "android_channel_id");
    }

    public Bundle asBundle() {
        return (Bundle) mBundle.clone();
    }

    public boolean isFirebaseBackgroundPayload() {
        return mBundle.containsKey("google.message_id");
    }

    public boolean isDataOnlyPushNotification() {
        return getTitle() == null && getBody() == null;
    }

    public int getBadge() {
        if (mBundle.containsKey("badge")) {
            return Integer.parseInt(mBundle.getString("badge"));
        }
        return -1;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(1024);
        for (String key : mBundle.keySet()) {
            sb.append(key).append("=").append(mBundle.get(key)).append(", ");
        }
        return sb.toString();
    }

    protected PushNotificationProps copy() {
        return new PushNotificationProps((Bundle) mBundle.clone());
    }

    private String getBundleStringFirstNotNull(String key1, String key2) {
        String result = mBundle.getString(key1);
        return result == null ? mBundle.getString(key2) : result;
    }

    public String getBigPicture() {
        return mBundle.getString("bigPicture");
    }

    public String getLargeIcon() {
        return mBundle.getString("largeIcon");
    }

    public String getTag() { 
        return mBundle.getString("tag"); 
    }
}
