package com.joopos.posprint;

import android.content.Context;
import android.icu.text.SimpleDateFormat;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONObject;

import java.util.Date;
import java.util.Locale;

public class MainsAwayHandler {
    private static final String ESC_FONT_SIZE_LARGE = "\u001B" + "!" + (char) 51;  // Double width + height + bold
    private static final String ESC_FONT_SIZE_MEDIUM = "\u001B" + "!" + (char) 46;
    private static final String ESC_FONT_SIZE_SMALL = "\u001B" + "!" + (char) 23;
    private static final String ESC_FONT_SIZE_RESET = "\u001B" + "!" + (char) 0;

    private final Context context;
    private final JSONObject data;
    private final String printerIP;
    private final int printerPort;

    public MainsAwayHandler(Context context, JSONObject data, String printerIP, int printerPort) {
        this.context = context;
        this.data = data;
        this.printerIP = printerIP;
        this.printerPort = printerPort;
    }

    public void printMainsAway() {
        StringBuilder text = new StringBuilder();

        String orderTime = data.optString("order_time", "");
        String customerName = data.optString("customer_name", "Walk In Customer");
        String waiterName = data.optString("waiter_name", "");
        int orderNo = data.optInt("order_no", 0);

        String orderType = data.optString("order_type", "");

        String tableNo = data.optString("tableno", "");
        int seatNo = data.optInt("table_seats", 0);

        String message = data.optString("message_print", "MAINS AWAY");

        text.append(new String(ESC_FONT_SIZE_LARGE)).append(centerTextNew("KOT :Kitchen", true)).append(new String(ESC_FONT_SIZE_RESET)).append("\n\n");
        text.append("Date: ").append(orderTime).append("\n");
        text.append("Customer: ").append(customerName).append("\n");
        text.append("Served by: ").append(waiterName).append("\n\n");
//        text.append(new String(ESC_FONT_SIZE_LARGE)).append(centerText("Takeaway #" + orderNo, true)).append(new String(ESC_FONT_SIZE_RESET)).append("\n\n");
        if(orderType.equals("dinein")) {

            if (tableNo != null &&
                    !tableNo.trim().isEmpty() &&
                    !tableNo.equalsIgnoreCase("null")) {
                String cleanTableNo = tableNo.replaceAll("(?i)table", "").trim();
                text.append(new String(ESC_FONT_SIZE_LARGE)).append(centerTextNew("Table: " + cleanTableNo, true)).append(new String(ESC_FONT_SIZE_RESET)).append("\n\n");
                text.append(new String(ESC_FONT_SIZE_LARGE)).append(centerTextNew("Seats: " + seatNo, true)).append(new String(ESC_FONT_SIZE_RESET)).append("\n\n");
            } else {
                text.append(new String(ESC_FONT_SIZE_LARGE)).append(centerTextNew("Table: -", true)).append(new String(ESC_FONT_SIZE_RESET)).append("\n\n");
                text.append(new String(ESC_FONT_SIZE_LARGE)).append(centerTextNew("Seats: -", true)).append(new String(ESC_FONT_SIZE_RESET)).append("\n\n");
            }

            /*text.append(new String(ESC_FONT_SIZE_LARGE)).append(centerText("Table: " + tableNo, true)).append(new String(ESC_FONT_SIZE_RESET)).append("\n\n");
            text.append(new String(ESC_FONT_SIZE_LARGE)).append(centerText("Seats: " + seatNo, true)).append(new String(ESC_FONT_SIZE_RESET)).append("\n\n");*/
        }else{
            text.append(new String(ESC_FONT_SIZE_LARGE)).append(centerTextNew(orderType+" "+orderNo, true)).append(new String(ESC_FONT_SIZE_RESET)).append("\n\n");
        }


        text.append("Message from POS:\n");
        text.append("-----------------------------------------\n");
        text.append(ESC_FONT_SIZE_LARGE).append(centerTextNew(message, true)).append(ESC_FONT_SIZE_RESET).append("\n");
        text.append("-----------------------------------------\n\n");
        text.append(ESC_FONT_SIZE_MEDIUM).append(centerText("*** KITCHEN COPY ***", true)).append(ESC_FONT_SIZE_RESET).append("\n\n");

        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault());
        String printedTime = sdf.format(new Date());

        text.append("Printed : ").append(printedTime).append("\n\n");

        String finalBytes = String.valueOf(text);
        try {
            //                                    todo _hide 02/12
//            new PrintConnection(context, printerIP, printerPort, finalBytes).execute();
            PrintConnection pc = new PrintConnection(context);

            pc.printWithStatusCheck(printerIP, printerPort, finalBytes, (success, msg) -> {
                Log.d("MainsAway", "Print Status: " + success + " → " + msg);
            });
        } catch (Exception e) {
            Log.e("MainsAwayPrint", "Error while printing mains away", e);
        }
    }

    private String centerText(String text, boolean isDoubleWidth) {
        int fullLineWidth = 45;
        int visualTextLength = isDoubleWidth ? text.length() * 2 : text.length();
        int spaces = (fullLineWidth - visualTextLength) / 2;
        if (spaces < 0) spaces = 0;
        return " ".repeat(spaces) + text;
    }

    private String centerTextNew(String text, boolean isDoubleWidth) {

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
}

