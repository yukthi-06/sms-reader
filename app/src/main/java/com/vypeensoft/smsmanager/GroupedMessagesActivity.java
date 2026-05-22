package com.vypeensoft.smsmanager;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class GroupedMessagesActivity extends AppCompatActivity {
    private RecyclerView rvGroupedSmsList;
    private SmsAdapter adapter;
    private String groupKey;
    private ActionMode actionMode;
    private String senderNumber;
    private List<SmsModel> groupedSmsList = new ArrayList<>();
    private final android.content.BroadcastReceiver smsRefreshReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, android.content.Intent intent) {
            loadGroupedMessages();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_grouped_messages);
        
        String groupDisplayName = getIntent().getStringExtra("group_display_name");
        groupKey = getIntent().getStringExtra("group_key");
        senderNumber = getIntent().getStringExtra("sender_number");
        setTitle(groupDisplayName != null ? groupDisplayName : "Messages");

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        
        rvGroupedSmsList = findViewById(R.id.rvGroupedSmsList);
        rvGroupedSmsList.setLayoutManager(new LinearLayoutManager(this));
        
        adapter = new SmsAdapter(new ArrayList<>(), new SmsAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(SmsModel sms) {
                if (adapter.isSelectionMode()) {
                    toggleSelection(sms.getId());
                } else {
                    Intent intent = new Intent(GroupedMessagesActivity.this, MessageDetailActivity.class);
                    intent.putExtra("sms_data", sms);
                    startActivity(intent);
                }
            }

            @Override
            public void onDeleteClick(SmsModel sms) {
                boolean confirmDelete = SettingsManager.isConfirmDelete(GroupedMessagesActivity.this);

                if (confirmDelete) {
                    new androidx.appcompat.app.AlertDialog.Builder(GroupedMessagesActivity.this)
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
                if (!adapter.isSelectionMode()) {
                    startSelectionMode();
                }
                toggleSelection(sms.getId());
            }

            private void performDelete(SmsModel sms) {
                SmsRepository.deleteSms(GroupedMessagesActivity.this, sms.getId(), () -> {
                    runOnUiThread(() -> {
                        android.widget.Toast.makeText(GroupedMessagesActivity.this, "Message deleted", android.widget.Toast.LENGTH_SHORT).show();
                        loadGroupedMessages();
                    });
                });
            }
        });
        adapter.setHideSender(true);
        rvGroupedSmsList.setAdapter(adapter);
        
        android.widget.EditText etReplyMessage = findViewById(R.id.etReplyMessage);
        android.widget.ImageButton btnSendReply = findViewById(R.id.btnSendReply);
        
        btnSendReply.setOnClickListener(v -> {
            String message = etReplyMessage.getText().toString().trim();
            if (message.isEmpty()) {
                return;
            }
            
            String recipient = senderNumber;
            if (recipient == null || recipient.isEmpty()) {
                if (groupedSmsList != null && !groupedSmsList.isEmpty()) {
                    for (SmsModel sms : groupedSmsList) {
                        if (!sms.isSent() && sms.getSender() != null && !sms.getSender().isEmpty()) {
                            recipient = sms.getSender();
                            break;
                        }
                    }
                    if (recipient == null || recipient.isEmpty()) {
                        recipient = groupedSmsList.get(0).getSender();
                    }
                }
            }
            
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
                    loadGroupedMessages();
                });
            }, () -> {
                runOnUiThread(() -> {
                    btnSendReply.setEnabled(true);
                    android.widget.Toast.makeText(this, "Failed to send message", android.widget.Toast.LENGTH_SHORT).show();
                });
            });
        });

        loadGroupedMessages();
    }

    private void startSelectionMode() {
        adapter.setSelectionMode(true);
        actionMode = startSupportActionMode(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                mode.getMenuInflater().inflate(R.menu.menu_selection, menu);
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                if (item.getItemId() == R.id.action_select_all) {
                    adapter.selectAll();
                    updateActionModeTitle(mode);
                    return true;
                } else if (item.getItemId() == R.id.action_delete) {
                    deleteSelectedMessages();
                    return true;
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                adapter.setSelectionMode(false);
                actionMode = null;
            }
        });
        updateActionModeTitle(actionMode);
    }

    private void toggleSelection(String id) {
        adapter.toggleSelection(id);
        if (adapter.getSelectedCount() == 0) {
            if (actionMode != null) {
                actionMode.finish();
            }
        } else {
            updateActionModeTitle(actionMode);
        }
    }

    private void updateActionModeTitle(ActionMode mode) {
        if (mode != null) {
            mode.setTitle(adapter.getSelectedCount() + " selected");
        }
    }

    private void deleteSelectedMessages() {
        boolean confirmDelete = SettingsManager.isConfirmDelete(this);
        int count = adapter.getSelectedCount();

        if (confirmDelete) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Delete Messages")
                .setMessage("Are you sure you want to delete " + count + " messages?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    List<String> idsToDelete = new ArrayList<>(adapter.getSelectedIds());
                    performBulkDelete(idsToDelete);
                })
                .setNegativeButton("Cancel", null)
                .show();
        } else {
            List<String> idsToDelete = new ArrayList<>(adapter.getSelectedIds());
            performBulkDelete(idsToDelete);
        }
    }

    private void performBulkDelete(List<String> ids) {
        new Thread(() -> {
            for (String id : ids) {
                // We don't use the callback here for each one, just once at the end
                SmsRepository.deleteSms(this, id, null);
            }
            runOnUiThread(() -> {
                android.widget.Toast.makeText(this, ids.size() + " messages deleted", android.widget.Toast.LENGTH_SHORT).show();
                if (actionMode != null) actionMode.finish();
                loadGroupedMessages();
            });
        }).start();
    }
    
    private void loadGroupedMessages() {
        String searchQuery = getIntent().getStringExtra("search_query");
        if (searchQuery == null) searchQuery = "";
        
        final String finalSearchQuery = searchQuery;

        SmsRepository.getAllSms(getContentResolver(), smsList -> {
            List<SmsModel> filtered = new ArrayList<>();
            for (SmsModel sms : smsList) {
                String currentGroupKey = MainActivity.getGroupKey(sms);
                String currentExtractedName = MainActivity.extractSenderName(sms.getSender());
                
                if (currentGroupKey.equals(groupKey) || currentExtractedName.equals(groupKey)) {
                    if (finalSearchQuery.isEmpty() || 
                        sms.getSender().toLowerCase().contains(finalSearchQuery) || 
                        sms.getBody().toLowerCase().contains(finalSearchQuery) ||
                        (sms.getContactName() != null && sms.getContactName().toLowerCase().contains(finalSearchQuery))) {
                        filtered.add(sms);
                    }
                }
            }
            runOnUiThread(() -> {
                this.groupedSmsList = filtered;
                adapter.updateList(filtered);
                if (filtered != null && !filtered.isEmpty()) {
                    if (senderNumber == null || senderNumber.isEmpty()) {
                        for (SmsModel sms : filtered) {
                            if (!sms.isSent() && sms.getSender() != null && !sms.getSender().isEmpty()) {
                                senderNumber = sms.getSender();
                                break;
                            }
                        }
                        if (senderNumber == null || senderNumber.isEmpty()) {
                            senderNumber = filtered.get(0).getSender();
                        }
                    }
                    rvGroupedSmsList.scrollToPosition(filtered.size() - 1);
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
        registerReceiver(smsRefreshReceiver, new android.content.IntentFilter("com.vypeensoft.smsmanager.REFRESH_SMS"));
        loadGroupedMessages();
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
