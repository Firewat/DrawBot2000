package com.example.drawbot;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.content.Context;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Einfacher, stabiler Image-to-G-Code Converter
 */
public class SimpleImageToGCodeConverter {

    public static class SimpleSettings {
        public int targetWidthMM = 50;
        public int targetHeightMM = 50;
        public int threshold = 128;             // Schwellwert für Schwarz/Weiß
        public float lineSpacing = 1.0f;        // Abstand zwischen Linien in mm
        public float feedRate = 500;            // Zeichengeschwindigkeit mm/min
        public float travelSpeed = 1500;        // Eilganggeschwindigkeit mm/min
        public float penUpZ = 2.0f;            // Z-Position für Stift hoch
        public float penDownZ = 0.0f;          // Z-Position für Stift runter
        public boolean invertImage = false;     // Bild invertieren
    }

    public static class SimpleResult {
        public List<String> gCodeCommands;
        public int totalCommands;
        public float estimatedTime;
        public String summary;
        public int totalMoves;
        public int drawingMoves;
        public float totalDistance;
        public float drawingDistance;

        public SimpleResult(List<String> commands) {
            this.gCodeCommands = commands;
            this.totalCommands = commands.size();
            this.totalMoves = 0;
            this.drawingMoves = 0;
            this.totalDistance = 0;
            this.drawingDistance = 0;
            this.estimatedTime = this.totalCommands * 0.1f; // Vereinfachte Schätzung
            this.summary = "G-Code: " + totalCommands + " Befehle";
        }
    }

    /**
     * Hauptkonvertierungsmethode - einfach und stabil
     */
    public static SimpleResult convertImageToGCode(Context context, Uri imageUri, SimpleSettings settings) {
        List<String> gcode = new ArrayList<>();

        try {
            // 1. Bild laden
            Bitmap bitmap = loadAndProcessImage(context, imageUri, settings);
            if (bitmap == null) {
                gcode.add("; FEHLER: Bild konnte nicht geladen werden");
                return new SimpleResult(gcode);
            }

            // 2. G-Code Header
            addSimpleHeader(gcode, settings);

            // 3. Bild in G-Code umwandeln (horizontaler Raster)
            convertBitmapToGCode(gcode, bitmap, settings);

            // 4. G-Code Footer
            addSimpleFooter(gcode, settings);

            return new SimpleResult(gcode);

        } catch (Exception e) {
            gcode.clear();
            gcode.add("; FEHLER: " + e.getMessage());
            gcode.add("; Bitte versuchen Sie es erneut");
            return new SimpleResult(gcode);
        }
    }

    /**
     * Lädt und verarbeitet das Bild
     */
    private static Bitmap loadAndProcessImage(Context context, Uri imageUri, SimpleSettings settings) {
        try {
            // Bild laden
            InputStream inputStream = context.getContentResolver().openInputStream(imageUri);
            if (inputStream == null) return null;

            Bitmap originalBitmap = BitmapFactory.decodeStream(inputStream);
            inputStream.close();

            if (originalBitmap == null) return null;

            // Auf Zielgröße skalieren
            Bitmap scaledBitmap = scaleBitmap(originalBitmap, settings);

            // In Schwarz-Weiß umwandeln
            Bitmap bwBitmap = convertToBlackWhite(scaledBitmap, settings);

            return bwBitmap;

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Skaliert das Bild auf die Zielgröße
     */
    private static Bitmap scaleBitmap(Bitmap original, SimpleSettings settings) {
        // Berechne Zielauflösung (z.B. 2 Pixel pro mm)
        int pixelsPerMM = 2;
        int targetWidth = settings.targetWidthMM * pixelsPerMM;
        int targetHeight = settings.targetHeightMM * pixelsPerMM;

        // Seitenverhältnis beibehalten
        float aspectRatio = (float) original.getWidth() / original.getHeight();
        if (aspectRatio > 1) {
            targetHeight = (int) (targetWidth / aspectRatio);
        } else {
            targetWidth = (int) (targetHeight * aspectRatio);
        }

        return Bitmap.createScaledBitmap(original, targetWidth, targetHeight, true);
    }

    /**
     * Wandelt das Bild in Schwarz-Weiß um
     */
    private static Bitmap convertToBlackWhite(Bitmap bitmap, SimpleSettings settings) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = bitmap.getPixel(x, y);

                // Graustufe berechnen
                int gray = (int) (0.299 * Color.red(pixel) +
                                 0.587 * Color.green(pixel) +
                                 0.114 * Color.blue(pixel));

                // Invertieren falls gewünscht
                if (settings.invertImage) {
                    gray = 255 - gray;
                }

                // Schwellwert anwenden
                int color = gray < settings.threshold ? Color.BLACK : Color.WHITE;
                result.setPixel(x, y, color);
            }
        }

        return result;
    }

    /**
     * Fügt G-Code Header hinzu
     */
    private static void addSimpleHeader(List<String> gcode, SimpleSettings settings) {
        gcode.add("; DrawBot G-Code - Einfacher Converter");
        gcode.add("; Größe: " + settings.targetWidthMM + "x" + settings.targetHeightMM + "mm");
        gcode.add("");
        gcode.add("G21 ; Millimeter-Modus");
        gcode.add("G90 ; Absolute Koordinaten");
        gcode.add("G94 ; Feed rate per minute");
        gcode.add("G0 Z" + settings.penUpZ + " ; Stift hoch");
        gcode.add("G0 X0 Y0 ; Home Position");
        gcode.add("G4 P0.5 ; Kurze Pause");
        gcode.add("; Start der Zeichnung");
    }

    /**
     * Fügt G-Code Footer hinzu
     */
    private static void addSimpleFooter(List<String> gcode, SimpleSettings settings) {
        gcode.add("G0 Z" + settings.penUpZ + " ; Stift hoch");
        gcode.add("G0 X0 Y0 ; Zurück nach Hause");
        gcode.add("; Ende der Zeichnung");
    }

    /**
     * Wandelt das Bitmap in G-Code um (horizontaler Raster)
     */
    private static void convertBitmapToGCode(List<String> gcode, Bitmap bitmap, SimpleSettings settings) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        // Skalierungsfaktoren
        float scaleX = (float) settings.targetWidthMM / width;
        float scaleY = (float) settings.targetHeightMM / height;

        // Linienabstand in Pixeln
        int lineStep = Math.max(1, Math.round(settings.lineSpacing / scaleY));

        // Durch das Bild scannen (horizontal)
        for (int y = 0; y < height; y += lineStep) {
            // Richtung alternieren für bessere Effizienz
            boolean leftToRight = (y / lineStep) % 2 == 0;

            List<Integer> blackPixels = new ArrayList<>();

            // Schwarze Pixel in dieser Zeile finden
            for (int x = 0; x < width; x++) {
                if (bitmap.getPixel(x, y) == Color.BLACK) {
                    blackPixels.add(x);
                }
            }

            if (!blackPixels.isEmpty()) {
                // Richtung umkehren falls nötig
                if (!leftToRight) {
                    List<Integer> reversed = new ArrayList<>();
                    for (int i = blackPixels.size() - 1; i >= 0; i--) {
                        reversed.add(blackPixels.get(i));
                    }
                    blackPixels = reversed;
                }

                // G-Code für diese Linie generieren
                drawLine(gcode, blackPixels, y, scaleX, scaleY, settings);
            }
        }
    }

    /**
     * Zeichnet eine horizontale Linie
     */
    private static void drawLine(List<String> gcode, List<Integer> pixels, int y,
                                float scaleX, float scaleY, SimpleSettings settings) {
        if (pixels.isEmpty()) return;

        Integer segmentStart = null;

        for (int i = 0; i < pixels.size(); i++) {
            int x = pixels.get(i);

            if (segmentStart == null) {
                segmentStart = x;
            }

            // Prüfe ob das nächste Pixel zusammenhängend ist
            boolean isLastPixel = (i == pixels.size() - 1);
            boolean hasGap = !isLastPixel && (pixels.get(i + 1) - x > 1);

            if (isLastPixel || hasGap) {
                // Zeichne Liniensegment von segmentStart bis x
                float startX = segmentStart * scaleX;
                float endX = x * scaleX;
                float yPos = y * scaleY;

                // G-Code für Liniensegment
                gcode.add(String.format("G0 X%.2f Y%.2f", startX, yPos));
                gcode.add("G0 Z" + settings.penDownZ);
                gcode.add(String.format("G1 X%.2f Y%.2f F%.0f", endX, yPos, settings.feedRate));
                gcode.add("G0 Z" + settings.penUpZ);

                segmentStart = null;
            }
        }
    }

    /**
     * Einfache Dummy-Methode für generateCombinedPreview (falls noch verwendet)
     */
    public static Bitmap generateCombinedPreview(Context context, Uri imageUri, List<String> gCodeCommands, SimpleSettings settings) {
        // Einfaches weißes Bild zurückgeben
        Bitmap preview = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < 400; y++) {
            for (int x = 0; x < 400; x++) {
                preview.setPixel(x, y, Color.WHITE);
            }
        }
        return preview;
    }
}
