package com.vypeensoft.smsmanager;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class PinnedMessagesActivity extends AppCompatActivity {
    private RecyclerView rvPinnedSmsList;
    private SmsAdapter adapter;
    private LinearLayout emptyStateContainer;
    private TextView tvEmptyState;

    private final android.content.BroadcastReceiver smsRefreshReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, android.content.Intent intent) {
            loadPinnedMessages();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pinned_messages);

        setTitle("Pinned Messages");
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        emptyStateContainer = findViewById(R.id.emptyStateContainer);
        tvEmptyState = findViewById(R.id.tvEmptyState);

        rvPinnedSmsList = findViewById(R.id.rvPinnedSmsList);
        rvPinnedSmsList.setLayoutManager(new LinearLayoutManager(this));

        adapter = new SmsAdapter(new ArrayList<>(), new SmsAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(SmsModel sms) {
                Intent intent = new Intent(PinnedMessagesActivity.this, MessageDetailActivity.class);
                intent.putExtra("sms_data", sms);
                startActivity(intent);
            }

            @Override
            public void onDeleteClick(SmsModel sms) {
                boolean confirmDelete = SettingsManager.isConfirmDelete(PinnedMessagesActivity.this);
                if (confirmDelete) {
                    new androidx.appcompat.app.AlertDialog.Builder(PinnedMessagesActivity.this)
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
                // Not supported for pinned messages view currently
            }

            @Override
            public void onPinClick(SmsModel sms, boolean isPinned) {
                sms.setPinned(isPinned);
                java.util.Set<String> pinnedIds = SettingsManager.getPinnedMessages(PinnedMessagesActivity.this);
                if (isPinned) {
                    pinnedIds.add(sms.getId());
                } else {
                    pinnedIds.remove(sms.getId());
                }
                SettingsManager.savePinnedMessages(PinnedMessagesActivity.this, pinnedIds);
                loadPinnedMessages(); // Refresh to remove unpinned items
            }

            private void performDelete(SmsModel sms) {
                SmsRepository.deleteSms(PinnedMessagesActivity.this, sms.getId(), () -> {
                    runOnUiThread(() -> {
                        Toast.makeText(PinnedMessagesActivity.this, "Message deleted", Toast.LENGTH_SHORT).show();
                        loadPinnedMessages();
                    });
                });
            }
        });

        rvPinnedSmsList.setAdapter(adapter);
    }

    private void loadPinnedMessages() {
        SmsRepository.getAllSms(getContentResolver(), smsList -> {
            java.util.Set<String> pinnedIds = SettingsManager.getPinnedMessages(this);
            List<SmsModel> pinnedSms = new ArrayList<>();
            for (SmsModel sms : smsList) {
                if (pinnedIds.contains(sms.getId())) {
                    sms.setPinned(true);
                    pinnedSms.add(sms);
                }
            }

            runOnUiThread(() -> {
                adapter.updateList(pinnedSms);
                if (pinnedSms.isEmpty()) {
                    emptyStateContainer.setVisibility(View.VISIBLE);
                    rvPinnedSmsList.setVisibility(View.GONE);
                } else {
                    emptyStateContainer.setVisibility(View.GONE);
                    rvPinnedSmsList.setVisibility(View.VISIBLE);
                }
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

    @Override
    protected void onResume() {
        super.onResume();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(smsRefreshReceiver, new android.content.IntentFilter("com.vypeensoft.smsmanager.REFRESH_SMS"), android.content.Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(smsRefreshReceiver, new android.content.IntentFilter("com.vypeensoft.smsmanager.REFRESH_SMS"));
        }
        loadPinnedMessages();
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
}
