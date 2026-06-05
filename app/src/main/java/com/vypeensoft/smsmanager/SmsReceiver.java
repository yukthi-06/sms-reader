package com.vypeensoft.smsmanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.SmsMessage;

public class SmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        String action = intent.getAction();

        if ("android.provider.Telephony.SMS_DELIVER".equals(action) ||
            "android.provider.Telephony.SMS_RECEIVED".equals(action)) {

            SmsMessage[] messages = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                messages = android.provider.Telephony.Sms.Intents.getMessagesFromIntent(intent);
            } else {
                Object[] pdus = (Object[]) intent.getSerializableExtra("pdus");
                if (pdus != null) {
                    messages = new SmsMessage[pdus.length];
                    for (int i = 0; i < pdus.length; i++) {
                        messages[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
                    }
                }
            }

            if (messages != null && messages.length > 0) {
                StringBuilder bodyBuilder = new StringBuilder();
                String sender = messages[0].getDisplayOriginatingAddress();
                long timestamp = messages[0].getTimestampMillis();

                for (SmsMessage msg : messages) {
                    bodyBuilder.append(msg.getDisplayMessageBody());
                }

                String body = bodyBuilder.toString();

                if ("android.provider.Telephony.SMS_DELIVER".equals(action)) {
                    // We are the default SMS app, so we must manually save the message to the inbox provider
                    SmsRepository.saveSmsToInbox(context, sender, body, timestamp);
                    if (isDefaultSmsApp(context)) {
                        showNotification(context, sender, body);
                    }
                } else {
                    // System wrote it automatically, but we invalidate our cache
                    SmsRepository.clearCache();
                    SmsRepository.updateAppStateBadge(context);
                    if (!isDefaultSmsApp(context)) {
                        showNotification(context, sender, body);
                    }
                }

                // Send broadcast to update the UI dynamically
                Intent refreshIntent = new Intent("com.vypeensoft.smsmanager.REFRESH_SMS");
                refreshIntent.putExtra("sender_number", sender);
                context.sendBroadcast(refreshIntent);
            }
        }
    }

    private boolean isDefaultSmsApp(Context context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            String defaultSmsPackage = android.provider.Telephony.Sms.getDefaultSmsPackage(context);
            return defaultSmsPackage != null && defaultSmsPackage.equals(context.getPackageName());
        }
        return false;
    }

    private String getContactName(Context context, String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) return null;
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        
        android.net.Uri uri = android.net.Uri.withAppendedPath(android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI, android.net.Uri.encode(phoneNumber));
        String[] projection = new String[]{android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME};
        try (android.database.Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void showNotification(Context context, String sender, String body) {
        // 1. Get settings
        boolean audio = SettingsManager.isNotificationAudio(context);
        boolean visual = SettingsManager.isNotificationVisual(context);
        boolean preview = SettingsManager.isNotificationPreview(context);

        // 2. Prepare title and text based on preview setting
        String title;
        String text;
        if (preview) {
            String contactName = getContactName(context, sender);
            title = contactName != null ? contactName : sender;
            text = body;
        } else {
            title = "New Message";
            text = "You have a new message";
        }

        // 3. Select channel ID
        String channelId;
        String channelName;
        int importance;
        android.net.Uri soundUri = null;
        
        if (audio && visual) {
            channelId = "sms_reader_c_audio_visual";
            channelName = "SMS Notifications (Sound & Visual)";
            importance = android.app.NotificationManager.IMPORTANCE_HIGH;
            soundUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION);
        } else if (audio && !visual) {
            channelId = "sms_reader_c_audio_no_visual";
            channelName = "SMS Notifications (Sound Only)";
            importance = android.app.NotificationManager.IMPORTANCE_DEFAULT;
            soundUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION);
        } else if (!audio && visual) {
            channelId = "sms_reader_c_no_audio_visual";
            channelName = "SMS Notifications (Visual Only)";
            importance = android.app.NotificationManager.IMPORTANCE_HIGH;
        } else {
            channelId = "sms_reader_c_no_audio_no_visual";
            channelName = "SMS Notifications (Silent)";
            importance = android.app.NotificationManager.IMPORTANCE_LOW;
        }

        android.app.NotificationManager notificationManager = (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) return;

        // Create Channel for Oreo+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(channelId, channelName, importance);
            if (soundUri != null) {
                android.media.AudioAttributes audioAttributes = new android.media.AudioAttributes.Builder()
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .build();
                channel.setSound(soundUri, audioAttributes);
            } else {
                channel.setSound(null, null);
            }
            channel.enableVibration(audio);
            notificationManager.createNotificationChannel(channel);
        }

        // 4. Intent to open MainActivity on click
        Intent clickIntent = new Intent(context, MainActivity.class);
        clickIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        
        int pendingFlags = android.app.PendingIntent.FLAG_UPDATE_CURRENT;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            pendingFlags |= android.app.PendingIntent.FLAG_IMMUTABLE;
        }
        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(context, 0, clickIntent, pendingFlags);

        // 5. Build Notification
        androidx.core.app.NotificationCompat.Builder builder = new androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setStyle(new androidx.core.app.NotificationCompat.BigTextStyle().bigText(text));

        // Pre-Oreo settings on builder
        if (visual) {
            builder.setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH);
            builder.setDefaults(androidx.core.app.NotificationCompat.DEFAULT_ALL);
        } else {
            builder.setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT);
        }

        if (!audio) {
            builder.setSound(null);
            builder.setVibrate(null);
        } else {
            builder.setSound(soundUri);
        }

        // 6. Post Notification (using sender's hashcode as ID)
        int notificationId = (sender != null) ? sender.hashCode() : 1001;
        notificationManager.notify(notificationId, builder.build());
    }
}
