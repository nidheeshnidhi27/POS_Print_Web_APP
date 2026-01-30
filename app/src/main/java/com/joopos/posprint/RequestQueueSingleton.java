package com.joopos.posprint;

import android.content.Context;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

public class RequestQueueSingleton {
    private static RequestQueue instance;

    public static synchronized RequestQueue get(Context context) {
        if (instance == null) {
            instance = Volley.newRequestQueue(context.getApplicationContext());
        }
        return instance;
    }
}
