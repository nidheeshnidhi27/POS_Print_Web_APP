package com.joopos.posprint;

import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.net.URLEncoder;

public class PrintRetryWorker extends Worker {
    public PrintRetryWorker(@NonNull android.content.Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String jobId = getInputData().getString("job_id");
        String baseUrl = getInputData().getString("base_url");
        if (baseUrl == null || baseUrl.isEmpty()) {
            return Result.failure();
        }
        try {
            PrintJobDao dao = PrintQueueDatabase.get(getApplicationContext()).dao();
            if (jobId != null) {
                PrintJobEntity job = dao.get(jobId);
                if (job == null || job.status == 1) {
                    return Result.success();
                }
            }
        } catch (Exception ignored) {
        }

        try {
            String encoded = URLEncoder.encode(baseUrl, "UTF-8");
            Uri deep = Uri.parse("app://open.my.app?base_url=" + encoded + (jobId != null ? "&job_id=" + jobId : ""));
            Intent svc = new Intent(getApplicationContext(), BackgroundPrintService.class);
            svc.setData(deep);
            getApplicationContext().startService(svc);
        } catch (Exception e) {
            return Result.retry();
        }

        try {
            PrintJobDao dao = PrintQueueDatabase.get(getApplicationContext()).dao();
            if (jobId != null) {
                dao.incAttempts(jobId);
            }
        } catch (Exception ignored) {}
        return Result.retry();
    }
}
