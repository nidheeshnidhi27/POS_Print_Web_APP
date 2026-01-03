package com.joopos.posprint;

import android.util.Log;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class PrinterStatusHelper {
    public static class Status {
        public boolean online;
        public boolean paperOk;
        public boolean busy;
        public byte[] raw;
        public String paper;
        public String status;
    }

    public static Status queryBasic(String ip, int port, int connectTimeoutMs, int readTimeoutMs) throws Exception {
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(ip, port), connectTimeoutMs);
            s.setSoTimeout(readTimeoutMs);
            InputStream in = s.getInputStream();
            OutputStream out = s.getOutputStream();
            while (in.available() > 0) { in.read(); }
            byte[] cmd = new byte[]{0x10, 0x04, 0x02};
            out.write(cmd);
            out.flush();
            byte[] buf = new byte[8];
            int r;
            try {
                r = in.read(buf);
            } catch (SocketTimeoutException te) {
                r = -1;
            }
            Status st = new Status();
            if (r <= 0) {
                st.online = false;
                st.paperOk = true;
                st.busy = false;
                st.raw = new byte[0];
                return st;
            }
            st.raw = new byte[r];
            System.arraycopy(buf, 0, st.raw, 0, r);
            int b = st.raw[0] & 0xFF;
            st.online = ((b >> 0) & 1) == 0;
            st.paperOk = ((b >> 3) & 1) == 0;
            st.busy = ((b >> 5) & 1) == 1;
            st.paper = st.paperOk ? "OK" : "Out";
            st.status = st.busy ? "busy" : "free";
            return st;
        } finally {
            try { s.close(); } catch (Exception ignored) {}
        }
    }

    public static boolean waitUntilReady(String ip, int port, long maxWaitMs) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < maxWaitMs) {
            try {
                Status st = queryBasic(ip, port, 5000, 800);
                if (st.online && st.paperOk && !st.busy) return true;
                Thread.sleep(250);
            } catch (Exception e) {
                Log.e("PrinterStatus", "waitUntilReady error", e);
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            }
        }
        return false;
    }
}
