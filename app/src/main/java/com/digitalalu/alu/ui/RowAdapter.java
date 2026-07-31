package com.digitalalu.alu.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.digitalalu.alu.R;
import com.digitalalu.alu.calc.Settings;
import com.digitalalu.alu.model.WindowItem;

import java.util.List;

public class RowAdapter extends RecyclerView.Adapter<RowAdapter.VH> {

    public interface Listener {
        void onChanged();
        void onDelete(WindowItem it);
        void onOpen(WindowItem it);
    }

    private final List<WindowItem> items;
    private final Settings st;
    private final Listener listener;

    public RowAdapter(List<WindowItem> items, Settings st, Listener listener) {
        this.items = items;
        this.st = st;
        this.listener = listener;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.dlg_row, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        WindowItem it = items.get(position);
        h.tv.setText(it.toString());
        h.itemView.setOnClickListener(v -> listener.onOpen(it));
        h.itemView.setOnLongClickListener(v -> {
            listener.onDelete(it);
            return true;
        });
    }

    @Override public int getItemCount() { return items.size(); }

    public static class VH extends RecyclerView.ViewHolder {
        TextView tv;
        public VH(View itemView) {
            super(itemView);
            tv = itemView.findViewById(android.R.id.text1);
        }
    }
}
