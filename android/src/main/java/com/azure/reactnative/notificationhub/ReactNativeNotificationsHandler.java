package com.azure.reactnative.notificationhub;

import android.app.AlarmManager;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.facebook.react.bridge.ReadableMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.microsoft.windowsazure.notifications.NotificationsHandler;

import java.util.Set;

public class ReactNativeNotificationsHandler extends NotificationsHandler {
    public static final String TAG = "ReactNativeNotificationsHandler";

    private static final long DEFAULT_VIBRATION = 300L;

    private Context context;

    @Override
    public void onReceive(Context context, Bundle bundle) {
        this.context = context;
        sendNotification(bundle);

        JSONObject json = new JSONObject();
        Set<String> keys = bundle.keySet();
        for (String key : keys) {
            try {
                json.put(key, bundle.get(key));
            } catch (JSONException e) {
            }
        }

        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(context);
        Intent event= new Intent(TAG);
        event.putExtra("event", ReactNativeNotificationHubModule.DEVICE_NOTIF_EVENT);
        event.putExtra("data", json.toString());
        localBroadcastManager.sendBroadcast(event);
    }

    private Class getMainActivityClass() {
        String packageName = context.getPackageName();
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(packageName);
        String className = launchIntent.getComponent().getClassName();
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    private AlarmManager getAlarmManager() {
        return (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    }

    private void sendNotification(Bundle bundle) {
        try {
            Class intentClass = getMainActivityClass();
            if (intentClass == null) {
                Log.e(TAG, "No activity class found for the notification");
                return;
            }

            if (bundle.getString("message") == null) {
                Log.e(TAG, "No message specified for the notification");
                return;
            }

            String notificationIdString = bundle.getString("id");
            if (notificationIdString == null) {
                Log.e(TAG, "No notification ID specified for the notification");
                return;
            }

            Resources res = context.getResources();
            String packageName = context.getPackageName();

            String title = bundle.getString("title");
            if (title == null) {
                ApplicationInfo appInfo = context.getApplicationInfo();
                title = context.getPackageManager().getApplicationLabel(appInfo).toString();
            }

            String channelId = bundle.getString("channelId");
            if (channelId == null) {
                channelId = title;
            }

            NotificationCompat.Builder notification = new NotificationCompat.Builder(context, channelId)
                    .setContentTitle(title)
                    .setTicker(bundle.getString("ticker"))
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(bundle.getBoolean("autoCancel", true));

            String group = bundle.getString("group");
            if (group != null) {
                notification.setGroup(group);
            }

            notification.setContentText(bundle.getString("message"));

            String largeIcon = bundle.getString("largeIcon");

            String subText = bundle.getString("subText");

            if (subText != null) {
                notification.setSubText(subText);
            }

            String numberString = bundle.getString("number");
            if (numberString != null) {
                notification.setNumber(Integer.parseInt(numberString));
            }

            int smallIconResId;
            int largeIconResId;

            String smallIcon = bundle.getString("smallIcon");

            if (smallIcon != null) {
                smallIconResId = res.getIdentifier(smallIcon, "mipmap", packageName);
            } else {
                smallIconResId = res.getIdentifier("ic_notification", "mipmap", packageName);
            }

            if (smallIconResId == 0) {
                smallIconResId = res.getIdentifier("ic_launcher", "mipmap", packageName);

                if (smallIconResId == 0) {
                    smallIconResId = android.R.drawable.ic_dialog_info;
                }
            }

            if (largeIcon != null) {
                largeIconResId = res.getIdentifier(largeIcon, "mipmap", packageName);
            } else {
                largeIconResId = res.getIdentifier("ic_launcher", "mipmap", packageName);
            }

            Bitmap largeIconBitmap = BitmapFactory.decodeResource(res, largeIconResId);

            if (largeIconResId != 0 && (largeIcon != null || Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)) {
                notification.setLargeIcon(largeIconBitmap);
            }

            notification.setSmallIcon(smallIconResId);
            String bigText = bundle.getString("bigText");

            if (bigText == null) {
                bigText = bundle.getString("message");
            }

            notification.setStyle(new NotificationCompat.BigTextStyle().bigText(bigText));

            Intent intent = new Intent(context, intentClass);
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            bundle.putBoolean("userInteraction", true);
            intent.putExtra("notification", bundle);

            if (!bundle.containsKey("playSound") || bundle.getBoolean("playSound")) {
                Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                String soundName = bundle.getString("soundName");
                if (soundName != null) {
                    if (!"default".equalsIgnoreCase(soundName)) {

                        // sound name can be full filename, or just the resource name.
                        // So the strings 'my_sound.mp3' AND 'my_sound' are accepted
                        // The reason is to make the iOS and android javascript interfaces compatible

                        int resId;
                        if (context.getResources().getIdentifier(soundName, "raw", context.getPackageName()) != 0) {
                            resId = context.getResources().getIdentifier(soundName, "raw", context.getPackageName());
                        } else {
                            soundName = soundName.substring(0, soundName.lastIndexOf('.'));
                            resId = context.getResources().getIdentifier(soundName, "raw", context.getPackageName());
                        }

                        soundUri = Uri.parse("android.resource://" + context.getPackageName() + "/" + resId);
                    }
                }
                notification.setSound(soundUri);
            }

            if (bundle.containsKey("ongoing") || bundle.getBoolean("ongoing")) {
                notification.setOngoing(bundle.getBoolean("ongoing"));
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                notification.setCategory(NotificationCompat.CATEGORY_CALL);

                String color = bundle.getString("color");
                if (color != null) {
                    notification.setColor(Color.parseColor(color));
                }
            }

            int notificationID = Integer.parseInt(notificationIdString);

            PendingIntent pendingIntent = PendingIntent.getActivity(context, notificationID, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT);

            NotificationManager notificationManager = notificationManager();

            notification.setContentIntent(pendingIntent);

            if (!bundle.containsKey("vibrate") || bundle.getBoolean("vibrate")) {
                long vibration = bundle.containsKey("vibration") ? (long) bundle.getDouble("vibration") : DEFAULT_VIBRATION;
                if (vibration == 0)
                    vibration = DEFAULT_VIBRATION;
                notification.setVibrate(new long[]{0, vibration});
            }

            JSONArray actionsArray = null;
            try {
                actionsArray = bundle.getString("actions") != null ? new JSONArray(bundle.getString("actions")) : null;
            } catch (JSONException e) {
                Log.e(TAG, "Exception while converting actions to JSON object.", e);
            }

            if (actionsArray != null) {
                // No icon for now. The icon value of 0 shows no icon.
                int icon = 0;

                // Add button for each actions.
                for (int i = 0; i < actionsArray.length(); i++) {
                    String action;
                    try {
                        action = actionsArray.getString(i);
                    } catch (JSONException e) {
                        Log.e(TAG, "Exception while getting action from actionsArray.", e);
                        continue;
                    }

                    Intent actionIntent = new Intent();
                    actionIntent.setAction(context.getPackageName() + "." + action);
                    // Add "action" for later identifying which button gets pressed.
                    bundle.putString("action", action);
                    actionIntent.putExtra("notification", bundle);
                    PendingIntent pendingActionIntent = PendingIntent.getBroadcast(context, notificationID, actionIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT);
                    notification.addAction(icon, action, pendingActionIntent);
                }
            }

            Notification info = notification.build();
            info.defaults |= Notification.DEFAULT_LIGHTS;

            if (bundle.containsKey("tag")) {
                String tag = bundle.getString("tag");
                notificationManager.notify(tag, notificationID, info);
            } else {
                notificationManager.notify(notificationID, info);
            }
        } catch (Exception e) {
            Log.e(TAG, "failed to send push notification", e);
        }
    }

    private NotificationManager notificationManager() {
        return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }
}
