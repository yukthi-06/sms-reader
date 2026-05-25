package com.vypeensoft.smsmanager;

import java.io.Serializable;

public class SmsModel implements Serializable {
    private String id;
    private String sender;
    private String contactName;
    private String body;
    private String timestamp;
    private boolean isRead;
    private int type; // 1 for inbox, 2 for sent
    private int groupCount; // Only used in grouped view
    private long date; // Epoch milliseconds for sorting

    public SmsModel(String id, String sender, String contactName, String body, String timestamp, boolean isRead, int type, long date) {
        this.id = id;
        this.sender = sender;
        this.contactName = contactName;
        this.body = body;
        this.timestamp = timestamp;
        this.isRead = isRead;
        this.type = type;
        this.groupCount = 0;
        this.date = date;
    }

    public String getId() {
        return id;
    }

    public String getSender() {
        return sender;
    }

    public String getContactName() {
        return contactName;
    }

    public String getBody() {
        return body;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        this.isRead = read;
    }

    public int getType() {
        return type;
    }

    public boolean isSent() {
        return type == 2;
    }

    public int getGroupCount() {
        return groupCount;
    }

    public void setGroupCount(int groupCount) {
        this.groupCount = groupCount;
    }

    public long getDate() {
        return date;
    }
}

