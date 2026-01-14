package com.joopos.posprint;

import android.content.Context;
import android.icu.text.SimpleDateFormat;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.Iterator;
import java.util.Locale;

public class KOTHandlerOnline {
    private static final String TAG = "KOTHandlerOnline";
    Context context;
    JSONObject response, details;
    private static final String ESC_FONT_SIZE_LARGE = "\u001B" + "!" + (char) 51;  // Double width + height + bold
    private static final String ESC_FONT_SIZE_MEDIUM = "\u001B" + "!" + (char) 46;
    private static final String ESC_FONT_SIZE_SMALL = "\u001B" + "!" + (char) 23;
    private static final String ESC_FONT_SIZE_NOTE = "\u001B" + "!" + (char) 32;
    private static final String ESC_FONT_SIZE_RESET = "\u001B" + "!" + (char) 0;
    public KOTHandlerOnline(Context context, JSONObject response, JSONObject details) {

        this.context = context;
        this.response = response;
        this.details = details;
    }
    /*public void handleKOT() {
        try {
            // Loop through each detail object (e.g., "1", "6")
            for (Iterator<String> keyIterator = details.keys(); keyIterator.hasNext(); ) {
                String key = keyIterator.next();
                JSONObject objectDetails = details.getJSONObject(key);
                // Get the printer ID and fetch printer details
                int printerId = objectDetails.getInt("printer");
                JSONObject printerDetails = getPrinterDetails(printerId, response.getJSONArray("printers"));
                if (printerDetails == null) {
                    Log.e(TAG, "Printer details not found for printer ID: " + printerId);
                    continue; // Skip this object if printer details are missing
                }
                // Extract printer IP and port
                String printerIP = printerDetails.optString("ip");
                int printerPort = Integer.parseInt(printerDetails.optString("port", "9100")); // Default to 9100 if port is not found
                // Format the text for the current detail object
                String formattedText = formatOnlineKOTText(response, objectDetails);

                int kotPrintCopies = response.getJSONArray("printsettings")
                        .getJSONObject(0)
                        .optInt("kot_print_copies", 1); // default 1


                *//*int copies;
                if (key.equals("8")) {
                    copies = 1;
                } else {
                    copies = kotPrintCopies;      // Print 3 times update for KOTTARAM RESTAURANT
                }

                Log.d("KOTHandler", "Printer ID " + printerId + " → copies = " + copies);*//*

                // 🔥 Execute printing multiple times
                for (int i = 0; i < kotPrintCopies; i++) {
                    //                                    todo _hide 02/12
                    *//*PrintConnection printConnection = new PrintConnection(context, printerIP, printerPort, formattedText);
                    printConnection.execute();*//*
                    PrintConnection printer = new PrintConnection(context);
                    printer.printWithStatusCheck(printerIP, printerPort, formattedText, (success, msg) -> {
                        Log.d("KOT_PRINT_CALLBACK", "Success=" + success + " | Message=" + msg);
                    });
                }

            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling KOT", e);
        }
    }*/


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
                String pType = printerDetails.optString("type", "network");

                // ------------------------------
                //  BUILD KOT PRINT TEXT
                // ------------------------------
                String textToPrint = formatOnlineKOTText(response, objectDetails);

                int kotCopies = response.getJSONArray("printsettings")
                        .getJSONObject(0)
                        .optInt("kot_print_copies", 1);

                // ------------------------------
                //  PRINT USING PrintConnection with status check
                // ------------------------------
                final int finalPrinterId = printerId;
                for (int i = 0; i < kotCopies; i++) {
                    final int finalCopyNo = i + 1;
                    if ("usb".equalsIgnoreCase(pType)
                            || "windows".equalsIgnoreCase(pType)
                            || "window".equalsIgnoreCase(pType)
                            || ip == null || ip.trim().isEmpty()) {
                        UsbPrintConnection usb = new UsbPrintConnection(context);
                        usb.printText(textToPrint, (success, msg) -> {
                            Log.d("KOT_PRINT_CALLBACK", "PrinterId=" + finalPrinterId + " Copy=" + finalCopyNo + " USB Success=" + success + " | " + msg);
                        });
                    } else {
                        PrintConnection printer = new PrintConnection(context);
                        printer.printFast(ip, port, textToPrint, (success, msg) -> {
                            Log.d("KOT_PRINT_CALLBACK", "PrinterId=" + finalPrinterId + " Copy=" + finalCopyNo + " Success=" + success + " | " + msg);
                        });
                    }
                    try { Thread.sleep(15); } catch (InterruptedException ignored) {}
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error in handleKOT", e);
        }
    }

    private String formatOnlineKOTText(JSONObject response, JSONObject objectDetails) {
        StringBuilder formattedText = new StringBuilder();
        try {
            JSONObject orderDetails = objectDetails.getJSONObject("order_details");
            formattedText.append(ESC_FONT_SIZE_LARGE)
                    .append(centerTextCat("KOT :Kitchen", true))
                    .append(ESC_FONT_SIZE_RESET).append("\n");
            formattedText.append("-".repeat(45)).append("\n");

            String type = orderDetails.optString("order_type", "");
            String orderNo = orderDetails.optString("id", "");
            String orderTime = orderDetails.optString("order_time", "");
            String waiter = orderDetails.optString("waiter_name", "");
            String customer = orderDetails.optString("customer_name", "");
            String address = orderDetails.optString("address", "");
            String postcode = orderDetails.optString("postcode", "");
            String phone = orderDetails.optString("contactno", "");
            String tableno = orderDetails.optString("tableno", "");
            int tableSeats = orderDetails.optInt("table_seats", 0);

            String displayType;
            switch (type) {
                case "dinein": displayType = "Dine-In"; break;
                case "takeaway": displayType = "Takeaway"; break;
                case "delivery": displayType = "Delivery"; break;
                default: displayType = type; break;
            }

            formattedText.append("\nDate: ").append(orderTime);

            if (customer.isEmpty()) {
                String firstName = orderDetails.optString("firstname", "").trim();
                String lastName = orderDetails.optString("lastname", "").trim();

                // Combine firstname and lastname (with space if both exist)
                customer = (firstName + " " + lastName).trim();
            }

// Append to print text
            formattedText.append("\nCustomer: ").append(customer);
            if (!TextUtils.isEmpty(address)) formattedText.append("\nAddress: ").append(address).append(", ").append(postcode);
            if (!TextUtils.isEmpty(phone)) formattedText.append("\nPhone: ").append(phone);
            /*if (type.equals("delivery")) {
                if (!TextUtils.isEmpty(address)) formattedText.append("\nAddress: ").append(address);
                if (!TextUtils.isEmpty(phone)) formattedText.append("\nPhone: ").append(phone);
            }*/

            if (!waiter.isEmpty()) {
                formattedText.append("\nServed by: ").append(waiter);
            }
            if (type.equals("dinein")) {

                if (tableno != null &&
                        !tableno.trim().isEmpty() &&
                        !tableno.equalsIgnoreCase("null")) {
                    String cleanTableNo = tableno.replaceAll("(?i)table", "").trim();
                    formattedText.append("\n")
                            .append(ESC_FONT_SIZE_LARGE)
                            .append("Table: " ).append(cleanTableNo)
                            .append(ESC_FONT_SIZE_RESET);
                    formattedText.append("\nSeats: ").append(tableSeats);
                } else {
                    formattedText.append("\nTable: -");
                    formattedText.append("\nSeats: -");
                }
                /*formattedText.append("\n").append(ESC_FONT_SIZE_LARGE).append(tableno).append(ESC_FONT_SIZE_RESET);
                formattedText.append("\nSeats: ").append(tableSeats);*/
            }

            formattedText.append("\n\n")
                    .append(ESC_FONT_SIZE_LARGE)
                    .append(centerTextCat(displayType + " #" + orderNo, true))
                    .append(ESC_FONT_SIZE_RESET)
                    .append("\n\n");

            formattedText.append("-".repeat(45)).append("\n");

            // ✅ Handle Both JSONObject and JSONArray types
            Object categoriesObj = objectDetails.opt("categories");

//            Object categoriesObj = objectDetails.opt("categories");

            if (categoriesObj instanceof JSONObject) {
                JSONObject categories = (JSONObject) categoriesObj;

                for (Iterator<String> catIterator = categories.keys(); catIterator.hasNext(); ) {
                    String category = catIterator.next();
                    Object itemObj = categories.get(category);

                    formattedText.append(ESC_FONT_SIZE_LARGE)
                            .append(centerTextCat(category, true))
                            .append(ESC_FONT_SIZE_RESET).append("\n")
                            .append("-".repeat(45)).append("\n");

                    // If it's a JSONObject, iterate by keys
                    if (itemObj instanceof JSONObject) {
                        JSONObject items = (JSONObject) itemObj;

                        for (Iterator<String> itemIterator = items.keys(); itemIterator.hasNext(); ) {
                            String itemId = itemIterator.next();
                            JSONObject item = items.getJSONObject(itemId);
                            formatItemBlock(item, category, formattedText);
                        }

                        // If it's a JSONArray, iterate directly
                    } else if (itemObj instanceof JSONArray) {
                        JSONArray itemsArray = (JSONArray) itemObj;

                        for (int j = 0; j < itemsArray.length(); j++) {
                            JSONObject item = itemsArray.getJSONObject(j);
                            formatItemBlock(item, category, formattedText);
                        }
                    }

                    formattedText.append("\n").append("-".repeat(45)).append("\n");
                }
            }

            formattedText.append("Special Instruction: ")
                    .append(orderDetails.optString("instruction")).append("\n")
                    .append("-".repeat(45)).append("\n");

            String deliveryTime = orderDetails.optString("deliverytime", "");

            if (deliveryTime != null &&
                    !deliveryTime.trim().isEmpty() &&
                    !deliveryTime.equalsIgnoreCase("null")) {

                formattedText.append(ESC_FONT_SIZE_MEDIUM)
                        .append("Requested for: ")
                        .append(deliveryTime)
                        .append(ESC_FONT_SIZE_RESET)
                        .append("\n")
                        .append("-".repeat(45))
                        .append("\n");

            }
            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault());
            String printedTime = sdf.format(new Date());

            formattedText.append("Printed : ").append(printedTime).append("\n\n");
        } catch (Exception e) {
            Log.e(TAG, "Error formatting KOT text", e);
        }
        return formattedText.toString();
    }

    private void formatItemBlock(JSONObject item, String category, StringBuilder formattedText) throws JSONException {
        // Skip main item name for BANQUET NIGHTS
        if (!category.equalsIgnoreCase("BANQUET NIGHTS")) {
            formattedText.append(ESC_FONT_SIZE_LARGE)
                    .append(item.optString("quantity")).append(" x ").append(item.optString("item"))
                    .append(ESC_FONT_SIZE_RESET).append("\n");
        }

        // Handle addon object (grouped or flat)
        Object addonObj = item.opt("addon");
        if (addonObj instanceof JSONObject) {
            JSONObject addons = (JSONObject) addonObj;

            // Check if this is grouped addons (BANQUET-style)
            boolean isGroupedAddon = false;
            for (Iterator<String> keyIterator = addons.keys(); keyIterator.hasNext(); ) {
                Object val = addons.opt(keyIterator.next());
                if (val instanceof JSONObject) {
                    isGroupedAddon = true;
                    break;
                }
            }

            if (category.equalsIgnoreCase("BANQUET NIGHTS") || isGroupedAddon) {
                // Grouped addons (e.g. BANQUET NIGHTS)
                for (Iterator<String> groupIterator = addons.keys(); groupIterator.hasNext(); ) {
                    String groupName = groupIterator.next();
                    JSONObject groupItems = addons.optJSONObject(groupName);
                    if (groupItems != null && groupItems.length() > 0) {
                        formattedText.append(ESC_FONT_SIZE_MEDIUM)
                                .append("\n").append(groupName)
                                .append(ESC_FONT_SIZE_RESET).append("\n");

                        for (Iterator<String> subItemIterator = groupItems.keys(); subItemIterator.hasNext(); ) {
                            String subItemKey = subItemIterator.next();
                            JSONObject addonItem = groupItems.optJSONObject(subItemKey);
                            if (addonItem != null) {
                                printAddonItem(addonItem, formattedText);
                            }
                        }
                    }
                }
                String other = item.optString("other");
                if (other != null && !other.trim().isEmpty()) {
                    formattedText.append("\n").append("Note: ").append(other).append("\n");
//                    formattedText.append(ESC_FONT_SIZE_NOTE).append("\n").append("Note: ").append(other).append(ESC_FONT_SIZE_RESET).append("\n");
                }
            } else {
                // Flat addon list
                for (Iterator<String> addonIterator = addons.keys(); addonIterator.hasNext(); ) {
                    String addonKey = addonIterator.next();
                    JSONObject addonItem = addons.getJSONObject(addonKey);
                    printAddonItem(addonItem, formattedText);
                }
            }
        } else if (addonObj instanceof JSONArray) {
            JSONArray addonArray = (JSONArray) addonObj;
            for (int i = 0; i < addonArray.length(); i++) {
                JSONObject addonItem = addonArray.optJSONObject(i);
                if (addonItem != null) {
                    printAddonItem(addonItem, formattedText);
                }
            }
        }

        // Optional 'other' field (only if not banquet)
        if (!category.equalsIgnoreCase("BANQUET NIGHTS")) {
            String other = item.optString("other");
            if (other != null && !other.trim().isEmpty()) {
                formattedText.append("\n").append("Note: ").append(other).append("\n");
            }
        }


        formattedText.append("\n");
    }


    private void printAddonItem(JSONObject addonItem, StringBuilder formattedText) {
        String adName = addonItem.optString("ad_name", "").trim();
        String adQty = addonItem.optString("ad_qty", "0").trim();

        // Skip if addon is empty or zero quantity
        if (!adName.isEmpty() && !adQty.equals("0")) {
            formattedText.append(ESC_FONT_SIZE_LARGE)
                    .append("  ").append(adQty).append(" x ").append(adName)
                    .append(ESC_FONT_SIZE_RESET).append("\n");
        }
    }

    private String centerTextCat(String text, boolean isDoubleWidth) {

        if (TextUtils.isEmpty(text)) return "";

        // Normal font = 40 columns
        // Double width font = 20 columns
        int printerWidth = isDoubleWidth ? 30 : 45;

        if (text.length() >= printerWidth) {
            return text; // no centering possible
        }

        int leftPadding = (printerWidth - text.length()) / 2;

        return String.format(
                Locale.US,
                "%" + (leftPadding + text.length()) + "s",
                text
        );
    }

    private String centerText(String text, boolean isDoubleWidth) {
        int fullLineWidth = 45;
        int visualTextLength = isDoubleWidth ? text.length() * 2 : text.length();
        int spaces = (fullLineWidth - visualTextLength) / 2;
        if (spaces < 0) spaces = 0;
        return " ".repeat(spaces) + text;
    }
    private JSONObject getPrinterDetails(int printerId, JSONArray printersArray) {
        for (int i = 0; i < printersArray.length(); i++) {
            try {
                JSONObject printer = printersArray.getJSONObject(i);
                if (printer.getInt("id") == printerId) {
                    return printer;
                }
            } catch (JSONException e) {
                Log.e("API", "Error fetching printer details", e);
            }
        }
        return null; // If no matching printer is found
    }
}
