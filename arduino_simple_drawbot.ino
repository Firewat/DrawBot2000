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

SoftwareSerial btSerial(10, 11); // RX, TX

// Stepper Setup (Driver Mode: 1 = DRIVER)
AccelStepper stepperX(AccelStepper::DRIVER, 2, 5); // STEP, DIR
AccelStepper stepperY(AccelStepper::DRIVER, 3, 6); // STEP, DIR

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

  // Z-Parameter extrahieren (für Stift hoch/runter)
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

void movePen(float targetZ) {
  Serial.println("Bewege Stift zu Z:" + String(targetZ));

  // Z < 0 = Stift unten (zeichnen)
  // Z >= 0 = Stift oben (bewegen)

  if (targetZ < 0) {
    penServo.write(45); // Stift runter (anpassen je nach Servo)
    Serial.println("Stift RUNTER - zeichnet");
  } else {
    penServo.write(90); // Stift hoch (anpassen je nach Servo)
    Serial.println("Stift HOCH - bewegt");
  }

  currentZ = targetZ;
  delay(300); // Mehr Zeit für Servo-Bewegung
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
