package com.vypeensoft.smsmanager;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.navigation.NavigationView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_READ_SMS = 101;

    private final android.content.BroadcastReceiver smsRefreshReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, android.content.Intent intent) {
            loadSms();
        }
    };

    private RecyclerView rvSmsList;
    private SmsAdapter smsAdapter;
    private List<SmsModel> allSmsList = new ArrayList<>();
    private EditText etSearch;
    private Button btnSearch;
    private TextView tvEmptyState;
    private View emptyStateContainer;
    private Button btnRequestPermission;
    private RadioGroup rgViewGroup;
    private boolean isGroupView = true;
    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle toggle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        drawerLayout = findViewById(R.id.drawer_layout);
        toggle = new ActionBarDrawerToggle(this, drawerLayout, R.string.app_name, R.string.app_name);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_settings) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            } else if (id == R.id.nav_help) {
                startActivity(new Intent(MainActivity.this, HelpActivity.class));
            } else if (id == R.id.nav_about) {
                startActivity(new Intent(MainActivity.this, AboutActivity.class));
            } else if (id == R.id.nav_export) {
                startActivity(new Intent(MainActivity.this, ExportActivity.class));
            }
            drawerLayout.closeDrawers();
            return true;
        });

        initViews();
        checkPermissions();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (toggle.onOptionsItemSelected(item)) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(smsRefreshReceiver, new android.content.IntentFilter("com.vypeensoft.smsmanager.REFRESH_SMS"), android.content.Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(smsRefreshReceiver, new android.content.IntentFilter("com.vypeensoft.smsmanager.REFRESH_SMS"));
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, "android.permission.WRITE_SMS") == PackageManager.PERMISSION_GRANTED) {
            loadSms();
            SmsRepository.updateAppStateBadge(this);
        } else {
            updateVisibility();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(smsRefreshReceiver);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initViews() {
        rvSmsList = findViewById(R.id.rvSmsList);
        etSearch = findViewById(R.id.etSearch);
        btnSearch = findViewById(R.id.btnSearch);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        emptyStateContainer = findViewById(R.id.emptyStateContainer);
        btnRequestPermission = findViewById(R.id.btnRequestPermission);
        rgViewGroup = findViewById(R.id.rgViewGroup);

        rvSmsList.setLayoutManager(new LinearLayoutManager(this));
        smsAdapter = new SmsAdapter(new ArrayList<>(), new SmsAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(SmsModel sms) {
                if (isGroupView) {
                    Intent intent = new Intent(MainActivity.this, GroupedMessagesActivity.class);
                    intent.putExtra("group_key", getGroupKey(sms));
                    intent.putExtra("group_display_name", sms.getContactName() != null ? sms.getContactName() : extractSenderName(sms.getSender()));
                    intent.putExtra("sender_number", sms.getSender());
                    intent.putExtra("search_query", etSearch.getText().toString().toLowerCase().trim());
                    startActivity(intent);
                } else {
                    Intent intent = new Intent(MainActivity.this, MessageDetailActivity.class);
                    intent.putExtra("sms_data", sms);
                    startActivity(intent);
                }
            }

            @Override
            public void onDeleteClick(SmsModel sms) {
                boolean confirmDelete = SettingsManager.isConfirmDelete(MainActivity.this);

                if (confirmDelete) {
                    new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
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
            }

            @Override
            public void onItemLongClick(SmsModel sms) {
                // Placeholder for multi-select in MainActivity if needed
            }

            @Override
            public void onPinClick(SmsModel sms, boolean isPinned) {
                sms.setPinned(isPinned);
                java.util.Set<String> pinnedIds = SettingsManager.getPinnedMessages(MainActivity.this);
                if (isPinned) {
                    pinnedIds.add(sms.getId());
                } else {
                    pinnedIds.remove(sms.getId());
                }
                SettingsManager.savePinnedMessages(MainActivity.this, pinnedIds);
                performSearch(); // Refresh list to reflect pinned status and reorder
            }

            private void performDelete(SmsModel sms) {
                SmsRepository.deleteSms(MainActivity.this, sms.getId(), () -> {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Message deleted", Toast.LENGTH_SHORT).show();
                        loadSms();
                    });
                });
            }
        });
        rvSmsList.setAdapter(smsAdapter);

        btnSearch.setOnClickListener(v -> performSearch());

        // Real-time filtering as well for better UX
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                performSearch();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        rgViewGroup.setOnCheckedChangeListener((group, checkedId) -> {
            isGroupView = (checkedId == R.id.rbGroup);
            performSearch();
        });
        rgViewGroup.check(R.id.rbGroup);

        btnRequestPermission.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, "android.permission.WRITE_SMS") != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                if ((!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_SMS) ||
                     !ActivityCompat.shouldShowRequestPermissionRationale(this, "android.permission.WRITE_SMS") ||
                     !ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.SEND_SMS) ||
                     !ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_CONTACTS)) &&
                        getSharedPreferences("prefs", MODE_PRIVATE).getBoolean("permission_requested", false)) {
                    // Open Settings
                    Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                } else {
                    checkPermissions();
                }
            }
        });

        findViewById(R.id.fabCompose).setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, ComposeSmsActivity.class));
        });

        View cvPinnedMessages = findViewById(R.id.cvPinnedMessages);
        cvPinnedMessages.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, PinnedMessagesActivity.class));
        });
    }

    private void checkPermissions() {
        boolean hasReadSms = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED;
        boolean hasWriteSms = ContextCompat.checkSelfPermission(this, "android.permission.WRITE_SMS") == PackageManager.PERMISSION_GRANTED;
        boolean hasSendSms = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
        boolean hasReadContacts = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
        boolean hasStorage = true;
        boolean hasNotification = true;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            hasStorage = android.os.Environment.isExternalStorageManager();
        } else {
            hasStorage = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            hasNotification = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }

        if (!hasReadSms || !hasWriteSms || !hasSendSms || !hasReadContacts || !hasStorage || !hasNotification) {
            getSharedPreferences("prefs", MODE_PRIVATE).edit().putBoolean("permission_requested", true).apply();
            
            List<String> permissions = new ArrayList<>();
            if (!hasReadSms) permissions.add(Manifest.permission.READ_SMS);
            if (!hasWriteSms) permissions.add("android.permission.WRITE_SMS");
            if (!hasSendSms) permissions.add(Manifest.permission.SEND_SMS);
            if (!hasReadContacts) permissions.add(Manifest.permission.READ_CONTACTS);
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                if (!hasNotification) permissions.add(Manifest.permission.POST_NOTIFICATIONS);
            }
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                if (!hasStorage) {
                    try {
                        Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                        intent.addCategory("android.intent.category.DEFAULT");
                        intent.setData(Uri.parse(String.format("package:%s", getPackageName())));
                        startActivity(intent);
                    } catch (Exception e) {
                        Intent intent = new Intent();
                        intent.setAction(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                        startActivity(intent);
                    }
                }
            } else {
                if (!hasStorage) {
                    permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                    permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
                }
            }

            if (!permissions.isEmpty()) {
                ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), PERMISSION_REQUEST_READ_SMS);
            }
        } else {
            loadSms();
        }
    }

    private void loadSms() {
        SmsRepository.getAllSms(getContentResolver(), smsList -> {
            runOnUiThread(() -> {
                java.util.Set<String> pinnedIds = SettingsManager.getPinnedMessages(this);
                for (SmsModel sms : smsList) {
                    if (pinnedIds.contains(sms.getId())) {
                        sms.setPinned(true);
                    }
                }
                allSmsList = smsList;
                performSearch(); // Re-apply search filter if any, which also calls updateVisibility
                SmsRepository.updateAppStateBadge(this);
            });
        });
    }

    private void performSearch() {
        String query = etSearch.getText().toString().toLowerCase().trim();
        List<SmsModel> filteredList = new ArrayList<>();

        if (query.isEmpty()) {
            filteredList.addAll(allSmsList);
        } else {
            for (SmsModel sms : allSmsList) {
                String sender = sms.getSender() != null ? sms.getSender().toLowerCase() : "";
                String body = sms.getBody() != null ? sms.getBody().toLowerCase() : "";
                String contact = sms.getContactName() != null ? sms.getContactName().toLowerCase() : "";
                
                if (sender.contains(query) || body.contains(query) || contact.contains(query)) {
                    filteredList.add(sms);
                }
            }
        }

        if (isGroupView) {
            Map<String, SmsModel> latestMessages = new LinkedHashMap<>();
            Map<String, Integer> groupCounts = new HashMap<>();
            
            for (SmsModel sms : filteredList) {
                String groupKey = getGroupKey(sms);
                
                // Count messages in this group
                int count = groupCounts.containsKey(groupKey) ? groupCounts.get(groupKey) : 0;
                groupCounts.put(groupKey, count + 1);
                
                if (!latestMessages.containsKey(groupKey)) {
                    latestMessages.put(groupKey, sms);
                }
            }
            
            // Apply counts to the representative models
            for (Map.Entry<String, SmsModel> entry : latestMessages.entrySet()) {
                SmsModel model = entry.getValue();
                model.setGroupCount(groupCounts.get(entry.getKey()));
            }
            
            filteredList = new ArrayList<>(latestMessages.values());
        }

        smsAdapter.setShowTrimmedSender(isGroupView);
        smsAdapter.updateList(filteredList);
        updateVisibility();
    }

    public static String getGroupKey(SmsModel sms) {
        if (sms == null) return "";
        if (sms.getContactName() != null && !sms.getContactName().isEmpty()) {
            return sms.getContactName();
        }
        return extractSenderName(sms.getSender());
    }

    public static String extractSenderName(String originalSender) {
        if (originalSender == null) return "";
        String sender = originalSender;

        // If the third character in sender name is a "-", then ignore the first three characters
        if (sender.length() >= 3 && sender.charAt(2) == '-') {
            sender = sender.substring(3);
        }

        // Then if the second last character is "-", then ignore the last two characters
        if (sender.length() >= 2 && sender.charAt(sender.length() - 2) == '-') {
            sender = sender.substring(0, sender.length() - 2);
        }

        return sender;
    }

    private void updateVisibility() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, "android.permission.WRITE_SMS") != PackageManager.PERMISSION_GRANTED) {
            emptyStateContainer.setVisibility(View.VISIBLE);
            tvEmptyState.setText("SMS permission is required to view messages.");
            btnRequestPermission.setVisibility(View.VISIBLE);
            rvSmsList.setVisibility(View.GONE);
            
            if ((!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_SMS) ||
                 !ActivityCompat.shouldShowRequestPermissionRationale(this, "android.permission.WRITE_SMS")) &&
                    getSharedPreferences("prefs", MODE_PRIVATE).getBoolean("permission_requested", false)) {
                btnRequestPermission.setText("Open Settings");
            } else {
                btnRequestPermission.setText("Grant Permission");
            }
        } else if (smsAdapter.getItemCount() == 0) {
            emptyStateContainer.setVisibility(View.VISIBLE);
            tvEmptyState.setText("No messages found");
            btnRequestPermission.setVisibility(View.GONE);
            rvSmsList.setVisibility(View.GONE);
        } else {
            emptyStateContainer.setVisibility(View.GONE);
            rvSmsList.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_READ_SMS) {
            boolean readSmsGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED;
            boolean writeSmsGranted = ContextCompat.checkSelfPermission(this, "android.permission.WRITE_SMS") == PackageManager.PERMISSION_GRANTED;
            boolean readContactsGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
            boolean storageGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;

            if (readSmsGranted && writeSmsGranted) {
                loadSms();
            } else {
                Toast.makeText(this, "SMS permissions denied. Cannot load messages.", Toast.LENGTH_SHORT).show();
                updateVisibility();
            }

            if (!storageGranted) {
                Toast.makeText(this, "Storage permission denied. Export features will be limited.", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
