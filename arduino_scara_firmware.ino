/*
  SCARA DrawBot Firmware für Arduino Uno R3
  Basiert auf bewährter Firmware, erweitert um SCARA-Kinematik

  Hardware:
  - Arduino Uno R3
  - 2x Stepper-Motoren (SCARA-Gelenke)
  - 1x Servo (Z-Achse)
  - HC-05/HC-06 Bluetooth-Modul

  Verbindungen:
  - Motor 1 (Schulter): Step=2, Dir=5
  - Motor 2 (Ellbogen): Step=3, Dir=6
  - Servo: Pin 9
  - Bluetooth: RX=10, TX=11
*/

#include <AccelStepper.h>
#include <Servo.h>
#include <SoftwareSerial.h>

// Pin-Definitionen (deine bewährte Konfiguration)
#define STEPPER_X_STEP 2
#define STEPPER_X_DIR 5
#define STEPPER_Y_STEP 3
#define STEPPER_Y_DIR 6
#define SERVO_PIN 9

// SCARA-Arm Parameter (ANPASSEN für deinen Arm!)
#define ARM1_LENGTH 100.0  // Länge des ersten Arms (mm)
#define ARM2_LENGTH 100.0  // Länge des zweiten Arms (mm)

// Kalibrierung (aus deiner funktionierenden Firmware)
const float steps_per_mm = 32.47; // Für lineare Bewegungen
const float steps_per_degree = steps_per_mm * 3.14159 / 180.0; // Für Rotation (approximation)

// Servo-Positionen (aus deiner funktionierenden Firmware)
const int servoUp = 90;    // Winkel für Stift hoch
const int servoDown = 20;  // Winkel für Stift runter

// Motor-Objekte (deine bewährte Konfiguration)
AccelStepper stepperX(AccelStepper::DRIVER, STEPPER_X_STEP, STEPPER_X_DIR);
AccelStepper stepperY(AccelStepper::DRIVER, STEPPER_Y_STEP, STEPPER_Y_DIR);
Servo zServo;
SoftwareSerial btSerial(10, 11); // RX, TX

// Variablen
String input;
float posX = 0, posY = 0, posZ = 1; // Z>0 = oben
float currentAngle1 = 0, currentAngle2 = 0; // Aktuelle Winkel der Gelenke

void setup() {
  Serial.begin(9600);
  btSerial.begin(9600);

  // Stepper-Konfiguration (deine bewährten Einstellungen)
  stepperX.setMaxSpeed(1000);
  stepperX.setAcceleration(500);
  stepperY.setMaxSpeed(1000);
  stepperY.setAcceleration(500);

  // Servo-Konfiguration (deine bewährte Einstellung)
  zServo.attach(SERVO_PIN);
  zServo.write(servoUp); // Stift hoch

  // GRBL-kompatible Startup-Nachricht
  btSerial.println();
  btSerial.println("Grbl 1.1h SCARA ['$' for help]");
  btSerial.println("[MSG:'$H'|'$X' to unlock]");

  Serial.println("SCARA DrawBot gestartet");
  Serial.println("Arm1: " + String(ARM1_LENGTH) + "mm, Arm2: " + String(ARM2_LENGTH) + "mm");
}

void loop() {
  // Bluetooth-Eingabe verarbeiten
  if (btSerial.available()) {
    input = btSerial.readStringUntil('\n');
    input.trim();
    processGCode(input);
  }

  // Stepper laufen lassen
  stepperX.run();
  stepperY.run();
}

void processGCode(String code) {
  code.trim();
  code.toUpperCase();

  Serial.println("Empfangen: " + code);

  // GRBL System-Befehle
  if (code == "$X") {
    btSerial.println("ok");
    Serial.println("Unlock");
    return;
  }

  if (code == "?") {
    String status = "<Idle|MPos:" + String(posX, 3) + "," + String(posY, 3) + "," + String(posZ, 3);
    status += "|Angles:" + String(currentAngle1, 2) + "," + String(currentAngle2, 2) + ">";
    btSerial.println(status);
    return;
  }

  // G-Code-Bewegungsbefehle
  if (code.startsWith("G0") || code.startsWith("G1") || code.startsWith("G00") || code.startsWith("G01")) {
    processMovement(code);
    btSerial.println("ok");
    return;
  }

  // G92 - Position setzen
  if (code.startsWith("G92")) {
    processG92(code);
    btSerial.println("ok");
    return;
  }

  // Für alle anderen Befehle: ok senden
  btSerial.println("ok");
}

void processMovement(String code) {
  float targetX = posX, targetY = posY, targetZ = posZ;

  // Parameter extrahieren (deine bewährte Methode)
  int xIndex = code.indexOf('X');
  if (xIndex != -1) targetX = code.substring(xIndex + 1).toFloat();

  int yIndex = code.indexOf('Y');
  if (yIndex != -1) targetY = code.substring(yIndex + 1).toFloat();

  int zIndex = code.indexOf('Z');
  if (zIndex != -1) targetZ = code.substring(zIndex + 1).toFloat();

  // Z-Achse zuerst bewegen (deine bewährte Reihenfolge)
  if (targetZ != posZ) {
    moveZ(targetZ);
    posZ = targetZ;
  }

  // X/Y-Bewegung mit SCARA-Kinematik
  if (targetX != posX || targetY != posY) {
    moveSCARA(targetX, targetY);
    posX = targetX;
    posY = targetY;
  }
}

void processG92(String code) {
  // Position setzen (für Kalibrierung)
  int xIndex = code.indexOf('X');
  if (xIndex != -1) posX = code.substring(xIndex + 1).toFloat();

  int yIndex = code.indexOf('Y');
  if (yIndex != -1) posY = code.substring(yIndex + 1).toFloat();

  int zIndex = code.indexOf('Z');
  if (zIndex != -1) posZ = code.substring(zIndex + 1).toFloat();

  Serial.println("Position gesetzt: X=" + String(posX) + " Y=" + String(posY) + " Z=" + String(posZ));
}

void moveZ(float z) {
  // Deine bewährte Z-Achsen-Steuerung
  if (z > 0) {
    zServo.write(servoUp);
    delay(300); // Zeit für Servo
  } else {
    zServo.write(servoDown);
    delay(300);
  }
}

void moveSCARA(float targetX, float targetY) {
  // Prüfe Erreichbarkeit
  float distance = sqrt(targetX * targetX + targetY * targetY);
  float maxReach = ARM1_LENGTH + ARM2_LENGTH;
  float minReach = abs(ARM1_LENGTH - ARM2_LENGTH);

  if (distance > maxReach || distance < minReach) {
    Serial.println("FEHLER: Ziel außerhalb der Reichweite!");
    Serial.println("Distanz: " + String(distance) + "mm, Max: " + String(maxReach) + "mm, Min: " + String(minReach) + "mm");
    return;
  }

  // Inverse Kinematik berechnen
  float newAngle1, newAngle2;
  if (inverseKinematics(targetX, targetY, &newAngle1, &newAngle2)) {
    // Zu den neuen Winkeln bewegen
    moveToAngles(newAngle1, newAngle2);

    // Winkel aktualisieren
    currentAngle1 = newAngle1;
    currentAngle2 = newAngle2;

    Serial.println("Bewegt zu X:" + String(targetX, 2) + " Y:" + String(targetY, 2));
    Serial.println("Winkel: θ1=" + String(newAngle1, 2) + "° θ2=" + String(newAngle2, 2) + "°");
  } else {
    Serial.println("FEHLER: Inverse Kinematik fehlgeschlagen!");
  }
}

bool inverseKinematics(float x, float y, float* angle1, float* angle2) {
  float L1 = ARM1_LENGTH;
  float L2 = ARM2_LENGTH;

  // Distanz zum Ziel
  float r = sqrt(x * x + y * y);

  // Prüfe Erreichbarkeit
  if (r > (L1 + L2) || r < abs(L1 - L2)) {
    return false;
  }

  // Cosinus-Regel für Winkel 2 (Ellbogen)
  float cos_angle2 = (r * r - L1 * L1 - L2 * L2) / (2.0 * L1 * L2);

  // Sicherstellen dass cos_angle2 im gültigen Bereich ist
  if (cos_angle2 < -1.0 || cos_angle2 > 1.0) {
    return false;
  }

  // Winkel 2 (Ellbogen) - wähle negative Lösung für "Ellbogen unten"
  *angle2 = -acos(cos_angle2) * 180.0 / PI;

  // Winkel 1 (Schulter)
  float beta = atan2(y, x) * 180.0 / PI;
  float alpha = atan2(L2 * sin(*angle2 * PI / 180.0),
                      L1 + L2 * cos(*angle2 * PI / 180.0)) * 180.0 / PI;
  *angle1 = beta - alpha;

  return true;
}

void moveToAngles(float targetAngle1, float targetAngle2) {
  // Berechne Winkeldifferenz
  float deltaAngle1 = targetAngle1 - currentAngle1;
  float deltaAngle2 = targetAngle2 - currentAngle2;

  // Umrechnung in Steps (approximation)
  long steps1 = deltaAngle1 * steps_per_degree;
  long steps2 = deltaAngle2 * steps_per_degree;

  // Bewegung setzen
  stepperX.move(steps1);
  stepperY.move(steps2);

  // Warten bis Bewegung abgeschlossen
  while (stepperX.distanceToGo() != 0 || stepperY.distanceToGo() != 0) {
    stepperX.run();
    stepperY.run();
  }
}
