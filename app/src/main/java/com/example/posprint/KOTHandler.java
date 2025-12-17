package com.example.posprint;

import static androidx.fragment.app.FragmentManager.TAG;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

public class KOTHandler {

    Context context;
    JSONObject response, details;

    private static final String ESC_FONT_SIZE_LARGE = "\u001B" + "!" + (char) 51;
    private static final String ESC_FONT_SIZE_MEDIUM = "\u001B" + "!" + (char) 46;
    private static final String ESC_FONT_SIZE_SMALL = "\u001B" + "!" + (char) 23;
    private static final String ESC_FONT_SIZE_RESET = "\u001B" + "!" + (char) 0;

    public KOTHandler(Context context, JSONObject response, JSONObject details) {
        this.context = context;
        this.response = response;
        this.details = details;
    }

    public void handleKOT() {
        try {

            for (Iterator<String> keyIterator = details.keys(); keyIterator.hasNext(); ) {

                String key = keyIterator.next();
                JSONObject objectDetails = details.getJSONObject(key);

                // ------------------------------
                //  GET PRINTER ID (supports int & JSONArray)
                // ------------------------------
                Object printerObj = objectDetails.get("printer");
                int printerId = -1;

                if (printerObj instanceof JSONArray) {
                    JSONArray printerArray = (JSONArray) printerObj;
                    if (printerArray.length() > 0) {
                        printerId = printerArray.optInt(0, -1);
                    }
                } else if (printerObj instanceof Number) {
                    printerId = ((Number) printerObj).intValue();
                }

                if (printerId == -1) {
                    Log.e(TAG, "Invalid printer ID.");
                    continue;
                }

                // ------------------------------
                //  GET PRINTER DETAILS FROM API
                // ------------------------------
                JSONObject printerDetails =
                        getPrinterDetails(printerId, response.getJSONArray("printers"));

                if (printerDetails == null) {
                    Log.e(TAG, "No printer found for printerId=" + printerId);
                    continue;
                }

                String ip = printerDetails.optString("ip");
                int port = Integer.parseInt(printerDetails.optString("port", "9100"));

                // ------------------------------
                //  BUILD KOT PRINT TEXT
                // ------------------------------
                String textToPrint = formatKOTText(response, objectDetails);

                int kotCopies = response.getJSONArray("printsettings")
                        .getJSONObject(0)
                        .optInt("kot_print_copies", 1);

                // ------------------------------
                //  PRINT USING NEW PrintConnection
                // ------------------------------
                PrintConnection printer = new PrintConnection(context);

                for (int i = 0; i < kotCopies; i++) {

                    printer.printWithStatusCheck(ip, port, textToPrint, (success, msg) -> {
                        Log.d("KOT_PRINT_CALLBACK", "Success=" + success + " | Message=" + msg);
                    });
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error in handleKOT", e);
        }
    }

    // ----------------------------------------------------------------------
    //  FORMAT KOT TEXT
    // ----------------------------------------------------------------------
    private String formatKOTText(JSONObject response, JSONObject objectDetails) {
        StringBuilder formattedText = new StringBuilder();

        try {
            JSONObject orderDetails = objectDetails.getJSONObject("order_details");

            formattedText.append(ESC_FONT_SIZE_LARGE)
                    .append(centerText("KOT :Kitchen", true))
                    .append(ESC_FONT_SIZE_RESET)
                    .append("\n")
                    .append("-".repeat(45))
                    .append("\n");

            String type = orderDetails.optString("order_type", "");
            String orderNo = orderDetails.optString("order_no", "");
            String orderTime = orderDetails.optString("order_time", "");
            String waiter = orderDetails.optString("waiter_name", "");
            String customer = orderDetails.optString("customer_name", "");
            String tableno = orderDetails.optString("tableno", "");
            int seats = orderDetails.optInt("table_seats", 0);

            formattedText.append("\nDate: ").append(orderTime)
                    .append("\nCustomer: ").append(customer)
                    .append("\nServed by: ").append(waiter);

            if (type.equals("dinein")) {
                if (!tableno.equals("null") && !tableno.isEmpty()) {
                    formattedText.append("\n")
                            .append(ESC_FONT_SIZE_LARGE)
                            .append("Table: ").append(tableno)
                            .append(ESC_FONT_SIZE_RESET)
                            .append("\nSeats: ").append(seats);
                } else {
                    formattedText.append("\nTable: -\nSeats: -");
                }
            }

            formattedText.append("\n\n")
                    .append(ESC_FONT_SIZE_LARGE)
                    .append(centerText(type.toUpperCase() + " #" + orderNo, true))
                    .append(ESC_FONT_SIZE_RESET)
                    .append("\n\n")
                    .append("-".repeat(45))
                    .append("\n");

            JSONObject categories = objectDetails.getJSONObject("categories");
            Iterator<String> cats = categories.keys();

            while (cats.hasNext()) {
                String cat = cats.next();
                JSONObject items = categories.getJSONObject(cat);

                formattedText.append(ESC_FONT_SIZE_LARGE)
                        .append(centerText(cat, true))
                        .append(ESC_FONT_SIZE_RESET)
                        .append("\n")
                        .append("-".repeat(45))
                        .append("\n");

                Iterator<String> itemKeys = items.keys();
                while (itemKeys.hasNext()) {
                    String id = itemKeys.next();
                    JSONObject item = items.getJSONObject(id);

                    formattedText.append(ESC_FONT_SIZE_LARGE)
                            .append(item.optString("quantity"))
                            .append(" x ")
                            .append(item.optString("item"))
                            .append(ESC_FONT_SIZE_RESET)
                            .append("\n");

                    String other = item.optString("other");
                    if (!other.equals("") && !other.equals("null")) {
                        formattedText.append("\nNote: ").append(other).append("\n");
                    }

                    formattedText.append("\n");
                }

                formattedText.append("-".repeat(45)).append("\n");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error formatting KOT text", e);
        }

        return formattedText.toString();
    }

    // ----------------------------------------------------------------------
    private String centerText(String text, boolean dbl) {
        int width = 45;
        int len = dbl ? text.length() * 2 : text.length();
        int spaces = (width - len) / 2;
        if (spaces < 0) spaces = 0;
        return " ".repeat(spaces) + text;
    }

    private JSONObject getPrinterDetails(int printerId, JSONArray printersArray) {
        for (int i = 0; i < printersArray.length(); i++) {
            try {
                JSONObject printer = printersArray.getJSONObject(i);
                if (printer.getInt("id") == printerId) return printer;
            } catch (Exception ignore) {}
        }
        return null;
    }
}
