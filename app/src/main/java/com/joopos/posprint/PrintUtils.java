package com.joopos.posprint;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class PrintUtils {

    // Fetch bitmap from URL
    public static Bitmap getBitmapFromURL(String src) {
        try {
            URL url = new URL(src);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            return BitmapFactory.decodeStream(input);
        } catch (Exception e) {
            Log.e("PrintUtils", "Error fetching bitmap from URL", e);
            return null;
        }
    }

    // Convert bitmap to ESC/POS bytes
    public static byte[] decodeBitmapToEscPos(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // ESC * m nL nH d1...dk  -> 24-dot double-density
        int widthBytes = (width + 7) / 8; // width in bytes

        for (int y = 0; y < height; y += 24) {
            baos.write(0x1B); // ESC
            baos.write('*');  // '*' Select bit-image mode
            baos.write(33);   // 24-dot double density
            baos.write(widthBytes & 0xFF);       // nL
            baos.write((widthBytes >> 8) & 0xFF);// nH

            for (int x = 0; x < width; x++) {
                for (int k = 0; k < 3; k++) { // 3 bytes for 24 dots
                    byte slice = 0;
                    for (int b = 0; b < 8; b++) {
                        int yPos = y + k * 8 + b;
                        int pixel = (yPos >= height) ? 0 : pixels[yPos * width + x];
                        int r = (pixel >> 16) & 0xFF;
                        int g = (pixel >> 8) & 0xFF;
                        int bColor = pixel & 0xFF;
                        int gray = (r + g + bColor) / 3;
                        if (gray < 128) { // threshold
                            slice |= (1 << (7 - b));
                        }
                    }
                    baos.write(slice);
                }
            }
            baos.write(0x0A); // Line feed
        }

        return baos.toByteArray();
    }

    // Generate receipt bytes including logo
    public static byte[] generateReceiptWithLogo(Bitmap logoBitmap) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            if (logoBitmap != null) {
                int printerWidthPx = 384; // typical 55mm printer width
                Bitmap scaledLogo = Bitmap.createScaledBitmap(
                        logoBitmap,
                        printerWidthPx,
                        (logoBitmap.getHeight() * printerWidthPx) / logoBitmap.getWidth(),
                        true
                );

                // Convert bitmap to ESC/POS bytes
                byte[] logoBytes = decodeBitmapToEscPos(scaledLogo);
                output.write(logoBytes);
                output.write("\n".getBytes());
            }

            // Add text
            output.write("Welcome to My Store\n".getBytes());
            output.write("--------------------------------\n".getBytes());
            output.write("Item 1       2.00\n".getBytes());
            output.write("Item 2       5.50\n".getBytes());
            output.write("--------------------------------\n".getBytes());
            output.write("TOTAL       7.50\n".getBytes());
            output.write("\n\n".getBytes());

            // Full cut
            output.write(new byte[]{0x1D, 0x56, 0x00});

        } catch (Exception e) {
            e.printStackTrace();
        }
        return output.toByteArray();
    }

    public static byte[] convertBitmapToEscPos(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        // width must be divisible by 8
        int newWidth = (width + 7) / 8 * 8;
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, newWidth, height, false);

        int[] pixels = new int[resized.getWidth() * resized.getHeight()];
        resized.getPixels(pixels, 0, resized.getWidth(), 0, 0, resized.getWidth(), resized.getHeight());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // GS v 0: Print raster bit image
        baos.write(0x1D);
        baos.write(0x76);
        baos.write(0x30);
        baos.write(0x00); // m = 0, normal

        // Width in bytes (LSB first)
        int widthBytes = resized.getWidth() / 8;
        baos.write(widthBytes & 0xFF);
        baos.write((widthBytes >> 8) & 0xFF);

        // Height in dots (LSB first)
        baos.write(height & 0xFF);
        baos.write((height >> 8) & 0xFF);

        // Image data
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < widthBytes; x++) {
                byte b = 0;
                for (int bit = 0; bit < 8; bit++) {
                    int pixelX = x * 8 + bit;
                    int pixelY = y;
                    int color = pixels[pixelY * resized.getWidth() + pixelX];
                    int r = (color >> 16) & 0xFF;
                    int g = (color >> 8) & 0xFF;
                    int blue = color & 0xFF;
                    int gray = (r + g + blue) / 3;
                    if (gray < 128) {
                        b |= (1 << (7 - bit));
                    }
                }
                baos.write(b);
            }
        }

        return baos.toByteArray();
    }



}
