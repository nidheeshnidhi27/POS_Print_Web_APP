package com.joopos.posprint;

import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

public class PrintConnection_PAY_new_WithoutStatus extends AsyncTask<Void, Void, Boolean> {
    public interface Callback {
        void onComplete(boolean success, String message);
    }
    private String printerIP;
    private int printerPort;
    private byte[] printableData;
    private Callback callback;

    public PrintConnection_PAY_new_WithoutStatus(String printerIP, int printerPort, byte[] printableData) {
        this.printerIP = printerIP;
        this.printerPort = printerPort;
        this.printableData = printableData;
    }
    public PrintConnection_PAY_new_WithoutStatus(String printerIP, int printerPort, byte[] printableData, Callback cb) {
        this.printerIP = printerIP;
        this.printerPort = printerPort;
        this.printableData = printableData;
        this.callback = cb;
    }

    @Override
    protected void onPostExecute(Boolean result) {
        if (result) {
            Log.d("Printer", "✅ Successfully printed (PAY)");
            if (callback != null) callback.onComplete(true, "Printed");
        } else {
            Log.e("Printer", "❌ Failed to print (PAY)");
            if (callback != null) callback.onComplete(false, "Failed");
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

                // Optional ping (non-blocking): proceed even if ping fails
                try {
                    boolean reachable = InetAddress.getByName(printerIP).isReachable(1000);
                    if (!reachable) {
                        Log.w("PrinterDebug", "🔌 Ping failed; proceeding to TCP connect");
                    }
                } catch (Exception e) {
                    Log.w("PrinterDebug", "Ping check error; proceeding to TCP connect: " + e.getMessage());
                }

                socket = new Socket();
                socket.connect(new InetSocketAddress(printerIP, printerPort), 2500); // fast timeout
                socket.setTcpNoDelay(true);

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



/*
package com.example.posprint;

import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
public class PrintConnection_PAY extends AsyncTask<Void, Void, Void> {
    private String printerIP;
    private int printerPort;
    private byte[] printableData;

    public PrintConnection_PAY(String printerIP, int printerPort, byte[] printableData) {
        this.printerIP = printerIP;
        this.printerPort = printerPort;
        this.printableData = printableData;
    }

    @Override
    protected Void doInBackground(Void... voids) {
        try (Socket socket = new Socket(printerIP, printerPort);
             OutputStream outputStream = socket.getOutputStream()) {
            outputStream.write(printableData);
            outputStream.flush();
        } catch (IOException e) {
            Log.e("PrintConnection", "Printing error", e);
        }
        return null;
    }
}
*/
