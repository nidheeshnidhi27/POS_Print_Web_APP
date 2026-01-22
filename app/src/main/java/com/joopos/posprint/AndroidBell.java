package com.joopos.posprint;

import android.content.Context;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.webkit.JavascriptInterface;

public class AndroidBell {
    private final Context context;

    public AndroidBell(Context context) {
        this.context = context.getApplicationContext();
    }

    @JavascriptInterface
    public void ring() {
        Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        if (uri == null) return;
        Ringtone ringtone = RingtoneManager.getRingtone(context, uri);
        if (ringtone == null) return;
        try {
            ringtone.play();
        } catch (Throwable ignored) {
        }
    }
}
