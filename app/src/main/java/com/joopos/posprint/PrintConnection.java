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
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PrintConnection {
    public interface Callback {
        void onComplete(boolean success, String message);
    }

    private static final String TAG = "PrintConnection";
    private static final Map<String, ExecutorService> PR_EXEC = new ConcurrentHashMap<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Context context;

    public PrintConnection(Context context) {
        this.context = context.getApplicationContext();
    }

    private static ExecutorService execFor(String key) {
        return PR_EXEC.computeIfAbsent(key, k -> Executors.newSingleThreadExecutor());
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
        String key = ip + ":" + port;
        execFor(key).execute(() -> {
            boolean success = false;
            String message = "Unknown error";

            Socket socket = null;
            try {
                socket = new Socket();
                socket.connect(new InetSocketAddress(ip, port), 4000);
                socket.setSoTimeout(200);

                InputStream input = socket.getInputStream();
                OutputStream output = socket.getOutputStream();

                drainWithTimeout(input);
                byte[] statusBytes;
                // Try DLE EOT 2 first (matches your backend)
                output.write(new byte[]{0x10, 0x04, 0x02});
                output.flush();
                statusBytes = readUntilTimeout(input);
                if (statusBytes == null || statusBytes.length == 0) {
                    // Fallback: DLE EOT 4
                    output.write(new byte[]{0x10, 0x04, 0x04});
                    output.flush();
                    statusBytes = readUntilTimeout(input);
                }

                if (statusBytes == null || statusBytes.length == 0) {
                    // No status response — proceed cautiously after drain
                    Log.w(TAG, "No status response; proceeding to print after drain");
                } else {
                    int first = statusBytes[0] & 0xFF;
                    Log.d(TAG, "Status bytes (hex): " + bytesToHex(statusBytes));
                    Log.d(TAG, "Status first byte: " + first);
                    boolean offline = ((first >> 0) & 1) == 1;
                    boolean paperEnd = ((first >> 3) & 1) == 1;
                    boolean busy = ((first >> 5) & 1) == 1;
                    if (offline) {
                        message = "Printer is offline";
                        showNotification(message);
                        success = false;
                        safeClose(socket);
                        postResult(callback, success, message);
                        return;
                    }
                    if (paperEnd) {
                        message = "Paper out";
                        showNotification(message);
                        success = false;
                        safeClose(socket);
                        postResult(callback, success, message);
                        return;
                    }
                    if (busy) {
                        try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                        drainWithTimeout(input);
                        output.write(new byte[]{0x10, 0x04, 0x02});
                        output.flush();
                        byte[] again = readUntilTimeout(input);
                        if (again != null && again.length > 0) {
                            int f2 = again[0] & 0xFF;
                            boolean stillBusy = ((f2 >> 5) & 1) == 1;
                            if (stillBusy) {
                                message = "Printer busy";
                                showNotification(message);
                                success = false;
                                safeClose(socket);
                                postResult(callback, success, message);
                                return;
                            }
                        }
                    }
                }

                // 4) Very important: drain again to ensure **no status bytes** remain before sending print data
                drainWithTimeout(input);

                // 5) Send the actual print data (split write allows printer to process)
                // Use CP858 as you used previously for special characters
                Charset cs = Charset.forName("CP858");
                output.write(textToPrint.getBytes(cs));
                output.write(new byte[]{0x0A}); // ensure final line commits
                output.flush();

                try { Thread.sleep(120); } catch (InterruptedException ignored) {}
                output.write(new byte[]{0x1B, 0x64, 0x02}); // feed 2 lines
                output.flush();
                try { Thread.sleep(120); } catch (InterruptedException ignored) {}
                output.write(new byte[]{0x1D, 0x56, 0x00}); // full cut
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

    public void printWithDrawerPulse(
            String ip,
            int port,
            byte[] drawerPulse,
            String textToPrint,
            Callback callback
    ) {
        String key = ip + ":" + port;
        execFor(key).execute(() -> {
            boolean success = false;
            String message = "Unknown error";
            Socket socket = null;

            try {
                socket = new Socket();
                socket.connect(new InetSocketAddress(ip, port), 4000);
                socket.setSoTimeout(200);

                InputStream input = socket.getInputStream();
                OutputStream output = socket.getOutputStream();

                PrinterStatusHelper.Status st =
                        PrinterStatusHelper.queryBasic(ip, port, 1000, 1000);

                if (!st.online || !st.paperOk) {
                    message = "Printer error: " + st.status;
                    showNotification(message);
                    postResult(callback, false, message);
                    safeClose(socket);
                    return;
                }

                drainWithTimeout(input);

                // 🔥 STEP 1: OPEN CASH DRAWER
                output.write(drawerPulse);
                output.flush();
                Thread.sleep(80);

                // 🔥 STEP 2: PRINT TEXT
                output.write(textToPrint.getBytes(Charset.forName("CP858")));
                output.write(new byte[]{0x0A}); // ensure final line commits
                output.flush();

                Thread.sleep(120);
                output.write(new byte[]{0x1B, 0x64, 0x02}); // feed
                output.flush();
                Thread.sleep(120);
                output.write(new byte[]{0x1D, 0x56, 0x00}); // cut
                output.flush();

                success = true;
                message = "Drawer opened & printed";

            } catch (Exception e) {
                message = "Drawer open failed: " + e.getMessage();
                showNotification(message);
                Log.e(TAG, message, e);
            } finally {
                safeClose(socket);
            }


            postResult(callback, success, message);
        });
    }
}
