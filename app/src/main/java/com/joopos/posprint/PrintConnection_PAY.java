package com.joopos.posprint;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.joopos.posprint.notification.NotificationUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PrintConnection_PAY {

    public interface Callback {
        void onComplete(boolean success, String message);
    }

    private static final String TAG = "PrintConnection_PAY";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Context context;

    public PrintConnection_PAY(Context context) {
        this.context = context.getApplicationContext();
    }

    public void printWithStatusCheck(String ip, int port, byte[] printableData, Callback callback) {
        executor.execute(() -> {

            boolean success = false;
            String message = "Unknown error";
            Socket socket = null;

            try {
                socket = new Socket();
                socket.connect(new InetSocketAddress(ip, port), 5000);
                socket.setSoTimeout(600);

                InputStream input = socket.getInputStream();
                OutputStream output = socket.getOutputStream();

                // 1) Drain old bytes
                drainWithTimeout(input);

                PrinterStatusHelper.Status st = PrinterStatusHelper.queryBasic(ip, port, 1000, 1000);
                if (!st.online) {
                    message = "Printer offline (PAY)";
                    showNotification(message);
                    post(callback, false, message);
                    safeClose(socket);
                    return;
                }
                if (!st.paperOk) {
                    message = "Paper " + st.paper + " (PAY)";
                    showNotification(message);
                    post(callback, false, message);
                    safeClose(socket);
                    return;
                }
                if (st.busy) {
                    try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                    st = PrinterStatusHelper.queryBasic(ip, port, 1000, 1000);
                    if (st.busy) {
                        message = "Printer " + st.status + " (PAY)";
                        showNotification(message);
                        post(callback, false, message);
                        safeClose(socket);
                        return;
                    }
                }

                // 4) Drain again → remove leftover status bytes
                drainWithTimeout(input);

                // 5) Send print data
                output.write(printableData);
                output.flush();

                Thread.sleep(60);

                message = "Printed Successfully (PAY)";
                success = true;

            } catch (SocketTimeoutException ste) {
                message = "Timeout Error (PAY)";
                showNotification(message);
                Log.e(TAG, "READ TIMEOUT", ste);
            } catch (Exception e) {
                message = "Failed (PAY): " + e.getMessage();
                showNotification(message);
                Log.e(TAG, "ERROR", e);
            } finally {
                safeClose(socket);
            }

            post(callback, success, message);
        });
    }

    // -------- Helper Methods --------

    private void post(Callback cb, boolean success, String msg) {
        mainHandler.post(() -> {
            if (cb != null) cb.onComplete(success, msg);
        });
    }

    private void showNotification(String message) {
        try { NotificationUtils.showPrinterError(context, message); } catch (Exception ignored) {}
    }

    private void safeClose(Socket socket) {
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
    }

    private byte[] readUntilTimeout(InputStream input) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[64];

            while (true) {
                try {
                    int r = input.read(buffer);
                    if (r == -1) break;
                    if (r > 0) baos.write(buffer, 0, r);
                } catch (SocketTimeoutException te) {
                    break;
                }
            }

            return baos.toByteArray();

        } catch (Exception e) {
            Log.e(TAG, "readUntilTimeout error", e);
            return new byte[0];
        }
    }

    private void drainWithTimeout(InputStream input) {
        try { readUntilTimeout(input); } catch (Exception ignored) {}
    }

    private String bytesToHex(byte[] data) {
        if (data == null) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : data) sb.append(String.format("%02X ", b));
        return sb.toString();
    }
}
