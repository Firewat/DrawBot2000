/*
  SCARA DrawBot Firmware für Arduino Uno R3
  Optimiert für geringen Speicherverbrauch
*/

#include <AccelStepper.h>
#include <Servo.h>
#include <SoftwareSerial.h>

// Pin-Definitionen
#define STEPPER_X_STEP 2
#define STEPPER_X_DIR 5
#define STEPPER_Y_STEP 3
#define STEPPER_Y_DIR 6
#define SERVO_PIN 9

// SCARA-Arm Parameter
#define ARM1_LENGTH 200.0
#define ARM2_LENGTH 200.0
#define STEPS_PER_REVOLUTION_1 6400.0
#define STEPS_PER_REVOLUTION_2 6400.0

// Konstanten im Flash-Speicher
const float steps_per_degree_1 PROGMEM = STEPS_PER_REVOLUTION_1 / 360.0;
const float steps_per_degree_2 PROGMEM = STEPS_PER_REVOLUTION_2 / 360.0;

// Motor-Objekte
AccelStepper stepperX(AccelStepper::DRIVER, STEPPER_X_STEP, STEPPER_X_DIR);
AccelStepper stepperY(AccelStepper::DRIVER, STEPPER_Y_STEP, STEPPER_Y_DIR);
Servo zServo;
SoftwareSerial btSerial(10, 11);

// Kompakte Variablen
float currentAngle1 = 90.0;
float currentAngle2 = 0.0;
float posX = 0.0;
float posY = ARM1_LENGTH + ARM2_LENGTH;
float maxSpeed = 1000.0;
float acceleration = 500.0;
float feedrate = 800.0;
float motorJerk = 0.8;

// Flags als Bits
byte systemFlags = 0;
#define FLAG_HOMED 0
#define FLAG_UNLOCKED 1
#define FLAG_COORD_SYSTEM 2
#define FLAG_POS_Z 3

// Servo-Positionen
byte customServoUp = 90;
byte customServoDown = 20;

// Kleiner String-Buffer
char cmdBuffer[32];

void setup() {
  Serial.begin(115200);
  btSerial.begin(115200);

  stepperX.setMaxSpeed(maxSpeed);
  stepperX.setAcceleration(acceleration);
  stepperY.setMaxSpeed(maxSpeed);
  stepperY.setAcceleration(acceleration);

  zServo.attach(SERVO_PIN);
  zServo.write(90);

  // Flags setzen
  bitSet(systemFlags, FLAG_COORD_SYSTEM); // Rectangular = true
  bitSet(systemFlags, FLAG_POS_Z); // Z = hoch

  btSerial.println(F("Grbl 1.1h SCARA"));
  Serial.println(F("SCARA DrawBot gestartet"));
}

void loop() {
  if (btSerial.available()) {
    byte len = btSerial.readBytesUntil('\n', cmdBuffer, 31);
    cmdBuffer[len] = 0;
    processCommand();
  }

  if (Serial.available()) {
    byte len = Serial.readBytesUntil('\n', cmdBuffer, 31);
    cmdBuffer[len] = 0;
    processCommand();
  }

  stepperX.run();
  stepperY.run();
}

void processCommand() {
  // String zu Großbuchstaben
  for (byte i = 0; cmdBuffer[i]; i++) {
    if (cmdBuffer[i] >= 'a' && cmdBuffer[i] <= 'z') {
      cmdBuffer[i] -= 32;
    }
  }

  switch (cmdBuffer[0]) {
    case 'G':
      processGCommand();
      break;
    case 'M':
      processMCommand();
      break;
    case '$':
      processSystemCommand();
      break;
    case 0:
      btSerial.println(F("ok"));
      break;
    default:
      btSerial.println(F("error:1"));
  }
}

void processGCommand() {
  if (cmdBuffer[1] == '1' || cmdBuffer[1] == '0') {
    // G0/G1 - Bewegung
    float x = posX, y = posY, z = bitRead(systemFlags, FLAG_POS_Z);
    float f = feedrate;

    parseParameters(x, y, z, f);

    if (bitRead(systemFlags, FLAG_COORD_SYSTEM)) {
      moveTo(x, y, z > 0);
    } else {
      moveToAngles(x, y, z > 0);
    }
    btSerial.println(F("ok"));

  } else if (cmdBuffer[1] == '2' && cmdBuffer[2] == '8') {
    // G28 - Home
    homeAxes();
    btSerial.println(F("ok"));

  } else if (cmdBuffer[1] == '9' && cmdBuffer[2] == '4') {
    // G94 - Rectangular
    bitSet(systemFlags, FLAG_COORD_SYSTEM);
    btSerial.println(F("ok"));

  } else if (cmdBuffer[1] == '9' && cmdBuffer[2] == '5') {
    // G95 - Angle
    bitClear(systemFlags, FLAG_COORD_SYSTEM);
    btSerial.println(F("ok"));

  } else {
    btSerial.println(F("ok"));
  }
}

void processMCommand() {
  if (cmdBuffer[1] == '3') {
    if (cmdBuffer[2] == 'S') {
      // M3S - Servo-Winkel setzen
      int angle = parseNumber(3);
      if (angle >= 0) {
        zServo.write(angle);
        if (angle > customServoUp) bitSet(systemFlags, FLAG_POS_Z);
        else bitClear(systemFlags, FLAG_POS_Z);
      }
    } else {
      // M3 - Stift runter
      zServo.write(customServoDown);
      bitClear(systemFlags, FLAG_POS_Z);
    }
    btSerial.println(F("ok"));

  } else if (cmdBuffer[1] == '4') {
    if (cmdBuffer[2] == ' ' && cmdBuffer[3] == 'L') {
      // M4 L T - Servo-Winkel konfigurieren
      int lVal = parseNumber(4);
      if (lVal >= 0) customServoDown = lVal;

      // Suche nach T
      for (byte i = 4; cmdBuffer[i]; i++) {
        if (cmdBuffer[i] == 'T') {
          int tVal = parseNumber(i + 1);
          if (tVal >= 0) customServoUp = tVal;
          break;
        }
      }
    } else {
      // M4 - Stift runter
      zServo.write(customServoDown);
      bitClear(systemFlags, FLAG_POS_Z);
    }
    btSerial.println(F("ok"));

  } else if (cmdBuffer[1] == '5') {
    // M5 - Stift hoch
    zServo.write(customServoUp);
    bitSet(systemFlags, FLAG_POS_Z);
    btSerial.println(F("ok"));

  } else if (strncmp_P(cmdBuffer + 1, PSTR("369"), 3) == 0) {
    // M369 - Position setzen
    float x = posX, y = posY, dummy = 0;
    parseParameters(x, y, dummy, dummy);

    float angle1, angle2;
    if (inverseKinematics(x, y, angle1, angle2)) {
      currentAngle1 = angle1;
      currentAngle2 = angle2;
      posX = x;
      posY = y;

      long steps1 = (long)(angle1 * pgm_read_float(&steps_per_degree_1));
      long steps2 = (long)(angle2 * pgm_read_float(&steps_per_degree_2));
      stepperX.setCurrentPosition(steps1);
      stepperY.setCurrentPosition(steps2);
      btSerial.println(F("ok"));
    } else {
      btSerial.println(F("error:2"));
    }

  } else if (strncmp_P(cmdBuffer + 1, PSTR("370"), 3) == 0) {
    // M370 - Position reset
    posX = 0.0;
    posY = ARM1_LENGTH + ARM2_LENGTH;
    currentAngle1 = 90.0;
    currentAngle2 = 0.0;

    long steps1 = (long)(90.0 * pgm_read_float(&steps_per_degree_1));
    long steps2 = 0;
    stepperX.setCurrentPosition(steps1);
    stepperY.setCurrentPosition(steps2);
    btSerial.println(F("ok"));

  } else if (strncmp_P(cmdBuffer + 1, PSTR("503"), 3) == 0) {
    // M503 - Parameter anzeigen
    btSerial.print(F("Speed:")); btSerial.println(maxSpeed);
    btSerial.print(F("Accel:")); btSerial.println(acceleration);
    btSerial.print(F("Feed:")); btSerial.println(feedrate);
    btSerial.print(F("Pos X:")); btSerial.print(posX);
    btSerial.print(F(" Y:")); btSerial.println(posY);
    btSerial.println(F("ok"));

  } else {
    btSerial.println(F("ok"));
  }
}

void processSystemCommand() {
  if (cmdBuffer[1] == 'H') {
    homeAxes();
    btSerial.println(F("ok"));
  } else if (cmdBuffer[1] == 'X') {
    bitSet(systemFlags, FLAG_UNLOCKED);
    btSerial.println(F("ok"));
  } else {
    btSerial.println(F("ok"));
  }
}

void parseParameters(float &x, float &y, float &z, float &f) {
  for (byte i = 0; cmdBuffer[i]; i++) {
    switch (cmdBuffer[i]) {
      case 'X':
        x = parseFloat(i + 1);
        break;
      case 'Y':
        y = parseFloat(i + 1);
        break;
      case 'Z':
        z = parseFloat(i + 1);
        break;
      case 'F':
        f = parseFloat(i + 1);
        feedrate = f;
        break;
    }
  }
}

float parseFloat(byte startPos) {
  byte i = startPos;
  float result = 0;
  float decimal = 0;
  byte decimalPlaces = 0;
  bool negative = false;

  if (cmdBuffer[i] == '-') {
    negative = true;
    i++;
  }

  while (cmdBuffer[i] >= '0' && cmdBuffer[i] <= '9') {
    result = result * 10 + (cmdBuffer[i] - '0');
    i++;
  }

  if (cmdBuffer[i] == '.') {
    i++;
    while (cmdBuffer[i] >= '0' && cmdBuffer[i] <= '9') {
      decimal = decimal * 10 + (cmdBuffer[i] - '0');
      decimalPlaces++;
      i++;
    }

    for (byte j = 0; j < decimalPlaces; j++) {
      decimal /= 10.0;
    }
    result += decimal;
  }

  return negative ? -result : result;
}

int parseNumber(byte startPos) {
  int result = 0;
  byte i = startPos;

  while (cmdBuffer[i] >= '0' && cmdBuffer[i] <= '9') {
    result = result * 10 + (cmdBuffer[i] - '0');
    i++;
  }

  return result;
}

void homeAxes() {
  currentAngle1 = 90.0;
  currentAngle2 = 0.0;
  posX = 0.0;
  posY = ARM1_LENGTH + ARM2_LENGTH;
  bitSet(systemFlags, FLAG_POS_Z);

  long steps1 = (long)(90.0 * pgm_read_float(&steps_per_degree_1));
  long steps2 = 0;

  stepperX.moveTo(steps1);
  stepperY.moveTo(steps2);

  while (stepperX.distanceToGo() != 0 || stepperY.distanceToGo() != 0) {
    stepperX.run();
    stepperY.run();
  }

  zServo.write(customServoUp);
  bitSet(systemFlags, FLAG_HOMED);
}

void moveTo(float x, float y, bool zUp) {
  zServo.write(zUp ? customServoUp : customServoDown);
  if (zUp) bitSet(systemFlags, FLAG_POS_Z);
  else bitClear(systemFlags, FLAG_POS_Z);

  float angle1, angle2;
  if (inverseKinematics(x, y, angle1, angle2)) {
    long steps1 = (long)(angle1 * pgm_read_float(&steps_per_degree_1));
    long steps2 = (long)(angle2 * pgm_read_float(&steps_per_degree_2));

    float speed = feedrate * 16.67;
    stepperX.setMaxSpeed(speed);
    stepperY.setMaxSpeed(speed);

    stepperX.moveTo(steps1);
    stepperY.moveTo(steps2);

    posX = x;
    posY = y;
    currentAngle1 = angle1;
    currentAngle2 = angle2;
  } else {
    btSerial.println(F("error:2"));
  }
}

void moveToAngles(float angle1, float angle2, bool zUp) {
  zServo.write(zUp ? customServoUp : customServoDown);
  if (zUp) bitSet(systemFlags, FLAG_POS_Z);
  else bitClear(systemFlags, FLAG_POS_Z);

  if (angle1 < 0) angle1 += 360;
  if (angle2 < 0) angle2 += 360;
  if (angle1 > 360) angle1 = fmod(angle1, 360);
  if (angle2 > 360) angle2 = fmod(angle2, 360);

  long steps1 = (long)(angle1 * pgm_read_float(&steps_per_degree_1));
  long steps2 = (long)(angle2 * pgm_read_float(&steps_per_degree_2));

  float speed = feedrate * 16.67;
  stepperX.setMaxSpeed(speed);
  stepperY.setMaxSpeed(speed);

  stepperX.moveTo(steps1);
  stepperY.moveTo(steps2);

  currentAngle1 = angle1;
  currentAngle2 = angle2;

  posX = ARM1_LENGTH * cos(radians(angle1)) + ARM2_LENGTH * cos(radians(angle1 + angle2));
  posY = ARM1_LENGTH * sin(radians(angle1)) + ARM2_LENGTH * sin(radians(angle1 + angle2));
}

bool inverseKinematics(float x, float y, float &angle1, float &angle2) {
  float distance = sqrt(x * x + y * y);

  if (distance > (ARM1_LENGTH + ARM2_LENGTH) || distance < abs(ARM1_LENGTH - ARM2_LENGTH)) {
    return false;
  }

  float cos_angle2 = (x * x + y * y - ARM1_LENGTH * ARM1_LENGTH - ARM2_LENGTH * ARM2_LENGTH) / (2 * ARM1_LENGTH * ARM2_LENGTH);

  if (cos_angle2 < -1 || cos_angle2 > 1) {
    return false;
  }

  angle2 = acos(cos_angle2);
  angle2 = degrees(angle2);

  float k1 = ARM1_LENGTH + ARM2_LENGTH * cos(radians(angle2));
  float k2 = ARM2_LENGTH * sin(radians(angle2));

  angle1 = atan2(y, x) - atan2(k2, k1);
  angle1 = degrees(angle1);

  if (angle1 < 0) angle1 += 360;
  if (angle2 < 0) angle2 += 360;

  return true;
}
