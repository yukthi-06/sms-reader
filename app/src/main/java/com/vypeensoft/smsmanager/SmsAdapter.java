package com.vypeensoft.smsmanager;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class SmsAdapter extends RecyclerView.Adapter<SmsAdapter.SmsViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(SmsModel sms);
        void onDeleteClick(SmsModel sms);
        void onItemLongClick(SmsModel sms);
    }

    private List<SmsModel> smsList;
    private OnItemClickListener listener;
    private boolean showTrimmedSender = false;
    private boolean hideSender = false;
    private java.util.Set<String> selectedIds = new java.util.HashSet<>();
    private boolean isSelectionMode = false;
    private boolean isExpandedAll = false;

    public SmsAdapter(List<SmsModel> smsList, OnItemClickListener listener) {
        this.smsList = smsList;
        this.listener = listener;
    }

    public void setSelectionMode(boolean selectionMode) {
        this.isSelectionMode = selectionMode;
        if (!selectionMode) {
            selectedIds.clear();
        }
        notifyDataSetChanged();
    }

    public boolean isSelectionMode() {
        return isSelectionMode;
    }

    public void setExpandedAll(boolean expandedAll) {
        this.isExpandedAll = expandedAll;
        notifyDataSetChanged();
    }

    public boolean isExpandedAll() {
        return isExpandedAll;
    }

    public void toggleSelection(String id) {
        if (selectedIds.contains(id)) {
            selectedIds.remove(id);
        } else {
            selectedIds.add(id);
        }
        notifyDataSetChanged();
    }

    public void selectAll() {
        for (SmsModel sms : smsList) {
            selectedIds.add(sms.getId());
        }
        notifyDataSetChanged();
    }

    public void clearSelection() {
        selectedIds.clear();
        notifyDataSetChanged();
    }

    public java.util.Set<String> getSelectedIds() {
        return selectedIds;
    }

    public int getSelectedCount() {
        return selectedIds.size();
    }

    public void setShowTrimmedSender(boolean showTrimmedSender) {
        this.showTrimmedSender = showTrimmedSender;
        // Don't call notifyDataSetChanged() here, because updateList is usually called right after
    }

    public void setHideSender(boolean hideSender) {
        this.hideSender = hideSender;
    }

    public void updateList(List<SmsModel> newList) {
        this.smsList = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SmsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_sms, parent, false);
        return new SmsViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SmsViewHolder holder, int position) {
        SmsModel sms = smsList.get(position);
        
        String senderText = sms.getSender();
        String contactName = sms.getContactName();
        boolean isFromContacts = contactName != null && !contactName.isEmpty();
        
        if (hideSender) {
            holder.tvSender.setVisibility(View.VISIBLE);
            String html = sms.isSent() ? "<b><font color='#3498DB'>📤 Sent</font></b>" : "<b><font color='#2ECC71'>📩 Received</font></b>";
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                holder.tvSender.setText(android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY));
            } else {
                holder.tvSender.setText(android.text.Html.fromHtml(html));
            }
        } else {
            holder.tvSender.setVisibility(View.VISIBLE);
            
            String prefix = sms.isSent() ? "To: " : "";
            String suffix = sms.isSent() ? " <small><font color='#3498DB'>(Sent)</font></small>" : "";
            String countStr = sms.getGroupCount() > 0 ? " <font color='#7F8C8D'>(" + sms.getGroupCount() + ")</font>" : "";

            if (isFromContacts) {
                String html = "<b>" + prefix + contactName + "</b>" + countStr + suffix + "<br/><small><font color='#888888'>" + senderText + "</font></small>";
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    holder.tvSender.setText(android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY));
                } else {
                    holder.tvSender.setText(android.text.Html.fromHtml(html));
                }
            } else {
                String displayName = showTrimmedSender ? MainActivity.extractSenderName(senderText) : senderText;
                String html = "<b>" + prefix + displayName + "</b>" + countStr + suffix;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    holder.tvSender.setText(android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY));
                } else {
                    holder.tvSender.setText(android.text.Html.fromHtml(html));
                }
            }
        }
        
        holder.tvTimestamp.setText(sms.getTimestamp());
        holder.tvBody.setText(sms.getBody());
        
        if (isExpandedAll) {
            holder.tvBody.setMaxLines(Integer.MAX_VALUE);
            holder.tvBody.setEllipsize(null);
        } else {
            int previewLines = SettingsManager.getPreviewLines(holder.itemView.getContext());
            holder.tvBody.setMaxLines(previewLines);
            holder.tvBody.setEllipsize(android.text.TextUtils.TruncateAt.END);
        }
        
        int fontSize = SettingsManager.getFontSize(holder.itemView.getContext());
        holder.tvSender.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, fontSize);
        holder.tvBody.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, fontSize);
        holder.tvTimestamp.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, Math.max(10, fontSize - 4));
        
        if (isSelectionMode && selectedIds.contains(sms.getId())) {
            ((com.google.android.material.card.MaterialCardView) holder.itemView).setCardBackgroundColor(android.graphics.Color.parseColor("#D1E3FF")); // Darker blue for selected
            ((com.google.android.material.card.MaterialCardView) holder.itemView).setStrokeColor(android.graphics.Color.parseColor("#3498DB"));
            ((com.google.android.material.card.MaterialCardView) holder.itemView).setStrokeWidth(4);
        } else if (!sms.isRead()) {
            holder.tvSender.setTypeface(null, android.graphics.Typeface.BOLD);
            holder.tvBody.setTypeface(null, android.graphics.Typeface.BOLD);
            holder.tvSender.setTextColor(android.graphics.Color.parseColor("#000000"));
            holder.tvBody.setTextColor(android.graphics.Color.parseColor("#111111"));
            ((com.google.android.material.card.MaterialCardView) holder.itemView).setCardBackgroundColor(android.graphics.Color.parseColor("#E8F0FE")); // Light blue for unread
            ((com.google.android.material.card.MaterialCardView) holder.itemView).setStrokeColor(android.graphics.Color.parseColor("#A1C9F2")); // Darker blue border for unread
            ((com.google.android.material.card.MaterialCardView) holder.itemView).setStrokeWidth(3); // Slightly thicker border for unread
        } else {
            holder.tvSender.setTypeface(null, android.graphics.Typeface.NORMAL);
            holder.tvBody.setTypeface(null, android.graphics.Typeface.NORMAL);
            holder.tvSender.setTextColor(android.graphics.Color.parseColor("#333333"));
            holder.tvBody.setTextColor(android.graphics.Color.parseColor("#555555"));
            ((com.google.android.material.card.MaterialCardView) holder.itemView).setCardBackgroundColor(android.graphics.Color.WHITE); // White background for read
            ((com.google.android.material.card.MaterialCardView) holder.itemView).setStrokeColor(android.graphics.Color.parseColor("#DDDDDD")); // Default border
            ((com.google.android.material.card.MaterialCardView) holder.itemView).setStrokeWidth(1); // Default border width
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(sms);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onItemLongClick(sms);
                return true;
            }
            return false;
        });

        holder.btnDelete.setVisibility(isSelectionMode ? View.GONE : View.VISIBLE);
        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeleteClick(sms);
            }
        });
    }

    @Override
    public int getItemCount() {
        return smsList != null ? smsList.size() : 0;
    }

    public static class SmsViewHolder extends RecyclerView.ViewHolder {
        TextView tvSender, tvTimestamp, tvBody;
        android.widget.ImageButton btnDelete;

        public SmsViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSender = itemView.findViewById(R.id.tvSender);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            tvBody = itemView.findViewById(R.id.tvBody);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
