package com.example.drawbot;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.net.Uri;
import android.content.Context;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

/**
 * Fortschrittlicher Image-to-G-Code-Converter
 * Ähnlich wie https://thuijzer.nl/image2gcode/
 */
public class ImageToGCodeConverter {

    public enum ConversionMode {
        RASTER_HORIZONTAL("Horizontal Raster", "Zeichnet horizontal von links nach rechts"),
        RASTER_VERTICAL("Vertikal Raster", "Zeichnet vertikal von oben nach unten"),
        RASTER_DIAGONAL("Diagonal Raster", "Zeichnet diagonal für Schattierungseffekt"),
        CONTOUR_FOLLOWING("Kontur folgen", "Folgt den Konturen der schwarzen Bereiche"),
        STIPPLING("Stippling/Punkte", "Erstellt Punkte basierend auf Bildinhalt"),
        SPIRAL("Spiral", "Zeichnet in Spiralform von außen nach innen")
    }

    public static class ConversionSettings {
        public ConversionMode mode = ConversionMode.RASTER_HORIZONTAL;
        public int targetWidthMM = 50;
        public int targetHeightMM = 50;
        public int threshold = 128;          // Schwellwert für Schwarz/Weiß
        public float lineSpacing = 0.5f;     // Abstand zwischen Linien in mm
        public float feedRate = 800;         // Zeichengeschwindigkeit
        public float travelSpeed = 1500;     // Bewegungsgeschwindigkeit
        public boolean optimizePath = true;  // Pfad optimieren
        public boolean invertImage = false;  // Bild invertieren
        public float penUpZ = 5.0f;          // Z-Position für Stift hoch
        public float penDownZ = -1.0f;       // Z-Position für Stift runter
    }

    public static class GCodeResult {
        public List<String> gCodeCommands;
        public int totalCommands;
        public float estimatedTime;
        public String summary;
        public int totalMoves;
        public int drawingMoves;
        public float totalDistance;

        public GCodeResult(List<String> commands, ConversionSettings settings) {
            this.gCodeCommands = commands;
            this.totalCommands = commands.size();
            calculateStatistics(commands, settings);
            this.summary = generateSummary();
        }

        private void calculateStatistics(List<String> commands, ConversionSettings settings) {
            totalMoves = 0;
            drawingMoves = 0;
            totalDistance = 0;

            float currentX = 0, currentY = 0;
            boolean penDown = false;

            for (String cmd : commands) {
                if (cmd.startsWith("G0") || cmd.startsWith("G1")) {
                    float x = extractCoordinate(cmd, 'X', currentX);
                    float y = extractCoordinate(cmd, 'Y', currentY);

                    if (x != currentX || y != currentY) {
                        float distance = (float) Math.sqrt(Math.pow(x - currentX, 2) + Math.pow(y - currentY, 2));
                        totalDistance += distance;
                        totalMoves++;

                        if (penDown) {
                            drawingMoves++;
                        }

                        currentX = x;
                        currentY = y;
                    }
                } else if (cmd.contains("Z" + settings.penDownZ)) {
                    penDown = true;
                } else if (cmd.contains("Z" + settings.penUpZ)) {
                    penDown = false;
                }
            }

            // Zeitschätzung basierend auf Geschwindigkeit und Distanz
            estimatedTime = (totalDistance / settings.feedRate) * 60; // in Sekunden
        }

        private float extractCoordinate(String cmd, char axis, float defaultValue) {
            int index = cmd.indexOf(axis);
            if (index == -1) return defaultValue;

            String remaining = cmd.substring(index + 1);
            StringBuilder number = new StringBuilder();

            for (char c : remaining.toCharArray()) {
                if (Character.isDigit(c) || c == '.' || c == '-') {
                    number.append(c);
                } else {
                    break;
                }
            }

            try {
                return Float.parseFloat(number.toString());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }

        private String generateSummary() {
            return String.format("G-Code: %d Befehle, %d Bewegungen (%d zeichnend), %.1fmm Distanz, ~%.1fs",
                totalCommands, totalMoves, drawingMoves, totalDistance, estimatedTime);
        }
    }

    /**
     * Hauptkonvertierungsmethode
     */
    public static GCodeResult convertImageToGCode(Context context, Uri imageUri, ConversionSettings settings) {
        try {
            // Bild laden
            InputStream inputStream = context.getContentResolver().openInputStream(imageUri);
            Bitmap originalBitmap = BitmapFactory.decodeStream(inputStream);
            inputStream.close();

            if (originalBitmap == null) {
                return new GCodeResult(createErrorResult("Fehler beim Laden des Bildes"), settings);
            }

            // Bild vorverarbeiten
            Bitmap processedBitmap = preprocessImage(originalBitmap, settings);

            // G-Code generieren basierend auf Modus
            List<String> gcode = generateGCode(processedBitmap, settings);

            return new GCodeResult(gcode, settings);

        } catch (Exception e) {
            return new GCodeResult(createErrorResult("Fehler: " + e.getMessage()), settings);
        }
    }

    /**
     * Bildvorverarbeitung
     */
    private static Bitmap preprocessImage(Bitmap original, ConversionSettings settings) {
        // Größe anpassen
        float aspectRatio = (float) original.getWidth() / original.getHeight();
        int targetWidth, targetHeight;

        if (aspectRatio > 1) {
            targetWidth = 100; // Pixel für Verarbeitung
            targetHeight = (int) (100 / aspectRatio);
        } else {
            targetHeight = 100;
            targetWidth = (int) (100 * aspectRatio);
        }

        Bitmap resized = Bitmap.createScaledBitmap(original, targetWidth, targetHeight, true);

        // Zu Schwarz-Weiß konvertieren
        Bitmap bw = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);

        for (int y = 0; y < targetHeight; y++) {
            for (int x = 0; x < targetWidth; x++) {
                int pixel = resized.getPixel(x, y);
                int gray = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3;

                if (settings.invertImage) {
                    gray = 255 - gray;
                }

                boolean isBlack = gray < settings.threshold;
                bw.setPixel(x, y, isBlack ? Color.BLACK : Color.WHITE);
            }
        }

        return bw;
    }

    /**
     * G-Code-Generierung basierend auf Konvertierungsmodus
     */
    private static List<String> generateGCode(Bitmap bitmap, ConversionSettings settings) {
        List<String> gcode = new ArrayList<>();

        // Header
        addHeader(gcode, settings);

        // Konvertierung je nach Modus
        switch (settings.mode) {
            case RASTER_HORIZONTAL:
                generateHorizontalRaster(gcode, bitmap, settings);
                break;
            case RASTER_VERTICAL:
                generateVerticalRaster(gcode, bitmap, settings);
                break;
            case RASTER_DIAGONAL:
                generateDiagonalRaster(gcode, bitmap, settings);
                break;
            case CONTOUR_FOLLOWING:
                generateContourFollowing(gcode, bitmap, settings);
                break;
            case STIPPLING:
                generateStippling(gcode, bitmap, settings);
                break;
            case SPIRAL:
                generateSpiral(gcode, bitmap, settings);
                break;
        }

        // Footer
        addFooter(gcode, settings);

        // Pfad optimieren falls gewünscht
        if (settings.optimizePath) {
            gcode = optimizePath(gcode);
        }

        return gcode;
    }

    private static void addHeader(List<String> gcode, ConversionSettings settings) {
        gcode.add("; DrawBot G-Code generiert");
        gcode.add("; Modus: " + settings.mode.name());
        gcode.add("; Größe: " + settings.targetWidthMM + "x" + settings.targetHeightMM + "mm");
        gcode.add("$X");
        gcode.add("G21"); // Millimeter
        gcode.add("G90"); // Absolute Koordinaten
        gcode.add("G94"); // Feed rate per minute
        gcode.add("G0 Z" + settings.penUpZ); // Stift hoch
        gcode.add("G0 X0 Y0"); // Home
    }

    private static void addFooter(List<String> gcode, ConversionSettings settings) {
        gcode.add("G0 Z" + settings.penUpZ); // Stift hoch
        gcode.add("G0 X0 Y0"); // Zurück nach Hause
        gcode.add("; Ende der Zeichnung");
    }

    /**
     * Horizontaler Raster-Modus
     */
    private static void generateHorizontalRaster(List<String> gcode, Bitmap bitmap, ConversionSettings settings) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float scaleX = (float) settings.targetWidthMM / width;
        float scaleY = (float) settings.targetHeightMM / height;

        int lineStep = Math.max(1, (int) (settings.lineSpacing / scaleY));

        for (int y = 0; y < height; y += lineStep) {
            boolean leftToRight = (y / lineStep) % 2 == 0;

            List<Integer> blackPixels = new ArrayList<>();
            for (int x = 0; x < width; x++) {
                if (bitmap.getPixel(x, y) == Color.BLACK) {
                    blackPixels.add(x);
                }
            }

            if (!blackPixels.isEmpty()) {
                if (!leftToRight) {
                    Collections.reverse(blackPixels);
                }

                drawHorizontalLine(gcode, blackPixels, y, scaleX, scaleY, settings);
            }
        }
    }

    private static void drawHorizontalLine(List<String> gcode, List<Integer> pixels, int y,
                                         float scaleX, float scaleY, ConversionSettings settings) {
        Integer start = null;

        for (int i = 0; i < pixels.size(); i++) {
            int x = pixels.get(i);

            if (start == null) {
                start = x;
            }

            // Prüfe ob nächster Pixel zusammenhängend ist
            boolean isLastPixel = (i == pixels.size() - 1);
            boolean nextPixelGap = !isLastPixel && (pixels.get(i + 1) - x > 1);

            if (isLastPixel || nextPixelGap) {
                // Zeichne Linie von start bis x
                float startX = start * scaleX;
                float endX = x * scaleX;
                float yPos = y * scaleY;

                gcode.add(String.format("G0 X%.2f Y%.2f", startX, yPos));
                gcode.add("G0 Z" + settings.penDownZ);
                gcode.add(String.format("G1 X%.2f Y%.2f F%.0f", endX, yPos, settings.feedRate));
                gcode.add("G0 Z" + settings.penUpZ);

                start = null;
            }
        }
    }

    /**
     * Vertikaler Raster-Modus
     */
    private static void generateVerticalRaster(List<String> gcode, Bitmap bitmap, ConversionSettings settings) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float scaleX = (float) settings.targetWidthMM / width;
        float scaleY = (float) settings.targetHeightMM / height;

        int lineStep = Math.max(1, (int) (settings.lineSpacing / scaleX));

        for (int x = 0; x < width; x += lineStep) {
            boolean topToBottom = (x / lineStep) % 2 == 0;

            List<Integer> blackPixels = new ArrayList<>();
            for (int y = 0; y < height; y++) {
                if (bitmap.getPixel(x, y) == Color.BLACK) {
                    blackPixels.add(y);
                }
            }

            if (!blackPixels.isEmpty()) {
                if (!topToBottom) {
                    Collections.reverse(blackPixels);
                }

                drawVerticalLine(gcode, blackPixels, x, scaleX, scaleY, settings);
            }
        }
    }

    private static void drawVerticalLine(List<String> gcode, List<Integer> pixels, int x,
                                       float scaleX, float scaleY, ConversionSettings settings) {
        Integer start = null;

        for (int i = 0; i < pixels.size(); i++) {
            int y = pixels.get(i);

            if (start == null) {
                start = y;
            }

            boolean isLastPixel = (i == pixels.size() - 1);
            boolean nextPixelGap = !isLastPixel && (pixels.get(i + 1) - y > 1);

            if (isLastPixel || nextPixelGap) {
                float startY = start * scaleY;
                float endY = y * scaleY;
                float xPos = x * scaleX;

                gcode.add(String.format("G0 X%.2f Y%.2f", xPos, startY));
                gcode.add("G0 Z" + settings.penDownZ);
                gcode.add(String.format("G1 X%.2f Y%.2f F%.0f", xPos, endY, settings.feedRate));
                gcode.add("G0 Z" + settings.penUpZ);

                start = null;
            }
        }
    }

    /**
     * Diagonaler Raster-Modus für Schattierungseffekt
     */
    private static void generateDiagonalRaster(List<String> gcode, Bitmap bitmap, ConversionSettings settings) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float scaleX = (float) settings.targetWidthMM / width;
        float scaleY = (float) settings.targetHeightMM / height;

        int spacing = Math.max(2, (int) (settings.lineSpacing / Math.min(scaleX, scaleY)));

        // Diagonale Linien von links-oben nach rechts-unten
        for (int startX = 0; startX < width + height; startX += spacing) {
            List<Point> linePoints = new ArrayList<>();

            for (int x = 0; x < width; x++) {
                int y = startX - x;
                if (y >= 0 && y < height) {
                    if (bitmap.getPixel(x, y) == Color.BLACK) {
                        linePoints.add(new Point(x, y));
                    }
                }
            }

            if (!linePoints.isEmpty()) {
                drawDiagonalLine(gcode, linePoints, scaleX, scaleY, settings);
            }
        }
    }

    private static class Point {
        int x, y;
        Point(int x, int y) { this.x = x; this.y = y; }
    }

    private static void drawDiagonalLine(List<String> gcode, List<Point> points,
                                       float scaleX, float scaleY, ConversionSettings settings) {
        if (points.isEmpty()) return;

        Point start = null;

        for (int i = 0; i < points.size(); i++) {
            Point current = points.get(i);

            if (start == null) {
                start = current;
            }

            boolean isLastPoint = (i == points.size() - 1);
            boolean nextPointGap = !isLastPoint &&
                (Math.abs(points.get(i + 1).x - current.x) > 1 ||
                 Math.abs(points.get(i + 1).y - current.y) > 1);

            if (isLastPoint || nextPointGap) {
                float startX = start.x * scaleX;
                float startY = start.y * scaleY;
                float endX = current.x * scaleX;
                float endY = current.y * scaleY;

                gcode.add(String.format("G0 X%.2f Y%.2f", startX, startY));
                gcode.add("G0 Z" + settings.penDownZ);
                gcode.add(String.format("G1 X%.2f Y%.2f F%.0f", endX, endY, settings.feedRate));
                gcode.add("G0 Z" + settings.penUpZ);

                start = null;
            }
        }
    }

    /**
     * Kontur-folgen Modus
     */
    private static void generateContourFollowing(List<String> gcode, Bitmap bitmap, ConversionSettings settings) {
        // Vereinfachte Konturverfolgung - findet Umrisse von schwarzen Bereichen
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float scaleX = (float) settings.targetWidthMM / width;
        float scaleY = (float) settings.targetHeightMM / height;

        boolean[][] visited = new boolean[width][height];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (bitmap.getPixel(x, y) == Color.BLACK && !visited[x][y]) {
                    List<Point> contour = traceContour(bitmap, x, y, visited);
                    if (contour.size() > 3) { // Nur größere Konturen
                        drawContour(gcode, contour, scaleX, scaleY, settings);
                    }
                }
            }
        }
    }

    private static List<Point> traceContour(Bitmap bitmap, int startX, int startY, boolean[][] visited) {
        List<Point> contour = new ArrayList<>();
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        // Einfache 4-Nachbarschafts-Verfolgung
        int[] dx = {0, 1, 0, -1};
        int[] dy = {-1, 0, 1, 0};

        int x = startX, y = startY;

        do {
            contour.add(new Point(x, y));
            visited[x][y] = true;

            boolean found = false;
            for (int i = 0; i < 4; i++) {
                int nx = x + dx[i];
                int ny = y + dy[i];

                if (nx >= 0 && nx < width && ny >= 0 && ny < height &&
                    bitmap.getPixel(nx, ny) == Color.BLACK && !visited[nx][ny]) {
                    x = nx;
                    y = ny;
                    found = true;
                    break;
                }
            }

            if (!found) break;

        } while (contour.size() < 1000); // Schutz vor Endlosschleife

        return contour;
    }

    private static void drawContour(List<String> gcode, List<Point> contour,
                                  float scaleX, float scaleY, ConversionSettings settings) {
        if (contour.isEmpty()) return;

        Point first = contour.get(0);
        gcode.add(String.format("G0 X%.2f Y%.2f", first.x * scaleX, first.y * scaleY));
        gcode.add("G0 Z" + settings.penDownZ);

        for (int i = 1; i < contour.size(); i++) {
            Point p = contour.get(i);
            gcode.add(String.format("G1 X%.2f Y%.2f F%.0f", p.x * scaleX, p.y * scaleY, settings.feedRate));
        }

        // Schließe die Kontur
        gcode.add(String.format("G1 X%.2f Y%.2f F%.0f", first.x * scaleX, first.y * scaleY, settings.feedRate));
        gcode.add("G0 Z" + settings.penUpZ);
    }

    /**
     * Stippling/Punkte Modus
     */
    private static void generateStippling(List<String> gcode, Bitmap bitmap, ConversionSettings settings) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float scaleX = (float) settings.targetWidthMM / width;
        float scaleY = (float) settings.targetHeightMM / height;

        for (int y = 0; y < height; y += 2) {
            for (int x = 0; x < width; x += 2) {
                if (bitmap.getPixel(x, y) == Color.BLACK) {
                    float xPos = x * scaleX;
                    float yPos = y * scaleY;

                    // Erstelle einen kleinen Punkt
                    gcode.add(String.format("G0 X%.2f Y%.2f", xPos, yPos));
                    gcode.add("G0 Z" + settings.penDownZ);
                    gcode.add("G4 P0.1"); // Kurze Pause für Punkt
                    gcode.add("G0 Z" + settings.penUpZ);
                }
            }
        }
    }

    /**
     * Spiral-Modus
     */
    private static void generateSpiral(List<String> gcode, Bitmap bitmap, ConversionSettings settings) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float scaleX = (float) settings.targetWidthMM / width;
        float scaleY = (float) settings.targetHeightMM / height;

        int centerX = width / 2;
        int centerY = height / 2;
        int maxRadius = Math.min(centerX, centerY);

        boolean penDown = false;

        for (int r = 1; r < maxRadius; r++) {
            for (int angle = 0; angle < 360; angle += 5) {
                double rad = Math.toRadians(angle);
                int x = (int) (centerX + r * Math.cos(rad));
                int y = (int) (centerY + r * Math.sin(rad));

                if (x >= 0 && x < width && y >= 0 && y < height) {
                    boolean shouldDraw = bitmap.getPixel(x, y) == Color.BLACK;
                    float xPos = x * scaleX;
                    float yPos = y * scaleY;

                    if (shouldDraw && !penDown) {
                        gcode.add(String.format("G0 X%.2f Y%.2f", xPos, yPos));
                        gcode.add("G0 Z" + settings.penDownZ);
                        penDown = true;
                    } else if (!shouldDraw && penDown) {
                        gcode.add("G0 Z" + settings.penUpZ);
                        penDown = false;
                    }

                    if (shouldDraw) {
                        gcode.add(String.format("G1 X%.2f Y%.2f F%.0f", xPos, yPos, settings.feedRate));
                    }
                }
            }
        }

        if (penDown) {
            gcode.add("G0 Z" + settings.penUpZ);
        }
    }

    /**
     * Pfadoptimierung
     */
    private static List<String> optimizePath(List<String> gcode) {
        // Einfache Optimierung: Entferne redundante Bewegungen
        List<String> optimized = new ArrayList<>();
        String lastCommand = null;

        for (String command : gcode) {
            if (!command.equals(lastCommand)) {
                optimized.add(command);
                lastCommand = command;
            }
        }

        return optimized;
    }

    private static List<String> createErrorResult(String error) {
        List<String> result = new ArrayList<>();
        result.add("; " + error);
        return result;
    }

    // Wrapper für Kompatibilität mit bestehender App
    public static GCodeResult convertImageToGCodeSimple(Context context, Uri imageUri, int targetSize, int threshold) {
        ConversionSettings settings = new ConversionSettings();
        settings.targetWidthMM = targetSize;
        settings.targetHeightMM = targetSize;
        settings.threshold = threshold;
        settings.mode = ConversionMode.RASTER_HORIZONTAL;

        return convertImageToGCode(context, imageUri, settings);
    }
}
