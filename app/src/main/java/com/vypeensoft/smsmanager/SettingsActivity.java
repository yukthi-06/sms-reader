package com.vypeensoft.smsmanager;

import android.os.Bundle;
import android.view.MenuItem;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Settings");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        org.json.JSONObject settings = SettingsManager.loadSettings(this);
        final int[] currentSize = {settings.optInt("font_size", 16)};

        android.widget.TextView tvFontSize = findViewById(R.id.tvFontSize);
        android.widget.Button btnDecreaseFont = findViewById(R.id.btnDecreaseFont);
        android.widget.Button btnIncreaseFont = findViewById(R.id.btnIncreaseFont);

        tvFontSize.setText(String.valueOf(currentSize[0]));

        btnDecreaseFont.setOnClickListener(v -> {
            if (currentSize[0] > 10) { // Minimum size 10
                currentSize[0] -= 2;
                tvFontSize.setText(String.valueOf(currentSize[0]));
                try {
                    settings.put("font_size", currentSize[0]);
                    SettingsManager.saveSettings(this, settings);
                } catch (Exception e) {}
            }
        });

        btnIncreaseFont.setOnClickListener(v -> {
            if (currentSize[0] < 36) { // Maximum size 36
                currentSize[0] += 2;
                tvFontSize.setText(String.valueOf(currentSize[0]));
                try {
                    settings.put("font_size", currentSize[0]);
                    SettingsManager.saveSettings(this, settings);
                } catch (Exception e) {}
            }
        });

        androidx.appcompat.widget.SwitchCompat switchConfirmDelete = findViewById(R.id.switchConfirmDelete);
        switchConfirmDelete.setChecked(settings.optBoolean("confirm_delete", true));
        switchConfirmDelete.setOnCheckedChangeListener((buttonView, isChecked) -> {
            try {
                settings.put("confirm_delete", isChecked);
                SettingsManager.saveSettings(this, settings);
            } catch (Exception e) {}
        });

        androidx.appcompat.widget.SwitchCompat switchSortAscending = findViewById(R.id.switchSortAscending);
        switchSortAscending.setChecked(settings.optBoolean("sort_ascending", true));
        switchSortAscending.setOnCheckedChangeListener((buttonView, isChecked) -> {
            try {
                settings.put("sort_ascending", isChecked);
                SettingsManager.saveSettings(this, settings);
            } catch (Exception e) {}
        });

        android.widget.Button btnOpenSystemSettings = findViewById(R.id.btnOpenSystemSettings);
        btnOpenSystemSettings.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            android.net.Uri uri = android.net.Uri.fromParts("package", getPackageName(), null);
            intent.setData(uri);
            startActivity(intent);
        });

        android.widget.Button btnMakeDefaultSms = findViewById(R.id.btnMakeDefaultSms);
        btnMakeDefaultSms.setOnClickListener(v -> {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                android.app.role.RoleManager roleManager = getSystemService(android.app.role.RoleManager.class);
                if (roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_SMS)) {
                    if (roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_SMS)) {
                        android.widget.Toast.makeText(this, "Already the default SMS app", android.widget.Toast.LENGTH_SHORT).show();
                    } else {
                        android.content.Intent roleRequestIntent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_SMS);
                        startActivityForResult(roleRequestIntent, 101);
                    }
                }
            } else {
                android.content.Intent intent = new android.content.Intent(android.provider.Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
                intent.putExtra(android.provider.Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, getPackageName());
                startActivity(intent);
            }
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
