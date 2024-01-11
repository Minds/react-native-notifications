package com.wix.reactnativenotifications.core.notification;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import java.util.List;

import com.facebook.react.bridge.ReactContext;
import com.wix.reactnativenotifications.core.AppLaunchHelper;
import com.wix.reactnativenotifications.core.AppLifecycleFacade;
import com.wix.reactnativenotifications.core.AppLifecycleFacade.AppVisibilityListener;
import com.wix.reactnativenotifications.core.AppLifecycleFacadeHolder;
import com.wix.reactnativenotifications.core.InitialNotificationHolder;
import com.wix.reactnativenotifications.core.JsIOHelper;
import com.wix.reactnativenotifications.core.NotificationIntentAdapter;
import com.wix.reactnativenotifications.core.ProxyService;
import com.wix.reactnativenotifications.core.BitmapLoader;
import com.wix.reactnativenotifications.helpers.ApplicationBadgeHelper;

import static com.wix.reactnativenotifications.Defs.NOTIFICATION_OPENED_EVENT_NAME;
import static com.wix.reactnativenotifications.Defs.NOTIFICATION_RECEIVED_EVENT_NAME;
import static com.wix.reactnativenotifications.Defs.NOTIFICATION_RECEIVED_BACKGROUND_EVENT_NAME;
import static com.wix.reactnativenotifications.Defs.LOGTAG;

public class PushNotification implements IPushNotification {

    final protected BitmapLoader mImageLoader;
    final protected Context mContext;
    final protected AppLifecycleFacade mAppLifecycleFacade;
    final protected AppLaunchHelper mAppLaunchHelper;
    final protected JsIOHelper mJsIOHelper;
    final protected PushNotificationProps mNotificationProps;
    final protected AppVisibilityListener mAppVisibilityListener = new AppVisibilityListener() {
        @Override
        public void onAppVisible() {
            mAppLifecycleFacade.removeVisibilityListener(this);
            dispatchImmediately();
        }

        @Override
        public void onAppNotVisible() {
        }
    };
    final private String DEFAULT_CHANNEL_ID = "channel_01";
    final private String DEFAULT_CHANNEL_NAME = "Channel Name";

    public static IPushNotification get(Context context, Bundle bundle) {
        Context appContext = context.getApplicationContext();
        if (appContext instanceof INotificationsApplication) {
            return ((INotificationsApplication) appContext).getPushNotification(context, bundle, AppLifecycleFacadeHolder.get(), new AppLaunchHelper());
        }
        return new PushNotification(context, bundle, AppLifecycleFacadeHolder.get(), new AppLaunchHelper(), new JsIOHelper(), new BitmapLoader(appContext));
    }

    protected PushNotification(Context context, Bundle bundle, AppLifecycleFacade appLifecycleFacade, AppLaunchHelper appLaunchHelper, JsIOHelper JsIOHelper, BitmapLoader imageLoader) {
        mContext = context;
        mAppLifecycleFacade = appLifecycleFacade;
        mAppLaunchHelper = appLaunchHelper;
        mJsIOHelper = JsIOHelper;
        mNotificationProps = createProps(bundle);
        mImageLoader = imageLoader;
        initDefaultChannel(context);
    }

    @Override
    public void onReceived() throws InvalidNotificationException {
        Log.e(LOGTAG, "onReceived: " + mNotificationProps.toString());
        if (!mAppLifecycleFacade.isAppVisible()) {
            postNotification(null);
            notifyReceivedBackgroundToJS();
        } else {
            notifyReceivedToJS();
        }
    }

    @Override
    public void onOpened() {
        digestNotification();
    }

    @Override
    public int onPostRequest(Integer notificationId) {
        return postNotification(notificationId);
    }

    @Override
    public PushNotificationProps asProps() {
        return mNotificationProps.copy();
    }

    protected int postNotification(Integer notificationId) {
        if (mNotificationProps.isDataOnlyPushNotification()) {
            return -1;
        }
        final PendingIntent pendingIntent = NotificationIntentAdapter.createPendingNotificationIntent(mContext, mNotificationProps);;
        final Notification notification = buildNotification(pendingIntent);

        int id = notificationId != null ? notificationId : 0;
        int badge = mNotificationProps.getBadge();
        if (badge >= 0) {
            ApplicationBadgeHelper.INSTANCE.setApplicationIconBadgeNumber(mContext, badge);
        }

        setLargeIconThenPostNotification(id, getNotificationBuilder(pendingIntent));
        return id;
        // return postNotification(notification, notificationId);1
    }

    protected void digestNotification() {
        if (!mAppLifecycleFacade.isReactInitialized()) {
            setAsInitialNotification();
            launchOrResumeApp();
            return;
        }

        final ReactContext reactContext = mAppLifecycleFacade.getRunningReactContext();
        if (reactContext.getCurrentActivity() == null) {
            setAsInitialNotification();
        }

        if (mAppLifecycleFacade.isAppVisible()) {
            dispatchImmediately();
        } else if (mAppLifecycleFacade.isAppDestroyed()) {
            launchOrResumeApp();
        } else {
            dispatchUponVisibility();
        }
    }

    protected PushNotificationProps createProps(Bundle bundle) {
        return new PushNotificationProps(bundle.containsKey("pushNotification") ? bundle.getBundle("pushNotification") : bundle);
    }

    protected void setAsInitialNotification() {
        InitialNotificationHolder.getInstance().set(mNotificationProps);
    }

    protected void dispatchImmediately() {
        notifyOpenedToJS();
    }

    protected void dispatchUponVisibility() {
        mAppLifecycleFacade.addVisibilityListener(getIntermediateAppVisibilityListener());

        // Make the app visible so that we'll dispatch the notification opening when visibility changes to 'true' (see
        // above listener registration).
        launchOrResumeApp();
    }

    protected AppVisibilityListener getIntermediateAppVisibilityListener() {
        return mAppVisibilityListener;
    }

    protected Notification buildNotification(PendingIntent intent) {
        return getNotificationBuilder(intent).build();
    }

    protected void setLargeIconThenPostNotification(final int notificationId, final Notification.Builder notificationBuilder) {
        final String icon = mNotificationProps.getLargeIcon();

        if (icon != null && (icon.startsWith("http://") || icon.startsWith("https://") || icon.startsWith("file://"))) {
            mImageLoader.loadUri(Uri.parse(icon), new BitmapLoader.OnBitmapLoadedCallback() {
                @Override
                public void onBitmapLoaded(Bitmap bitmap) {
                    notificationBuilder.setLargeIcon(bitmap);
                    setBigPictureThenPostNotification(notificationId, notificationBuilder);
                }
            });
        } else {
            if (icon != null) {
                final int id = mContext.getResources().getIdentifier(icon, "drawable", mContext.getPackageName());
                final Bitmap bitmap = id != 0 ? BitmapFactory.decodeResource(mContext.getResources(), id) : null;

                if (bitmap != null) {
                    notificationBuilder.setLargeIcon(bitmap);
                }
            }

            setBigPictureThenPostNotification(notificationId, notificationBuilder);
        }
    }

    protected void setBigPictureThenPostNotification(final int notificationId, final Notification.Builder notificationBuilder) {
        final String bigPicture = mNotificationProps.getBigPicture();

        if (bigPicture != null && (bigPicture.startsWith("http://") || bigPicture.startsWith("https://") || bigPicture.startsWith("file://"))) {
            mImageLoader.loadUri(Uri.parse(bigPicture), new BitmapLoader.OnBitmapLoadedCallback() {
                @Override
                public void onBitmapLoaded(Bitmap bitmap) {
                    notificationBuilder.setStyle(new Notification.BigPictureStyle().bigPicture(bitmap).setSummaryText(mNotificationProps.getBody()));
                    postNotification(notificationId, notificationBuilder.build());
                }
            });
        } else {
            postNotification(notificationId, notificationBuilder.build());
        }
    }

    protected Notification.Builder getNotificationBuilder(PendingIntent intent) {
        final Notification.Builder notification = new Notification.Builder(mContext)
                .setContentTitle(mNotificationProps.getTitle())
                .setContentText(mNotificationProps.getBody())
                .setContentIntent(intent)
                .setDefaults(Notification.DEFAULT_ALL)
                .setAutoCancel(true);

        setUpIcon(notification);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final NotificationManager notificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
            String channelId = mNotificationProps.getChannelId();
            NotificationChannel channel = notificationManager.getNotificationChannel(channelId);
            notification.setChannelId(channel != null ? channelId : DEFAULT_CHANNEL_ID);
        }

        return notification;
    }

    private void setUpIcon(Notification.Builder notification) {
        int iconResId = getAppResourceId("notification_icon", "drawable");
        if (iconResId != 0) {
            notification.setSmallIcon(iconResId);
        } else {
            notification.setSmallIcon(mContext.getApplicationInfo().icon);
        }

        setUpIconColor(notification);
    }

    private void setUpIconColor(Notification.Builder notification) {
        int colorResID = getAppResourceId("colorAccent", "color");
        if (colorResID != 0) {
            int color = mContext.getResources().getColor(colorResID);
            notification.setColor(color);
        }
    }

    protected int postNotification(Notification notification, Integer notificationId) {
        int id = notificationId != null ? notificationId : createNotificationId(notification);
        postNotification(id, notification);
        return id;
    }

    protected void postNotification(int id, Notification notification) {
        final NotificationManager notificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(mNotificationProps.getTag(), id, notification);
    }

    protected int createNotificationId(Notification notification) {
        return (int) System.nanoTime();
    }

    private void notifyReceivedToJS() {
        try {
            Bundle bundle = mNotificationProps.asBundle();
            mJsIOHelper.sendEventToJS(NOTIFICATION_RECEIVED_EVENT_NAME, bundle, mAppLifecycleFacade.getRunningReactContext());
        } catch (NullPointerException ex) {
            Log.e(LOGTAG, "notifyReceivedToJS: Null pointer exception");
        }
    }

    private void notifyReceivedBackgroundToJS() {
        try {
            Bundle bundle = mNotificationProps.asBundle();
            mJsIOHelper.sendEventToJS(NOTIFICATION_RECEIVED_BACKGROUND_EVENT_NAME, bundle, mAppLifecycleFacade.getRunningReactContext());
        } catch (NullPointerException ex) {
            Log.e(LOGTAG, "notifyReceivedBackgroundToJS: Null pointer exception");
        }
    }

    private void notifyOpenedToJS() {
        Bundle response = new Bundle();

        try {
            response.putBundle("notification", mNotificationProps.asBundle());
            mJsIOHelper.sendEventToJS(NOTIFICATION_OPENED_EVENT_NAME, response, mAppLifecycleFacade.getRunningReactContext());
        } catch (NullPointerException ex) {
            Log.e(LOGTAG, "notifyOpenedToJS: Null pointer exception");
        }
    }

    protected void launchOrResumeApp() {
        if (NotificationIntentAdapter.canHandleTrampolineActivity(mContext)) {
            final Intent intent = mAppLaunchHelper.getLaunchIntent(mContext);
            mContext.startActivity(intent);
        }
    }

    private int getAppResourceId(String resName, String resType) {
        return mContext.getResources().getIdentifier(resName, resType, mContext.getPackageName());
    }

    private void initDefaultChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            List<NotificationChannel> channels = notificationManager.getNotificationChannels();
            boolean defaultChannelExists = false;

            for (NotificationChannel channel : channels) {
                if (channel.getId().equals(DEFAULT_CHANNEL_ID)) {
                    defaultChannelExists = true;
                    break;
                }
            }
            
            if (!defaultChannelExists) {
                NotificationChannel defaultChannel = new NotificationChannel(
                    DEFAULT_CHANNEL_ID,
                    DEFAULT_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                );
                notificationManager.createNotificationChannel(defaultChannel);
            }
        }
    }
}
