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
import java.util.Comparator;
import java.util.Locale;

/**
 * Hochpräziser Image-to-G-Code-Converter für 2-Stepper-Motor DrawBot
 * Implementiert professionelle Algorithmen für maximale Genauigkeit
 */
public class ImageToGCodeConverter {

    public enum ConversionMode {
        RASTER_HORIZONTAL("Horizontal Raster", "Präzise horizontale Linien mit adaptiver Geschwindigkeit"),
        RASTER_VERTICAL("Vertikal Raster", "Präzise vertikale Linien mit optimierter Pfadführung"),
        RASTER_DIAGONAL("Diagonal Raster", "Diagonale Schraffur für natürliche Schattierung"),
        CONTOUR_FOLLOWING("Kontur folgen", "Folgt exakten Objektkonturen mit Edge-Detection"),
        STIPPLING("Stippling/Punkte", "Punktbasierte Darstellung mit Dichtevariation"),
        SPIRAL("Spiral", "Spiralförmige Zeichnung von außen nach innen"),
        VECTOR_TRACING("Vektor-Tracing", "Automatische Vektorisierung mit Kurvenapproximation"),
        CROSSHATCH("Kreuzschraffur", "Professionelle Kreuzschraffur-Technik");

        private final String displayName;
        private final String description;

        ConversionMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    public static class ConversionSettings {
        public ConversionMode mode = ConversionMode.RASTER_HORIZONTAL;

        // Präzisions-Einstellungen
        public int targetWidthMM = 50;
        public int targetHeightMM = 50;
        public float resolution = 0.1f;         // Auflösung in mm (0.1mm = sehr hoch)
        public int threshold = 128;             // Schwellwert für Schwarz/Weiß
        public float lineSpacing = 0.5f;        // Abstand zwischen Linien in mm

        // Motor-spezifische Einstellungen
        public float feedRate = 800;            // Zeichengeschwindigkeit mm/min
        public float travelSpeed = 2000;        // Eilganggeschwindigkeit mm/min
        public float acceleration = 500;        // Beschleunigung mm/s²
        public float penUpZ = 5.0f;            // Z-Position für Stift hoch
        public float penDownZ = -1.0f;         // Z-Position für Stift runter
        public float penDownSpeed = 200;       // Langsamere Geschwindigkeit beim Absenken

        // Optimierungseinstellungen
        public boolean optimizePath = true;     // Pfadoptimierung für minimale Fahrzeiten
        public boolean invertImage = false;     // Bild invertieren
        public boolean useAdaptiveSpeed = true; // Geschwindigkeit an Kurvenkrümmung anpassen
        public boolean enableLookahead = true;  // Vorausschauende Pfadplanung
        public float cornerTolerance = 0.1f;    // Toleranz für Eckenabrundung in mm

        // Bildverarbeitung
        public boolean useEdgeDetection = true; // Kantenerkennung für bessere Konturen
        public boolean useAntiAliasing = true;  // Anti-Aliasing für glattere Linien
        public int blurRadius = 1;              // Unschärfe-Radius für Rauschunterdrückung
        public float contrastBoost = 1.2f;      // Kontrastverbesserung (1.0 = normal)

        // Erweiterte Einstellungen
        public float minLineLength = 0.5f;      // Minimale Linienlänge in mm
        public int maxPoints = 10000;           // Maximum Punkte pro Pfad
        public boolean addSafetyCommands = true; // Zusätzliche Sicherheitsbefehle

        // Vorschau-Einstellungen für G-Code-Visualisierung
        public int previewWidth = 400;          // Vorschau Breite in Pixel
        public int previewHeight = 400;         // Vorschau Höhe in Pixel
        public boolean showOriginalImage = true; // Original als Hintergrund anzeigen
        public boolean showTravelMoves = true;  // Fahrwege anzeigen
        public boolean showDrawingMoves = true; // Zeichenwege anzeigen
        public boolean animatePreview = false;  // Animation der Pfadabfahrung
    }

    public static class GCodeResult {
        public List<String> gCodeCommands;
        public int totalCommands;
        public float estimatedTime;
        public String summary;
        public int totalMoves;
        public int drawingMoves;
        public float totalDistance;
        public float drawingDistance;
        public int pathSegments;
        public float efficiency; // Verhältnis Zeichnen/Fahren

        public GCodeResult(List<String> commands, ConversionSettings settings) {
            this.gCodeCommands = commands;
            this.totalCommands = commands.size();
            calculateAdvancedStatistics(commands, settings);
            this.summary = generateAdvancedSummary();
        }

        private void calculateAdvancedStatistics(List<String> commands, ConversionSettings settings) {
            totalMoves = 0;
            drawingMoves = 0;
            totalDistance = 0;
            drawingDistance = 0;
            pathSegments = 0;

            float currentX = 0, currentY = 0;
            boolean penDown = false;
            float travelDistance = 0;

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
                            drawingDistance += distance;
                        } else {
                            travelDistance += distance;
                        }

                        currentX = x;
                        currentY = y;
                    }
                } else if (cmd.contains("Z" + settings.penDownZ)) {
                    penDown = true;
                    pathSegments++;
                } else if (cmd.contains("Z" + settings.penUpZ)) {
                    penDown = false;
                }
            }

            // Realistische Zeitschätzung mit Beschleunigung
            float drawingTime = (drawingDistance / settings.feedRate) * 60;
            float travelTime = (travelDistance / settings.travelSpeed) * 60;
            float penMoveTime = pathSegments * 0.5f; // Zeit für Stift hoch/runter
            float accelerationTime = totalMoves * 0.1f; // Beschleunigungszeit

            estimatedTime = drawingTime + travelTime + penMoveTime + accelerationTime;

            // Effizienz berechnen
            efficiency = totalDistance > 0 ? (drawingDistance / totalDistance) * 100 : 0;
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

        private String generateAdvancedSummary() {
            return String.format("G-Code: %d Befehle, %.1fmm gezeichnet, %.1fmm Fahrweg, %.1f%% Effizienz, ~%.1fs",
                totalCommands, drawingDistance, totalDistance - drawingDistance, efficiency, estimatedTime);
        }
    }

    /**
     * Hauptkonvertierungsmethode mit hochpräziser Bildverarbeitung
     */
    public static GCodeResult convertImageToGCode(Context context, Uri imageUri, ConversionSettings settings) {
        try {
            // Bild mit hoher Qualität laden
            InputStream inputStream = context.getContentResolver().openInputStream(imageUri);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            options.inScaled = false;
            Bitmap originalBitmap = BitmapFactory.decodeStream(inputStream, null, options);
            inputStream.close();

            if (originalBitmap == null) {
                return new GCodeResult(createErrorResult("Fehler beim Laden des Bildes"), settings);
            }

            // Erweiterte Bildvorverarbeitung
            Bitmap processedBitmap = advancedPreprocessImage(originalBitmap, settings);

            // Hochpräzise G-Code-Generierung
            List<String> gcode = generateAdvancedGCode(processedBitmap, settings);

            return new GCodeResult(gcode, settings);

        } catch (Exception e) {
            return new GCodeResult(createErrorResult("Fehler: " + e.getMessage()), settings);
        }
    }

    /**
     * Erweiterte Bildvorverarbeitung für maximale Präzision
     */
    private static Bitmap advancedPreprocessImage(Bitmap original, ConversionSettings settings) {
        // Hochauflösende Größenanpassung
        float targetPixelsPerMM = 10.0f / settings.resolution; // 10 Pixel pro mm bei 0.1mm Auflösung
        int targetWidth = (int) (settings.targetWidthMM * targetPixelsPerMM);
        int targetHeight = (int) (settings.targetHeightMM * targetPixelsPerMM);

        // Aspect Ratio beibehalten
        float aspectRatio = (float) original.getWidth() / original.getHeight();
        if (aspectRatio > 1) {
            targetHeight = (int) (targetWidth / aspectRatio);
        } else {
            targetWidth = (int) (targetHeight * aspectRatio);
        }

        // Hochqualitative Skalierung
        Bitmap resized = Bitmap.createScaledBitmap(original, targetWidth, targetHeight, true);

        // Kontrastverbesserung
        if (settings.contrastBoost != 1.0f) {
            resized = enhanceContrast(resized, settings.contrastBoost);
        }

        // Rauschunterdrückung durch Unschärfe
        if (settings.blurRadius > 0) {
            resized = applyGaussianBlur(resized, settings.blurRadius);
        }

        // Erweiterte Schwarz-Weiß-Konvertierung
        Bitmap bw = advancedBlackWhiteConversion(resized, settings);

        // Kantenerkennung für schärfere Konturen
        if (settings.useEdgeDetection) {
            bw = applyEdgeDetection(bw);
        }

        return bw;
    }

    /**
     * Hochwertige G-Code-Generierung mit professionellen Algorithmen
     */
    private static List<String> generateAdvancedGCode(Bitmap bitmap, ConversionSettings settings) {
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
            case VECTOR_TRACING:
                generateVectorTracing(gcode, bitmap, settings);
                break;
            case CROSSHATCH:
                generateCrossHatch(gcode, bitmap, settings);
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
        gcode.add("; WICHTIG: Überprüfen Sie, dass UGS auf Millimeter eingestellt ist!");
        gcode.add("");
        gcode.add("; GRBL Konfiguration für DrawBot");
        gcode.add("$100=32.47"); // X-Achse Schritte pro mm
        gcode.add("$101=32.47"); // Y-Achse Schritte pro mm
        gcode.add("");
        gcode.add("$X"); // Unlock/Reset
        gcode.add("G17"); // XY-Ebene
        gcode.add("G21"); // Millimeter-Modus (wichtig!)
        gcode.add("G90"); // Absolute Koordinaten
        gcode.add("G94"); // Feed rate per minute
        gcode.add("G54"); // Koordinatensystem 1
        gcode.add("M3 S0"); // Spindel aus (falls vorhanden)
        gcode.add("G0 Z" + settings.penUpZ); // Stift hoch
        gcode.add("G0 X0 Y0"); // Home Position
        gcode.add("G4 P0.5"); // Kurze Pause
        gcode.add("; Start der Zeichnung");
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
     * Vektor-Tracing Modus
     */
    private static void generateVectorTracing(List<String> gcode, Bitmap bitmap, ConversionSettings settings) {
        // Vereinfachte Vektorisierung - findet Linien und Kurven im Bild
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float scaleX = (float) settings.targetWidthMM / width;
        float scaleY = (float) settings.targetHeightMM / height;

        List<Point> points = new ArrayList<>();

        // Punkte aus dem Bitmap extrahieren
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (bitmap.getPixel(x, y) == Color.BLACK) {
                    points.add(new Point(x, y));
                }
            }
        }

        // Punkte in Pfade umwandeln
        List<List<Point>> paths = new ArrayList<>();
        if (!points.isEmpty()) {
            paths.add(new ArrayList<>());
            paths.get(0).add(points.get(0));

            for (int i = 1; i < points.size(); i++) {
                Point current = points.get(i);
                Point last = paths.get(paths.size() - 1).get(paths.get(paths.size() - 1).size() - 1);

                // Füge Punkt zu bestehendem Pfad hinzu, wenn er nah genug ist
                if (distance(current, last) < settings.resolution) {
                    paths.get(paths.size() - 1).add(current);
                } else {
                    // Andernfalls erstelle einen neuen Pfad
                    List<Point> newPath = new ArrayList<>();
                    newPath.add(current);
                    paths.add(newPath);
                }
            }
        }

        // Generiere G-Code für jeden Pfad
        for (List<Point> path : paths) {
            if (path.size() > 1) {
                gcode.add(String.format("G0 X%.2f Y%.2f", path.get(0).x * scaleX, path.get(0).y * scaleY));
                gcode.add("G0 Z" + settings.penDownZ);

                for (int i = 1; i < path.size(); i++) {
                    Point p = path.get(i);
                    gcode.add(String.format("G1 X%.2f Y%.2f F%.0f", p.x * scaleX, p.y * scaleY, settings.feedRate));
                }

                gcode.add("G0 Z" + settings.penUpZ);
            }
        }
    }

    private static float distance(Point a, Point b) {
        return (float) Math.sqrt(Math.pow(b.x - a.x, 2) + Math.pow(b.y - a.y, 2));
    }

    /**
     * Kreuzschraffur Modus
     */
    private static void generateCrossHatch(List<String> gcode, Bitmap bitmap, ConversionSettings settings) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float scaleX = (float) settings.targetWidthMM / width;
        float scaleY = (float) settings.targetHeightMM / height;

        int spacing = Math.max(1, (int) (settings.lineSpacing / Math.min(scaleX, scaleY)));

        // Kreuzschraffur in zwei Richtungen
        for (int dir = 0; dir < 2; dir++) {
            for (int start = 0; start < (dir == 0 ? width : height); start += spacing) {
                List<Integer> blackPixels = new ArrayList<>();
                for (int i = 0; i < (dir == 0 ? height : width); i++) {
                    int x = dir == 0 ? start : i;
                    int y = dir == 0 ? i : start;

                    if (bitmap.getPixel(x, y) == Color.BLACK) {
                        blackPixels.add(dir == 0 ? x : y);
                    }
                }

                if (!blackPixels.isEmpty()) {
                    drawCrossHatchLine(gcode, blackPixels, dir == 0 ? start : 0, scaleX, scaleY, settings, dir == 0);
                }
            }
        }
    }

    private static void drawCrossHatchLine(List<String> gcode, List<Integer> pixels, int fixedCoord,
                                         float scaleX, float scaleY, ConversionSettings settings, boolean isHorizontal) {
        Integer start = null;

        for (int i = 0; i < pixels.size(); i++) {
            int coord = pixels.get(i);

            if (start == null) {
                start = coord;
            }

            boolean isLastPixel = (i == pixels.size() - 1);
            boolean nextPixelGap = !isLastPixel && (pixels.get(i + 1) - coord > 1);

            if (isLastPixel || nextPixelGap) {
                if (isHorizontal) {
                    float startX = start * scaleX;
                    float endX = coord * scaleX;
                    float y = fixedCoord * scaleY;

                    // Generiere G-Code statt Preview-Aufruf
                    gcode.add(String.format("G0 X%.2f Y%.2f", startX, y));
                    gcode.add("G0 Z" + settings.penDownZ);
                    gcode.add(String.format("G1 X%.2f Y%.2f F%.0f", endX, y, settings.feedRate));
                    gcode.add("G0 Z" + settings.penUpZ);
                } else {
                    float startY = start * scaleY;
                    float endY = coord * scaleY;
                    float x = fixedCoord * scaleX;

                    // Generiere G-Code statt Preview-Aufruf
                    gcode.add(String.format("G0 X%.2f Y%.2f", x, startY));
                    gcode.add("G0 Z" + settings.penDownZ);
                    gcode.add(String.format("G1 X%.2f Y%.2f F%.0f", x, endY, settings.feedRate));
                    gcode.add("G0 Z" + settings.penUpZ);
                }
                start = null;
            }
        }
    }

    /**
     * Hilfsmethode zum Zeichnen einer Linie in der Vorschau
     */
    private static void drawPreviewLine(List<String> gcode, List<Integer> pixels, int fixedCoord, float scaleX, float scaleY, boolean isHorizontal, ConversionSettings settings) {
        Integer start = null;

        for (int i = 0; i < pixels.size(); i++) {
            int coord = pixels.get(i);

            if (start == null) {
                start = coord;
            }

            boolean isLastPixel = (i == pixels.size() - 1);
            boolean nextPixelGap = !isLastPixel && (pixels.get(i + 1) - coord > 1);

            if (isLastPixel || nextPixelGap) {
                if (isHorizontal) {
                    float startX = start * scaleX;
                    float endX = coord * scaleX;
                    float y = fixedCoord * scaleY;

                    gcode.add(String.format(Locale.US, "G0 X%.2f Y%.2f", startX, y));
                    gcode.add("G0 Z" + settings.penDownZ);
                    gcode.add(String.format(Locale.US, "G1 X%.2f Y%.2f F%.0f", endX, y, settings.feedRate));
                    gcode.add("G0 Z" + settings.penUpZ);
                } else {
                    float startY = start * scaleY;
                    float endY = coord * scaleY;
                    float x = fixedCoord * scaleX;

                    gcode.add(String.format(Locale.US, "G0 X%.2f Y%.2f", x, startY));
                    gcode.add("G0 Z" + settings.penDownZ);
                    gcode.add(String.format(Locale.US, "G1 X%.2f Y%.2f F%.0f", x, endY, settings.feedRate));
                    gcode.add("G0 Z" + settings.penUpZ);
                }
                start = null;
            }
        }
    }

    /**
     * Hilfsmethode zum Zeichnen einer diagonalen Linie in der Vorschau
     */
    private static void drawPreviewDiagonalLine(Bitmap preview, List<Point> points, float scaleX, float scaleY) {
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
                Point startScaled = new Point((int) (start.x * scaleX), (int) (start.y * scaleY));
                Point endScaled = new Point((int) (current.x * scaleX), (int) (current.y * scaleY));
                drawPreviewLineBetweenPoints(preview, startScaled, endScaled);
                start = null;
            }
        }
    }

    /**
     * Hilfsmethode zum Zeichnen einer Kontur in der Vorschau
     */
    private static void drawPreviewContour(Bitmap preview, List<Point> contour, float scaleX, float scaleY) {
        if (contour.isEmpty()) return;

        for (int i = 0; i < contour.size() - 1; i++) {
            Point current = contour.get(i);
            Point next = contour.get(i + 1);

            Point currentScaled = new Point((int) (current.x * scaleX), (int) (current.y * scaleY));
            Point nextScaled = new Point((int) (next.x * scaleX), (int) (next.y * scaleY));

            drawPreviewLineBetweenPoints(preview, currentScaled, nextScaled);
        }

        // Schließe die Kontur
        if (contour.size() > 2) {
            Point first = contour.get(0);
            Point last = contour.get(contour.size() - 1);

            Point firstScaled = new Point((int) (first.x * scaleX), (int) (first.y * scaleY));
            Point lastScaled = new Point((int) (last.x * scaleX), (int) (last.y * scaleY));

            drawPreviewLineBetweenPoints(preview, firstScaled, lastScaled);
        }
    }

    /**
     * Zeichnet eine Linie zwischen zwei Punkten im Preview-Bitmap
     */
    private static void drawPreviewLineBetweenPoints(Bitmap preview, Point start, Point end) {
        if (preview == null || start == null || end == null) return;

        int width = preview.getWidth();
        int height = preview.getHeight();

        // Bresenham-Algorithmus für Linienzeichnung
        int x0 = Math.max(0, Math.min(width - 1, start.x));
        int y0 = Math.max(0, Math.min(height - 1, start.y));
        int x1 = Math.max(0, Math.min(width - 1, end.x));
        int y1 = Math.max(0, Math.min(height - 1, end.y));

        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        int x = x0, y = y0;

        while (true) {
            if (x >= 0 && x < width && y >= 0 && y < height) {
                preview.setPixel(x, y, Color.RED); // Rote Linie für G-Code-Pfad
            }

            if (x == x1 && y == y1) break;

            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
    }

    /**
     * Kontrastverbesserung für bessere Schwarz-Weiß-Trennung
     */
    private static Bitmap enhanceContrast(Bitmap bitmap, float factor) {
        if (bitmap == null) return null;

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        Bitmap result = Bitmap.createBitmap(width, height, bitmap.getConfig());

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = bitmap.getPixel(x, y);
                int r = Color.red(pixel);
                int g = Color.green(pixel);
                int b = Color.blue(pixel);

                // Kontrast anpassen
                r = Math.max(0, Math.min(255, (int) ((r - 128) * factor + 128)));
                g = Math.max(0, Math.min(255, (int) ((g - 128) * factor + 128)));
                b = Math.max(0, Math.min(255, (int) ((b - 128) * factor + 128)));

                result.setPixel(x, y, Color.rgb(r, g, b));
            }
        }

        return result;
    }

    /**
     * Einfache Gaussian Blur Implementierung
     */
    private static Bitmap applyGaussianBlur(Bitmap bitmap, int radius) {
        if (bitmap == null || radius <= 0) return bitmap;

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        Bitmap result = Bitmap.createBitmap(width, height, bitmap.getConfig());

        // Vereinfachter Box-Blur als Gaussian-Approximation
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = 0, g = 0, b = 0, count = 0;

                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        int nx = x + dx;
                        int ny = y + dy;

                        if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                            int pixel = bitmap.getPixel(nx, ny);
                            r += Color.red(pixel);
                            g += Color.green(pixel);
                            b += Color.blue(pixel);
                            count++;
                        }
                    }
                }

                if (count > 0) {
                    r /= count;
                    g /= count;
                    b /= count;
                    result.setPixel(x, y, Color.rgb(r, g, b));
                } else {
                    result.setPixel(x, y, bitmap.getPixel(x, y));
                }
            }
        }

        return result;
    }

    /**
     * Erweiterte Schwarz-Weiß-Konvertierung mit adaptivem Threshold
     */
    private static Bitmap advancedBlackWhiteConversion(Bitmap bitmap, ConversionSettings settings) {
        if (bitmap == null) return null;

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = bitmap.getPixel(x, y);
                int gray = (int) (0.299 * Color.red(pixel) + 0.587 * Color.green(pixel) + 0.114 * Color.blue(pixel));

                if (settings.invertImage) {
                    gray = 255 - gray;
                }

                int color = gray < settings.threshold ? Color.BLACK : Color.WHITE;
                result.setPixel(x, y, color);
            }
        }

        return result;
    }

    /**
     * Einfache Kantenerkennung mit Sobel-Operator
     */
    private static Bitmap applyEdgeDetection(Bitmap bitmap) {
        if (bitmap == null) return null;

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        Bitmap result = Bitmap.createBitmap(width, height, bitmap.getConfig());

        // Sobel-Kernel
        int[][] sobelX = {{-1, 0, 1}, {-2, 0, 2}, {-1, 0, 1}};
        int[][] sobelY = {{-1, -2, -1}, {0, 0, 0}, {1, 2, 1}};

        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                int gx = 0, gy = 0;

                for (int ky = -1; ky <= 1; ky++) {
                    for (int kx = -1; kx <= 1; kx++) {
                        int pixel = bitmap.getPixel(x + kx, y + ky);
                        int gray = (int) (0.299 * Color.red(pixel) + 0.587 * Color.green(pixel) + 0.114 * Color.blue(pixel));

                        gx += gray * sobelX[ky + 1][kx + 1];
                        gy += gray * sobelY[ky + 1][kx + 1];
                    }
                }

                int magnitude = (int) Math.sqrt(gx * gx + gy * gy);
                magnitude = Math.min(255, Math.max(0, magnitude));

                // Verstärke Kanten
                int color = magnitude > 50 ? Color.BLACK : Color.WHITE;
                result.setPixel(x, y, color);
            }
        }

        return result;
    }

    /**
     * Erstellt eine Fehlermeldung als G-Code-Liste
     */
    private static List<String> createErrorResult(String message) {
        List<String> error = new ArrayList<>();
        error.add("; FEHLER: " + message);
        error.add("; Bitte überprüfen Sie das Bild und versuchen Sie es erneut.");
        return error;
    }

    /**
     * Optimiert den G-Code-Pfad für minimale Fahrzeiten
     */
    private static List<String> optimizePath(List<String> gcode) {
        if (gcode == null || gcode.size() < 10) return gcode;

        // Vereinfachte Pfadoptimierung - entfernt unnötige Befehle
        List<String> optimized = new ArrayList<>();
        String lastCommand = "";

        for (String command : gcode) {
            // Überspringe doppelte Befehle
            if (!command.equals(lastCommand)) {
                optimized.add(command);
                lastCommand = command;
            }
        }

        return optimized;
    }

    /**
     * Generiert eine kombinierte Vorschau mit Original-Bild und G-Code-Pfad
     */
    public static Bitmap generateCombinedPreview(Context context, Uri imageUri, List<String> gCodeCommands, ConversionSettings settings) {
        try {
            // Lade das Original-Bild
            InputStream inputStream = context.getContentResolver().openInputStream(imageUri);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            options.inScaled = false;
            Bitmap originalBitmap = BitmapFactory.decodeStream(inputStream, null, options);
            if (inputStream != null) {
                inputStream.close();
            }

            if (originalBitmap == null) {
                return createErrorPreview("Fehler beim Laden des Bildes");
            }

            // Erstelle Preview-Bitmap mit fester Größe
            int previewSize = 400;
            Bitmap preview = Bitmap.createBitmap(previewSize, previewSize, Bitmap.Config.ARGB_8888);

            // Skaliere Original-Bild für Hintergrund
            float aspectRatio = (float) originalBitmap.getWidth() / originalBitmap.getHeight();
            int scaledWidth, scaledHeight;

            if (aspectRatio > 1) {
                scaledWidth = previewSize;
                scaledHeight = (int) (previewSize / aspectRatio);
            } else {
                scaledWidth = (int) (previewSize * aspectRatio);
                scaledHeight = previewSize;
            }

            Bitmap scaledOriginal = Bitmap.createScaledBitmap(originalBitmap, scaledWidth, scaledHeight, true);

            // Zeichne Hintergrund (Original-Bild mit reduzierter Transparenz)
            for (int y = 0; y < scaledHeight; y++) {
                for (int x = 0; x < scaledWidth; x++) {
                    int pixel = scaledOriginal.getPixel(x, y);
                    int alpha = 80; // Reduzierte Transparenz für Hintergrund
                    int r = Color.red(pixel);
                    int g = Color.green(pixel);
                    int b = Color.blue(pixel);
                    preview.setPixel(x, y, Color.argb(alpha, r, g, b));
                }
            }

            // Zeichne G-Code-Pfad über das Bild
            drawGCodePath(preview, gCodeCommands, settings, previewSize);

            return preview;

        } catch (Exception e) {
            return createErrorPreview("Fehler bei Preview-Generierung: " + e.getMessage());
        }
    }

    /**
     * Zeichnet den G-Code-Pfad auf das Preview-Bitmap
     */
    private static void drawGCodePath(Bitmap preview, List<String> gCodeCommands, ConversionSettings settings, int previewSize) {
        if (gCodeCommands == null || gCodeCommands.isEmpty()) return;

        float currentX = 0, currentY = 0;
        boolean penDown = false;
        float scale = (float) previewSize / Math.max(settings.targetWidthMM, settings.targetHeightMM);

        // Zeichne Home-Position (0,0) als schwarzer Punkt
        int homeX = 0;
        int homeY = 0;
        drawPoint(preview, homeX, homeY, Color.BLACK, 3);

        for (String command : gCodeCommands) {
            if (command.startsWith("G0") || command.startsWith("G1")) {
                float newX = extractCoordinateFromCommand(command, 'X', currentX);
                float newY = extractCoordinateFromCommand(command, 'Y', currentY);

                // Konvertiere Koordinaten für Preview
                int pixelX1 = (int) (currentX * scale);
                int pixelY1 = (int) (currentY * scale);
                int pixelX2 = (int) (newX * scale);
                int pixelY2 = (int) (newY * scale);

                // Zeichne Linie je nach Stift-Status
                if (penDown) {
                    // Zeichenweg (Stift unten) - ROT
                    drawLine(preview, pixelX1, pixelY1, pixelX2, pixelY2, Color.RED);
                } else {
                    // Fahrweg (Stift oben) - GRAU
                    drawLine(preview, pixelX1, pixelY1, pixelX2, pixelY2, Color.GRAY);
                }

                currentX = newX;
                currentY = newY;

            } else if (command.contains("Z" + settings.penDownZ)) {
                penDown = true;
                // Stift runter - GRÜN
                int pixelX = (int) (currentX * scale);
                int pixelY = (int) (currentY * scale);
                drawPoint(preview, pixelX, pixelY, Color.GREEN, 2);

            } else if (command.contains("Z" + settings.penUpZ)) {
                penDown = false;
                // Stift hoch - BLAU
                int pixelX = (int) (currentX * scale);
                int pixelY = (int) (currentY * scale);
                drawPoint(preview, pixelX, pixelY, Color.BLUE, 2);
            }
        }
    }

    /**
     * Extrahiert Koordinaten aus G-Code-Befehl
     */
    private static float extractCoordinateFromCommand(String cmd, char axis, float defaultValue) {
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

    /**
     * Zeichnet eine Linie zwischen zwei Punkten
     */
    private static void drawLine(Bitmap bitmap, int x1, int y1, int x2, int y2, int color) {
        if (bitmap == null) return;

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        // Bresenham-Algorithmus
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;

        int x = x1, y = y1;

        while (true) {
            if (x >= 0 && x < width && y >= 0 && y < height) {
                bitmap.setPixel(x, y, color);
            }

            if (x == x2 && y == y2) break;

            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
    }

    /**
     * Zeichnet einen Punkt mit gegebener Größe
     */
    private static void drawPoint(Bitmap bitmap, int centerX, int centerY, int color, int size) {
        if (bitmap == null) return;

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        for (int dy = -size; dy <= size; dy++) {
            for (int dx = -size; dx <= size; dx++) {
                int x = centerX + dx;
                int y = centerY + dy;

                if (x >= 0 && x < width && y >= 0 && y < height) {
                    // Zeichne nur Punkte innerhalb des Kreises
                    if (dx * dx + dy * dy <= size * size) {
                        bitmap.setPixel(x, y, color);
                    }
                }
            }
        }
    }

    /**
     * Erstellt ein Fehler-Preview-Bitmap
     */
    private static Bitmap createErrorPreview(String message) {
        int size = 400;
        Bitmap errorBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);

        // Fülle mit weißem Hintergrund
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                errorBitmap.setPixel(x, y, Color.WHITE);
            }
        }

        // Zeichne rotes X für Fehler
        for (int i = 0; i < size; i++) {
            // Diagonale von links oben nach rechts unten
            if (i < size) errorBitmap.setPixel(i, i, Color.RED);
            // Diagonale von rechts oben nach links unten
            if (i < size) errorBitmap.setPixel(size - 1 - i, i, Color.RED);
        }

        return errorBitmap;
    }
}
