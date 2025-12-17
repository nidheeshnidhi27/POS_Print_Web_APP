package com.example.posprint;

import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

public class PrintConnection_PAY_old extends AsyncTask<Void, Void, Boolean> {
    private String printerIP;
    private int printerPort;
    private byte[] printableData;

    public PrintConnection_PAY_old(String printerIP, int printerPort, byte[] printableData) {
        this.printerIP = printerIP;
        this.printerPort = printerPort;
        this.printableData = printableData;
    }

    @Override
    protected void onPostExecute(Boolean result) {
        if (result) {
            Log.d("Printer", "✅ Successfully printed (PAY)");
        } else {
            Log.e("Printer", "❌ Failed to print (PAY)");
        }
    }

    @Override
    protected Boolean doInBackground(Void... voids) {
        int maxRetries = 3;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            Socket socket = null;
            OutputStream outputStream = null;

            try {
                Log.d("PrinterDebug", "🟡 Attempt " + attempt + ": Connecting to " + printerIP + ":" + printerPort);

                // Optional: ping before trying to connect
                boolean reachable = InetAddress.getByName(printerIP).isReachable(2000);
                if (!reachable) {
                    Log.e("PrinterDebug", "Printer not reachable (ping failed) on attempt " + attempt);
                    throw new IOException("Ping failed");
                }

                socket = new Socket();
                socket.connect(new InetSocketAddress(printerIP, printerPort), 7000); // 7s timeout

                outputStream = socket.getOutputStream();
                outputStream.write(printableData);
                outputStream.flush();

                Log.d("PrinterDebug", "✅ Print success on attempt " + attempt);
                return true;

            } catch (IOException e) {
                Log.e("PrinterDebug", "❌ Attempt " + attempt + " failed: " + e.getMessage()
                        + " [IP=" + printerIP + ", Port=" + printerPort + "]");
                if (attempt == maxRetries) {
                    return false; // Give up after max retries
                }

                int delay = attempt * 2000;
                Log.d("PrinterDebug", "🔁 Waiting " + delay + "ms before retrying...");
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ignored) {}

            } finally {
                try {
                    if (outputStream != null) outputStream.close();
                    if (socket != null) socket.close();
                } catch (IOException ignored) {}
            }
        }

        return false;
    }
}
