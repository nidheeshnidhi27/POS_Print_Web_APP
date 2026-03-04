package com.joopos.posprint;

import android.content.Context;
import android.util.Log;

import androidx.work.BackoffPolicy;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class PrintQueueRepository {
    private final Context context;
    private final PrintJobDao dao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public PrintQueueRepository(Context context) {
        this.context = context.getApplicationContext();
        this.dao = PrintQueueDatabase.get(this.context).dao();
    }

    public String enqueue(String baseUrl, String jobId, String printType) {
        String normalizedType = printType == null ? "" : printType.trim().toLowerCase(java.util.Locale.ROOT);
        try {
            String ord = extractOrderId(baseUrl);
            PrintJobEntity existing = PrintQueueDatabase.get(context).dao().findPendingByOrderAndType(ord, normalizedType);
            if (existing != null) {
                Log.d("QueueRepo", "Duplicate pending found for order=" + ord + " type=" + normalizedType + " → reuse jobId=" + existing.jobId);
                enqueueWork(existing.jobId, baseUrl);
                return existing.jobId;
            }
            existing = PrintQueueDatabase.get(context).dao().findPendingByBaseUrl(baseUrl);
            if (existing != null) {
                Log.d("QueueRepo", "Duplicate pending found for url=" + baseUrl + " → reuse jobId=" + existing.jobId);
                enqueueWork(existing.jobId, baseUrl);
                return existing.jobId;
            }
        } catch (Exception ignored) {}
        final String id = (jobId == null || jobId.isEmpty()) ? UUID.randomUUID().toString() : jobId;
        PrintJobEntity job = new PrintJobEntity();
        job.jobId = id;
        job.baseUrl = baseUrl;
        job.orderId = extractOrderId(baseUrl);
        job.printType = normalizedType;
        job.printerIp = null;
        job.attempts = 0;
        job.status = 0;
        job.createdAt = System.currentTimeMillis();
        executor.execute(() -> {
            dao.insert(job);
            Log.d("QueueRepo", "Enqueued job id=" + id + " url=" + baseUrl);
            notifyChange();
        });
        enqueueWork(id, baseUrl);
        return id;
    }

    public String track(String baseUrl, String jobId, String printType) {
        String normalizedType = printType == null ? "" : printType.trim().toLowerCase(java.util.Locale.ROOT);
        try {
            String ord = extractOrderId(baseUrl);
            PrintJobEntity existing = PrintQueueDatabase.get(context).dao().findPendingByOrderAndType(ord, normalizedType);
            if (existing != null) {
                Log.d("QueueRepo", "Duplicate pending found for order=" + ord + " type=" + normalizedType + " → reuse jobId=" + existing.jobId + " (track only)");
                return existing.jobId;
            }
            existing = PrintQueueDatabase.get(context).dao().findPendingByBaseUrl(baseUrl);
            if (existing != null) {
                Log.d("QueueRepo", "Duplicate pending found for url=" + baseUrl + " → reuse jobId=" + existing.jobId + " (track only)");
                return existing.jobId;
            }
        } catch (Exception ignored) {}
        final String id = (jobId == null || jobId.isEmpty()) ? UUID.randomUUID().toString() : jobId;
        PrintJobEntity job = new PrintJobEntity();
        job.jobId = id;
        job.baseUrl = baseUrl;
        job.orderId = extractOrderId(baseUrl);
        job.printType = normalizedType;
        job.printerIp = null;
        job.attempts = 0;
        job.status = 0;
        job.createdAt = System.currentTimeMillis();
        executor.execute(() -> {
            dao.insert(job);
            Log.d("QueueRepo", "Tracking job id=" + id + " url=" + baseUrl + " (no schedule)");
            notifyChange();
        });
        return id;
    }

    public void enqueueWork(String jobId, String baseUrl) {
        Data data = new Data.Builder()
                .putString("job_id", jobId)
                .putString("base_url", baseUrl)
                .build();
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(PrintRetryWorker.class)
                .setInputData(data)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build();
        Log.d("QueueRepo", "Scheduling retry for job id=" + jobId);
        WorkManager.getInstance(context)
                .enqueueUniqueWork("print-" + jobId, ExistingWorkPolicy.REPLACE, req);
    }

    public void markSuccess(String jobId) {
        executor.execute(() -> {
            dao.markSuccess(jobId);
            dao.delete(jobId);
            try {
                WorkManager.getInstance(context).cancelUniqueWork("print-" + jobId);
            } catch (Exception ignored) {}
            Log.d("QueueRepo", "Marked success and deleted job id=" + jobId);
            notifyChange();
        });
    }

    public void updateMeta(String jobId, String orderId, String printerIp) {
        executor.execute(() -> {
            dao.updateMeta(jobId, orderId, printerIp);
            Log.d("QueueRepo", "Updated meta for job id=" + jobId + " ip=" + printerIp);
            notifyChange();
        });
    }

    private String extractOrderId(String baseUrl) {
        try {
            int qIdx = baseUrl.indexOf('?');
            String query = qIdx >= 0 ? baseUrl.substring(qIdx + 1) : baseUrl;
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    String k = pair.substring(0, eq).toLowerCase();
                    String v = pair.substring(eq + 1);
                    if (k.equals("orderid") || k.equals("order_no") || k.equals("id")) {
                        return v;
                    }
                }
            }
        } catch (Exception ignored) {}
        return "";
    }

    private void notifyChange() {
        try {
            android.content.Intent i = new android.content.Intent("com.joopos.posprint.QUEUE_CHANGED");
            context.sendBroadcast(i);
            Log.d("QueueRepo", "Broadcast QUEUE_CHANGED");
        } catch (Exception ignored) {}
    }
}
