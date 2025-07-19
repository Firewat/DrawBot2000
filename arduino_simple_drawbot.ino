/*
  DrawBot Firmware mit AccelStepper und "ok" Antworten
  Funktioniert mit der Android App ohne GRBL

  Hardware:
  - Stepper X: Step Pin 2, Dir Pin 5
  - Stepper Y: Step Pin 3, Dir Pin 6
  - Bluetooth: RX Pin 10, TX Pin 11
*/

#include <AccelStepper.h>
#include <SoftwareSerial.h>

#define HALFSTEP 8

// Motor pin definitions
#define X_motorPin1  5
#define X_motorPin2  4
#define X_motorPin3  3
#define X_motorPin4  2
#define Y_motorPin1  A3
#define Y_motorPin2  A2
#define Y_motorPin3  A1
#define Y_motorPin4  A0
#define Z_motorPin1  13
#define Z_motorPin2  12
#define Z_motorPin3  9
#define Z_motorPin4  8

SoftwareSerial btSerial(10, 11);

// Stepper Setup für 28BYJ-48 mit ULN2003 (HALFSTEP)
AccelStepper stepperX(HALFSTEP, X_motorPin1, X_motorPin3, X_motorPin2, X_motorPin4);
AccelStepper stepperY(HALFSTEP, Y_motorPin1, Y_motorPin3, Y_motorPin2, Y_motorPin4);
AccelStepper stepperZ(HALFSTEP, Z_motorPin1, Z_motorPin3, Z_motorPin2, Z_motorPin4);

String input;
float posX = 0;
float posY = 0;
float currentZ = 0; // Aktuelle Z-Position für Stift
bool isUnlocked = false; // Für $X Unlock-Befehl

void setup() {
  Serial.begin(115200);
  btSerial.begin(115200);

  // Stepper konfigurieren
  stepperX.setMaxSpeed(1000);
  stepperX.setAcceleration(500);
  stepperY.setMaxSpeed(1000);
  stepperY.setAcceleration(500);

  // Startup-Nachricht für die App
  delay(1000);
  btSerial.println("DrawBot v1.0 Ready");
  btSerial.println("[INFO: Send $X to unlock, then G1 commands]");

  Serial.println("DrawBot gestartet - warte auf Bluetooth-Befehle");
}

void loop() {
  // Befehle vom Bluetooth empfangen
  if (btSerial.available()) {
    input = btSerial.readStringUntil('\n');
    input.trim();
    if (input.length() > 0) {
      processCommand(input);
    }
  }

  // Wichtig: Stepper regelmäßig updaten
  stepperX.run();
  stepperY.run();
}

void processCommand(String command) {
  command.trim();
  command.toUpperCase();

  Serial.print("Empfangen: ");
  Serial.println(command);

  // $X - Unlock (für Kompatibilität mit der App)
  if (command == "$X") {
    isUnlocked = true;
    btSerial.println("ok");
    Serial.println("DrawBot entsperrt");
    return;
  }

  // Status abfragen
  if (command == "?") {
    String status = "<Idle|MPos:" + String(posX, 3) + "," + String(posY, 3) + ",0.000>";
    btSerial.println(status);
    Serial.println("Status gesendet: " + status);
    return;
  }

  // Prüfen ob entsperrt (außer für System-Befehle)
  if (!isUnlocked && !command.startsWith("$") && !command.startsWith("G21") && !command.startsWith("G90")) {
    btSerial.println("error:9"); // Alarm state
    Serial.println("FEHLER: DrawBot ist gesperrt - sende $X zum entsperren");
    return;
  }

  // G-Code verarbeiten
  if (command.startsWith("G1") || command.startsWith("G0")) {
    processGCode(command);
    btSerial.println("ok"); // Wichtig: "ok" für die App
  }
  else if (command.startsWith("G21")) {
    // Millimeter-Einheiten (ignorieren, aber ok senden)
    btSerial.println("ok");
    Serial.println("Millimeter-Modus bestätigt");
  }
  else if (command.startsWith("G90")) {
    // Absolute Koordinaten (ignorieren, aber ok senden)
    btSerial.println("ok");
    Serial.println("Absoluter Modus bestätigt");
  }
  else if (command.startsWith("G94")) {
    // Feed rate per minute (ignorieren, aber ok senden)
    btSerial.println("ok");
    Serial.println("Feed-Rate-Modus bestätigt");
  }
  else if (command.startsWith("G")) {
    // Andere G-Codes
    btSerial.println("ok");
    Serial.println("G-Code akzeptiert: " + command);
  }
  else if (command.startsWith("M")) {
    // M-Codes (ignorieren, aber ok senden)
    btSerial.println("ok");
    Serial.println("M-Code akzeptiert: " + command);
  }
  else {
    // Unbekannte Befehle
    btSerial.println("ok");
    Serial.println("Unbekannter Befehl akzeptiert: " + command);
  }
}

void processGCode(String code) {
  float x = posX;
  float y = posY;
  float z = currentZ;
  bool hasMovement = false;
  bool isRapidMove = code.startsWith("G0"); // G0 = schnell, G1 = langsam

  // --- Änderung: Bei jedem G0 Stift hoch, bei jedem G1 Stift runter ---
  if (isRapidMove) {
    movePen(1); // Stift hoch (Z1)
  } else if (code.startsWith("G1")) {
    movePen(-1); // Stift runter (Z0)
  }
  // --- Ende Änderung ---

  // X-Parameter extrahieren
  int xIndex = code.indexOf('X');
  if (xIndex != -1) {
    int xEnd = findNextSpace(code, xIndex);
    x = code.substring(xIndex + 1, xEnd).toFloat();
    hasMovement = true;
  }

  // Y-Parameter extrahieren
  int yIndex = code.indexOf('Y');
  if (yIndex != -1) {
    int yEnd = findNextSpace(code, yIndex);
    y = code.substring(yIndex + 1, yEnd).toFloat();
    hasMovement = true;
  }

  // Z-Parameter extrahieren (für manuelle Z-Steuerung, falls gewünscht)
  int zIndex = code.indexOf('Z');
  if (zIndex != -1) {
    int zEnd = findNextSpace(code, zIndex);
    z = code.substring(zIndex + 1, zEnd).toFloat();
    movePen(z);
  }

  // Nur bewegen wenn X oder Y Parameter vorhanden
  if (hasMovement) {
    Serial.print("Fahre zu X=");
    Serial.print(x);
    Serial.print(" Y=");
    Serial.print(y);
    if (isRapidMove) {
      Serial.println(" (SCHNELL - G0)");
    } else {
      Serial.println(" (LANGSAM - G1)");
    }

    // Konvertieren in Steps (40 Schritte = 1 mm für langsamere, präzisere Bewegung)
    long targetX = x * 40;  // Reduziert von 80 auf 40
    long targetY = y * 40;

    // Unterschiedliche Geschwindigkeiten für G0 vs G1
    if (isRapidMove) {
      // G0 = Schnelle Bewegung (nicht zeichnen)
      stepperX.setMaxSpeed(1500);  // Schneller
      stepperY.setMaxSpeed(1500);
    } else {
      // G1 = Langsame Bewegung (zeichnen)
      stepperX.setMaxSpeed(800);   // Langsamer für Präzision
      stepperY.setMaxSpeed(800);
    }

    stepperX.moveTo(targetX);
    stepperY.moveTo(targetY);

    // Warten bis Bewegung abgeschlossen ist
    while (stepperX.distanceToGo() != 0 || stepperY.distanceToGo() != 0) {
      stepperX.run();
      stepperY.run();
    }

    // Position aktualisieren
    posX = x;
    posY = y;
  }
}

// --- Änderung: Pen (Z) wird bei G0/G1 automatisch gehoben/gesenkt ---
// Siehe processGCode und movePen für Details

void movePen(float targetZ) {
  Serial.println("Bewege Stift zu Z:" + String(targetZ));

  // Z < 0 = Stift unten (zeichnen)
  // Z >= 0 = Stift oben (bewegen)

  long targetSteps;
  if (targetZ < 0) {
    // Stift runter (z.B. 0 mm = unten)
    targetSteps = 0; // Passe ggf. an, falls "unten" eine andere Position ist
    Serial.println("Stift RUNTER - zeichnet (Stepper)");
  } else {
    // Stift hoch (z.B. 10 mm = oben)
    targetSteps = 10 * 40; // 10 mm nach oben, 40 Steps/mm (anpassen falls nötig)
    Serial.println("Stift HOCH - bewegt (Stepper)");
  }

  stepperZ.setMaxSpeed(400); // Geschwindigkeit für Z
  stepperZ.setAcceleration(200);
  stepperZ.moveTo(targetSteps);
  while (stepperZ.distanceToGo() != 0) {
    stepperZ.run();
  }

  currentZ = targetZ;
  delay(200); // Kurze Pause für Mechanik
}

// --- Erweiterung: Nach G-Code-Ende Stift hoch und Home-Fahrt ---
void finishDrawing() {
  // Stift hoch
  movePen(1); // Z1 = Stift hoch
  delay(300); // Warten bis Stift oben ist
  // Home-Position anfahren (X=0, Y=0)
  Serial.println("Fahre zur Home-Position (0,0)");
  long targetX = 0;
  long targetY = 0;
  stepperX.setMaxSpeed(1500);
  stepperY.setMaxSpeed(1500);
  stepperX.moveTo(targetX);
  stepperY.moveTo(targetY);
  while (stepperX.distanceToGo() != 0 || stepperY.distanceToGo() != 0) {
    stepperX.run();
    stepperY.run();
  }
  posX = 0;
  posY = 0;
  Serial.println("Home-Position erreicht, Stift oben.");
}

// Hilfsfunktion um das Ende eines Parameters zu finden
int findNextSpace(String str, int startIndex) {
  for (int i = startIndex + 1; i < str.length(); i++) {
    char c = str.charAt(i);
    if (c == ' ' || isAlpha(c)) {
      return i;
    }
  }
  return str.length();
}

// Am Ende des G-Codes (z.B. nach letzter Bewegung) finishDrawing() aufrufen!
