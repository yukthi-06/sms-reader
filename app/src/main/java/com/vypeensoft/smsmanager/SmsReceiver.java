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
                } else {
                    // System wrote it automatically, but we invalidate our cache
                    SmsRepository.clearCache();
                    SmsRepository.updateAppStateBadge(context);
                }

                // Send broadcast to update the UI dynamically
                Intent refreshIntent = new Intent("com.vypeensoft.smsmanager.REFRESH_SMS");
                refreshIntent.putExtra("sender_number", sender);
                context.sendBroadcast(refreshIntent);
            }
        }
    }
}
