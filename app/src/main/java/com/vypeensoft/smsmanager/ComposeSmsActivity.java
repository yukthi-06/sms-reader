package com.vypeensoft.smsmanager;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class ComposeSmsActivity extends AppCompatActivity {
    private static final int CONTACT_PICKER_REQUEST = 201;
    
    private EditText etRecipient;
    private EditText etMessageBody;
    private ImageButton btnSelectContact;
    private Button btnSendSms;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compose_sms);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("New Message");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        etRecipient = findViewById(R.id.etRecipient);
        etMessageBody = findViewById(R.id.etMessageBody);
        btnSelectContact = findViewById(R.id.btnSelectContact);
        btnSendSms = findViewById(R.id.btnSendSms);

        btnSelectContact.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
            startActivityForResult(intent, CONTACT_PICKER_REQUEST);
        });

        btnSendSms.setOnClickListener(v -> {
            String recipient = etRecipient.getText().toString().trim();
            String message = etMessageBody.getText().toString().trim();

            if (recipient.isEmpty()) {
                Toast.makeText(this, "Please enter a recipient", Toast.LENGTH_SHORT).show();
                return;
            }

            if (message.isEmpty()) {
                Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show();
                return;
            }

            btnSendSms.setEnabled(false);

            SmsRepository.sendSms(this, recipient, message, () -> {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Message sent successfully", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }, () -> {
                runOnUiThread(() -> {
                    btnSendSms.setEnabled(true);
                    Toast.makeText(this, "Failed to send message", Toast.LENGTH_SHORT).show();
                });
            });
        });

        handleIncomingIntent();
    }

    private void handleIncomingIntent() {
        Intent intent = getIntent();
        if (intent != null) {
            Uri data = intent.getData();
            if (data != null && data.getSchemeSpecificPart() != null) {
                String number = data.getSchemeSpecificPart();
                // Scheme specific part might contain queries like ?body=... 
                // but usually for smsto: it's just the number. Let's just use it or decode it.
                if (number.contains("?")) {
                    number = number.substring(0, number.indexOf('?'));
                }
                etRecipient.setText(number);
            }

            if (intent.hasExtra("sms_body")) {
                etMessageBody.setText(intent.getStringExtra("sms_body"));
            } else if (intent.hasExtra(Intent.EXTRA_TEXT)) {
                etMessageBody.setText(intent.getStringExtra(Intent.EXTRA_TEXT));
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CONTACT_PICKER_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri contactUri = data.getData();
            if (contactUri != null) {
                String[] projection = new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER};
                try (Cursor cursor = getContentResolver().query(contactUri, projection, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                        String number = cursor.getString(numberIndex);
                        etRecipient.setText(number);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(this, "Failed to load contact number", Toast.LENGTH_SHORT).show();
                }
            }
        }
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
