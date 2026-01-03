package com.joopos.posprint;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.joopos.posprint.notification.NotificationUtils;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

public class PrintConnection_new extends AsyncTask<Void, Void, Boolean> {

    private Context context;
    private String printerIp;
    private int printerPort;
    private String textToPrint;

    public PrintConnection_new(Context context, String printerIp, int printerPort, String textToPrint) {
        this.context = context.getApplicationContext();
        this.printerIp = printerIp;
        this.printerPort = printerPort;
        this.textToPrint = textToPrint;
    }

    private void showStatusNotification(String message) {
        NotificationUtils.showPrinterError(context, message);
    }

    @Override
    protected Boolean doInBackground(Void... voids) {

        int maxRetries = 3;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {

            Socket socket = null;
            OutputStream outputStream = null;
            InputStream inputStream = null;

            try {
                Log.d("PrinterDebug", "🟡 Attempt " + attempt);

                // -------------------------------
                // PING CHECK (Optional)
                // -------------------------------
                boolean reachable = InetAddress.getByName(printerIp).isReachable(1500);
                /*
                if (!reachable) {
                    if (attempt == maxRetries) {
                        showStatusNotification("Printer is offline");
                    }
                    continue;
                }
                */

                // -------------------------------
                // SOCKET CONNECT
                // -------------------------------
                socket = new Socket();
                socket.connect(new InetSocketAddress(printerIp, printerPort), 5000);

                outputStream = socket.getOutputStream();
                inputStream = socket.getInputStream();

                // ============================================
                // FINAL FIX — CLEAR BUFFER BEFORE STATUS CHECK
                // ============================================
                while (inputStream.available() > 0) {
                    inputStream.read();
                }

                // -------------------------------
                // SEND STATUS COMMAND
                // -------------------------------
                byte[] CMD = new byte[]{0x1D, (byte) 0xF8, 0x31};
                outputStream.write(CMD);
                outputStream.flush();

                Thread.sleep(120);

                // ============================================
                // READ ALL RETURNED BYTES (UP TO 32)
                // ============================================
                byte[] buffer = new byte[32];
                int count = inputStream.read(buffer);

                if (count <= 0) {
                    showStatusNotification("Printer is offline");
                    return false;
                }

                // Extract ONLY first 4 important status bytes
                int b1 = buffer[0] & 0xFF;
                int b2 = buffer[1] & 0xFF;
                int b3 = buffer[2] & 0xFF;
                int b4 = buffer[3] & 0xFF;

                Log.d("PrinterStatus", "Bytes: " + b1 + "," + b2 + "," + b3 + "," + b4);

                // ============================================
                // CLEAR BUFFER AGAIN — FIXES “1” PRINT ISSUE
                // ============================================
                while (inputStream.available() > 0) {
                    inputStream.read();
                }

                // -------------------------------
                // ERROR INTERPRETATION
                // -------------------------------
                boolean offline      = ((b1 >> 3) & 1) == 1;
                boolean noPaper      = ((b2 >> 5) & 1) == 1;
                boolean coverOpen    = ((b2 >> 7) & 1) == 0;
                boolean cutterError  = ((b3 >> 3) & 1) == 1;
                boolean headOverheat = ((b4 >> 2) & 1) == 1;

                if (coverOpen) {
                    showStatusNotification("Printer cover is open");
                    return false;
                }
                if (offline) {
                    showStatusNotification("Printer is offline");
                    return false;
                }
                if (noPaper) {
                    showStatusNotification("No paper in printer");
                    return false;
                }
                if (cutterError) {
                    showStatusNotification("Cutter error");
                    return false;
                }
                if (headOverheat) {
                    showStatusNotification("Printer head overheated");
                    return false;
                }

                // -------------------------------
                // PRINT CONTENT (BUFFER CLEAN NOW)
                // -------------------------------
                String cut = "\u001DVA0";
                outputStream.write((textToPrint + cut).getBytes("CP858"));
                outputStream.flush();

                Log.d("PrinterDebug", "✔ Print OK");
                return true;

            } catch (Exception e) {

                Log.e("PrinterDebug", "❌ Exception: " + e);

                if (attempt == maxRetries) {
                    showStatusNotification("Printer is offline");
                    return false;
                }

                try { Thread.sleep(attempt * 1500); } catch (Exception ignored) {}

            } finally {
                try { if (outputStream != null) outputStream.close(); } catch (Exception ignored) {}
                try { if (inputStream != null) inputStream.close(); } catch (Exception ignored) {}
                try { if (socket != null) socket.close(); } catch (Exception ignored) {}
            }
        }

        return false;
    }
}
