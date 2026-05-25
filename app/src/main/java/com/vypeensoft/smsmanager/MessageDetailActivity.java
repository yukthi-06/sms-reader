package com.vypeensoft.smsmanager;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MessageDetailActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_message_detail);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Message Details");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        
        TextView tvSender = findViewById(R.id.tvDetailSender);
        TextView tvTimestamp = findViewById(R.id.tvDetailTimestamp);
        TextView tvBody = findViewById(R.id.tvDetailBody);

        SmsModel sms = (SmsModel) getIntent().getSerializableExtra("sms_data");
        if (sms != null) {
            String senderText = sms.getSender();
            String contactName = sms.getContactName();
            
            String prefix = sms.isSent() ? "To: " : "";
            String suffix = sms.isSent() ? " <small><font color='#3498DB'>(Sent)</font></small>" : "";

            if (contactName != null && !contactName.isEmpty()) {
                String html = "<b>" + prefix + contactName + "</b>" + suffix + "<br/><small><font color='#888888'>" + senderText + "</font></small>";
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    tvSender.setText(android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY));
                } else {
                    tvSender.setText(android.text.Html.fromHtml(html));
                }
            } else {
                tvSender.setText(prefix + senderText);
            }
            tvTimestamp.setText(sms.getTimestamp());
            tvBody.setText(sms.getBody());
            
            int fontSize = SettingsManager.getFontSize(this);
            tvSender.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, fontSize + 4);
            tvBody.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, fontSize);
            tvTimestamp.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, Math.max(10, fontSize - 2));
            
            if (!sms.isRead()) {
                SmsRepository.markSmsAsRead(this, sms.getId());
                sms.setRead(true);
            }

            android.widget.EditText etReplyMessage = findViewById(R.id.etReplyMessageDetail);
            android.widget.ImageButton btnSendReply = findViewById(R.id.btnSendReplyDetail);

            btnSendReply.setOnClickListener(v -> {
                String message = etReplyMessage.getText().toString().trim();
                if (message.isEmpty()) {
                    return;
                }

                String recipient = sms.getSender();
                if (recipient == null || recipient.isEmpty()) {
                    android.widget.Toast.makeText(this, "Could not determine recipient", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }

                final String finalRecipient = recipient;
                btnSendReply.setEnabled(false);

                SmsRepository.sendSms(this, finalRecipient, message, () -> {
                    runOnUiThread(() -> {
                        btnSendReply.setEnabled(true);
                        etReplyMessage.setText("");
                        android.widget.Toast.makeText(this, "Message sent", android.widget.Toast.LENGTH_SHORT).show();
                        
                        // Send broadcast to update the UI dynamically
                        android.content.Intent refreshIntent = new android.content.Intent("com.vypeensoft.smsmanager.REFRESH_SMS");
                        refreshIntent.putExtra("sender_number", finalRecipient);
                        sendBroadcast(refreshIntent);
                    });
                }, () -> {
                    runOnUiThread(() -> {
                        btnSendReply.setEnabled(true);
                        android.widget.Toast.makeText(this, "Failed to send message", android.widget.Toast.LENGTH_SHORT).show();
                    });
                });
            });

            android.widget.ImageButton btnDelete = findViewById(R.id.btnDeleteDetail);
            btnDelete.setOnClickListener(v -> {
                boolean confirmDelete = SettingsManager.isConfirmDelete(this);

                if (confirmDelete) {
                    new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Delete Message")
                        .setMessage("Are you sure you want to delete this message?")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            performDelete(sms);
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                } else {
                    performDelete(sms);
                }
            });
        }
    }

    private void performDelete(SmsModel sms) {
        SmsRepository.deleteSms(this, sms.getId(), () -> {
            runOnUiThread(() -> {
                android.widget.Toast.makeText(this, "Message deleted", android.widget.Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
