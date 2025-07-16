package com.example.drawbot;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Vereinfachte SVG-zu-G-Code-Konvertierung ohne externe Bibliotheken
 */
public class SimpleSvgToGCodeConverter {

    private static final String TAG = "SimpleSvgToGCode";

    public static class Settings {
        public float drawingWidthMM = 100.0f;
        public float drawingHeightMM = 100.0f;
        public float feedRateMM_Min = 1000.0f;
        public float penDownZ = 0.0f;
        public float penUpZ = 2.0f;
        public float segmentLength = 1.0f;
        public boolean addComments = true;

        // SCARA-spezifische Einstellungen
        public boolean isScaraMode = true;  // Aktiviert SCARA-Koordinatensystem
        public float arm1Length = 100.0f;   // Länge des ersten Arms (mm)
        public float arm2Length = 100.0f;   // Länge des zweiten Arms (mm)
        public float offsetX = 0.0f;        // Offset vom Schultergelenk
        public float offsetY = 100.0f;      // Offset vom Schultergelenk (Arbeitsbereich zentrieren)
    }

    public static class Result {
        public List<String> gCodeLines = new ArrayList<>();
        public String summary = "";
        public int totalCommands = 0;
        public float totalDistance = 0.0f;
        public boolean success = false;
        public String errorMessage = "";
    }

    public static Result convertSvgToGCode(Context context, Uri svgUri, Settings settings) {
        Result result = new Result();

        try {
            Log.d(TAG, "=== STARTE SVG-zu-G-CODE KONVERTIERUNG ===");

            // SVG-Datei lesen
            String svgContent = readSvgFile(context, svgUri);
            if (svgContent == null || svgContent.isEmpty()) {
                result.errorMessage = "SVG-Datei konnte nicht gelesen werden";
                Log.e(TAG, "SVG-Datei leer oder null");
                return result;
            }

            Log.d(TAG, "SVG-Inhalt gelesen: " + svgContent.length() + " Zeichen");
            Log.d(TAG, "SVG-Anfang: " + svgContent.substring(0, Math.min(200, svgContent.length())));

            // SVG-Dimensionen extrahieren
            SvgDimensions dimensions = extractDimensions(svgContent);
            if (dimensions == null) {
                Log.w(TAG, "SVG-Dimensionen nicht gefunden, verwende Fallback");
                dimensions = new SvgDimensions(100, 100); // Fallback
            }

            Log.d(TAG, "SVG-Dimensionen: " + dimensions.width + "x" + dimensions.height);

            // Skalierung berechnen
            float scaleX = settings.drawingWidthMM / dimensions.width;
            float scaleY = settings.drawingHeightMM / dimensions.height;
            float scale = Math.min(scaleX, scaleY);

            Log.d(TAG, "Skalierung: " + scale + " (scaleX: " + scaleX + ", scaleY: " + scaleY + ")");

            // G-Code generieren
            generateGCode(svgContent, result, settings, scale, dimensions);

            result.success = true;
            result.totalCommands = result.gCodeLines.size();
            result.summary = String.format(Locale.US,
                "SVG: %.1f×%.1fmm, Skalierung: %.3f, Befehle: %d, Distanz: %.1fmm",
                dimensions.width, dimensions.height, scale, result.totalCommands, result.totalDistance);

            Log.d(TAG, "=== KONVERTIERUNG ERFOLGREICH ===");
            Log.d(TAG, "Generierte Befehle: " + result.totalCommands);
            Log.d(TAG, "Gesamtdistanz: " + result.totalDistance);

        } catch (Exception e) {
            result.success = false;
            result.errorMessage = "Fehler bei der Konvertierung: " + e.getMessage();
            Log.e(TAG, "Konvertierungsfehler", e);

            // Füge Fehlermeldung als G-Code-Kommentar hinzu
            result.gCodeLines.clear();
            result.gCodeLines.add("; FEHLER: " + e.getMessage());
            result.gCodeLines.add("; Stack Trace: " + android.util.Log.getStackTraceString(e));
        }

        return result;
    }

    private static String readSvgFile(Context context, Uri uri) throws IOException {
        InputStream inputStream = context.getContentResolver().openInputStream(uri);
        if (inputStream == null) return null;

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder content = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            content.append(line).append("\n");
        }

        reader.close();
        inputStream.close();
        return content.toString();
    }

    private static SvgDimensions extractDimensions(String svgContent) {
        // ViewBox extrahieren
        Pattern viewBoxPattern = Pattern.compile("viewBox=[\"']([^\"']*)[\"']");
        Matcher matcher = viewBoxPattern.matcher(svgContent);

        if (matcher.find()) {
            String viewBox = matcher.group(1);
            String[] values = viewBox.trim().split("\\s+");
            if (values.length >= 4) {
                try {
                    float width = Float.parseFloat(values[2]);
                    float height = Float.parseFloat(values[3]);
                    return new SvgDimensions(width, height);
                } catch (NumberFormatException e) {
                    // Fallback zu width/height
                }
            }
        }

        // Width/Height extrahieren
        Pattern widthPattern = Pattern.compile("width=[\"']([^\"']*)[\"']");
        Pattern heightPattern = Pattern.compile("height=[\"']([^\"']*)[\"']");

        matcher = widthPattern.matcher(svgContent);
        String widthStr = matcher.find() ? matcher.group(1) : "100";

        matcher = heightPattern.matcher(svgContent);
        String heightStr = matcher.find() ? matcher.group(1) : "100";

        try {
            float width = Float.parseFloat(widthStr.replaceAll("[^0-9.]", ""));
            float height = Float.parseFloat(heightStr.replaceAll("[^0-9.]", ""));
            return new SvgDimensions(width, height);
        } catch (NumberFormatException e) {
            return new SvgDimensions(100, 100);
        }
    }

    private static void generateGCode(String svgContent, Result result, Settings settings, float scale, SvgDimensions dimensions) {
        List<String> gcode = result.gCodeLines;

        // Header
        if (settings.addComments) {
            gcode.add("; ========================================");
            if (settings.isScaraMode) {
                gcode.add("; SCARA DrawBot G-Code Generator");
                gcode.add(String.format(Locale.US, "; Arm1: %.1fmm, Arm2: %.1fmm", settings.arm1Length, settings.arm2Length));
                gcode.add(String.format(Locale.US, "; Arbeitsbereich-Offset: (%.1f,%.1f)mm", settings.offsetX, settings.offsetY));
            } else {
                gcode.add("; SimpleSvgToGCode Converter");
            }
            gcode.add(String.format(Locale.US, "; SVG: %.1f×%.1fmm, Skalierung: %.3f", dimensions.width, dimensions.height, scale));
            gcode.add(String.format(Locale.US, "; Ziel: %.1f×%.1fmm", settings.drawingWidthMM, settings.drawingHeightMM));
            gcode.add("; ========================================");
        }

        gcode.add("G21 ; Millimeter");
        gcode.add("G90 ; Absolute Koordinaten");
        gcode.add("G92 X0 Y0 Z0 ; Aktuelle Position als Nullpunkt");
        gcode.add(String.format(Locale.US, "G0 Z%.2f ; Stift hoch", settings.penUpZ));
        gcode.add("G0 X0 Y0 ; Home");
        gcode.add("G4 P1.0 ; Pause für Stabilisierung");
        gcode.add("");

        // SCARA-spezifische Koordinaten-Transformation
        float offsetX, offsetY, svgHeight;

        if (settings.isScaraMode) {
            // SCARA-Koordinatensystem: Nullpunkt am Schultergelenk
            // Arbeitsbereich in den erreichbaren Bereich des Arms verschieben
            offsetX = settings.offsetX;
            offsetY = settings.offsetY;
            svgHeight = dimensions.height * scale;

            // Prüfe ob der Arbeitsbereich in der Reichweite des SCARA-Arms liegt
            float maxReach = settings.arm1Length + settings.arm2Length;
            float workspaceMaxX = offsetX + settings.drawingWidthMM;
            float workspaceMaxY = offsetY + settings.drawingHeightMM;
            float maxDistance = (float) Math.sqrt(workspaceMaxX * workspaceMaxX + workspaceMaxY * workspaceMaxY);

            if (maxDistance > maxReach) {
                gcode.add("; WARNUNG: Arbeitsbereich könnte außerhalb der SCARA-Reichweite liegen!");
                gcode.add(String.format(Locale.US, "; Max. Distanz: %.1fmm, SCARA-Reichweite: %.1fmm", maxDistance, maxReach));
            }

            if (settings.addComments) {
                gcode.add(String.format(Locale.US, "; SCARA-Transformation: Offset=(%.1f,%.1f)mm", offsetX, offsetY));
                gcode.add(String.format(Locale.US, "; Max. Reichweite: %.1fmm", maxReach));
            }
        } else {
            // Standard kartesisches System
            offsetX = 0;
            offsetY = 0;
            svgHeight = dimensions.height * scale;

            if (settings.addComments) {
                gcode.add(String.format(Locale.US, "; Kartesische Transformation: Scale=%.3f", scale));
                gcode.add(String.format(Locale.US, "; Y-Achsen-Spiegelung: SVG-Höhe=%.1fmm", svgHeight));
            }
        }

        // Pfade extrahieren und konvertieren
        List<String> paths = extractPaths(svgContent);
        gcode.add(String.format("; Gefundene Pfade: %d", paths.size()));
        for (int i = 0; i < paths.size(); i++) {
            gcode.add(String.format("; === Pfad %d ===", i + 1));
            result.totalDistance += convertPathToGCode(paths.get(i), gcode, settings, scale, svgHeight, offsetX, offsetY);
        }

        // Linien extrahieren
        List<SvgLine> lines = extractLines(svgContent);
        gcode.add(String.format("; Gefundene Linien: %d", lines.size()));
        for (int i = 0; i < lines.size(); i++) {
            gcode.add(String.format("; === Linie %d ===", i + 1));
            result.totalDistance += convertLineToGCode(lines.get(i), gcode, settings, scale, svgHeight, offsetX, offsetY);
        }

        // Rechtecke extrahieren
        List<SvgRect> rects = extractRects(svgContent);
        gcode.add(String.format("; Gefundene Rechtecke: %d", rects.size()));
        for (int i = 0; i < rects.size(); i++) {
            gcode.add(String.format("; === Rechteck %d ===", i + 1));
            result.totalDistance += convertRectToGCode(rects.get(i), gcode, settings, scale, svgHeight, offsetX, offsetY);
        }

        // Kreise extrahieren
        List<SvgCircle> circles = extractCircles(svgContent);
        gcode.add(String.format("; Gefundene Kreise: %d", circles.size()));
        for (int i = 0; i < circles.size(); i++) {
            gcode.add(String.format("; === Kreis %d ===", i + 1));
            result.totalDistance += convertCircleToGCode(circles.get(i), gcode, settings, scale, svgHeight, offsetX, offsetY);
        }

        // Footer
        gcode.add("");
        gcode.add("; === ENDE DER ZEICHNUNG ===");
        gcode.add(String.format(Locale.US, "G0 Z%.2f ; Stift hoch", settings.penUpZ));
        gcode.add("G0 X0 Y0 ; Home");
        gcode.add("M2 ; Ende");

        if (settings.addComments) {
            gcode.add(String.format(Locale.US, "; Gesamtdistanz: %.1fmm", result.totalDistance));
        }
    }

    private static List<String> extractPaths(String svgContent) {
        List<String> paths = new ArrayList<>();
        Pattern pattern = Pattern.compile("<path[^>]*d=[\"']([^\"']*)[\"']");
        Matcher matcher = pattern.matcher(svgContent);

        while (matcher.find()) {
            paths.add(matcher.group(1));
        }

        return paths;
    }

    private static float convertPathToGCode(String pathData, List<String> gcode, Settings settings, float scale, float svgHeight, float offsetX, float offsetY) {
        if (pathData == null || pathData.trim().isEmpty()) return 0;

        gcode.add("; SVG Path: " + pathData.substring(0, Math.min(30, pathData.length())) + "...");

        float distance = 0;
        String[] tokens = pathData.replaceAll(",", " ").split("\\s+");

        float currentX = 0, currentY = 0;
        boolean penDown = false;

        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i].trim();

            if (token.equals("M") || token.equals("m")) {
                // Move to
                if (i + 2 < tokens.length) {
                    try {
                        float x = Float.parseFloat(tokens[i + 1]) * scale;
                        float y = Float.parseFloat(tokens[i + 2]) * scale;

                        if (token.equals("m")) {
                            x += currentX;
                            y += currentY;
                        }

                        // Y-Achsen-Spiegelung und Offset anwenden
                        x += offsetX;
                        y = svgHeight - y + offsetY;

                        if (penDown) {
                            gcode.add(String.format(Locale.US, "G0 Z%.2f", settings.penUpZ));
                            penDown = false;
                        }

                        gcode.add(String.format(Locale.US, "G0 X%.3f Y%.3f", x, y));
                        currentX = x;
                        currentY = y;
                        i += 2;
                    } catch (NumberFormatException e) {
                        // Ignoriere fehlerhafte Koordinaten
                    }
                }
            } else if (token.equals("L") || token.equals("l")) {
                // Line to
                if (i + 2 < tokens.length) {
                    try {
                        float x = Float.parseFloat(tokens[i + 1]) * scale;
                        float y = Float.parseFloat(tokens[i + 2]) * scale;

                        if (token.equals("l")) {
                            x += currentX;
                            y += currentY;
                        }

                        // Y-Achsen-Spiegelung und Offset anwenden
                        x += offsetX;
                        y = svgHeight - y + offsetY;

                        if (!penDown) {
                            gcode.add(String.format(Locale.US, "G1 Z%.2f F%.0f", settings.penDownZ, settings.feedRateMM_Min));
                            penDown = true;
                        }

                        gcode.add(String.format(Locale.US, "G1 X%.3f Y%.3f", x, y));

                        distance += Math.sqrt(Math.pow(x - currentX, 2) + Math.pow(y - currentY, 2));
                        currentX = x;
                        currentY = y;
                        i += 2;
                    } catch (NumberFormatException e) {
                        // Ignoriere fehlerhafte Koordinaten
                    }
                }
            } else if (token.equals("Z") || token.equals("z")) {
                // Close path
                if (penDown) {
                    gcode.add(String.format(Locale.US, "G0 Z%.2f", settings.penUpZ));
                    penDown = false;
                }
            }
        }

        if (penDown) {
            gcode.add(String.format(Locale.US, "G0 Z%.2f", settings.penUpZ));
        }

        return distance;
    }

    private static List<SvgLine> extractLines(String svgContent) {
        List<SvgLine> lines = new ArrayList<>();
        Pattern pattern = Pattern.compile("<line[^>]*x1=[\"']([^\"']*)[\"'][^>]*y1=[\"']([^\"']*)[\"'][^>]*x2=[\"']([^\"']*)[\"'][^>]*y2=[\"']([^\"']*)[\"']");
        Matcher matcher = pattern.matcher(svgContent);

        while (matcher.find()) {
            try {
                float x1 = Float.parseFloat(matcher.group(1));
                float y1 = Float.parseFloat(matcher.group(2));
                float x2 = Float.parseFloat(matcher.group(3));
                float y2 = Float.parseFloat(matcher.group(4));
                lines.add(new SvgLine(x1, y1, x2, y2));
            } catch (NumberFormatException e) {
                // Ignoriere fehlerhafte Linien
            }
        }

        return lines;
    }

    private static float convertLineToGCode(SvgLine line, List<String> gcode, Settings settings, float scale, float svgHeight, float offsetX, float offsetY) {
        float x1 = line.x1 * scale + offsetX;
        float y1 = svgHeight - (line.y1 * scale) + offsetY;
        float x2 = line.x2 * scale + offsetX;
        float y2 = svgHeight - (line.y2 * scale) + offsetY;

        gcode.add(String.format(Locale.US, "G0 X%.3f Y%.3f", x1, y1));
        gcode.add(String.format(Locale.US, "G1 Z%.2f F%.0f", settings.penDownZ, settings.feedRateMM_Min));
        gcode.add(String.format(Locale.US, "G1 X%.3f Y%.3f", x2, y2));
        gcode.add(String.format(Locale.US, "G0 Z%.2f", settings.penUpZ));

        return (float) Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    }

    private static List<SvgRect> extractRects(String svgContent) {
        List<SvgRect> rects = new ArrayList<>();
        Pattern pattern = Pattern.compile("<rect[^>]*x=[\"']([^\"']*)[\"'][^>]*y=[\"']([^\"']*)[\"'][^>]*width=[\"']([^\"']*)[\"'][^>]*height=[\"']([^\"']*)[\"']");
        Matcher matcher = pattern.matcher(svgContent);

        while (matcher.find()) {
            try {
                float x = Float.parseFloat(matcher.group(1));
                float y = Float.parseFloat(matcher.group(2));
                float width = Float.parseFloat(matcher.group(3));
                float height = Float.parseFloat(matcher.group(4));
                rects.add(new SvgRect(x, y, width, height));
            } catch (NumberFormatException e) {
                // Ignoriere fehlerhafte Rechtecke
            }
        }

        return rects;
    }

    private static float convertRectToGCode(SvgRect rect, List<String> gcode, Settings settings, float scale, float svgHeight, float offsetX, float offsetY) {
        float x = rect.x * scale + offsetX;
        float y = svgHeight - (rect.y * scale) + offsetY;
        float width = rect.width * scale;
        float height = rect.height * scale;

        // Rechteck zeichnen (im Uhrzeigersinn)
        gcode.add(String.format(Locale.US, "G0 X%.3f Y%.3f", x, y));
        gcode.add(String.format(Locale.US, "G1 Z%.2f F%.0f", settings.penDownZ, settings.feedRateMM_Min));
        gcode.add(String.format(Locale.US, "G1 X%.3f Y%.3f", x + width, y));
        gcode.add(String.format(Locale.US, "G1 X%.3f Y%.3f", x + width, y - height));
        gcode.add(String.format(Locale.US, "G1 X%.3f Y%.3f", x, y - height));
        gcode.add(String.format(Locale.US, "G1 X%.3f Y%.3f", x, y));
        gcode.add(String.format(Locale.US, "G0 Z%.2f", settings.penUpZ));

        return 2 * (width + height);
    }

    private static List<SvgCircle> extractCircles(String svgContent) {
        List<SvgCircle> circles = new ArrayList<>();
        Pattern pattern = Pattern.compile("<circle[^>]*cx=[\"']([^\"']*)[\"'][^>]*cy=[\"']([^\"']*)[\"'][^>]*r=[\"']([^\"']*)[\"']");
        Matcher matcher = pattern.matcher(svgContent);

        while (matcher.find()) {
            try {
                float cx = Float.parseFloat(matcher.group(1));
                float cy = Float.parseFloat(matcher.group(2));
                float r = Float.parseFloat(matcher.group(3));
                circles.add(new SvgCircle(cx, cy, r));
            } catch (NumberFormatException e) {
                // Ignoriere fehlerhafte Kreise
            }
        }

        return circles;
    }

    private static float convertCircleToGCode(SvgCircle circle, List<String> gcode, Settings settings, float scale, float svgHeight, float offsetX, float offsetY) {
        float cx = circle.cx * scale + offsetX;
        float cy = svgHeight - (circle.cy * scale) + offsetY;
        float r = circle.r * scale;

        float startX = cx + r;
        float startY = cy;

        gcode.add(String.format(Locale.US, "G0 X%.3f Y%.3f", startX, startY));
        gcode.add(String.format(Locale.US, "G1 Z%.2f F%.0f", settings.penDownZ, settings.feedRateMM_Min));
        gcode.add(String.format(Locale.US, "G02 X%.3f Y%.3f I%.3f J%.3f", startX, startY, -r, 0.0f));
        gcode.add(String.format(Locale.US, "G0 Z%.2f", settings.penUpZ));

        return (float) (2 * Math.PI * r);
    }

    // Hilfsklassen
    private static class SvgDimensions {
        final float width, height;
        SvgDimensions(float width, float height) {
            this.width = width;
            this.height = height;
        }
    }

    private static class SvgLine {
        final float x1, y1, x2, y2;
        SvgLine(float x1, float y1, float x2, float y2) {
            this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
        }
    }

    private static class SvgRect {
        final float x, y, width, height;
        SvgRect(float x, float y, float width, float height) {
            this.x = x; this.y = y; this.width = width; this.height = height;
        }
    }

    private static class SvgCircle {
        final float cx, cy, r;
        SvgCircle(float cx, float cy, float r) {
            this.cx = cx; this.cy = cy; this.r = r;
        }
    }
}
