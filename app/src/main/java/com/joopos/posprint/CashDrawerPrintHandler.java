package com.joopos.posprint;

import android.content.Context;
import android.icu.text.SimpleDateFormat;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.Locale;

public class CashDrawerPrintHandler {

    private Context context;
    private JSONObject response;

    // ESC/POS cash drawer pulse commands
    // ESC p m t1 t2
    private static final byte[] OPEN_DRAWER_PIN_0 =
            new byte[]{0x1B, 0x70, 0x00, 0x3C, (byte) 0xFF};

    private static final byte[] OPEN_DRAWER_PIN_1 =
            new byte[]{0x1B, 0x70, 0x01, 0x3C, (byte) 0xFF};

    public CashDrawerPrintHandler(Context context, JSONObject response) {
        this.context = context;
        this.response = response;
    }

    public void handleCashDrawerPrint() {
        try {
            JSONArray printers = response.getJSONArray("printers");
            JSONArray printerSetup = response.getJSONArray("printersetup");

            int printerId = printerSetup.getInt(0);
            JSONObject printer = getPrinterDetails(printerId, printers);
            if (printer == null) return;

            String printerIP = printer.optString("ip");
            int printerPort = Integer.parseInt(printer.optString("port", "9100"));

            // Get drawer pulse command
            byte[] drawerPulse = getDrawerPulseCommand(response);

            // Format print text
            String formattedText = formatDrawerText(response);

            PrintConnection pc = new PrintConnection(context);
            pc.printWithDrawerPulse(
                    printerIP,
                    printerPort,
                    drawerPulse,
                    formattedText,
                    (success, msg) -> Log.d("CashDrawerPrint", success + " → " + msg)
            );

        } catch (Exception e) {
            Log.e("CashDrawerPrint", "Error opening cash drawer", e);
        }
    }

    // ---------------- TEXT FORMAT ----------------

    private String formatDrawerText(JSONObject response) {
        StringBuilder builder = new StringBuilder();

        try {
            builder.append("------------------------------------------\n");
            builder.append(centerText("CASH DRAWER OPEN", false)).append("\n");
            builder.append("------------------------------------------\n");

            // Read cash drawer pin
            JSONObject restSettings = response.optJSONObject("rest_settings");
            String cashDrawerPin = null;

            if (restSettings != null && !restSettings.isNull("cash_drawer_pin")) {
                cashDrawerPin = restSettings.optString("cash_drawer_pin");
            }

            // Print PIN only if exists
            if (cashDrawerPin != null && !cashDrawerPin.trim().isEmpty()) {
                builder.append(centerText("Drawer PIN : " + cashDrawerPin, false)).append("\n");
                builder.append("------------------------------------------\n");
            }

//            builder.append(centerText("Thank you for visiting us!", false)).append("\n");

            /*SimpleDateFormat sdf =
                    new SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault());
            builder.append("Printed : ").append(sdf.format(new Date())).append("\n");*/

        } catch (Exception e) {
            Log.e("CashDrawerPrint", "Format error", e);
        }

        return builder.toString();
    }

    // ---------------- DRAWER PIN LOGIC ----------------

    private byte[] getDrawerPulseCommand(JSONObject response) {
        try {
            JSONObject restSettings = response.optJSONObject("rest_settings");
            if (restSettings != null && !restSettings.isNull("cash_drawer_pin")) {
                int pin = restSettings.optInt("cash_drawer_pin", 0);
                return (pin == 1) ? OPEN_DRAWER_PIN_1 : OPEN_DRAWER_PIN_0;
            }
        } catch (Exception e) {
            Log.e("CashDrawerPrint", "Drawer pin read error", e);
        }
        return OPEN_DRAWER_PIN_0; // default
    }

    // ---------------- HELPERS ----------------

    private String centerText(String text, boolean isDoubleWidth) {
        int fullLineWidth = 45;
        int visualLength = isDoubleWidth ? text.length() * 2 : text.length();
        int spaces = (fullLineWidth - visualLength) / 2;
        if (spaces < 0) spaces = 0;
        return " ".repeat(spaces) + text;
    }

    private JSONObject getPrinterDetails(int id, JSONArray printers) throws JSONException {
        for (int i = 0; i < printers.length(); i++) {
            JSONObject printer = printers.getJSONObject(i);
            if (printer.getInt("id") == id) return printer;
        }
        return null;
    }
}
