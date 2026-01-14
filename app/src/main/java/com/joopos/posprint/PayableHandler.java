package com.joopos.posprint;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.icu.text.SimpleDateFormat;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PayableHandler {
    private static final String TAG = "InvoiceHandler";
    private Context context;
    private JSONObject data, details;
    private JSONArray outlets;
    private String printerIP;
    private int printerPort;
    int totalItems = 0;

    private static final byte[] ESC_FONT_SIZE_LARGE_Double = new byte[]{0x1B, 0x21, 0x34};

    private static final byte[] ESC_FONT_SIZE_LARGE = new byte[]{0x1B, 0x21, 0x1A};
    private static final byte[] ESC_FONT_SIZE_RESET = new byte[]{0x1B, 0x21, 0x00};
    private static final byte[] ESC_FONT_SIZE_MEDIUM = new byte[]{0x1B, 0x21, 0x0C};
    private static final byte[] SET_CODE_PAGE_CP437 = new byte[]{0x1B, 0x74, 0x00};

    private static final byte[] ESC_ALIGN_CENTER = new byte[]{0x1B, 0x61, 0x01};
    private static final byte[] ESC_ALIGN_LEFT   = new byte[]{0x1B, 0x61, 0x00};


    int lineLength = 40;
    String givenAmt = "Given Amount: ";
    String changeAmt = "Change Amount: ";
    String subtotalLabel = "Subtotal: ";
    String bagFeeLabel = "Bag Fee: ";
    String delChargeLabel = "Delivery Fee: ";
    String tipAmountLabel = "Tip Amount: ";
    String discountLabel = "Discount: ";
    String totalPayLabel = "Total PAYABLE: ";
    String totalItemLabel = "Total Item(s) ";
    String printType;
    private JSONObject restSettings, features;
    private JSONObject settings;
    private final Map<String, String> queryParams;

    private Bitmap logoBitmap;


    public PayableHandler(Context context, JSONObject data, JSONArray outlets, JSONObject details, String printerIP, int printerPort, String printType, JSONObject restSettings, Map<String, String> queryParams) {
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

    public PayableHandler(Context context, JSONObject data, JSONArray outlets, JSONObject details, String printerIP, int printerPort, String printType, JSONObject restSettings,JSONObject features, Map<String, String> queryParams, Bitmap logoBitmap) {
        this.context = context;
        this.data = data;
        this.outlets = outlets;
        this.details = details;
        this.printerIP = printerIP;
        this.printerPort = printerPort;
        this.printType = printType;
        this.restSettings = restSettings;
        this.features = features;
        this.queryParams = queryParams;
        this.logoBitmap = logoBitmap;
    }

    private JSONObject detailsObject;
    private JSONArray detailsArray;

    public PayableHandler(Context context, JSONObject data, JSONArray outlets, JSONArray details, String printerIP, int printerPort, String printType, JSONObject restSettings, Map<String, String> queryParams, JSONObject settings) {
        this.context = context;
        this.data = data;
        this.outlets = outlets;
        this.detailsObject = null;
        this.detailsArray = details;
        this.printerIP = printerIP;
        this.printerPort = printerPort;
        this.printType = printType;
        this.restSettings = restSettings;
        this.settings = settings;
        this.queryParams = queryParams;
    }

    public PayableHandler(Context context, JSONObject data, JSONArray outlets, JSONArray details, String printerIP, int printerPort, String printType, JSONObject restSettings, JSONObject features, Map<String, String> queryParams, JSONObject settings) {
        this.context = context;
        this.data = data;
        this.outlets = outlets;
        this.detailsObject = null;
        this.detailsArray = details;
        this.printerIP = printerIP;
        this.printerPort = printerPort;
        this.printType = printType;
        this.restSettings = restSettings;
        this.features = features;
        this.settings = settings;
        this.queryParams = queryParams;
    }

    public byte[] formatPayableBytes() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            output.write(SET_CODE_PAGE_CP437);
            if (outlets.length() > 0) {
                JSONObject outlet = outlets.getJSONObject(0);

                String outletName = outlet.getString("name");
                String outletPhone = outlet.getString("phone");
                String outletAddress = outlet.getString("address");

                ByteArrayOutputStream output1 = new ByteArrayOutputStream();

                if (logoBitmap != null) {
                    int printerWidthPx = 384; // for 55mm printer
                    int printerHieghtPx = 60;
                    Bitmap scaledLogo = Bitmap.createScaledBitmap(
                            logoBitmap,
                            printerWidthPx,
                            (printerHieghtPx * printerWidthPx) / logoBitmap.getWidth(),
//                            printerHieghtPx,
                            true
                    );

                    byte[] logoBytes = convertBitmapToEscPos(scaledLogo);
                    output.write(new byte[]{0x1B, 0x61, 0x01});

                    // Write logo
                    output.write(logoBytes);

                    // Reset alignment: ESC a 0
                    output.write(new byte[]{0x1B, 0x61, 0x00});

                    output.write("\n".getBytes());
                } else {
                    output.write(ESC_ALIGN_CENTER);
                    output.write(ESC_FONT_SIZE_LARGE);
                    output.write(ESC_ALIGN_CENTER);
                    output.write(centerText(outletName).getBytes());
                    output.write("\n".getBytes());
                    output.write(ESC_FONT_SIZE_RESET);
                    output.write(ESC_ALIGN_LEFT);
                }
                output.write(ESC_ALIGN_CENTER);
                output.write(ESC_FONT_SIZE_MEDIUM);
                output.write(centerText(outletAddress).getBytes());
                output.write("\n".getBytes());
                output.write(centerText("Phone: " + outletPhone).getBytes());
                output.write("\n".getBytes());


                String vat = restSettings.optString("vat", "");

                if (vat != null && !vat.equalsIgnoreCase("null") && !vat.trim().isEmpty()) {
                    output.write(centerText("VAT: " + vat).getBytes());
                    output.write("\n".getBytes());

                }

                output.write(ESC_FONT_SIZE_MEDIUM);
                String invoice = data.getString("order_no");
//                output.write(centerText("Invoice No: #" + invoice).getBytes());

                output.write("Invoice No: ".getBytes());

// Set large font for invoice value
                byte[] largeFontOn = new byte[]{0x1B, 0x21, 0x30};  // Double width + height
                byte[] fontReset = new byte[]{0x1B, 0x21, 0x00};    // Reset to normal font

// Print invoice value in large font
                output.write(largeFontOn);
                output.write(("#"+invoice).getBytes());
                output.write(fontReset);

                output.write("\n".getBytes());
                output.write(ESC_ALIGN_LEFT);
                output.write(ESC_FONT_SIZE_RESET);
                String type = data.getString("order_type");

                String tableno = data.optString("tableno", "");
                int tableSeats = data.optInt("table_seats", 0);

                String address = data.optString("customer_address", "");
                String postcode = data.optString("postcode", "");
                String phone = data.optString("customer_phone", "");

                if(type.equalsIgnoreCase("dinein")) {

                    output.write(ESC_ALIGN_CENTER);
                    output.write(ESC_FONT_SIZE_LARGE);
                    output.write(centerText("Dine-In").getBytes());
                    output.write("\n".getBytes());
                    output.write(ESC_FONT_SIZE_RESET);
                    output.write(ESC_ALIGN_LEFT);

                    output.write("-------------------------------------------\n".getBytes());
                    output.write(("Date: " + data.getString("order_time") + "\n").getBytes());
                    output.write(("Customer: " + data.getString("customer_name") + "\n").getBytes());

                    if (address != null && !address.trim().isEmpty() && !"null".equalsIgnoreCase(address)) {
                        output.write(("Address: " + address + ", " + postcode + "\n").getBytes());
                    }

                    if (phone != null && !phone.trim().isEmpty() && !"null".equalsIgnoreCase(phone)) {
                        output.write(("Phone: " + phone + "\n").getBytes());
                    }

//                    if (!TextUtils.isEmpty(address)) output.write(("Address: " + data.optString("customer_address", "") + ", " + data.getString("postcode") + "\n").getBytes());
//                    if (!TextUtils.isEmpty(phone)) output.write(("Phone: " + data.optString("customer_phone", "") + "\n").getBytes());

                    if (tableno != null &&
                            !tableno.trim().isEmpty() &&
                            !tableno.equalsIgnoreCase("null")) {

                        output.write(ESC_FONT_SIZE_LARGE);
                        output.write((data.getString("tableno") + "\n").getBytes());
                        output.write(ESC_FONT_SIZE_RESET);
                        output.write(("Seats: " + data.getString("table_seats") + "\n").getBytes());
                    } else {
                        output.write(ESC_FONT_SIZE_LARGE);
                        output.write(("Table: - \n").getBytes());
                        output.write(ESC_FONT_SIZE_RESET);
                        output.write(("Seats: -" + "\n").getBytes());
                    }


                }else if(type.equalsIgnoreCase("delivery")){

                    output.write(ESC_ALIGN_CENTER);
                    output.write(ESC_FONT_SIZE_LARGE);
                    output.write(centerText("Delivery").getBytes());
                    output.write("\n".getBytes());
                    output.write(ESC_FONT_SIZE_RESET);
                    output.write(ESC_ALIGN_LEFT);

                    output.write("-------------------------------------------\n".getBytes());
                    output.write(("Date: " + data.getString("order_time") + "\n").getBytes());
                    output.write(("Customer: " + data.getString("customer_name") + "\n").getBytes());
                    output.write(("Address: " + data.getString("customer_address")+ ", "  + data.getString("postcode") + "\n").getBytes());
                    output.write(("Phone: " + data.getString("customer_phone") + "\n").getBytes());
                }else if(type.equalsIgnoreCase("takeaway")) {

                    output.write(ESC_ALIGN_CENTER);
                    output.write(ESC_FONT_SIZE_LARGE);
                    output.write(centerText("Takeaway").getBytes());
                    output.write("\n".getBytes());
                    output.write(ESC_FONT_SIZE_RESET);
                    output.write(ESC_ALIGN_LEFT);

                    output.write("-------------------------------------------\n".getBytes());
                    output.write(("Date: " + data.getString("order_time") + "\n").getBytes());
                    output.write(("Customer: " + data.getString("customer_name") + "\n").getBytes());

                    if (address != null && !address.trim().isEmpty() && !"null".equalsIgnoreCase(address)) {
                        output.write(("Address: " + address + ", " + postcode + "\n").getBytes());
                    }

                    if (phone != null && !phone.trim().isEmpty() && !"null".equalsIgnoreCase(phone)) {
                        output.write(("Phone: " + phone + "\n").getBytes());
                    }

//                    if (address != null && !TextUtils.isEmpty(address)) output.write(("Address: " + data.optString("customer_address", "") + ", " + data.getString("postcode") + "\n").getBytes());
//                    if (phone != null && !TextUtils.isEmpty(phone)) output.write(("Phone: " + data.optString("customer_phone", "") + "\n").getBytes());
                }else{
                    output.write(ESC_ALIGN_CENTER);
                    output.write(ESC_FONT_SIZE_LARGE);
                    output.write(centerText(type).getBytes());
                    output.write("\n".getBytes());
                    output.write(ESC_FONT_SIZE_RESET);
                    output.write(ESC_ALIGN_LEFT);

                    output.write("-------------------------------------------\n".getBytes());
                    output.write(("Date: " + data.getString("order_time") + "\n").getBytes());
                    output.write(("Customer: " + data.getString("customer_name") + "\n").getBytes());
                    if (address != null && !address.trim().isEmpty() && !"null".equalsIgnoreCase(address)) {
                        output.write(("Address: " + address + ", " + postcode + "\n").getBytes());
                    }

                    if (phone != null && !phone.trim().isEmpty() && !"null".equalsIgnoreCase(phone)) {
                        output.write(("Phone: " + phone + "\n").getBytes());
                    }
//                    if (address != null && !TextUtils.isEmpty(address)) output.write(("Address: " + data.optString("customer_address", "") + ", " + data.getString("postcode") + "\n").getBytes());
//                    if (!TextUtils.isEmpty(phone)) output.write(("Phone: " + data.optString("customer_phone", "") + "\n").getBytes());
                }

            }
            output.write("\n-------------------------------------------\n".getBytes());

            Iterator<String> categories = details.keys();
            while (categories.hasNext()) {

                JSONObject items = details.getJSONObject(categories.next());
                Iterator<String> itemIds = items.keys();

                while (itemIds.hasNext()) {
                    JSONObject item = items.getJSONObject(itemIds.next());
                    output.write(ESC_FONT_SIZE_MEDIUM);

                    String itemName = item.getString("item");
                    double quantity = Double.parseDouble(item.getString("quantity"));
                    double amount = Double.parseDouble(item.getString("amount"));

                    JSONObject addonObj = item.optJSONObject("addon");
                    boolean hasAddon = addonObj != null && addonObj.length() > 0;

                    totalItems += quantity;

                    // ------------ WORD WRAP FUNCTION FOR ITEM NAME ----------
                    int maxNameWidth = 32;  // Adjust for printer width

                    List<String> nameLines = new ArrayList<>();
                    String[] words = itemName.split(" ");
                    StringBuilder current = new StringBuilder();

                    for (String w : words) {
                        if (current.length() + w.length() + 1 > maxNameWidth) {
                            nameLines.add(current.toString());
                            current = new StringBuilder(w);
                        } else {
                            if (current.length() > 0) current.append(" ");
                            current.append(w);
                        }
                    }
                    nameLines.add(current.toString());
                    // --------------------------------------------------------

                    // ---------- FIRST LINE (with qty + amount right aligned) ----------
                    String firstLineName = nameLines.get(0);
                    String firstLine = String.format("%.0f x %-"+maxNameWidth+"s", quantity, firstLineName);

                    output.write(firstLine.getBytes());

                    if (amount > 0) {
                        output.write(new byte[]{(byte) 0x9C});  // £
                        double amountNew = quantity * amount;
                        output.write(String.format("%.2f", amountNew).getBytes());
                    }
                    output.write("\n".getBytes());
                    // --------------------------------------------------------------------

                    // -------- REMAINING WRAPPED LINES (NO AMOUNT) --------
                    for (int i = 1; i < nameLines.size(); i++) {
                        output.write(("    " + nameLines.get(i) + "\n").getBytes());
                    }

                    // ---------------- ADDONS ----------------
                    if (hasAddon) {
                        Iterator<String> addonKeys = addonObj.keys();
                        while (addonKeys.hasNext()) {
                            JSONObject addon = addonObj.getJSONObject(addonKeys.next());

                            String adName = addon.optString("ad_name", "").trim();
                            String adQtyStr = addon.optString("ad_qty", "").trim();
                            String adPriceStr = addon.optString("ad_price", "").trim();

                            // 🚫 SKIP EMPTY / ZERO ADDONS COMPLETELY
                            if (adName.isEmpty()
                                    || adQtyStr.isEmpty()
                                    || adQtyStr.equals("0")) {
                                continue;
                            }
                            double adQty = Double.parseDouble(adQtyStr);
                            double adPrice = adPriceStr.isEmpty() ? 0 : Double.parseDouble(adPriceStr);

                            // WRAP ADDON NAMES ALSO
                            List<String> addonLines = new ArrayList<>();
                            String[] adWords = adName.split(" ");
                            StringBuilder adCurrent = new StringBuilder();

                            int addonWidth = 29;  // keeps addon alignment clean

                            for (String w : adWords) {
                                if (adCurrent.length() + w.length() + 1 > addonWidth) {
                                    addonLines.add(adCurrent.toString());
                                    adCurrent = new StringBuilder(w);
                                } else {
                                    if (adCurrent.length() > 0) adCurrent.append(" ");
                                    adCurrent.append(w);
                                }
                            }
                            addonLines.add(adCurrent.toString());

                            // First addon line (with price)
                            String adFirst = String.format("   %.0f x %-"+addonWidth+"s", adQty, addonLines.get(0));
                            output.write(adFirst.getBytes());

                            if (adPrice > 0) {
                                output.write(new byte[]{(byte) 0x9C});
                                double priceNew = adQty * adPrice;
                                output.write(String.format("%.2f", priceNew).getBytes());
                            }
                            output.write("\n".getBytes());

                            // Remaining addon wrapped lines
                            for (int i = 1; i < addonLines.size(); i++) {
                                output.write(("       " + addonLines.get(i) + "\n").getBytes());
                            }
                        }
                    }

                    output.write("\n".getBytes());
                    output.write(ESC_FONT_SIZE_RESET);
                }
            }

            output.write("-------------------------------------------\n".getBytes());

            double givenAmountBal = parseSafeDouble(data.optString("paid", "0"));
            double grandTotalBal    = parseSafeDouble(data.optString("grandtotal", "0"));

            double balanceAmount = givenAmountBal - grandTotalBal;

            if(data.getString("payment_method").equalsIgnoreCase("cash")) {
                String givenAmont = data.getString("paid");
                if (givenAmont != null && !givenAmont.equalsIgnoreCase("null") && !givenAmont.equals("0.00")) {
                    output.write(paddedLine(givenAmt, givenAmont));
                }
            }

            String subtotal = data.getString("subtotal");
            output.write(paddedLine(subtotalLabel, subtotal));

            if ("Before_Invoice".equalsIgnoreCase(printType)) {
                String tipAmount = queryParams.getOrDefault("tip", "0.00");

                if (tipAmount != null && !tipAmount.equalsIgnoreCase("null") && !tipAmount.equals("0.00") && !tipAmount.equalsIgnoreCase("undefined") && !tipAmount.isEmpty()) {
                    output.write(paddedLine(tipAmountLabel, tipAmount));
                }
                String serviceFee = queryParams.getOrDefault("servicefee", "0.00");
                if (serviceFee != null && !serviceFee.equalsIgnoreCase("null") && !serviceFee.equals("0.00")) {
                    output.write(paddedLine("Service Charge", serviceFee));
                }

                String discount = queryParams.getOrDefault("discount", "0.00");
//                if (!"0".equals(discount) && !"".equals(discount)) {
                if (discount != null && !discount.equalsIgnoreCase("null") && !discount.equals("0.00") && !"0".equals(discount) && !"".equals(discount)) {
                    output.write(paddedLine(discountLabel, discount));
                }
                String bagFee = data.optString("bag_fee", "0.00");
                if (bagFee != null && !bagFee.equalsIgnoreCase("null") && !bagFee.equals("0.00")) {
                    output.write(paddedLine(bagFeeLabel, bagFee));
                }

                String deliveryFee = queryParams.getOrDefault("delfee", "0.00");
                if (deliveryFee != null && !deliveryFee.equalsIgnoreCase("null") && !deliveryFee.equals("0.00") && !"0".equals(deliveryFee) && !"".equals(deliveryFee)) {
//                if (!"0".equals(deliveryFee) && !"".equals(deliveryFee)) {
                    output.write(paddedLine("Delivery Fee", deliveryFee));
                }

                String deposit = queryParams.getOrDefault("deposit", "0.00");
                if (deposit != null && !deposit.equalsIgnoreCase("null") && !deposit.equals("0.00") && !"0".equals(deposit) && !"".equals(deposit)) {
//                if (!"0".equals(deposit) && !"".equals(deposit)) {
                    output.write(paddedLine("Deposit", deposit));
                }

                String grandTotal = queryParams.getOrDefault("total", "0.00");
                if (!"0".equals(grandTotal) && !"".equals(grandTotal)) {
                    output.write(ESC_FONT_SIZE_MEDIUM);
                    output.write(paddedLine(totalPayLabel, grandTotal));
                    output.write(ESC_FONT_SIZE_RESET);
                }
            }
            else {
                String tipAmount = data.optString("tips", "0.00");
                if (tipAmount != null && !tipAmount.equalsIgnoreCase("null") && !tipAmount.equals("0.00") && !tipAmount.equalsIgnoreCase(".00")) {
                    output.write(paddedLine(tipAmountLabel, tipAmount));
                }
                String serviceCharge = data.optString("service_charge", "0.00");
                if (serviceCharge != null && !serviceCharge.equalsIgnoreCase("null") && !serviceCharge.equals("0.00")) {
                    output.write(paddedLine("Service Charge", serviceCharge));
                }
                String bagFee = data.optString("bag_fee", "0.00");
                if (bagFee != null && !bagFee.equalsIgnoreCase("null") && !bagFee.equals("0.00")) {
                    output.write(paddedLine(bagFeeLabel, bagFee));
                }
                String deliveryCharge = data.optString("delivery_charge", "0.00");
                if (deliveryCharge != null && !deliveryCharge.equalsIgnoreCase("null") && !deliveryCharge.equals("0.00")) {
                    output.write(paddedLine(delChargeLabel, deliveryCharge));
                }
                String discount = data.optString("discount", "0.00");
                if (discount != null && !discount.equalsIgnoreCase("null") && !discount.equals("0.00")) {
                    output.write(paddedLine(discountLabel, discount));
                }
                String grandTotal = data.getString("grandtotal");
                if (grandTotal != null && !grandTotal.equalsIgnoreCase("null") && !grandTotal.equals("0.00")) {
                    output.write(ESC_FONT_SIZE_MEDIUM);
//                    TODO NIDHI 04/11
                    output.write(paddedLine("Total", grandTotal));
                    output.write(ESC_FONT_SIZE_RESET);
                }

                if(data.getString("payment_method").equalsIgnoreCase("cash")) {
                    output.write(paddedLine(changeAmt, String.format(Locale.US, "%.2f", balanceAmount)));
                }

            }
            String totalItemLabel = "Total Item(s):";
            String totalItemLine = String.format("%-27s %10s\n", totalItemLabel, String.valueOf(totalItems));
            output.write(totalItemLine.getBytes("CP437"));
            String paySatus = data.getString("payment_status");
            if(paySatus.equals("1")) {
                String paymentMode = "Payment Mode:";
                String paymentMethod = data.getString("payment_method");
                output.write(String.format("%-30s %10s\n", paymentMode, String.valueOf(paymentMethod)).getBytes("CP437"));
            }
            output.write("-------------------------------------------\n".getBytes());
            output.write(ESC_FONT_SIZE_MEDIUM);
            output.write(centerText("Thank you for visiting us!").getBytes());
            output.write("\n".getBytes());
            output.write(ESC_FONT_SIZE_RESET);
            output.write("-------------------------------------------\n".getBytes());
            String siteUrl = restSettings.optString("online_url", "");
            String footerText = restSettings.optString("footer_text", "");

            if (siteUrl != null && !siteUrl.equalsIgnoreCase("null") && !siteUrl.trim().isEmpty()) {
                output.write(new byte[]{0x1B, 0x61, 0x01});  // Center alignment
                output.write(generateQRCodeESC(siteUrl));   // Your method for QR code bytes
                output.write("\n".getBytes());
                output.write(new byte[]{0x1B, 0x61, 0x00});  // Left alignment
            }
            if (footerText != null && !footerText.equalsIgnoreCase("null") && !footerText.trim().isEmpty()) {
                output.write(ESC_FONT_SIZE_MEDIUM);
                output.write(new byte[]{0x1B, 0x74, 0x00}); // Code page CP437 for £
                output.write(new byte[]{0x1B, 0x61, 0x01}); // Center alignment
                output.write(footerText.getBytes("CP437"));
                output.write("\n\n".getBytes());
                output.write(new byte[]{0x1B, 0x61, 0x00});
                output.write(ESC_FONT_SIZE_RESET);
            }
            output.write(("Served by: "+ data.getString("waiter_name") +"\n").getBytes());

            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault());
            String printedTime = sdf.format(new Date());

            output.write(("Printed : " + printedTime + "\n\n").getBytes());

            output.write(new byte[]{0x1B, 0x64, 0x03}); // Feed 3 lines

            int cashDrawerFeature = features != null ? features.optInt("cash_drawer", 0) : 0;
            if (cashDrawerFeature == 1) {
                int pin = 0;
                if (restSettings != null && !restSettings.isNull("cash_drawer_pin")) {
                    pin = restSettings.optInt("cash_drawer_pin", 0);
                }
                byte[] drawerPulse = new byte[]{0x1B, 0x70, (byte) (pin == 1 ? 0x01 : 0x00), 0x3C, (byte) 0xFF};
                output.write(drawerPulse);
            }
            output.write(new byte[]{0x1D, 0x56, 0x00}); // Full cut

        } catch (Exception e) {
            Log.e(TAG, "Error formatting print text", e);
        }
        return output.toByteArray();
    }

    public static byte[] convertBitmapToEscPos(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int newWidth = (width + 7) / 8 * 8;
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, newWidth, height, false);

        int[] pixels = new int[resized.getWidth() * resized.getHeight()];
        resized.getPixels(pixels, 0, resized.getWidth(), 0, 0, resized.getWidth(), resized.getHeight());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        baos.write(0x1D);
        baos.write(0x76);
        baos.write(0x30);
        baos.write(0x00);

        int widthBytes = resized.getWidth() / 8;
        baos.write(widthBytes & 0xFF);
        baos.write((widthBytes >> 8) & 0xFF);
        baos.write(height & 0xFF);
        baos.write((height >> 8) & 0xFF);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < widthBytes; x++) {
                byte b = 0;
                for (int bit = 0; bit < 8; bit++) {
                    int pixelX = x * 8 + bit;
                    int pixelY = y;
                    int color = pixels[pixelY * resized.getWidth() + pixelX];
                    int r = (color >> 16) & 0xFF;
                    int g = (color >> 8) & 0xFF;
                    int bColor = color & 0xFF;
                    int gray = (r + g + bColor) / 3;

                    if (gray < 128) b |= (byte)(1 << (7 - bit));
                }
                baos.write(b);
            }
        }
        return baos.toByteArray();
    }

    private byte[] decodeBitmapToEscPos(Bitmap bitmap) throws IOException {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int bytesPerLine = (width + 7) / 8;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        for (int y = 0; y < height; y += 24) {
            baos.write(0x1B); // ESC
            baos.write('*');  // Select bit-image mode
            baos.write(33);   // 24-dot double-density
            baos.write(bytesPerLine & 0xFF);       // nL
            baos.write((bytesPerLine >> 8) & 0xFF); // nH

            for (int x = 0; x < width; x++) {
                for (int k = 0; k < 3; k++) { // 3 bytes = 24 dots vertically
                    byte slice = 0;
                    for (int b = 0; b < 8; b++) {
                        int yPos = y + k * 8 + b;
                        int pixel = 0;
                        if (yPos < height) {
                            int color = pixels[yPos * width + x];
                            int r = (color >> 16) & 0xFF;
                            int g = (color >> 8) & 0xFF;
                            int bColor = color & 0xFF;
                            int gray = (r + g + bColor) / 3;
                            if (gray < 128) {
                                pixel = 1;
                            }
                        }
                        slice |= (pixel << (7 - b));
                    }
                    baos.write(slice);
                }
            }
            baos.write(0x0A); // line feed after each 24-dot block
        }

        return baos.toByteArray();
    }

    public static Bitmap getBitmapFromURL(String src) {
        try {
            URL url = new URL(src);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            return BitmapFactory.decodeStream(input);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    public byte[] formatPayableSplitBytes() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            output.write(SET_CODE_PAGE_CP437);
            if (outlets.length() > 0) {
                JSONObject outlet = outlets.getJSONObject(0);
                String outletName = outlet.getString("name");
                String outletPhone = outlet.getString("phone");
                String outletAddress = outlet.getString("address");
                output.write(ESC_FONT_SIZE_LARGE);
                output.write(centerText(outletName).getBytes());
                output.write("\n".getBytes());
                output.write(ESC_FONT_SIZE_RESET);
                output.write(ESC_FONT_SIZE_MEDIUM);
                output.write(centerText(outletAddress).getBytes());
                output.write("\n".getBytes());
                output.write(centerText("Phone: " + outletPhone).getBytes());
                output.write("\n".getBytes());
                String invoice = data.getString("order_no");
//                output.write(centerText("Invoice No: #" + invoice).getBytes());

                output.write("Invoice No: ".getBytes());

// Set large font for invoice value
                byte[] largeFontOn = new byte[]{0x1B, 0x21, 0x30};  // Double width + height
                byte[] fontReset = new byte[]{0x1B, 0x21, 0x00};    // Reset to normal font

// Print invoice value in large font
                output.write(largeFontOn);
                output.write(("#"+invoice).getBytes());
                output.write(fontReset);


                output.write("\n".getBytes());
                output.write(ESC_FONT_SIZE_RESET);
                String type = data.getString("order_type");
                if(type.equalsIgnoreCase("dinein")) {
                    output.write(ESC_FONT_SIZE_LARGE);
                    output.write(centerText("Dine-In").getBytes());
                    output.write(ESC_FONT_SIZE_RESET);
                    output.write("\n-------------------------------------------\n".getBytes());
                    output.write(("Date: " + data.getString("order_time") + "\n").getBytes());
                    output.write(("Customer: " + data.getString("customer_name") + "\n").getBytes());
                    output.write(ESC_FONT_SIZE_LARGE);
                    output.write((data.getString("tableno") + "\n").getBytes());
                    output.write(ESC_FONT_SIZE_RESET);
                    output.write(("Seats: " + data.getString("table_seats") + "\n").getBytes());
                }else if(type.equalsIgnoreCase("delivery")){
                    output.write(ESC_FONT_SIZE_LARGE);
                    output.write(centerText("Delivery").getBytes());
                    output.write(ESC_FONT_SIZE_RESET);
                    output.write("\n-------------------------------------------\n".getBytes());
                    output.write(("Date: " + data.getString("order_time") + "\n").getBytes());
                    output.write(("Customer: " + data.getString("customer_name") + "\n").getBytes());
                    output.write(("Address: " + data.getString("customer_address")+ ", "  + data.getString("postcode") + "\n").getBytes());
                    output.write(("Phone: " + data.getString("customer_phone") + "\n").getBytes());
                }else{

                    String address = data.optString("customer_address", "");
                    String postcode = data.optString("postcode", "");
                    String phone = data.optString("customer_phone", "");

                    output.write(ESC_FONT_SIZE_LARGE);
                    output.write(centerText("Takeaway").getBytes());
                    output.write(ESC_FONT_SIZE_RESET);
                    output.write("\n-------------------------------------------\n".getBytes());
                    output.write(("Date: " + data.getString("order_time") + "\n").getBytes());
                    output.write(("Customer: " + data.getString("customer_name") + "\n").getBytes());

//                    if (address != null && !address.trim().isEmpty() && !"null".equalsIgnoreCase(address)) output.write(("Address: " + data.optString("customer_address", "") + ", " + data.getString("postcode") + "\n").getBytes());
                    if (address != null && !address.trim().isEmpty() && !"null".equalsIgnoreCase(address)) {
                        output.write(("Address: " + address + ", " + postcode + "\n").getBytes());
                    }

                    if (phone != null && !phone.trim().isEmpty() && !"null".equalsIgnoreCase(phone)) {
                        output.write(("Phone: " + phone + "\n").getBytes());
                    }
//                    if (!TextUtils.isEmpty(phone)) output.write(("Phone: " + data.optString("customer_phone", "") + "\n").getBytes());
                }

            }

            output.write("\n-------------------------------------------\n".getBytes());

            Iterator<String> categories = details.keys();
            while (categories.hasNext()) {
                JSONObject items = details.getJSONObject(categories.next());
                Iterator<String> itemIds = items.keys();

                while (itemIds.hasNext()) {
                    JSONObject item = items.getJSONObject(itemIds.next());
                    output.write(ESC_FONT_SIZE_MEDIUM);

                    String itemName = item.getString("item");
                    double quantity = Double.parseDouble(item.getString("quantity"));
                    double amount = Double.parseDouble(item.getString("amount"));

                    JSONObject addonObj = item.optJSONObject("addon");
                    boolean hasAddon = addonObj != null && addonObj.length() > 0;

                    totalItems += quantity;

                    // ✅ Print item name first
                    if (amount > 0) {
                        // Item has valid amount
                        String mainLine = String.format("%.0f x %-33s", quantity, itemName);
                        output.write(mainLine.getBytes());
                        output.write(new byte[]{(byte) 0x9C}); // £ symbol
                        double amountNew =  quantity * amount;
                        output.write(String.format("%.2f\n", amountNew).getBytes());
                    } else {
                        // Item has 0 amount — show only name (no price)
                        String mainLine = String.format("%.0f x %s\n", quantity, itemName);
                        output.write(mainLine.getBytes());
                    }

                    // ✅ Handle addons
                    if (hasAddon) {
                        Iterator<String> addonKeys = addonObj.keys();
                        while (addonKeys.hasNext()) {
                            JSONObject addon = addonObj.getJSONObject(addonKeys.next());
                            String adName = addon.getString("ad_name");
                            double adQty = Double.parseDouble(addon.getString("ad_qty"));
                            double adPrice = Double.parseDouble(addon.getString("ad_price"));

//                            totalItems += adQty;
                            if (adPrice > 0) {
                                // Addon with price
                                String adLine = String.format("   %.0f x %-30s", adQty, adName);
                                output.write(adLine.getBytes());
                                output.write(new byte[]{(byte) 0x9C}); // £ symbol
                                double priceNew = adQty * adPrice;
                                output.write(String.format("%.2f\n", priceNew).getBytes());
                            } else {
                                // Addon without price
                                String adLine = String.format("   %.0f x %s\n", adQty, adName);
                                output.write(adLine.getBytes());
                            }
                        }
                    }

                    output.write("\n".getBytes());
                    output.write(ESC_FONT_SIZE_RESET);
                }
            }

            output.write("-------------------------------------------\n".getBytes());

            String subtotal = data.getString("sub_total");
            output.write(paddedLine(subtotalLabel, subtotal));

            if ("Before_Invoice".equalsIgnoreCase(printType)) {
                String tipAmount = queryParams.getOrDefault("tip", "0.00");
                if (!"0".equals(tipAmount) && !"".equals(tipAmount)) {
                    output.write(paddedLine(tipAmountLabel, tipAmount));
                }
                String serviceFee = queryParams.getOrDefault("servicefee", "0.00");
                if (!"0".equals(serviceFee) && !"".equals(serviceFee)) {
                    output.write(paddedLine("Service Charge", serviceFee));
                }

                String discount = queryParams.getOrDefault("discount", "0.00");
                if (!"0".equals(discount) && !"".equals(discount)) {
                    output.write(paddedLine(discountLabel, discount));
                }

                String bagFee = data.optString("bag_fee", "0.00");
                if (bagFee != null && !bagFee.equalsIgnoreCase("null") && !bagFee.equals("0.00")) {
                    output.write(paddedLine(bagFeeLabel, bagFee));
                }

                String deliveryFee = queryParams.getOrDefault("delfee", "0.00");
                if (!"0".equals(deliveryFee) && !"".equals(deliveryFee)) {
                    output.write(paddedLine("Delivery Fee", deliveryFee));
                }

                String deposit = queryParams.getOrDefault("deposit", "0.00");
                if (!"0".equals(deposit) && !"".equals(deposit)) {
                    output.write(paddedLine("Deposit", deposit));
                }

                String grandTotal = queryParams.getOrDefault("sub_total", "0.00");
                if (!"0".equals(grandTotal) && !"".equals(grandTotal)) {
                    output.write(ESC_FONT_SIZE_MEDIUM);
                    output.write(paddedLine(totalPayLabel, grandTotal));
                    output.write(ESC_FONT_SIZE_RESET);
                }
            }
            else {
                String tipAmount = data.optString("tips", "0.00");
                if (tipAmount != null && !tipAmount.equalsIgnoreCase("null") && !tipAmount.equals("0.00")) {
                    output.write(paddedLine(tipAmountLabel, tipAmount));
                }

                String serviceCharge = data.optString("service_charge", "0.00");
                if (serviceCharge != null && !serviceCharge.equalsIgnoreCase("null") && !serviceCharge.equals("0.00")) {
                    output.write(paddedLine("Service Charge", serviceCharge));
                }
                String bagFee = data.optString("bag_fee", "0.00");
                if (bagFee != null && !bagFee.equalsIgnoreCase("null") && !bagFee.equals("0.00")) {
                    output.write(paddedLine(bagFeeLabel, bagFee));
                }

                String deliveryCharge = data.optString("delivery_charge", "0.00");
                if (deliveryCharge != null && !deliveryCharge.equalsIgnoreCase("null") && !deliveryCharge.equals("0.00")) {
                    output.write(paddedLine(delChargeLabel, deliveryCharge));
                }

                String discount = data.optString("discount", "0.00");
                if (discount != null && !discount.equalsIgnoreCase("null") && !discount.equals("0.00")) {
                    output.write(paddedLine(discountLabel, discount));
                }
                double subTotal = Double.parseDouble(subtotal);
                double serCharge = Double.parseDouble(serviceCharge);
                double gTotal = subTotal + serCharge;
                String grandTotal = String.format("%.2f", gTotal);
                if (grandTotal != null && !grandTotal.equalsIgnoreCase("null") && !grandTotal.equals("0.00")) {
                    output.write(ESC_FONT_SIZE_MEDIUM);
//                    TODO NIDHI 04/11
                    output.write(paddedLine("Total", grandTotal));
//                    output.write(paddedLine(totalPayLabel, grandTotal));
                    output.write(ESC_FONT_SIZE_RESET);
                }
            }
            String totalItemLabel = "Total Item(s):";
            String totalItemLine = String.format("%-27s %10s\n", totalItemLabel, String.valueOf(totalItems));
            output.write(totalItemLine.getBytes("CP437"));
            String paySatus = data.getString("payment_status");
            if(paySatus.equals("1")) {
                String paymentMode = "Payment Mode:";
                String paymentMethod = data.getString("payment_method");
                output.write(String.format("%-30s %10s\n", paymentMode, String.valueOf(paymentMethod)).getBytes("CP437"));
            }
            output.write("-------------------------------------------\n".getBytes());
            output.write(ESC_FONT_SIZE_MEDIUM);
            output.write(centerText("Thank you for visiting us!").getBytes());
            output.write("\n".getBytes());
            output.write(ESC_FONT_SIZE_RESET);
            output.write("-------------------------------------------\n".getBytes());
            String siteUrl = restSettings.optString("online_url", "");
            String footerText = restSettings.optString("footer_text", "");

            if (siteUrl != null && !siteUrl.equalsIgnoreCase("null") && !siteUrl.trim().isEmpty()) {
                output.write(new byte[]{0x1B, 0x61, 0x01});  // Center alignment
                output.write(generateQRCodeESC(siteUrl));   // Your method for QR code bytes
                output.write("\n".getBytes());
                output.write(new byte[]{0x1B, 0x61, 0x00});  // Left alignment
            }
            if (footerText != null && !footerText.equalsIgnoreCase("null") && !footerText.trim().isEmpty()) {
                output.write(ESC_FONT_SIZE_MEDIUM);
                output.write(new byte[]{0x1B, 0x74, 0x00}); // Code page CP437 for £
                output.write(new byte[]{0x1B, 0x61, 0x01}); // Center alignment
                output.write(footerText.getBytes("CP437"));
                output.write("\n\n".getBytes());
                output.write(new byte[]{0x1B, 0x61, 0x00});
                output.write(ESC_FONT_SIZE_RESET);
            }
            output.write(("Served by: "+ data.getString("waiter_name") +"\n\n").getBytes());

            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault());
            String printedTime = sdf.format(new Date());

            output.write(("Printed : " + printedTime + "\n\n").getBytes());

            output.write(new byte[]{0x1B, 0x64, 0x03}); // Feed 3 lines
            output.write(new byte[]{0x1D, 0x56, 0x00}); // Full cut
        } catch (Exception e) {
            Log.e(TAG, "Error formatting print text", e);
        }
        return output.toByteArray();
    }

    public byte[] formatOnlinePayableBytes() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int totalItems = 0;
        try {
            output.write(SET_CODE_PAGE_CP437);
            if (outlets.length() > 0) {
                JSONObject outlet = outlets.getJSONObject(0);
                String outletName = outlet.optString("name", "");
                String outletPhone = outlet.optString("phone", "");
                String outletAddress = outlet.optString("address", "");

                output.write(ESC_ALIGN_CENTER);
                output.write(ESC_FONT_SIZE_LARGE);
                output.write(centerText(outletName).getBytes());
                output.write("\n".getBytes());
                output.write(ESC_FONT_SIZE_RESET);
                output.write(ESC_FONT_SIZE_MEDIUM);
                output.write(centerText(outletAddress).getBytes());
                output.write("\n".getBytes());
                output.write(centerText("Phone: " + outletPhone).getBytes());
                output.write("\n".getBytes());
                output.write(ESC_ALIGN_LEFT);
            }
            String invoice = data.optString("id", "");
            output.write(ESC_ALIGN_CENTER);
//            output.write(centerText("Online Invoice No: #" + invoice).getBytes());

            output.write("Online Invoice No: ".getBytes());

// Set large font for invoice value
            byte[] largeFontOn = new byte[]{0x1B, 0x21, 0x30};  // Double width + height
            byte[] fontReset = new byte[]{0x1B, 0x21, 0x00};    // Reset to normal font

// Print invoice value in large font
            output.write(largeFontOn);
            output.write(("#"+invoice).getBytes());
            output.write(fontReset);

            output.write("\n".getBytes());
            output.write(ESC_ALIGN_LEFT);
            String type = data.optString("order_type", "");
            /*output.write(ESC_FONT_SIZE_LARGE);
            output.write(centerText(type).getBytes());
//            output.write(centerText(type.substring(0, 1).toUpperCase() + type.substring(1)).getBytes());
            output.write(ESC_ALIGN_LEFT);
            output.write(ESC_FONT_SIZE_RESET);*/


            output.write(ESC_ALIGN_CENTER);
            output.write(ESC_FONT_SIZE_LARGE);
            output.write(centerText(type.substring(0, 1).toUpperCase() + type.substring(1)).getBytes());
            output.write("\n".getBytes());
            output.write(ESC_FONT_SIZE_RESET);
            output.write(ESC_ALIGN_LEFT);


            output.write("\n-------------------------------------------\n".getBytes());
            output.write(("Date: " + data.optString("order_time", "") + "\n").getBytes());
            output.write(("Customer: " + data.optString("firstname", "") + " " + data.optString("lastname", "") + "\n").getBytes());

            String address = data.optString("address", "");
            String postcode = data.optString("postcode", "");
            String phone = data.optString("contactno", "");

            if (address != null && !address.trim().isEmpty() && !"null".equalsIgnoreCase(address)) {
                output.write(("Address: " + address + ", " + postcode + "\n").getBytes());
            }

            if (phone != null && !phone.trim().isEmpty() && !"null".equalsIgnoreCase(phone)) {
                output.write(("Phone: " + phone + "\n").getBytes());
            }

//            if (!TextUtils.isEmpty(address)) output.write(("Address: " + data.optString("address", "") + ", " + data.getString("postcode") + "\n").getBytes());
//            if (!TextUtils.isEmpty(phone)) output.write(("Phone: " + data.optString("contactno", "") + "\n").getBytes());

            if (type.equalsIgnoreCase("dinein")) {
                output.write(ESC_FONT_SIZE_LARGE);
                output.write((data.optString("tableno", "") + "\n").getBytes());
                output.write(ESC_FONT_SIZE_RESET);
                output.write(("Seats: " + data.optString("table_seats", "") + "\n").getBytes());
            }/* else if (type.equalsIgnoreCase("delivery")) {
                output.write(("Address: " + data.optString("address", "") + ", " + data.getString("postcode") + "\n").getBytes());
                output.write(("Phone: " + data.optString("contactno", "") + "\n").getBytes());
            }*/

   /////////////


            output.write("\n-------------------------------------------\n".getBytes());

            JSONArray itemsArray = getDetailsArray();

            for (int i = 0; i < itemsArray.length(); i++) {
                JSONObject item = itemsArray.getJSONObject(i);
                output.write(ESC_FONT_SIZE_MEDIUM);

                String itemName = item.optString("item", "");
                double quantity = parseSafeDouble(item.optString("quantity", "0"));
                double amount = parseSafeDouble(item.optString("amount", "0"));

                JSONArray addonArray = item.optJSONArray("addon");
                boolean hasAddon = addonArray != null && addonArray.length() > 0;

                totalItems += quantity;

                // ================= ITEM NAME WORD WRAP (SAME AS INVOICE) =================
                int maxNameWidth = 32;

                List<String> nameLines = new ArrayList<>();
                String[] words = itemName.split(" ");
                StringBuilder current = new StringBuilder();

                for (String w : words) {
                    if (current.length() + w.length() + 1 > maxNameWidth) {
                        nameLines.add(current.toString());
                        current = new StringBuilder(w);
                    } else {
                        if (current.length() > 0) current.append(" ");
                        current.append(w);
                    }
                }
                nameLines.add(current.toString());
                // ========================================================================

                // -------- FIRST LINE (qty + name + amount) --------
                String firstLine = String.format(
                        "%.0f x %-"+maxNameWidth+"s",
                        quantity,
                        nameLines.get(0)
                );

                output.write(firstLine.getBytes("CP437"));

                if (amount > 0) {
                    output.write(new byte[]{(byte) 0x9C}); // £
                    double amountNew = quantity * amount;
                    output.write(String.format("%.2f", amountNew).getBytes("CP437"));
                }
                output.write("\n".getBytes());
                // -------------------------------------------------

                // -------- REMAINING ITEM NAME LINES (NO AMOUNT) --------
                for (int l = 1; l < nameLines.size(); l++) {
                    output.write(("    " + nameLines.get(l) + "\n").getBytes("CP437"));
                }

                // ================= ADDONS (WRAPPED SAME STYLE) =================
                if (hasAddon) {
                    for (int j = 0; j < addonArray.length(); j++) {
                        JSONObject addon = addonArray.getJSONObject(j);

                        String adName = addon.optString("ad_name", "").trim();
                        double adQty = parseSafeDouble(addon.optString("ad_qty", "0"));
                        double adPrice = parseSafeDouble(addon.optString("ad_price", "0"));

                        if (adName.isEmpty()) continue;

                        int addonWidth = 29;
                        List<String> addonLines = new ArrayList<>();
                        String[] adWords = adName.split(" ");
                        StringBuilder adCurrent = new StringBuilder();

                        for (String w : adWords) {
                            if (adCurrent.length() + w.length() + 1 > addonWidth) {
                                addonLines.add(adCurrent.toString());
                                adCurrent = new StringBuilder(w);
                            } else {
                                if (adCurrent.length() > 0) adCurrent.append(" ");
                                adCurrent.append(w);
                            }
                        }
                        addonLines.add(adCurrent.toString());

                        // First addon line
                        String adFirst = String.format(
                                "   %.0f x %-"+addonWidth+"s",
                                adQty,
                                addonLines.get(0)
                        );
                        output.write(adFirst.getBytes("CP437"));

                        if (adPrice > 0) {
                            output.write(new byte[]{(byte) 0x9C});
                            double priceNew = adQty * adPrice;
                            output.write(String.format("%.2f", priceNew).getBytes("CP437"));
                        }
                        output.write("\n".getBytes());

                        // Remaining addon wrapped lines
                        for (int k = 1; k < addonLines.size(); k++) {
                            output.write(("       " + addonLines.get(k) + "\n").getBytes("CP437"));
                        }
                    }
                }
                // ================================================================

                output.write("\n".getBytes());
                output.write(ESC_FONT_SIZE_RESET);
            }

            output.write("-------------------------------------------\n".getBytes());

            output.write(paddedLine(subtotalLabel, data.optString("sub_total", "0.00")));
            String tips = data.optString("tips", "0.00");
            if (!tips.equals("0.00")) {
                output.write(paddedLine(tipAmountLabel, tips));
            }
            String serviceCharge = data.optString("service_charge", "0.00");
            if (!serviceCharge.equals("0.00")) {
                output.write(paddedLine("Service Charge", serviceCharge));
            }
            String bagFee = data.optString("bag_fee", "0.00");
            if (!bagFee.equals("0.00")) {
                output.write(paddedLine(bagFeeLabel, bagFee));
            }

            String deliveryCharge = data.optString("delivery_charge", "0.00");
            if (deliveryCharge != null && !deliveryCharge.equalsIgnoreCase("null") && !deliveryCharge.equals("0.00")) {
                output.write(paddedLine(delChargeLabel, deliveryCharge));
            }

            String discount = data.optString("discount", "0.00");
            if (!discount.equals("0.00")) {
                output.write(paddedLine(discountLabel, discount));
            }
            output.write(ESC_FONT_SIZE_MEDIUM);
            output.write(paddedLine(totalPayLabel, data.optString("grandtotal", "0.00")));
            output.write(ESC_FONT_SIZE_RESET);
            output.write(String.format("%-27s %10s\n", "Total Item(s):", String.valueOf(totalItems)).getBytes("CP437"));
            String paymentMode = data.optString("payment_method");
            if (data.optString("payment_status", "0").equals("1")) {
                if(paymentMode.equalsIgnoreCase("Card Payment Method")){
                    paymentMode = "Card";
                }
                output.write(String.format("%-30s %10s\n", "Payment Mode:", paymentMode).getBytes("CP437"));
            }
            String instruction = settings.optString("instruction", "");
            if(!instruction.isEmpty())
                output.write(("Special Instructions: " + instruction).getBytes("CP437"));
            output.write("-------------------------------------------\n".getBytes());
            String deliveryTime = data.optString("deliverytime", "");
            output.write(ESC_FONT_SIZE_LARGE_Double);
            output.write(("Requested for: "+deliveryTime).getBytes());
            output.write("\n".getBytes());
            output.write(ESC_FONT_SIZE_RESET);
            output.write("-------------------------------------------\n".getBytes());
            output.write(ESC_FONT_SIZE_MEDIUM);
            output.write(centerText("Thank you for visiting us!").getBytes());
            output.write("\n".getBytes());
            output.write(ESC_FONT_SIZE_RESET);
            output.write("-------------------------------------------\n".getBytes());
            String siteUrl = settings.optString("online_url", "");
            String footerText = settings.optString("footer_text", "");
            if (!siteUrl.isEmpty()) {
                output.write(new byte[]{0x1B, 0x61, 0x01});
                output.write(generateQRCodeESC(siteUrl));
                output.write("\n".getBytes());
                output.write(new byte[]{0x1B, 0x61, 0x00});
            }
            if (footerText != null && !footerText.equalsIgnoreCase("null") && !footerText.isEmpty()) {
                output.write(ESC_FONT_SIZE_MEDIUM);
                output.write(new byte[]{0x1B, 0x74, 0x00});
                output.write(new byte[]{0x1B, 0x61, 0x01});
                output.write(footerText.getBytes("CP437"));
                output.write("\n\n".getBytes());
                output.write(new byte[]{0x1B, 0x61, 0x00});
                output.write(ESC_FONT_SIZE_RESET);
            }
            output.write(("Served by: " + data.optString("waiter_name", "") + "\n\n").getBytes());

            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault());
            String printedTime = sdf.format(new Date());

            output.write(("Printed : " + printedTime + "\n\n").getBytes());

            output.write(new byte[]{0x1B, 0x64, 0x03});
            int cashDrawerFeature = features != null ? features.optInt("cash_drawer", 0) : 0;
            if (cashDrawerFeature == 1) {
                int pin = 0;
                if (restSettings != null && !restSettings.isNull("cash_drawer_pin")) {
                    pin = restSettings.optInt("cash_drawer_pin", 0);
                }
                byte[] drawerPulse = new byte[]{0x1B, 0x70, (byte) (pin == 1 ? 0x01 : 0x00), 0x3C, (byte) 0xFF};
                output.write(drawerPulse);
            }
            output.write(new byte[]{0x1D, 0x56, 0x00});

        } catch (Exception e) {
            Log.e(TAG, "Error formatting online payable print", e);
        }
        return output.toByteArray();
    }

    private double parseSafeDouble(String value) {
        try {
            if (value == null || value.trim().isEmpty()) return 0.0;
            return Double.parseDouble(value.trim());
        } catch (Exception e) {
            return 0.0;
        }
    }

    // Unified getter
    private JSONArray getDetailsArray() throws JSONException {
        if (detailsArray != null) {
            return detailsArray;
        } else if (detailsObject != null) {
            // Try to extract array from inside the object
            if (detailsObject.has("details")) {
                return detailsObject.getJSONArray("details");
            } else {
                throw new JSONException("detailsObject does not contain 'details' key");
            }
        } else {
            throw new JSONException("No valid details data available");
        }
    }

    private double printItem(JSONObject item, ByteArrayOutputStream output) throws IOException {
        double itemTotalQty = 0;

        output.write(ESC_FONT_SIZE_MEDIUM);
        String itemName = item.optString("item", "Unknown");
        output.write(itemName.getBytes());
        output.write("\n".getBytes());

        double quantity = parseDoubleSafe(item.optString("quantity", "0"));
        double amount = parseDoubleSafe(item.optString("amount", "0"));
        itemTotalQty += quantity;

        // Handle addon: can be JSONArray or JSONObject (optional)
        Object addonObj = item.opt("addon");

        if (addonObj instanceof JSONArray) {
            JSONArray addons = (JSONArray) addonObj;
            for (int j = 0; j < addons.length(); j++) {
                JSONObject addon = addons.optJSONObject(j);
                if (addon != null) {
                    String adName = addon.optString("ad_name");
                    double adQty = parseDoubleSafe(addon.optString("ad_qty", "0"));
                    double adPrice = parseDoubleSafe(addon.optString("ad_price", "0"));
                    double adTotal = adQty * adPrice;

                    if (!TextUtils.isEmpty(adName) && adQty > 0) {
                        output.write(("  " + adName + "\n").getBytes());
                        String adLine = String.format("  %.0f X %.2f %28s", adQty, adPrice, "");
                        output.write(adLine.getBytes());
                        output.write(new byte[]{(byte) 0x9C}); // Align right if ESC/POS supported
                        output.write(String.format("%.2f\n", adTotal).getBytes());

                        itemTotalQty += adQty;
                    }
                }
            }
        } else if (addonObj instanceof JSONObject) {
            JSONObject addons = (JSONObject) addonObj;
            Iterator<String> keys = addons.keys();
            while (keys.hasNext()) {
                JSONObject addon = addons.optJSONObject(keys.next());
                if (addon != null) {
                    String adName = addon.optString("ad_name");
                    double adQty = parseDoubleSafe(addon.optString("ad_qty", "0"));
                    double adPrice = parseDoubleSafe(addon.optString("ad_price", "0"));
                    double adTotal = adQty * adPrice;

                    if (!TextUtils.isEmpty(adName) && adQty > 0) {
                        output.write(("  " + adName + "\n").getBytes());
                        String adLine = String.format("  %.0f X %.2f %28s", adQty, adPrice, "");
                        output.write(adLine.getBytes());
                        output.write(new byte[]{(byte) 0x9C});
                        output.write(String.format("%.2f\n", adTotal).getBytes());

                        itemTotalQty += adQty;
                    }
                }
            }
        } else {
            // No addon: print normal item
            double total = quantity * amount;
            String line = String.format("%s X %.2f %30s", item.optString("quantity"), amount, "");
            output.write(line.getBytes());
            output.write(new byte[]{(byte) 0x9C});
            output.write(String.format("%.2f\n", total).getBytes());
        }

        output.write("\n".getBytes());
        output.write(ESC_FONT_SIZE_RESET);
        return itemTotalQty;
    }

    private double parseDoubleSafe(String input) {
        try {
            return Double.parseDouble(input);
        } catch (Exception e) {
            return 0;
        }
    }

    private byte[] generateQRCodeESC(String qrData) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(new byte[]{0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, 0x08}); // QR size
            baos.write(new byte[]{0x1D, 0x28, 0x6B}); // QR code: store
            int len = qrData.length() + 3;
            baos.write((byte) (len % 256));
            baos.write((byte) (len / 256));
            baos.write(new byte[]{0x31, 0x50, 0x30});
            baos.write(qrData.getBytes("UTF-8"));
            baos.write(new byte[]{0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30}); // QR code: print
            return baos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return new byte[0];
        }
    }

    private byte[] paddedLine(String label, String value) {
        int totalWidth = 42;
        if(value.length() < 3)
            value += ".00";
        String amountWithSymbol = "£" + value;
        String formatted = String.format("%-" + (totalWidth - amountWithSymbol.length()) + "s%s\n", label, amountWithSymbol);

        try {
            return formatted.getBytes("CP437");
        } catch (UnsupportedEncodingException e) {
            return formatted.getBytes();
        }
    }

    private String centerText(String text) {
        int spaces = (lineLength - text.length()) / 2;
        return " ".repeat(Math.max(0, spaces)) + text + " ".repeat(Math.max(0, spaces));
    }

    private String centerTextOnline(String text, boolean isDoubleWidth) {

        if (TextUtils.isEmpty(text)) return "";

        // Normal font = 40 columns
        // Double width font = 20 columns
        int printerWidth = isDoubleWidth ? 24 : 45;

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
