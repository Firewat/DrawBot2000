/*
  GRBL-kompatible DrawBot Firmware mit Bluetooth
  Für Arduino Uno mit HC-05/HC-06 Bluetooth Modul

  Verbindungen:
  - HC-05/HC-06 RX → Arduino Pin 2
  - HC-05/HC-06 TX → Arduino Pin 3
  - HC-05/HC-06 VCC → Arduino 5V
  - HC-05/HC-06 GND → Arduino GND

  Stepper-Motoren:
  - X-Axis: Step Pin 4, Dir Pin 5
  - Y-Axis: Step Pin 6, Dir Pin 7
  - Z-Axis (Servo): Pin 9
*/

#include <SoftwareSerial.h>
#include <Servo.h>

// Bluetooth Serial
SoftwareSerial bluetooth(2, 3); // RX, TX

// Servo für Z-Achse (Stift heben/senken)
Servo penServo;

// Stepper Motor Pins
#define X_STEP_PIN 4
#define X_DIR_PIN 5
#define Y_STEP_PIN 6
#define Y_DIR_PIN 7
#define PEN_SERVO_PIN 9

// Aktuelle Position
float currentX = 0.0;
float currentY = 0.0;
float currentZ = 5.0; // Stift oben

// Einstellungen
float stepsPerMM = 80.0; // Steps pro Millimeter (anpassen je nach Motor)
int stepDelay = 1000; // Microsekunden zwischen Steps
bool isAlarmState = true; // GRBL startet im Alarm-Zustand

// G-Code Parser Variablen
String inputBuffer = "";
bool absoluteMode = true; // G90 = absolute, G91 = relative
float feedRate = 1000.0; // mm/min

void setup() {
  // Serial für Debug (optional)
  Serial.begin(115200);

  // Bluetooth Serial
  bluetooth.begin(115200); // Standard HC-05/HC-06 Baudrate

  // Motor Pins konfigurieren
  pinMode(X_STEP_PIN, OUTPUT);
  pinMode(X_DIR_PIN, OUTPUT);
  pinMode(Y_STEP_PIN, OUTPUT);
  pinMode(Y_DIR_PIN, OUTPUT);

  // Servo konfigurieren
  penServo.attach(PEN_SERVO_PIN);
  penServo.write(90); // Stift oben (anpassen je nach Servo)

  // GRBL Startup-Nachricht senden
  bluetooth.println();
  bluetooth.println("Grbl 1.1h ['$' for help]");
  bluetooth.println("[MSG:'$H'|'$X' to unlock]");

  Serial.println("GRBL-kompatible DrawBot gestartet");
  Serial.println("Verbindung über Bluetooth möglich");
}

void loop() {
  // Befehle vom Bluetooth empfangen
  if (bluetooth.available()) {
    char c = bluetooth.read();

    if (c == '\n' || c == '\r') {
      if (inputBuffer.length() > 0) {
        processGCode(inputBuffer);
        inputBuffer = "";
      }
    } else {
      inputBuffer += c;
    }
  }

  // Debug: Auch Serial Monitor unterstützen
  if (Serial.available()) {
    char c = Serial.read();
    if (c == '\n' || c == '\r') {
      if (inputBuffer.length() > 0) {
        processGCode(inputBuffer);
        inputBuffer = "";
      }
    } else {
      inputBuffer += c;
    }
  }
}

void processGCode(String command) {
  command.trim();
  command.toUpperCase();

  Serial.println("Empfangen: " + command);

  // GRBL System-Befehle
  if (command == "$X") {
    // Unlock (Alarm ausschalten)
    isAlarmState = false;
    bluetooth.println("ok");
    Serial.println("Alarm deaktiviert");
    return;
  }

  if (command == "?") {
    // Status abfragen
    String status = "<";
    if (isAlarmState) {
      status += "Alarm";
    } else {
      status += "Idle";
    }
    status += "|MPos:" + String(currentX, 3) + "," + String(currentY, 3) + "," + String(currentZ, 3);
    status += "|WPos:" + String(currentX, 3) + "," + String(currentY, 3) + "," + String(currentZ, 3);
    status += ">";
    bluetooth.println(status);
    return;
  }

  if (command.startsWith("$$")) {
    // GRBL-Einstellungen anzeigen (vereinfacht)
    bluetooth.println("$0=10 (step pulse, usec)");
    bluetooth.println("$1=25 (step idle delay, msec)");
    bluetooth.println("$100=" + String(stepsPerMM, 3) + " (x, step/mm)");
    bluetooth.println("$101=" + String(stepsPerMM, 3) + " (y, step/mm)");
    bluetooth.println("ok");
    return;
  }

  // Prüfen ob im Alarm-Zustand
  if (isAlarmState && !command.startsWith("$")) {
    bluetooth.println("error:9"); // Alarm state error
    return;
  }

  // G-Code-Befehle verarbeiten
  if (command.startsWith("G")) {
    processMovementCommand(command);
  } else if (command.startsWith("M")) {
    processMCommand(command);
  } else {
    // Für alle anderen Befehle: ok senden
    bluetooth.println("ok");
  }
}

void processMovementCommand(String command) {
  // G-Code-Parameter extrahieren
  float targetX = currentX;
  float targetY = currentY;
  float targetZ = currentZ;
  bool hasX = false, hasY = false, hasZ = false;

  // G0/G1 - Bewegung
  if (command.startsWith("G0") || command.startsWith("G1") || command.startsWith("G00") || command.startsWith("G01")) {

    // X-Parameter
    int xIndex = command.indexOf('X');
    if (xIndex >= 0) {
      targetX = command.substring(xIndex + 1).toFloat();
      hasX = true;
    }

    // Y-Parameter
    int yIndex = command.indexOf('Y');
    if (yIndex >= 0) {
      String yString = command.substring(yIndex + 1);
      // Entferne nachfolgende Buchstaben
      for (int i = 0; i < yString.length(); i++) {
        if (isAlpha(yString.charAt(i))) {
          yString = yString.substring(0, i);
          break;
        }
      }
      targetY = yString.toFloat();
      hasY = true;
    }

    // Z-Parameter
    int zIndex = command.indexOf('Z');
    if (zIndex >= 0) {
      String zString = command.substring(zIndex + 1);
      for (int i = 0; i < zString.length(); i++) {
        if (isAlpha(zString.charAt(i))) {
          zString = zString.substring(0, i);
          break;
        }
      }
      targetZ = zString.toFloat();
      hasZ = true;
    }

    // Bewegung ausführen
    if (hasX || hasY) {
      moveToPosition(targetX, targetY);
    }

    if (hasZ) {
      movePen(targetZ);
    }

    bluetooth.println("ok");
    return;
  }

  // G21 - Millimeter-Einheiten
  if (command.startsWith("G21")) {
    bluetooth.println("ok");
    return;
  }

  // G90 - Absolute Koordinaten
  if (command.startsWith("G90")) {
    absoluteMode = true;
    bluetooth.println("ok");
    return;
  }

  // G91 - Relative Koordinaten
  if (command.startsWith("G91")) {
    absoluteMode = false;
    bluetooth.println("ok");
    return;
  }

  // G94 - Feed rate per minute
  if (command.startsWith("G94")) {
    bluetooth.println("ok");
    return;
  }

  // Unbekannter G-Code
  bluetooth.println("ok"); // Tolerant sein
}

void processMCommand(String command) {
  // M-Codes (Miscellaneous commands)
  bluetooth.println("ok");
}

void moveToPosition(float targetX, float targetY) {
  Serial.println("Bewege zu X:" + String(targetX) + " Y:" + String(targetY));

  // Berechne Schritte
  int stepsX = (targetX - currentX) * stepsPerMM;
  int stepsY = (targetY - currentY) * stepsPerMM;

  // Richtung setzen
  digitalWrite(X_DIR_PIN, stepsX >= 0 ? HIGH : LOW);
  digitalWrite(Y_DIR_PIN, stepsY >= 0 ? HIGH : LOW);

  // Absolute Werte
  stepsX = abs(stepsX);
  stepsY = abs(stepsY);

  // Simultane Bewegung (einfacher Bresenham-Algorithmus)
  int maxSteps = max(stepsX, stepsY);

  for (int i = 0; i < maxSteps; i++) {
    // X-Step
    if (i < stepsX) {
      digitalWrite(X_STEP_PIN, HIGH);
      delayMicroseconds(stepDelay);
      digitalWrite(X_STEP_PIN, LOW);
    }

    // Y-Step
    if (i < stepsY) {
      digitalWrite(Y_STEP_PIN, HIGH);
      delayMicroseconds(stepDelay);
      digitalWrite(Y_STEP_PIN, LOW);
    }

    delayMicroseconds(stepDelay);
  }

  // Position aktualisieren
  currentX = targetX;
  currentY = targetY;
}

void movePen(float targetZ) {
  Serial.println("Bewege Stift zu Z:" + String(targetZ));

  // Z < 0 = Stift unten (zeichnen)
  // Z >= 0 = Stift oben (bewegen)

  if (targetZ < 0) {
    penServo.write(45); // Stift runter (anpassen je nach Servo)
  } else {
    penServo.write(90); // Stift hoch (anpassen je nach Servo)
  }

  currentZ = targetZ;
  delay(500); // Zeit für Servo-Bewegung
}
