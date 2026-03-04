package com.joopos.posprint;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.net.URLEncoder;
import java.util.List;

public class PrintQueueActivity extends AppCompatActivity {
    private PrintJobDao dao;
    private RecyclerView recyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_queue);
        dao = PrintQueueDatabase.get(getApplicationContext()).dao();
        recyclerView = findViewById(R.id.queueList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        refreshList();
        FloatingActionButton fab = findViewById(R.id.refreshBtn);
        fab.setOnClickListener(v -> refreshList());
    }

    private void refreshList() {
        new Thread(() -> {
            List<PrintJobEntity> jobs;
            try {
                jobs = dao.listPending();
            } catch (Exception e) {
                jobs = java.util.Collections.emptyList();
            }
            List<PrintJobEntity> finalJobs = jobs;
            runOnUiThread(() -> {
                Log.d("QueueActivity", "Loaded items=" + finalJobs.size());
                recyclerView.setAdapter(new QueueAdapter(finalJobs));
            });
        }).start();
    }

    class QueueAdapter extends RecyclerView.Adapter<QueueAdapter.Holder> {
        private final List<PrintJobEntity> items;
        QueueAdapter(List<PrintJobEntity> items) { this.items = items; }
        class Holder extends RecyclerView.ViewHolder {
            TextView title;
            TextView subtitle;
            Holder(View v) {
                super(v);
                title = v.findViewById(R.id.title);
                subtitle = v.findViewById(R.id.subtitle);
            }
        }
        @Override
        public Holder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            View v = getLayoutInflater().inflate(R.layout.item_queue, parent, false);
            return new Holder(v);
        }
        @Override
        public void onBindViewHolder(Holder h, int pos) {
            PrintJobEntity job = items.get(pos);
            String ord = job.orderId != null ? job.orderId : "";
            String ip = job.printerIp != null ? job.printerIp : "";
            h.title.setText("Order: " + ord);
            String type = job.printType != null && !job.printType.isEmpty() ? job.printType : "(unknown)";
            if (!"(unknown)".equals(type)) {
                type = type.replace('_', ' ');
                type = Character.toUpperCase(type.charAt(0)) + type.substring(1);
            }
            h.subtitle.setText("Type: " + type + "   Printer: " + (ip.isEmpty() ? "(auto)" : ip));
        }
        @Override
        public int getItemCount() { return items.size(); }
    }
}
