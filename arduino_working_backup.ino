#include <AccelStepper.h>
#include <SoftwareSerial.h>

#define STEPPER_X_STEP 2
#define STEPPER_X_DIR 5
#define STEPPER_Y_STEP 3
#define STEPPER_Y_DIR 6

// SCARA-Arm Parameter (ANPASSEN für deinen Arm!)
const float ARM1_LENGTH = 155.0; // Länge des ersten Arms (mm)
const float ARM2_LENGTH = 155.0; // Länge des zweiten Arms (mm)

// Kalibrierung für SCARA-Motoren
const float steps_per_degree = 8.888; // Steps pro Grad (200*16/360 = 8.888)
const int servoUp = 90;    // Winkel für Stift hoch
const int servoDown = 20;  // Winkel für Stift runter

// X-Achse: 28BYJ-48 Stepper Motor (HALF4WIRE, Pins 5, 4, 3, 2) // IN1=5, IN2=4, IN3=3, IN4=2
AccelStepper stepperX(AccelStepper::HALF4WIRE, 5, 4, 3, 2);
// Y-Achse: 28BYJ-48 Stepper Motor (HALF4WIRE, Pins A3, A2, A1, A0) // IN1=A3, IN2=A2, IN3=A1, IN4=A0
AccelStepper stepperY(AccelStepper::HALF4WIRE, A3, A2, A1, A0);
// Z-Achse: 28BYJ-48 Stepper Motor (HALF4WIRE, Pins 13, 12, 9, 8) // IN1=13, IN2=12, IN3=9, IN4=8
AccelStepper stepperZ(AccelStepper::HALF4WIRE, 13, 12, 9, 8);
// Servo entfällt, da Z jetzt Stepper ist

String input;
float posX = 0, posY = 0, posZ = 0; // Z=0 unten, Z=1 oben
float currentAngle1 = 0, currentAngle2 = 0; // Aktuelle Winkel der Gelenke

SoftwareSerial btSerial(10, 11); // RX, TX für Bluetooth-Modul

String gcodeBuffer = "";

void setup() {
  Serial.begin(115200);
  btSerial.begin(115200);

  stepperX.setMaxSpeed(200);
  stepperX.setAcceleration(50);
  stepperY.setMaxSpeed(200);
  stepperY.setAcceleration(50);
  stepperZ.setMaxSpeed(200);
  stepperZ.setAcceleration(50);

  Serial.println("SCARA DrawBot bereit");
  btSerial.println("Bluetooth bereit");
}

void loop() {
  while (btSerial.available()) {
    char c = btSerial.read();
    Serial.write(c); // Debug: zeige empfangene Zeichen im Serial Monitor
    if (c == '\n' || c == '\r') {
      gcodeBuffer.trim();
      if (gcodeBuffer.length() > 0) {
        Serial.println("GCode empfangen: " + gcodeBuffer);
        processGCode(gcodeBuffer);
        gcodeBuffer = "";
      }
    } else {
      gcodeBuffer += c;
    }
  }
  stepperX.run();
  stepperY.run();
  stepperZ.run();
}

void processGCode(String code) {
  code.trim();
  code.toUpperCase();

  Serial.println("GCode empfangen: " + code);

  // GRBL System-Befehle
  if (code == "$X") {
    btSerial.println("ok");
    Serial.println("SCARA Unlock");
    return;
  }

  if (code == "?") {
    String status = "<Idle|MPos:" + String(posX, 3) + "," + String(posY, 3) + "," + String(posZ, 3) + ">";
    btSerial.println(status);
    return;
  }

  // G-Code-Bewegungen
  float x = posX, y = posY, z = posZ;
  int xIndex = code.indexOf('X');
  if (xIndex != -1) x = parseFloat(code, xIndex + 1);
  int yIndex = code.indexOf('Y');
  if (yIndex != -1) y = parseFloat(code, yIndex + 1);
  int zIndex = code.indexOf('Z');
  if (zIndex != -1) z = parseFloat(code, zIndex + 1);

  if (code.startsWith("G0") || code.startsWith("G1") || code.startsWith("G00") || code.startsWith("G01")) {
    // Z zuerst bewegen
    if (z != posZ) {
      moveZ(z);
      posZ = z;
    }
    // X/Y bewegen
    moveXY(x, y);
    posX = x;
    posY = y;
    btSerial.println("ok");
  } else if (code.startsWith("G92")) {
    // Position setzen
    if (xIndex != -1) posX = x;
    if (yIndex != -1) posY = y;
    if (zIndex != -1) posZ = z;
    btSerial.println("ok");
  } else {
    btSerial.println("ok");
  }
}

void moveXY(float x, float y) {
  // Umrechnung: 1mm = 64 Steps (Beispielwert, ggf. anpassen)
  long stepsX = x * 64;
  long stepsY = y * 64;
  stepperX.moveTo(stepsX);
  stepperY.moveTo(stepsY);
  while (stepperX.distanceToGo() != 0 || stepperY.distanceToGo() != 0) {
    stepperX.run();
    stepperY.run();
  }
}

void moveZ(float z) {
  // Z=0 unten, Z=1 oben (Beispielwerte, ggf. anpassen)
  if (z > 0) {
    stepperZ.moveTo(100);
  } else {
    stepperZ.moveTo(0);
  }
  while (stepperZ.distanceToGo() != 0) {
    stepperZ.run();
  }
}

float parseFloat(String command, int startIndex) {
  String valueStr = "";
  for (int i = startIndex; i < command.length(); i++) {
    char c = command.charAt(i);
    if (c == '.' || c == '-' || c == '+' || isDigit(c)) {
      valueStr += c;
    } else {
      break;
    }
  }
  return valueStr.toFloat();
}

void readData()
{
  while ( Serial.available() )         // Read while there are available characters
  {
    delay(3);
    char c = Serial.read();
    if (c != '\n' && c != '\r') {
      myString += c;                     // build the command string ohne Zeilenende
    }
  }
  Serial.print(myString);              // Output the full command string - for debugging
}

void checkData()
{
  myString.trim(); // Zeilenenden und Leerzeichen entfernen
  if (( myString.length() == 4) && (myString.startsWith("com") ))
  {
    validCommand = true;
    command = myString[3];             // Get the command character
    Serial.print(" command is   ");
    Serial.println(command);
  }
  else  // input data string must contain at least 1 character, or we get stuck in a loop
  if ( myString.length() > 0)
  {
    validCommand = false;
    Serial.println("   command string is invalid");
  }
  myString = "";                        // Clear the input data string for next time
}
