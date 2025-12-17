package com.example.posprint.notification;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import androidx.core.app.NotificationCompat;

import com.example.posprint.MainActivity;
import com.example.posprint.R;

public class NotificationUtils {

    public static void showPrinterError(Context context, String message) {

        // Optional: When user taps, open app (or do nothing)
        Intent tapIntent = new Intent(context, MainActivity.class);
        tapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                tapIntent,
                PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_pos)  // your app icon
                        .setContentTitle("Printer Error")
                        .setContentText(message)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)   // heads-up trigger
                        .setCategory(NotificationCompat.CATEGORY_ERROR)  // popup type
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent);

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        manager.notify(101, builder.build());
    }
}
