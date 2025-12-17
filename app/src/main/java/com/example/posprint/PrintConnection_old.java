package com.example.posprint;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

public class PrintConnection_old extends AsyncTask<Void, Void, Boolean> {
    private String printerIp;
    private int printerPort;
    private String textToPrint;
    Context context;

    public PrintConnection_old(Context context, String printerIp, int printerPort, String textToPrint) {
        this.context = context;
        this.printerIp = printerIp;
        this.printerPort = printerPort;
        this.textToPrint = textToPrint;
    }

    @Override
    protected void onPostExecute(Boolean result) {
        if (result) {
            Log.d("Printer", "✅ Successfully printed!");
        } else {
            Log.e("Printer", "❌ Error occurred while printing.");
        }
    }

    @Override
    protected Boolean doInBackground(Void... voids) {
        int maxRetries = 3;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            Socket socket = null;
            OutputStream outputStream = null;

            try {
                Log.d("PrinterDebug", "🟡 Attempt " + attempt + ": Connecting to " + printerIp + ":" + printerPort);

                // Optional: Check if the printer is reachable via ping
                boolean reachable = InetAddress.getByName(printerIp).isReachable(2000); // 2 seconds timeout
                if (!reachable) {
                    Log.e("PrinterDebug", "🔌 Printer not reachable (ping failed) on attempt " + attempt);
                    throw new IOException("Ping failed");
                }

                // Try to connect with 7 second timeout
                socket = new Socket();
                socket.connect(new InetSocketAddress(printerIp, printerPort), 7000);

                outputStream = socket.getOutputStream();
                String paperCutCommand = "\u001DVA0"; // Full cut command
                String finalTextToPrint = textToPrint + paperCutCommand;

                // Use CP858 encoding for £ and special chars
                outputStream.write(finalTextToPrint.getBytes("CP858"));
                outputStream.flush();

                Log.d("PrinterDebug", "✅ Print success on attempt " + attempt);
                return true;

            } catch (IOException e) {
                Log.e("PrinterDebug", "❌ Attempt " + attempt + " failed: " + e.getMessage()
                        + " [IP=" + printerIp + ", Port=" + printerPort + "]");

                if (attempt == maxRetries) {
                    return false; // Final failure
                }

                // Exponential backoff: 2s, 4s, 6s
                int retryDelayMillis = attempt * 2000;
                Log.d("PrinterDebug", "🔁 Waiting " + retryDelayMillis + "ms before retrying...");

                try {
                    Thread.sleep(retryDelayMillis);
                } catch (InterruptedException ignored) {
                }

            } finally {
                try {
                    if (outputStream != null) outputStream.close();
                    if (socket != null) socket.close();
                } catch (IOException ignored) {
                }
            }
        }

        return false; // All attempts failed
    }
}
