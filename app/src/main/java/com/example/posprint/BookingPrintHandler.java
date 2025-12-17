package com.example.posprint;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

public class BookingPrintHandler {
    private Context context;
    private JSONObject response;
//    private static final String ESC_FONT_SIZE_LARGE = "\u001B" + "!" + (char) 51;  // Double width + height + bold
    private static final String ESC_FONT_BOLD_ON = "\u001B\u0045\u0001";
    private static final String ESC_FONT_BOLD_OFF = "\u001B\u0045\u0000";
    private static final String ESC_FONT_SIZE_LARGE = "\u001D\u0021\u0011";  // Double width + height
    private static final String ESC_FONT_RESET = "\u001D\u0021\u0000";
    private static final String ESC_FONT_SIZE_MEDIUM = "\u001B" + "!" + (char) 46;
    private static final String ESC_FONT_SIZE_SMALL = "\u001B" + "!" + (char) 23;
    private static final String ESC_FONT_SIZE_RESET = "\u001B" + "!" + (char) 0;

    public BookingPrintHandler(Context context, JSONObject response) {
        this.context = context;
        this.response = response;
    }

    public void handleBookingPrint() {
        try {
            JSONArray printers = response.getJSONArray("printers");
            JSONArray printerSetup = response.getJSONArray("printersetup");
            int printerId = printerSetup.getInt(0); // assuming only one printer used
            JSONObject printer = getPrinterDetails(printerId, printers);

            if (printer == null) return;

            String printerIP = printer.optString("ip");
            int printerPort = Integer.parseInt(printer.optString("port", "9100"));

            String formattedText = formatBookingText(response);
            //                                    todo _hide 02/12
//            new PrintConnection(context, printerIP, printerPort, formattedText).execute();

        } catch (Exception e) {
            Log.e("BookingPrint", "Error printing booking", e);
        }
    }

    private String formatBookingText(JSONObject response) {
        StringBuilder builder = new StringBuilder();

        try {
            JSONObject outlet = response.getJSONArray("outlets").getJSONObject(0);
            JSONObject booking = response.getJSONArray("data").getJSONObject(0);

            String name = outlet.optString("name");
            String address = outlet.optString("address");
            String phone = outlet.optString("phone");
            String bNote = booking.optString("booking_notes");

            // Bold + Large Title - Restaurant Name (centered)
            builder.append(ESC_FONT_BOLD_ON)
                    .append(ESC_FONT_SIZE_LARGE)
                    .append(centerText(name.toUpperCase(), true))
                    .append(ESC_FONT_RESET).append("\n");

            // Address and Phone Centered
            builder.append(centerText(address, false)).append("\n");
            builder.append(centerText(phone, false)).append("\n");

            // Booking ID and Status Centered
            builder.append(ESC_FONT_BOLD_ON)
                    .append(centerText("BOOKING ID : " + booking.optInt("booking_id"), false)).append("\n");
            builder.append(centerText("BOOKING STATUS : " + booking.optString("status").toUpperCase(), false)).append("\n")
                    .append(ESC_FONT_BOLD_OFF);

            builder.append("-----------------------------------------\n");

            // Booking Details - Left Aligned
            builder.append("Name : ").append(booking.optString("booking_name")).append("\n");
            builder.append("Tel  : ").append(booking.optString("mobile")).append("\n");
            builder.append("Email: ").append(booking.optString("email")).append("\n");
            if (bNote != null && !bNote.equalsIgnoreCase("null") && !bNote.trim().isEmpty()) {
                builder.append("Notes: ").append(bNote).append("\n");
            }
            builder.append("No of Guests: ").append(booking.optInt("number_guest")).append("\n");

            builder.append("Booked Time : ").append(booking.optString("date_created")).append("\n");

            builder.append("------------------------------------------\n");

            builder.append("Date of Booking: ").append(booking.optString("date_booking")).append("\n");
            builder.append("Time of Booking: ").append(booking.optString("booking_time")).append("\n");

            builder.append("------------------------------------------\n");

            // Footer Centered
            builder.append(centerText("Thank you for visiting us!", false)).append("\n");

        } catch (Exception e) {
            Log.e("BookingPrint", "Error formatting booking text", e);
        }

        return builder.toString();
    }

    private String centerText(String text, boolean isDoubleWidth) {
        int fullLineWidth = 45;
        int visualTextLength = isDoubleWidth ? text.length() * 2 : text.length();
        int spaces = (fullLineWidth - visualTextLength) / 2;
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

