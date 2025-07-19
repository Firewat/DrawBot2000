// G-Code 3-Axis Stepper Motor Controller for Arduino UNO
// Compatible with 28BYJ-48 Stepper Motors and ULN2003 Driver Boards
// KEIN "ok"-Feedback mehr, arbeitet mit Feedrate/Delay

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

AccelStepper stepperX(HALFSTEP, X_motorPin1, X_motorPin3, X_motorPin2, X_motorPin4);
AccelStepper stepperY(HALFSTEP, Y_motorPin1, Y_motorPin3, Y_motorPin2, Y_motorPin4);
AccelStepper stepperZ(HALFSTEP, Z_motorPin1, Z_motorPin3, Z_motorPin2, Z_motorPin4);

String inputString = "";
boolean stringComplete = false;

struct Position {
  float x = 0.0;
  float y = 0.0;
  float z = 0.0;
};
Position currentPos;

const long STEPS_PER_REVOLUTION = 2048;
const float STEPS_PER_MM = 85.33;
const float DEFAULT_SPEED = 500.0;
const float MAX_SPEED = 1000.0;

float currentFeedrate = 500.0;
boolean absoluteMode = true;
boolean motorsEnabled = false;

void setup() {
  Serial.begin(9600);
  btSerial.begin(9600);
  Serial.println("G-Code Controller wartet auf Verbindung...");
  btSerial.println("G-Code Stepper bereit");
  stepperX.disableOutputs();
  stepperX.setMaxSpeed(MAX_SPEED);
  stepperY.disableOutputs();
  stepperY.setMaxSpeed(MAX_SPEED);
  stepperZ.disableOutputs();
  stepperZ.setMaxSpeed(MAX_SPEED);
  Serial.println("G-Code 3-Axis Stepper Controller Ready");
  btSerial.println("Send G-code commands:");
  btSerial.println("G28 - Home all axes");
  btSerial.println("G0/G1 X# Y# Z# - Move to position");
  btSerial.println("M17 - Enable motors");
  btSerial.println("M18 - Disable motors");
}

void loop() {
  readData();
  if (stringComplete) {
    processGCode();
    inputString = "";
    stringComplete = false;
  }
  if (motorsEnabled) {
    stepperX.run();
    stepperY.run();
    stepperZ.run();
  }
}

void readData() {
  while (btSerial.available()) {
    char c = btSerial.read();
    if (c == '\n' || c == '\r') {
      if (inputString.length() > 0) {
        stringComplete = true;
      }
      return;
    } else {
      inputString += c;
    }
  }
  while (Serial.available()) {
    char c = Serial.read();
    if (c == '\n' || c == '\r') {
      if (inputString.length() > 0) {
        stringComplete = true;
      }
      return;
    } else {
      inputString += c;
    }
  }
}

void processGCode() {
  inputString.trim();
  inputString.toUpperCase();
  if (inputString.length() == 0) return;
  Serial.print("Received: ");
  Serial.println(inputString);
  btSerial.print("Received: ");
  btSerial.println(inputString);
  if (inputString.startsWith("G0") || inputString.startsWith("G1")) {
    handleLinearMove();
  }
  else if (inputString.startsWith("G28")) {
    handleHome();
  }
  else if (inputString.startsWith("G90")) {
    absoluteMode = true;
    Serial.println("Absolute positioning mode");
    btSerial.println("Absolute positioning mode");
  }
  else if (inputString.startsWith("G91")) {
    absoluteMode = false;
    Serial.println("Relative positioning mode");
    btSerial.println("Relative positioning mode");
  }
  else if (inputString.startsWith("M17")) {
    enableMotors();
  }
  else if (inputString.startsWith("M18") || inputString.startsWith("M84")) {
    disableMotors();
  }
  else if (inputString.startsWith("M114")) {
    reportPosition();
  }
  else if (inputString.startsWith("TEST")) {
    testMotors();
  }
  else {
    Serial.println("Unknown command: " + inputString);
    btSerial.println("Unknown command: " + inputString);
  }
  // KEIN ok/ook Feedback mehr!
}

void handleLinearMove() {
  if (!motorsEnabled) {
    Serial.println("Motors disabled - enable with M17");
    btSerial.println("Motors disabled - enable with M17");
    return;
  }
  float x = currentPos.x;
  float y = currentPos.y;
  float z = currentPos.z;
  int xIndex = inputString.indexOf('X');
  if (xIndex != -1) {
    x = parseFloat(inputString, xIndex + 1);
    if (!absoluteMode) x += currentPos.x;
  }
  int yIndex = inputString.indexOf('Y');
  if (yIndex != -1) {
    y = parseFloat(inputString, yIndex + 1);
    if (!absoluteMode) y += currentPos.y;
  }
  int zIndex = inputString.indexOf('Z');
  if (zIndex != -1) {
    z = parseFloat(inputString, zIndex + 1);
    if (!absoluteMode) z += currentPos.z;
  }
  moveTo(x, y, z);
}

void handleHome() {
  Serial.println("Homing all axes");
  btSerial.println("Homing all axes");
  enableMotors();
  moveTo(0, 0, 0);
  Serial.println("Homing complete");
  btSerial.println("Homing complete");
}

// Hardlimit: 10x10cm (100x100mm) - clamp all X/Y positions
float clamp(float val, float minVal, float maxVal) {
  if (val < minVal) return minVal;
  if (val > maxVal) return maxVal;
  return val;
}

void moveTo(float x, float y, float z) {
  // Begrenze X und Y auf 0..100mm
  x = clamp(x, 0.0, 100.0);
  y = clamp(y, 0.0, 100.0);
  long stepsX = x * STEPS_PER_MM;
  long stepsY = y * STEPS_PER_MM;
  long stepsZ = z * STEPS_PER_MM;
  stepperX.setAcceleration(2000.0);
  stepperX.setSpeed(1000.0);
  stepperX.setCurrentPosition(stepperX.currentPosition());
  stepperX.moveTo(stepsX);
  stepperY.setAcceleration(2000.0);
  stepperY.setSpeed(1000.0);
  stepperY.setCurrentPosition(stepperY.currentPosition());
  stepperY.moveTo(stepsY);
  stepperZ.setAcceleration(2000.0);
  stepperZ.setSpeed(1000.0);
  stepperZ.setCurrentPosition(stepperZ.currentPosition());
  stepperZ.moveTo(stepsZ);
  currentPos.x = x;
  currentPos.y = y;
  currentPos.z = z;
  Serial.print("Moving to X:");
  Serial.print(x);
  Serial.print(" Y:");
  Serial.print(y);
  Serial.print(" Z:");
  Serial.println(z);
  btSerial.print("Moving to X:");
  btSerial.print(x);
  btSerial.print(" Y:");
  btSerial.print(y);
  btSerial.print(" Z:");
  btSerial.println(z);
}

void enableMotors() {
  stepperX.enableOutputs();
  stepperY.enableOutputs();
  stepperZ.enableOutputs();
  motorsEnabled = true;
  Serial.println("Motors enabled");
  btSerial.println("Motors enabled");
}

void disableMotors() {
  stepperX.stop();
  stepperX.setCurrentPosition(0);
  stepperX.run();
  stepperX.disableOutputs();
  stepperY.stop();
  stepperY.setCurrentPosition(0);
  stepperY.run();
  stepperY.disableOutputs();
  stepperZ.stop();
  stepperZ.setCurrentPosition(0);
  stepperZ.run();
  stepperZ.disableOutputs();
  motorsEnabled = false;
  Serial.println("Motors disabled");
  btSerial.println("Motors disabled");
}

void testMotors() {
  Serial.println("Testing motors - 1 revolution each");
  btSerial.println("Testing motors - 1 revolution each");
  enableMotors();
  Serial.println("Testing X-Axis");
  btSerial.println("Testing X-Axis");
  stepperX.setAcceleration(100.0);
  stepperX.setSpeed(50.0);
  stepperX.setCurrentPosition(0);
  stepperX.moveTo(STEPS_PER_REVOLUTION);
  while (stepperX.distanceToGo() != 0) {
    stepperX.run();
  }
  Serial.println("Testing Y-Axis");
  btSerial.println("Testing Y-Axis");
  stepperY.setAcceleration(100.0);
  stepperY.setSpeed(50.0);
  stepperY.setCurrentPosition(0);
  stepperY.moveTo(STEPS_PER_REVOLUTION);
  while (stepperY.distanceToGo() != 0) {
    stepperY.run();
  }
  Serial.println("Testing Z-Axis");
  btSerial.println("Testing Z-Axis");
  stepperZ.setAcceleration(100.0);
  stepperZ.setSpeed(50.0);
  stepperZ.setCurrentPosition(0);
  stepperZ.moveTo(STEPS_PER_REVOLUTION);
  while (stepperZ.distanceToGo() != 0) {
    stepperZ.run();
  }
  Serial.println("Motor test complete");
  btSerial.println("Motor test complete");
}

void reportPosition() {
  Serial.print("Current position - X:");
  Serial.print(currentPos.x);
  Serial.print(" Y:");
  Serial.print(currentPos.y);
  Serial.print(" Z:");
  Serial.println(currentPos.z);
  btSerial.print("Current position - X:");
  btSerial.print(currentPos.x);
  btSerial.print(" Y:");
  btSerial.print(currentPos.y);
  btSerial.print(" Z:");
  btSerial.println(currentPos.z);
}

float parseFloat(String str, int startIndex) {
  int endIndex = startIndex;
  while (endIndex < str.length() &&
         (isDigit(str[endIndex]) || str[endIndex] == '.' || str[endIndex] == '-')) {
    endIndex++;
  }
  return str.substring(startIndex, endIndex).toFloat();
}

