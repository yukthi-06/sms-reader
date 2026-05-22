package com.vypeensoft.smsmanager;

import android.content.ContentValues;
import android.content.Context;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SmsRepository {
    private static List<SmsModel> cachedSmsList = new ArrayList<>();
    private static boolean isCacheValid = false;

    public interface SmsLoadCallback {
        void onSmsLoaded(List<SmsModel> smsList);
    }

    public static void clearCache() {
        isCacheValid = false;
    }

    public static void getAllSms(ContentResolver contentResolver, SmsLoadCallback callback) {
        // Return cached data immediately if valid
        if (isCacheValid && !cachedSmsList.isEmpty()) {
            callback.onSmsLoaded(new ArrayList<>(cachedSmsList));
        }

        new Thread(() -> {
            List<SmsModel> smsList = new ArrayList<>();
            try {
                // Step 1: Pre-fetch all contacts into a map for fast lookup
                Map<String, String> contactMap = new HashMap<>();
                try {
                    Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
                    String[] projection = new String[]{
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                    };
                    try (Cursor contactCursor = contentResolver.query(uri, projection, null, null, null)) {
                        if (contactCursor != null) {
                            int numIdx = contactCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                            int nameIdx = contactCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                            while (contactCursor.moveToNext()) {
                                String number = contactCursor.getString(numIdx);
                                String name = contactCursor.getString(nameIdx);
                                if (number != null && name != null) {
                                    // Remove all non-digit characters for matching
                                    String digitsOnly = number.replaceAll("\\D", "");
                                    
                                    // Store original and digitsOnly versions
                                    contactMap.put(number, name);
                                    if (!digitsOnly.isEmpty()) {
                                        contactMap.put(digitsOnly, name);
                                        
                                        // Also store the last 10 digits to handle country code variations
                                        if (digitsOnly.length() >= 10) {
                                            String last10 = digitsOnly.substring(digitsOnly.length() - 10);
                                            contactMap.put(last10, name);
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                // Step 2: Load SMS messages
                Uri uriSms = Uri.parse("content://sms/");
                Cursor cursor = contentResolver.query(uriSms, new String[]{"_id", "address", "body", "date", "read", "type"}, null, null, "date DESC");

                if (cursor != null) {
                    int indexId = cursor.getColumnIndex("_id");
                    int indexAddress = cursor.getColumnIndex("address");
                    int indexBody = cursor.getColumnIndex("body");
                    int indexDate = cursor.getColumnIndex("date");
                    int indexRead = cursor.getColumnIndex("read");
                    int indexType = cursor.getColumnIndex("type");

                    while (cursor.moveToNext()) {
                        try {
                            String id = cursor.getString(indexId);
                            String address = cursor.getString(indexAddress);
                            String body = cursor.getString(indexBody);
                            long dateMillis = cursor.getLong(indexDate);
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MMM-dd HH:mm:ss", Locale.getDefault());
                            String timestamp = sdf.format(new Date(dateMillis));
                            boolean isRead = true;
                            if (indexRead != -1) {
                                isRead = cursor.getInt(indexRead) == 1;
                            }
                            
                            int type = 1; // default to inbox
                            if (indexType != -1) {
                                type = cursor.getInt(indexType);
                            }

                            // Only include Inbox (1) and Sent (2) messages
                            if (type != 1 && type != 2) continue;

                            String contactName = null;
                            if (address != null && !address.isEmpty()) {
                                contactName = contactMap.get(address);
                                
                                if (contactName == null) {
                                    String digitsOnly = address.replaceAll("\\D", "");
                                    contactName = contactMap.get(digitsOnly);
                                    
                                    if (contactName == null && digitsOnly.length() >= 10) {
                                        String last10 = digitsOnly.substring(digitsOnly.length() - 10);
                                        contactName = contactMap.get(last10);
                                    }
                                }
                            }
                            
                            smsList.add(new SmsModel(id, address, contactName, body, timestamp, isRead, type));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    cursor.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            callback.onSmsLoaded(smsList);
            
            // Update cache
            synchronized (SmsRepository.class) {
                cachedSmsList = new ArrayList<>(smsList);
                isCacheValid = true;
            }
        }).start();
    }

    public static void markSmsAsRead(Context context, String messageId) {
        isCacheValid = false; // Invalidate cache
        new Thread(() -> {
            try {
                ContentValues values = new ContentValues();
                values.put("read", 1);
                Uri uriSms = Uri.parse("content://sms");
                context.getContentResolver().update(uriSms, values, "_id=" + messageId, null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public static void deleteSms(Context context, String messageId, Runnable onSuccess) {
        isCacheValid = false; // Invalidate cache
        new Thread(() -> {
            try {
                Uri uriSms = Uri.parse("content://sms/" + messageId);
                int rowsDeleted = context.getContentResolver().delete(uriSms, null, null);
                if (rowsDeleted > 0 && onSuccess != null) {
                    onSuccess.run();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public static void exportMessages(Context context, List<SmsModel> smsList, String format, String exportPath, Runnable onSuccess) {
        new Thread(() -> {
            try {
                java.io.File dir = new java.io.File(exportPath);
                if (!dir.exists()) dir.mkdirs();

                String timeStamp = new SimpleDateFormat("yyyyMMdd.HHmmss", Locale.getDefault()).format(new Date());
                String fileName = "SMS-" + timeStamp + "." + format.toLowerCase();
                java.io.File file = new java.io.File(dir, fileName);

                java.io.FileWriter writer = new java.io.FileWriter(file);
                if ("xml".equalsIgnoreCase(format)) {
                    writer.write("<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>\n");
                    writer.write("<smses count=\"" + smsList.size() + "\">\n");
                    for (SmsModel sms : smsList) {
                        writer.write("  <sms address=\"" + escapeXml(sms.getSender()) + 
                                     "\" body=\"" + escapeXml(sms.getBody()) + 
                                     "\" date=\"" + sms.getTimestamp() + 
                                     "\" read=\"" + (sms.isRead() ? "1" : "0") + "\" />\n");
                    }
                    writer.write("</smses>");
                } else if ("json".equalsIgnoreCase(format)) {
                    writer.write("[\n");
                    for (int i = 0; i < smsList.size(); i++) {
                        SmsModel sms = smsList.get(i);
                        writer.write("  {\n");
                        writer.write("    \"sender\": \"" + escapeJson(sms.getSender()) + "\",\n");
                        writer.write("    \"body\": \"" + escapeJson(sms.getBody()) + "\",\n");
                        writer.write("    \"timestamp\": \"" + sms.getTimestamp() + "\",\n");
                        writer.write("    \"read\": " + sms.isRead() + "\n");
                        writer.write("  }" + (i == smsList.size() - 1 ? "" : ",") + "\n");
                    }
                    writer.write("]");
                } else if ("csv".equalsIgnoreCase(format)) {
                    writer.write("Sender,Body,Timestamp,Read\n");
                    for (SmsModel sms : smsList) {
                        writer.write("\"" + escapeCsv(sms.getSender()) + "\",\"" + 
                                     escapeCsv(sms.getBody()) + "\",\"" + 
                                     sms.getTimestamp() + "\"," + 
                                     sms.isRead() + "\n");
                    }
                }
                writer.close();
                if (onSuccess != null) onSuccess.run();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private static String escapeXml(String str) {
        if (str == null) return "";
        return str.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String escapeCsv(String str) {
        if (str == null) return "";
        return str.replace("\"", "\"\"");
    }

    public static void sendSms(Context context, String recipient, String message, Runnable onSuccess, Runnable onFailure) {
        new Thread(() -> {
            try {
                android.telephony.SmsManager smsManager;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    smsManager = context.getSystemService(android.telephony.SmsManager.class);
                } else {
                    smsManager = android.telephony.SmsManager.getDefault();
                }

                if (smsManager == null) {
                    smsManager = android.telephony.SmsManager.getDefault();
                }

                ArrayList<String> parts = smsManager.divideMessage(message);
                if (parts.size() > 1) {
                    smsManager.sendMultipartTextMessage(recipient, null, parts, null, null);
                } else {
                    smsManager.sendTextMessage(recipient, null, message, null, null);
                }

                // Manually save the sent SMS to content://sms/sent
                saveSmsToSent(context, recipient, message);

                if (onSuccess != null) {
                    onSuccess.run();
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (onFailure != null) {
                    onFailure.run();
                }
            }
        }).start();
    }

    public static void saveSmsToSent(Context context, String recipient, String message) {
        clearCache(); // Invalidate cache
        try {
            ContentValues values = new ContentValues();
            values.put("address", recipient);
            values.put("body", message);
            values.put("date", System.currentTimeMillis());
            values.put("read", 1); // Sent is read
            values.put("type", 2); // 2 is sent
            context.getContentResolver().insert(Uri.parse("content://sms/sent"), values);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void saveSmsToInbox(Context context, String sender, String message, long timestamp) {
        clearCache(); // Invalidate cache
        try {
            ContentValues values = new ContentValues();
            values.put("address", sender);
            values.put("body", message);
            values.put("date", timestamp);
            values.put("read", 0); // Unread
            values.put("type", 1); // 1 is inbox
            context.getContentResolver().insert(Uri.parse("content://sms/inbox"), values);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
