package com.example.posprint;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;
import java.util.Map;

public class PettyCashHandler {
    private static final String TAG = "InvoiceHandler";
    private Context context;
    private JSONObject data, details;
    private JSONArray outlets;
    private String printerIP;
    private int printerPort;
    int totalItems = 0;

    private static final String ESC_FONT_SIZE_LARGE = "\u001B" + "!" + (char) 51;  // Double width + height + bold
    private static final String ESC_FONT_SIZE_MEDIUM = "\u001B" + "!" + (char) 46;
    private static final String ESC_FONT_SIZE_SMALL = "\u001B" + "!" + (char) 26;
    private static final String ESC_FONT_SIZE_RESET = "\u001B" + "!" + (char) 0;

    String printType;
    private JSONObject restSettings;
    private final Map<String, String> queryParams;

    public PettyCashHandler(Context context, JSONObject data, JSONArray outlets, JSONObject details, String printerIP, int printerPort, String printType, JSONObject restSettings, Map<String, String> queryParams) {
        this.context = context;
        this.data = data;
        this.outlets = outlets;
        this.details = details;
        this.printerIP = printerIP;
        this.printerPort = printerPort;
        this.printType = printType;
        this.restSettings = restSettings;
        this.queryParams = queryParams;
    }

    String formatPettyCashPrint(JSONObject response) {
        StringBuilder formattedText = new StringBuilder();

        try {
            JSONObject data = response.optJSONObject("data");
            JSONObject restSettings = response.optJSONObject("rest_settings");

            JSONArray outlets = response.optJSONArray("outlets");
            JSONObject outlet = (outlets != null && outlets.length() > 0) ? outlets.optJSONObject(0) : null;

            String outletName = outlet != null ? outlet.optString("name", "") : "";
            String outletAddress = outlet != null ? outlet.optString("address", "") : "";
            String outletPhone = outlet != null ? outlet.optString("phone", "") : "";

            String createdAt = data.optString("created_at", "");
            String name = data.optString("name", "");
            String createdBy = data.optString("waiter_name", "");
            String amount = data.optString("grandtotal", "0.00");
            String currency = restSettings.optString("currency", "£");

            // Header
            formattedText.append(ESC_FONT_SIZE_LARGE)
                    .append(centerText(outletName, true))
                    .append(ESC_FONT_SIZE_RESET).append("\n");

            formattedText.append(centerText(outletAddress, false)).append("\n");
            formattedText.append(centerText(outletPhone, false)).append("\n");

            formattedText.append(ESC_FONT_SIZE_LARGE)
                    .append(centerText("Petty Cash", true))
                    .append(ESC_FONT_SIZE_RESET).append("\n");

            formattedText.append("-".repeat(41)).append("\n");

            // Date and creator
            formattedText.append("date: ").append(createdAt).append("\n");
            formattedText.append("Created by: ").append(createdBy).append("\n");

            formattedText.append("-".repeat(41)).append("\n");

            // Item title (reason/name)
            formattedText.append(name.toUpperCase()).append("\n");

            // Left: "1 x amount", Right: currency + amount
            String left = "1 x " + amount;
            String right = currency + amount;
            formattedText.append(left)
                    .append(spaces(41 - left.length() - right.length()))
                    .append(right).append("\n");

            formattedText.append("-".repeat(41)).append("\n");

            // Total line
            String totalLabel = "TOTAL :";
            formattedText.append(totalLabel)
                    .append(spaces(41 - totalLabel.length() - right.length()))
                    .append(right).append("\n");

            formattedText.append("-".repeat(41)).append("\n");

            formattedText.append(centerText("Thank you for visiting us!", false)).append("\n");

        } catch (Exception e) {
            Log.e("PettyCashPrint", "Error formatting petty cash", e);
        }

        return formattedText.toString();
    }

    public String formatTodayPettyCashPrint(JSONObject data) {
        StringBuilder formattedText = new StringBuilder();
        double totalAmount = 0.0;

        try {
            JSONObject outlet = (outlets != null && outlets.length() > 0) ? outlets.optJSONObject(0) : null;
            String outletName = outlet != null ? outlet.optString("name", "") : "";
            String outletAddress = outlet != null ? outlet.optString("address", "") : "";
            String outletPhone = outlet != null ? outlet.optString("phone", "") : "";
            String currency = restSettings.optString("currency", "£");

            // Header
            formattedText.append(ESC_FONT_SIZE_LARGE)
                    .append(centerText(outletName, true))
                    .append(ESC_FONT_SIZE_RESET).append("\n");

            formattedText.append(centerText(outletAddress, false)).append("\n");
            formattedText.append(centerText(outletPhone, false)).append("\n");

            formattedText.append(ESC_FONT_SIZE_LARGE)
                    .append(centerText("Petty Cash", true))
                    .append(ESC_FONT_SIZE_RESET).append("\n");

            formattedText.append("-".repeat(41)).append("\n");

            String commonDate = "";

            for (Iterator<String> keyIterator = data.keys(); keyIterator.hasNext(); ) {
                String key = keyIterator.next();
                JSONObject entry = data.getJSONObject(key);

                String name = entry.optString("name", "");
                String amount = entry.optString("grandtotal", "0.00");
                String createdBy = entry.optString("waiter_name", "");
                String createdAt = entry.optString("created_at", "");

                if (commonDate.isEmpty()) commonDate = createdAt;

                double amt = Double.parseDouble(amount);
                totalAmount += amt;

                formattedText.append(name.toUpperCase()).append("\n");
                formattedText.append("1 x ").append(amount);
                formattedText.append(spaces(41 - ("1 x " + amount).length() - (currency + amount).length()))
                        .append(currency).append(amount).append("\n");
            }

            // Footer
            formattedText.append("-".repeat(41)).append("\n");
            formattedText.append("TOTAL :");
            formattedText.append(spaces(41 - "TOTAL :".length() - (currency + String.format("%.2f", totalAmount)).length()))
                    .append(currency).append(String.format("%.2f", totalAmount)).append("\n");

            formattedText.append("-".repeat(41)).append("\n");
            formattedText.append(centerText("Thank you for visiting us!", false)).append("\n");

        } catch (Exception e) {
            Log.e(TAG, "Error formatting today petty cash", e);
        }
        return formattedText.toString();
    }

    private String centerText(String text, boolean isDoubleWidth) {
        int fullLineWidth = 45;
        int visualTextLength = isDoubleWidth ? text.length() * 2 : text.length();
        int spaces = (fullLineWidth - visualTextLength) / 2;
        if (spaces < 0) spaces = 0;
        return " ".repeat(spaces) + text;
    }
    private String spaces(int count) {
        return " ".repeat(Math.max(count, 0));
    }
}