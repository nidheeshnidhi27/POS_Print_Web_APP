package com.example.posprint;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Map;

public class ReportHandler {
    private static final String TAG = "ReportHandler";
    private static final String ESC_FONT_BOLD_ON = "\u001B\u0045\u0001";
    private static final String ESC_FONT_BOLD_OFF = "\u001B\u0045\u0000";
    private static final String ESC_FONT_SIZE_LARGE = "\u001D\u0021\u0011";  // Double width + height
    private static final String ESC_FONT_RESET = "\u001D\u0021\u0000";
    public static String buildDailySummaryReport(JSONObject response, String type) {
        StringBuilder builder = new StringBuilder();

        // Header
        builder.append(ESC_FONT_BOLD_ON)
                .append(ESC_FONT_SIZE_LARGE)
                .append(type.replace("_", " ").toUpperCase())
                .append("\n")
                .append(response.optString("today_date", ""))
                .append("\n")
                .append(ESC_FONT_RESET).append(ESC_FONT_BOLD_OFF)
                .append("--------------------------------------------\n");

        // Booking / Guests (BOLD + LARGE)
        builder.append(ESC_FONT_BOLD_ON);
        builder.append("TOTAL BOOKING : ").append(response.optInt("total_bookings", 0)).append("\n\n");
        builder.append("TOTAL GUEST : ").append(response.optInt("booking_guests", 0)).append("\n\n");
        builder.append("TOTAL DINE-IN CUSTOMER: ").append(response.optInt("total_persons", 0)).append("\n\n");
        builder.append(ESC_FONT_BOLD_OFF);

        // Orders (BOLD + LARGE)
        builder.append(ESC_FONT_BOLD_ON);
        builder.append("Total In-Store Cash Orders : ").append(response.optInt("offline_cash_orders", 0)).append("\n\n");
        builder.append("Total In-Store Card Orders : ").append(response.optInt("offline_card_orders", 0)).append("\n\n");
        builder.append("Total In-Store Orders : ").append(response.optInt("total_offline_orders", 0)).append("\n\n");
        builder.append("Total Online Card Orders : ").append(response.optInt("total_online_card_orders", 0)).append("\n\n");
        builder.append("Total Online Cash Orders : ").append(response.optInt("total_online_cash_orders", 0)).append("\n\n");
        builder.append("Total Online Orders : ").append(response.optInt("total_online_orders", 0)).append("\n\n");

        builder.append("Total Cash amount : ").append(String.format("%.2f", response.optDouble("total_cash_amount", 0))).append("\n\n");
        builder.append("Total Petty Cash : ").append(String.format("%.2f", response.optDouble("total_petty_cash", 0))).append("\n\n");
        builder.append("Total Tips : ").append(String.format("%.2f", response.optDouble("total_tips", 0))).append("\n\n");
        builder.append("Total Cash Present : ").append(String.format("%.2f", response.optDouble("total_cash_present", 0))).append("\n\n");
        builder.append("Total Card amount from in-store machine : ")
                .append(String.format("%.2f", response.optDouble("total_offline_card_amount", 0))).append("\n\n");
        builder.append("Total Online Card Amount : ").append(String.format("%.2f", response.optDouble("total_online_card_amount", 0))).append("\n\n");
        builder.append("Total Card Amount : ").append(String.format("%.2f", response.optDouble("total_card_amount", 0))).append("\n");
        builder.append("Total Amount : ").append(String.format("%.2f", response.optDouble("total_amount", 0))).append("\n\n");
        builder.append("Total Dry Sales Amount : ").append(response.optString("totalFoodSales", "0.00")).append("\n\n");
        builder.append("Total Wet Sales Amount : ").append(response.optString("totalDrinkSales", "0.00")).append("\n\n");
        builder.append(ESC_FONT_BOLD_OFF);

        // Cancelled Orders
        builder.append("---------------------------------------------\n");
        builder.append(ESC_FONT_BOLD_ON);
        builder.append(centerText("CANCELLED ORDERS",false)).append("\n");
        builder.append(ESC_FONT_BOLD_OFF);
        builder.append("---------------------------------------------\n");
        builder.append("Order no.  Type           Amount      Reason\n");

        JSONArray cancelledOrders = response.optJSONArray("cancelledOrders");
        if (cancelledOrders != null && cancelledOrders.length() > 0) {
            for (int i = 0; i < cancelledOrders.length(); i++) {
                JSONObject order = cancelledOrders.optJSONObject(i);
                if (order != null) {
                    int orderNo = order.optInt("order_no", 0);
                    String typeStr = order.optString("order_type", "");
                    String amount = order.optString("amount", "0.00");
                    String reason = order.optString("other_info", "");

                    builder.append(String.format("%-10s %-13s %-10s %s\n",
                            orderNo, typeStr, amount, reason));
                }
            }
        }

        builder.append("---------------------------------------------\n");
        builder.append(ESC_FONT_BOLD_ON);
        builder.append("CANCELLED ORDERS : ")
                .append(response.optInt("total_cancelledOrders", 0)).append("\n");
        builder.append("TOTAL CANCELLED AMOUNT : ")
                .append(String.format("%.2f", response.optDouble("total_cancelled_amount", 0))).append("\n");
        builder.append(ESC_FONT_BOLD_OFF);
        builder.append("----------------------------------------------\n");
        builder.append(centerText("Thank you for visiting us!",false));
        builder.append("\n----------------------------------------------\n");

        return builder.toString();
    }

    public static String buildOnlineReport(JSONObject response, String type) {
        StringBuilder builder = new StringBuilder();

        // Header
        builder.append(ESC_FONT_BOLD_ON)
                .append(ESC_FONT_SIZE_LARGE)
                .append(type.replace("_", " ").toUpperCase())
                .append("\n")
                .append(response.optString("today_date", ""))
                .append("\n")
                .append(ESC_FONT_RESET).append(ESC_FONT_BOLD_OFF)
                .append("--------------------------------------------\n");

        if(type.equals("booking_report")){
            builder.append(ESC_FONT_BOLD_ON);
            builder.append("Total Bookings : ").append(response.optInt("total_bookings", 0)).append("\n\n");
            builder.append("Total Guests : ").append(response.optString("sum_of_guest", "0")).append("\n\n");
            builder.append(ESC_FONT_BOLD_OFF);

            builder.append("----------------------------------------------\n");
            builder.append("           Thank you for visiting us!\n");
            builder.append("----------------------------------------------\n");
        }else {
            // Main content (bold + large, left aligned)
            builder.append(ESC_FONT_BOLD_ON);
            builder.append("Total Cash Orders : ").append(response.optInt("total_cash_orders", 0)).append("\n\n");
            builder.append("Total Card Orders : ").append(response.optInt("total_card_orders", 0)).append("\n\n");
            builder.append("Total Orders : ").append(response.optInt("total_orders", 0)).append("\n\n");
            builder.append("Total Cash Amount : ").append(response.optString("total_cash_amount", "0.00")).append("\n\n");
            builder.append("Total Card Amount : ").append(response.optString("total_card_amount", "0.00")).append("\n\n");
            builder.append("Total Amount : ").append(response.optString("total_amount", "0.00")).append("\n\n");
            builder.append(ESC_FONT_BOLD_OFF);
            builder.append("----------------------------------------------\n");
            builder.append("           Thank you for visiting us!\n");
            builder.append("----------------------------------------------\n");
        }
        return builder.toString();
    }

    private static String centerText(String text, boolean isDoubleWidth) {
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
