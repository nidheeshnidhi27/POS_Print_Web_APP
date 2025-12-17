package com.example.posprint;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.posprint.notification.NotificationUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PrintConnection {

    public interface Callback {
        void onComplete(boolean success, String message);
    }

    private static final String TAG = "PrintConnection";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Context context;

    public PrintConnection(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Main entry — checks status (non-printing) then prints if OK.
     *
     * @param ip        printer ip
     * @param port      printer port (usually 9100)
     * @param textToPrint ESC/POS formatted text
     * @param callback  called on main thread
     */
    public void printWithStatusCheck(String ip, int port, String textToPrint, Callback callback) {
        executor.execute(() -> {
            boolean success = false;
            String message = "Unknown error";

            Socket socket = null;
            try {
                socket = new Socket();
                socket.connect(new InetSocketAddress(ip, port), 4000);
                socket.setSoTimeout(200); // short read timeout for status drain

                InputStream input = socket.getInputStream();
                OutputStream output = socket.getOutputStream();

                // 1) Drain any leftover bytes before asking status
                drainWithTimeout(input);

                // 2) SEND NON-PRINTING STATUS CMD: DLE EOT 4 (0x10 0x04 0x04)
                byte[] statusCmd = new byte[]{0x10, 0x04, 0x04};
                output.write(statusCmd);
                output.flush();

                // 3) Read response into a buffer until timeout (safe drain + capture)
                byte[] statusBytes = readUntilTimeout(input);

                // interpret status if we got anything
                if (statusBytes == null || statusBytes.length == 0) {
                    // consider offline or no response
                    message = "Printer did not respond to status check";
                    showNotification(message);
                    success = false;
                    safeClose(socket);
                    postResult(callback, success, message);
                    return;
                }

                // Example: many printers send one or more bytes; use first byte for basic checks
                int first = statusBytes[0] & 0xFF;
                Log.d(TAG, "Status bytes (hex): " + bytesToHex(statusBytes));
                Log.d(TAG, "Status first byte: " + first);

                // Basic interpretation (may vary across printer models)
                boolean paperOut = ((first >> 3) & 1) == 1;
                boolean coverOpen = ((first >> 5) & 1) == 1; // some printers differ on bit positions

                if (paperOut) {
                    message = "Printer out of paper";
                    showNotification(message);
                    success = false;
                    safeClose(socket);
                    postResult(callback, success, message);
                    return;
                }
                if (coverOpen) {
                    message = "Printer cover open";
                    showNotification(message);
                    success = false;
                    safeClose(socket);
                    postResult(callback, success, message);
                    return;
                }

                // 4) Very important: drain again to ensure **no status bytes** remain before sending print data
                drainWithTimeout(input);

                // 5) Send the actual print data (split write allows printer to process)
                // Use CP858 as you used previously for special characters
                Charset cs = Charset.forName("CP858");
                output.write(textToPrint.getBytes(cs));
                output.flush();

                // small pause then send cut (separate write reduces mixing)
                try { Thread.sleep(60); } catch (InterruptedException ignored) {}

                String cut = "\u001DVA0";
                output.write(cut.getBytes(cs));
                output.flush();

                message = "Printed successfully";
                success = true;

            } catch (SocketTimeoutException ste) {
                message = "Printer read timed out (possible slow response)";
                showNotification(message);
                Log.e(TAG, "SocketTimeoutException", ste);
                success = false;
            } catch (Exception e) {
                message = "Printing failed: " + e.getMessage();
                showNotification(message);
                Log.e(TAG, "Printing exception", e);
                success = false;
            } finally {
                safeClose(socket);
            }

            postResult(callback, success, message);
        });
    }

    // ---------- helpers ----------

    private void postResult(Callback callback, boolean success, String message) {
        mainHandler.post(() -> {
            if (callback != null) callback.onComplete(success, message);
        });
    }

    private void showNotification(String message) {
        try {
            NotificationUtils.showPrinterError(context, message);
        } catch (Exception ignored) {}
    }

    private void safeClose(Socket s) {
        try {
            if (s != null) s.close();
        } catch (Exception ignored) {}
    }

    /**
     * Read bytes until socket timeout occurs. Returns empty array if nothing read.
     */
    private byte[] readUntilTimeout(InputStream input) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[64];

            // Keep reading until a timeout occurs (socket has a SoTimeout)
            while (true) {
                try {
                    int r = input.read(buf);
                    if (r == -1) break;
                    if (r > 0) baos.write(buf, 0, r);
                    // keep trying until timeout triggers
                } catch (SocketTimeoutException ste) {
                    // no more data arrived within timeout window -> return what we have
                    break;
                }
            }

            return baos.toByteArray();
        } catch (Exception e) {
            Log.e(TAG, "readUntilTimeout error", e);
            return new byte[0];
        }
    }

    /**
     * Drain any immediately available bytes and wait briefly to collect possible late bytes.
     * Uses the socket's SoTimeout to avoid blocking forever.
     */
    private void drainWithTimeout(InputStream input) {
        try {
            // quick aggressive drain loop: read until no more data (timeout will break)
            readUntilTimeout(input);
        } catch (Exception ignored) {}
    }

    private static String bytesToHex(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02X ", b));
        return sb.toString().trim();
    }
}
