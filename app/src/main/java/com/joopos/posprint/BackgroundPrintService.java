package com.joopos.posprint;

import static com.joopos.posprint.PrintUtils.convertBitmapToEscPos;

import android.app.IntentService;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Collectors;

public class BackgroundPrintService extends IntentService {
    public BackgroundPrintService() {
        super("BackgroundPrintService");
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        Uri data = intent != null ? intent.getData() : null;
        Log.d("PrintService", "Started with URI: " + data);

        if (data == null) return;

        try {
            String raw = data.toString();

            String query = raw.substring(raw.indexOf('?') + 1);
            Map<String,String> params = Uri.parse(raw).getQueryParameterNames().stream()
                    .collect(Collectors.toMap(name -> name, name -> Uri.parse(raw).getQueryParameter(name)));

                // ✅ Manually extract base_url so & inside it isn't lost
                String baseUrl = null;
                int idx = raw.indexOf("base_url=");
                if (idx != -1) {
                    baseUrl = raw.substring(idx + "base_url=".length());
                    baseUrl = URLDecoder.decode(baseUrl, "UTF-8");
                }

                if (baseUrl == null) {
                    Log.e("PrintService", "No base_url found in deep link");
                    return;
                }

                // Ensure HTTPS
                if (baseUrl.startsWith("http://")) {
                    baseUrl = baseUrl.replace("http://", "https://");
                }

                // ✅ Add split_id if invoice_print and missing
                if (baseUrl.contains("invoice_print") && !baseUrl.contains("split_id=")) {
                    if (baseUrl.contains("?")) {
                        baseUrl += "&split_id=1";
                    } else {
                        baseUrl += "?split_id=1";
                    }
                }
                if(baseUrl.contains("print_today_petty_cash")){
                    int index = baseUrl.indexOf("&");
                    if (index != -1) {
                        baseUrl = baseUrl.substring(0, index);
                    }
                }

                Log.d("PrintService", "Final API URL: " + baseUrl);

            RequestQueue queue = Volley.newRequestQueue(getApplicationContext());
            JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, baseUrl, null,
                    response -> {
                        try {

                            String type = response.getString("print_type");
                            Log.d("PrintService", "Response OK, type=" + type);
                            if ("kot".equalsIgnoreCase(type)) {

                                JSONObject details = response.getJSONObject("details");
                                new KOTHandler(getApplicationContext(), response, details).handleKOT();

                            }else if ("reprint_kot".equalsIgnoreCase(type)) {

                                JSONObject details = response.getJSONObject("details");
                                new KOTHandler(getApplicationContext(), response, details).handleKOT();
                            }else if ("online_kot".equalsIgnoreCase(type)) {
                                JSONObject details = response.getJSONObject("details");
                                new KOTHandlerOnline(getApplicationContext(), response, details).handleKOT();
                            }
                            else if ("online_booking".equalsIgnoreCase(type)) {
                                new BookingPrintHandler(getApplicationContext(), response).handleBookingPrint();
                            }
                            else if ("online_invoice".equalsIgnoreCase(type)) {
                                Object detailsObj = response.get("details");  // ✅ Can be JSONObject or JSONArray
                                JSONArray printers = response.getJSONArray("printers");
                                JSONObject payData = response.getJSONObject("data");
                                JSONArray outlets = response.getJSONArray("outlets");
                                JSONArray printerSetupArray = response.getJSONArray("printersetup");

                                String printerIP = "";
                                int printerPort = 9100;
                                String printerType = "network";

                                if (printerSetupArray.length() > 0) {
                                    int printerIdToUse = printerSetupArray.getInt(0);

                                    // Loop through printers to find matching ID
                                    for (int i = 0; i < printers.length(); i++) {
                                        JSONObject printer = printers.getJSONObject(i);
                                        int printerId = printer.getInt("id");

                                        if (printerId == printerIdToUse) {
                                            printerIP = printer.optString("ip", "");
                                            printerPort = Integer.parseInt(printer.optString("port", "9100"));
                                            printerType = printer.optString("type", "network");
                                            break;
                                        }
                                    }
                                }

                                if (!printerIP.isEmpty() || "usb".equalsIgnoreCase(printerType) || "windows".equalsIgnoreCase(printerType) || "window".equalsIgnoreCase(printerType)) {
                                    JSONObject restSettings = response.has("rest_settings") ? response.getJSONObject("rest_settings") : new JSONObject();
                                    JSONObject settings = response.has("settings") ? response.getJSONObject("settings") : new JSONObject();
                                    JSONObject features = response.has("features") ? response.getJSONObject("features") : new JSONObject();

                                    PayableHandler payableHandler;

                                    // ✅ Pass the correct type to the constructor
                                    if (detailsObj instanceof JSONObject) {
                                        payableHandler = new PayableHandler(this, payData, outlets, (JSONObject) detailsObj, printerIP, printerPort, type, restSettings, features, params, null);
                                    } else if (detailsObj instanceof JSONArray) {
                                        payableHandler = new PayableHandler(this, payData, outlets, (JSONArray) detailsObj, printerIP, printerPort, type, restSettings, features, params, settings);
                                    } else {
                                        throw new JSONException("Invalid type for details: must be JSONObject or JSONArray");
                                    }

                                    int invoicePrintCopies = response.getJSONArray("printsettings")
                                            .getJSONObject(0)
                                            .optInt("invoice_print_copies", 1); // default 1

                                    byte[] formattedBytes = payableHandler.formatOnlinePayableBytes();
//   TODO _NEW invoice print count based on api
                                    for (int i = 0; i < invoicePrintCopies; i++) {
                                        if ("usb".equalsIgnoreCase(printerType) || "windows".equalsIgnoreCase(printerType) || "window".equalsIgnoreCase(printerType) || printerIP.trim().isEmpty()) {
                                            UsbPrintConnection usb = new UsbPrintConnection(this);
                                            usb.printBytes(formattedBytes, (success, msg) -> Log.d("PAY_USB", "Callback: " + msg));
                                        } else {
                                            PrintConnection_PAY payConn = new PrintConnection_PAY(this);
                                            payConn.printFastBytes(printerIP, printerPort, formattedBytes, (success, msg) -> {
                                                Log.d("PAY_FAST", "Callback: " + msg);
                                            });
                                        }

                                    }

                                } else {
                                    Log.e("PrintError", "No valid printer IP found in printersetup.");
                                }
                            }

                            else if ("invoice".equalsIgnoreCase(type)) {
                                JSONObject details = response.getJSONObject("details");
                                JSONArray printers = response.getJSONArray("printers");
                                JSONObject payData = response.getJSONObject("data");
                                JSONArray outlets = response.getJSONArray("outlets");
                                JSONArray printerSetupArray = response.getJSONArray("printersetup");

                                String printerIP = "";
                                int printerPort = 9100;
                                String printerType = "network";
                                String formattedText;

                                if (printerSetupArray.length() > 0) {
                                    int printerIdToUse = printerSetupArray.getInt(0);

                                    // Loop through printers to find matching ID
                                    for (int i = 0; i < printers.length(); i++) {
                                        JSONObject printer = printers.getJSONObject(i);
                                        int printerId = printer.getInt("id");

                                        if (printerId == printerIdToUse) {
                                            printerIP = printer.optString("ip", "");
                                            printerPort = Integer.parseInt(printer.optString("port", "9100"));
                                            printerType = printer.optString("type", "network");
                                            break;
                                        }
                                    }
                                }

                                if (!printerIP.isEmpty() || "usb".equalsIgnoreCase(printerType) || "windows".equalsIgnoreCase(printerType) || "window".equalsIgnoreCase(printerType)) {

                                    JSONObject restSettings = response.has("rest_settings") ? response.getJSONObject("rest_settings") : new JSONObject();
                                    JSONObject features = response.has("features") ? response.getJSONObject("features") : new JSONObject();


//                                    String finalPrinterIP = printerIP;
//                                    int finalPrinterPort = printerPort;
                                    String finalPrinterIP = printerIP;
                                    int finalPrinterPort = printerPort;
                                    String finalPrinterType = printerType;
                                    new Thread(() -> {
                                        try {
                                            Bitmap logoBitmap = null;
                                            if (features.optInt("print_logo") == 1) {
                                                String logoUrl = restSettings.optString("base_url");
                                                logoBitmap = PayableHandler.getBitmapFromURL(logoUrl); // network call in background
                                            }


                                            PayableHandler payableHandler = new PayableHandler(
                                                    this,
                                                    payData,
                                                    outlets,
                                                    details,
                                                    finalPrinterIP,
                                                    finalPrinterPort,
                                                    type,
                                                    restSettings,
                                                    features,
                                                    params,
                                                    logoBitmap // pass preloaded bitmap
                                            );

                                            byte[] formattedBytes = payableHandler.formatPayableBytes(); // safe now

                                            int invoicePrintCopies = response.getJSONArray("printsettings")
                                                    .getJSONObject(0)
                                                    .optInt("invoice_print_copies", 1); // default 1

                                            //   TODO _NEW invoice print count based on api
                                            for (int i = 0; i < invoicePrintCopies; i++) {
                                                if ("usb".equalsIgnoreCase(finalPrinterType) || "windows".equalsIgnoreCase(finalPrinterType) || "window".equalsIgnoreCase(finalPrinterType) || finalPrinterIP.trim().isEmpty()) {
                                                    UsbPrintConnection usb = new UsbPrintConnection(this);
                                                    usb.printBytes(formattedBytes, (success, msg) -> Log.d("PAY_USB", "Callback: " + msg));
                                                } else {
                                                    PrintConnection_PAY payConn = new PrintConnection_PAY(this);
                                                    payConn.printWithStatusCheck(finalPrinterIP, finalPrinterPort, formattedBytes, (success, msg) -> {
                                                        Log.d("PAY", "Callback: " + msg);
                                                    });
                                                }
                                            }

                                        } catch (Exception e) {
                                            Log.e("PrintError", "Error printing invoice", e);
                                        }
                                    }).start();

                                } else {
                                    Log.e("PrintError", "No valid printer IP found in printersetup.");
                                }

                            }

                            else if ("equal_split_payable_invoice".equalsIgnoreCase(type)) {
                                JSONObject details = response.getJSONObject("details");
                                JSONArray printers = response.getJSONArray("printers");
                                JSONObject payData = response.getJSONObject("data");
                                JSONArray outlets = response.getJSONArray("outlets");
                                JSONArray printerSetupArray = response.getJSONArray("printersetup");

                                String printerIP = "";
                                int printerPort = 9100;
                                String printerType = "network";
                                String formattedText;

                                if (printerSetupArray.length() > 0) {
                                    int printerIdToUse = printerSetupArray.getInt(0);

                                    // Loop through printers to find matching ID
                                    for (int i = 0; i < printers.length(); i++) {
                                        JSONObject printer = printers.getJSONObject(i);
                                        int printerId = printer.getInt("id");

                                        if (printerId == printerIdToUse) {
                                            printerIP = printer.optString("ip", "");
                                            printerPort = Integer.parseInt(printer.optString("port", "9100"));
                                            printerType = printer.optString("type", "network");
                                            break;
                                        }
                                    }
                                }

                                if (!printerIP.isEmpty() || "usb".equalsIgnoreCase(printerType) || "windows".equalsIgnoreCase(printerType) || "window".equalsIgnoreCase(printerType)) {

                                    JSONObject restSettings = response.has("rest_settings") ? response.getJSONObject("rest_settings") : new JSONObject();
                                    PayableHandler payableHandler = new PayableHandler(this, payData, outlets, details, printerIP, printerPort, type, restSettings, params);

                                    byte[] formattedBytes = payableHandler.formatPayableSplitBytes(); // Now returns byte[]
                                    /*PrintConnection_PAY printConnection = new PrintConnection_PAY(printerIP, printerPort, formattedBytes);
                                    printConnection.execute();*/
                                    if ("usb".equalsIgnoreCase(printerType) || "windows".equalsIgnoreCase(printerType) || "window".equalsIgnoreCase(printerType) || printerIP.trim().isEmpty()) {
                                        UsbPrintConnection usb = new UsbPrintConnection(this);
                                        usb.printBytes(formattedBytes, (success, msg) -> Log.d("PAY_USB", "Callback: " + msg));
                                    } else {
                                        PrintConnection_PAY payConn = new PrintConnection_PAY(this);
                                        payConn.printWithStatusCheck(printerIP, printerPort, formattedBytes, (success, msg) -> {
                                            Log.d("PAY", "Callback: " + msg);
                                        });
                                    }

                                } else {
                                    Log.e("PrintError", "No valid printer IP found in printersetup.");
                                }

                            }

                            else if ("Invoice_split_item".equalsIgnoreCase(type)) {
                                JSONObject valueDetails = response.getJSONObject("value_details");
                                JSONArray printers = response.getJSONArray("printers");
                                JSONArray outlets = response.getJSONArray("outlets");
                                JSONArray printerSetupArray = response.getJSONArray("printersetup");

                                String printerIP = "";
                                int printerPort = 9100;
                                String printerType = "network";
                                if (printerSetupArray.length() > 0) {
                                    int printerIdToUse = printerSetupArray.getInt(0);
                                    for (int i = 0; i < printers.length(); i++) {
                                        JSONObject printer = printers.getJSONObject(i);
                                        if (printer.getInt("id") == printerIdToUse) {
                                            printerIP = printer.optString("ip", "");
                                            printerPort = Integer.parseInt(printer.optString("port", "9100"));
                                            printerType = printer.optString("type", "network");
                                            break;
                                        }
                                    }
                                }

                                if (!printerIP.isEmpty() || "usb".equalsIgnoreCase(printerType) || "windows".equalsIgnoreCase(printerType) || "window".equalsIgnoreCase(printerType)) {
                                    JSONObject restSettings = response.has("rest_settings")
                                            ? response.getJSONObject("rest_settings")
                                            : new JSONObject();

                                    // Loop through each split invoice
                                    Iterator<String> keys = valueDetails.keys();
                                    while (keys.hasNext()) {
                                        String key = keys.next();
                                        JSONObject splitObj = valueDetails.getJSONObject(key);
                                        JSONObject payData = splitObj.getJSONObject("data");
                                        JSONObject details = splitObj.getJSONObject("details");

                                        PayableHandler payableHandler = new PayableHandler(
                                                this,
                                                payData,
                                                outlets,
                                                details,
                                                printerIP,
                                                printerPort,
                                                type,
                                                restSettings,
                                                params
                                        );

                                        byte[] formattedBytes = payableHandler.formatPayableSplitBytes();
                                        /*PrintConnection_PAY printConnection = new PrintConnection_PAY(printerIP, printerPort, formattedBytes);
                                        printConnection.execute();*/

                                        if ("usb".equalsIgnoreCase(printerType) || "windows".equalsIgnoreCase(printerType) || "window".equalsIgnoreCase(printerType) || printerIP.trim().isEmpty()) {
                                            UsbPrintConnection usb = new UsbPrintConnection(this);
                                            usb.printBytes(formattedBytes, (success, msg) -> Log.d("PAY_USB", "Callback: " + msg));
                                        } else {
                                            PrintConnection_PAY payConn = new PrintConnection_PAY(this);
                                            payConn.printWithStatusCheck(printerIP, printerPort, formattedBytes, (success, msg) -> {
                                                Log.d("PAY", "Callback: " + msg);
                                            });
                                        }

                                        Thread.sleep(50);
                                    }
                                } else {
                                    Log.e("PrintError", "No valid printer IP found in printersetup.");
                                }
                            }

                            else if ("Before_Invoice".equalsIgnoreCase(type)) {
                                JSONObject details = response.getJSONObject("details");
                                JSONArray printers = response.getJSONArray("printers");
                                JSONObject payData = response.getJSONObject("data");
                                JSONArray outlets = response.getJSONArray("outlets");
                                JSONArray printerSetupArray = response.getJSONArray("printersetup");

                                String printerIP = "";
                                int printerPort = 9100;
                                String printerType = "network";
                                String formattedText;

                                if (printerSetupArray.length() > 0) {
                                    int printerIdToUse = printerSetupArray.getInt(0);

                                    // Loop through printers to find matching ID
                                    for (int i = 0; i < printers.length(); i++) {
                                        JSONObject printer = printers.getJSONObject(i);

                                        int printerId = printer.getInt("id");

                                        if (printerId == printerIdToUse) {
                                            printerIP = printer.optString("ip", "");
                                            printerPort = Integer.parseInt(printer.optString("port", "9100"));
                                            printerType = printer.optString("type", "network");
                                            break;
                                        }
                                    }
                                }

                                if (!printerIP.isEmpty() || "usb".equalsIgnoreCase(printerType) || "windows".equalsIgnoreCase(printerType) || "window".equalsIgnoreCase(printerType)) {

                                    JSONObject restSettings = response.has("rest_settings") ? response.getJSONObject("rest_settings") : new JSONObject();
                                    PayableHandler payableHandler = new PayableHandler(this, payData, outlets, details, printerIP, printerPort, type, restSettings, params);
//                                    PayableHandler payableHandler = new PayableHandler(this, payData, outlets, details, printerIP, printerPort, type);
                                    byte[] formattedBytes = payableHandler.formatPayableBytes(); // Now returns byte[]
                                    /*PrintConnection_PAY printConnection = new PrintConnection_PAY(printerIP, printerPort, formattedBytes);
                                    printConnection.execute();*/
                                    if ("usb".equalsIgnoreCase(printerType) || "windows".equalsIgnoreCase(printerType) || "window".equalsIgnoreCase(printerType) || printerIP.trim().isEmpty()) {
                                        UsbPrintConnection usb = new UsbPrintConnection(this);
                                        usb.printBytes(formattedBytes, (success, msg) -> Log.d("PAY_USB", "Callback: " + msg));
                                    } else {
                                        PrintConnection_PAY payConn = new PrintConnection_PAY(this);
                                        payConn.printWithStatusCheck(printerIP, printerPort, formattedBytes, (success, msg) -> {
                                            Log.d("PAY", "Callback: " + msg);
                                        });
                                    }

                                } else {
                                    Log.e("PrintError", "No valid printer IP found in printersetup.");
                                }

                            }else if ("petty_cash".equalsIgnoreCase(type)) {

                                JSONObject details = new JSONObject(); // ✅ Fix: dummy empty object

                                JSONArray printers = response.getJSONArray("printers");
                                JSONObject payData = response.getJSONObject("data");
                                JSONArray outlets = response.getJSONArray("outlets");
                                JSONArray printerSetupArray = response.getJSONArray("printersetup");

                                String printerIP = "";
                                int printerPort = 9100;
                                String printerType = "network";

                                if (printerSetupArray.length() > 0) {
                                    int printerIdToUse = printerSetupArray.getInt(0);

                                    for (int i = 0; i < printers.length(); i++) {
                                        JSONObject printer = printers.getJSONObject(i);
                                        int printerId = printer.getInt("id");

                                        if (printerId == printerIdToUse) {
                                            printerIP = printer.optString("ip", "");
                                            printerPort = Integer.parseInt(printer.optString("port", "9100"));
                                            printerType = printer.optString("type", "network");
                                            break;
                                        }
                                    }
                                }

                                if (!printerIP.isEmpty() || "usb".equalsIgnoreCase(printerType) || "windows".equalsIgnoreCase(printerType) || "window".equalsIgnoreCase(printerType)) {
                                    JSONObject restSettings = response.has("rest_settings") ? response.getJSONObject("rest_settings") : new JSONObject();
                                    PettyCashHandler pettyCashHandler = new PettyCashHandler(
                                            this, payData, outlets, details, printerIP, printerPort, type, restSettings, params
                                    );

                                    String formattedText = pettyCashHandler.formatPettyCashPrint(response); // ⬅ Make sure to pass full response or adjust inside
//                                    todo _hide 02/12
                                    /*PrintConnection printConnection = new PrintConnection(this,printerIP, printerPort, formattedText);
                                    printConnection.execute();*/

                                    if ("usb".equalsIgnoreCase(printerType) || "windows".equalsIgnoreCase(printerType) || "window".equalsIgnoreCase(printerType) || printerIP.trim().isEmpty()) {
                                        UsbPrintConnection usb = new UsbPrintConnection(this);
                                        usb.printText(formattedText, (success, msg) -> Log.d("PrintService_USB", type + " → " + success + " / " + msg));
                                    } else {
                                        PrintConnection pc = new PrintConnection(this);
                                        pc.printWithStatusCheck(printerIP, printerPort, formattedText, (success, msg) -> {
                                            Log.d("PrintService", type + " → " + success + " / " + msg);
                                        });
                                    }

                                } else {
                                    Log.e("PrintError", "No valid printer IP found in printersetup.");
                                }
                            }else if ("petty_cash_invoice".equalsIgnoreCase(type)) {
                                JSONArray printers = response.getJSONArray("printers");
                                JSONArray outlets = response.getJSONArray("outlets");
                                JSONArray printerSetupArray = response.getJSONArray("printersetup");
                                JSONObject pettyCashData = response.getJSONObject("data");
                                JSONObject restSettings = response.optJSONObject("rest_settings");

                                String printerIP = "";
                                int printerPort = 9100;
                                String printerType = "network";

                                if (printerSetupArray.length() > 0) {
                                    int printerIdToUse = printerSetupArray.getInt(0);
                                    for (int i = 0; i < printers.length(); i++) {
                                        JSONObject printer = printers.getJSONObject(i);
                                        if (printer.getInt("id") == printerIdToUse) {
                                            printerIP = printer.optString("ip", "");
                                            printerPort = Integer.parseInt(printer.optString("port", "9100"));
                                            printerType = printer.optString("type", "network");
                                            break;
                                        }
                                    }
                                }

                                if (!printerIP.isEmpty() || "usb".equalsIgnoreCase(printerType) || "windows".equalsIgnoreCase(printerType) || "window".equalsIgnoreCase(printerType)) {
                                    PettyCashHandler pettyCashHandler = new PettyCashHandler(this, pettyCashData, outlets, null, printerIP, printerPort, type, restSettings, params);
                                    String formattedBytes = pettyCashHandler.formatTodayPettyCashPrint(pettyCashData);
                                    //                                    todo _hide 02/12
//                                    new PrintConnection(this, printerIP, printerPort, formattedBytes).execute();
                                    if ("usb".equalsIgnoreCase(printerType) || "windows".equalsIgnoreCase(printerType) || "window".equalsIgnoreCase(printerType) || printerIP.trim().isEmpty()) {
                                        UsbPrintConnection usb = new UsbPrintConnection(this);
                                        usb.printText(formattedBytes, (success, msg) -> Log.d("PrintService_USB", type + " → " + success + " / " + msg));
                                    } else {
                                        PrintConnection pc = new PrintConnection(this);
                                        pc.printWithStatusCheck(printerIP, printerPort, formattedBytes, (success, msg) -> {
                                            Log.d("PrintService", type + " → " + success + " / " + msg);
                                        });
                                    }

                                } else {
                                    Log.e("PrintError", "No valid printer IP found for print_today_petty_cash.");
                                }
                            }else if ("mainsaway".equalsIgnoreCase(type)) {
                                JSONObject payData = response.getJSONObject("data");
                                JSONArray printers = response.getJSONArray("printers");
                                JSONArray printerSetupArray = response.getJSONArray("printersetup");

                                String printerIP = "";
                                int printerPort = 9100;
                                String printerType = "network";

                                if (printerSetupArray.length() > 0) {
                                    int printerIdToUse = printerSetupArray.getInt(0);
                                    for (int i = 0; i < printers.length(); i++) {
                                        JSONObject printer = printers.getJSONObject(i);
                                        if (printer.getInt("id") == printerIdToUse) {
                                            printerIP = printer.optString("ip", "");
                                            printerPort = Integer.parseInt(printer.optString("port", "9100"));
                                            printerType = printer.optString("type", "network");
                                            break;
                                        }
                                    }
                                }

                                if (!printerIP.isEmpty()) {
                                    new MainsAwayHandler(this, payData, printerIP, printerPort, printerType).printMainsAway();
                                } else {
                                    Log.e("PrintError", "No valid printer IP found for mainsaway.");
                                }
                            }

// Reports
                            else if("daily_summary_report".equalsIgnoreCase(type)){

                                String printerIP = "";
                                int printerPort = 9100;
                                String printerType = "network";

                                JSONArray printers = response.getJSONArray("printers");
                                JSONArray printerSetupArray = response.getJSONArray("printersetup");

                                if (printerSetupArray.length() > 0) {
                                    int printerIdToUse = printerSetupArray.getInt(0);
                                    for (int i = 0; i < printers.length(); i++) {
                                        JSONObject printer = printers.getJSONObject(i);
                                        if (printer.getInt("id") == printerIdToUse) {
                                            printerIP = printer.optString("ip", "");
                                            printerPort = Integer.parseInt(printer.optString("port", "9100"));
                                            printerType = printer.optString("type", "network");
                                            break;
                                        }
                                    }
                                }

                                String printData = ReportHandler.buildDailySummaryReport(response, "daily_summary_report");

                                if (!printerIP.isEmpty() || "usb".equalsIgnoreCase(printerType) || "windows".equalsIgnoreCase(printerType) || "window".equalsIgnoreCase(printerType)) {
                                    //                                    todo _hide 02/12
                                    /*PrintConnection printConnection = new PrintConnection(this, printerIP, printerPort, printData);
                                    printConnection.execute();*/

                                    if ("usb".equalsIgnoreCase(printerType) || "windows".equalsIgnoreCase(printerType) || "window".equalsIgnoreCase(printerType) || printerIP.trim().isEmpty()) {
                                        UsbPrintConnection usb = new UsbPrintConnection(this);
                                        usb.printText(printData, (success, msg) -> Log.d("DailyReportPrint_USB", success + " → " + msg));
                                    } else {
                                        PrintConnection pc = new PrintConnection(this);
                                        pc.printWithStatusCheck(printerIP, printerPort, printData, (success, msg) -> {
                                            Log.d("DailyReportPrint", success + " → " + msg);
                                        });
                                    }

                                } else {
                                    Log.e("PrintError", "No valid printer IP found for mainsaway.");
                                }

                            }

                            else if("online_report".equalsIgnoreCase(type) || "offline_report".equalsIgnoreCase(type) || "booking_report".equalsIgnoreCase(type)) {

                                String printerIP = "";
                                int printerPort = 9100;
                                String printerType = "network";

                                JSONArray printers = response.getJSONArray("printers");
                                JSONArray printerSetupArray = response.getJSONArray("printersetup");

                                if (printerSetupArray.length() > 0) {
                                    int printerIdToUse = printerSetupArray.getInt(0);
                                    for (int i = 0; i < printers.length(); i++) {
                                        JSONObject printer = printers.getJSONObject(i);
                                        if (printer.getInt("id") == printerIdToUse) {
                                            printerIP = printer.optString("ip", "");
                                            printerPort = Integer.parseInt(printer.optString("port", "9100"));
                                            printerType = printer.optString("type", "network");
                                            break;
                                        }
                                    }
                                }

                                String printData = ReportHandler.buildOnlineReport(response, type);

                                if (!printerIP.isEmpty() || "usb".equalsIgnoreCase(printerType) || "windows".equalsIgnoreCase(printerType) || "window".equalsIgnoreCase(printerType)) {
                                    //                                    todo _hide 02/12
                                    /*PrintConnection printConnection = new PrintConnection(this, printerIP, printerPort, printData);
                                    printConnection.execute();*/

                                    if ("usb".equalsIgnoreCase(printerType) || "windows".equalsIgnoreCase(printerType) || "window".equalsIgnoreCase(printerType) || printerIP.trim().isEmpty()) {
                                        UsbPrintConnection usb = new UsbPrintConnection(this);
                                        usb.printText(printData, (success, msg) -> Log.d("OnlineReportPrint_USB", type + " → " + success + " / " + msg));
                                    } else {
                                        PrintConnection pc = new PrintConnection(this);
                                        pc.printWithStatusCheck(printerIP, printerPort, printData, (success, msg) -> {
                                            Log.d("OnlineReportPrint", type + " → " + success + " / " + msg);
                                        });
                                    }

                                } else {
                                    Log.e("PrintError", "No valid printer IP found for online_report.");
                                }
                            }
                            else if ("open_cash_drawer".equalsIgnoreCase(type)) {
                                new CashDrawerPrintHandler(getApplicationContext(), response).handleCashDrawerPrint();
                            }

                            else {
                                Log.w("PrintService", "Type not supported: " + type);
                            }
                        } catch (Exception e) {
                            Log.e("PrintService", "JSON error", e);
                        }
                    },
                    error -> Log.e("PrintService", "Volley error", error)
            ) {
                @Override
                public Map<String, String> getHeaders() {
                    Map<String,String> h = new HashMap<>();
                    h.put("X-API-KEY", "zF3warIELPw61WV4V722hU4l63y752Al");
                    return h;
                }
            };

            request.setRetryPolicy(new DefaultRetryPolicy(
                    3000, // 3 seconds timeout
                    0,    // no retries to avoid delay
                    1.0f  // no backoff
            ));
            queue.add(request);
        } catch (Exception e) {
            Log.e("PrintService", "Error handling deep link", e);
        }
    }
}
