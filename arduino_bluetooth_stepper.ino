// Bluetooth-kompatibles Sketch für 28BYJ-48 Stepper mit Kommandos
// com0  STOP
// com1  step+
// com2  step-
// com3  run+
// com4  run-
// comZ  STOP after Bluetooth disconnection

#include <AccelStepper.h>
#include <SoftwareSerial.h>

#define HALFSTEP 8
#define motorPin1  5     // IN1 auf ULN2003
#define motorPin2  4     // IN2 auf ULN2003
#define motorPin3  3     // IN3 auf ULN2003
#define motorPin4  2     // IN4 auf ULN2003

// Bluetooth-Modul an Pin 10 (RX), 11 (TX)
SoftwareSerial btSerial(10, 11); // RX, TX

// Zwei Stepper für X und Y
AccelStepper stepperX(HALFSTEP, 5, 3, 4, 2); // X-Achse (IN1, IN3, IN2, IN4)
AccelStepper stepperY(HALFSTEP, A3, A1, A2, A0); // Y-Achse (IN1, IN3, IN2, IN4)

String myString = "";
boolean validCommand = false;
boolean justRunX = false;
boolean justRunY = false;
long mySteps = 2048;
float mySpeed = 500.0;
char command;
char axis;

void setup()
{
  Serial.begin(9600);
  btSerial.begin(9600);
  Serial.println("Bluetooth wartet auf Verbindung...");
  btSerial.println("DrawingBot bereit");
  stepperX.disableOutputs();
  stepperY.disableOutputs();
  stepperX.setMaxSpeed(1000.0);
  stepperY.setMaxSpeed(1000.0);
}

void loop()
{
  validCommand = false;
  // Zeichen von Bluetooth oder USB einlesen, bis Zeilenende
  while (btSerial.available()) {
    char c = btSerial.read();
    if (c == '\n' || c == '\r') {
      myString.trim();
      if (myString.length() > 0) {
        checkData();
        if (validCommand) {
          processCommand();
        }
      }
      myString = "";
    } else {
      myString += c;
    }
  }
  while (Serial.available()) {
    char c = Serial.read();
    if (c == '\n' || c == '\r') {
      myString.trim();
      if (myString.length() > 0) {
        checkData();
        if (validCommand) {
          processCommand();
        }
      }
      myString = "";
    } else {
      myString += c;
    }
  }
  if (justRunX) stepperX.runSpeed(); else stepperX.run();
  if (justRunY) stepperY.runSpeed(); else stepperY.run();
}

void checkData()
{
  myString.trim();
  // Erwartet: com1X, com2Y, com3B, ...
  if ((myString.length() == 5) && (myString.startsWith("com")))
  {
    validCommand = true;
    command = myString[3];
    axis = myString[4];
    Serial.print(" command is   "); Serial.print(command); Serial.print(" axis: "); Serial.println(axis);
    btSerial.print(" command is   "); btSerial.print(command); btSerial.print(" axis: "); btSerial.println(axis);
  }
  else if (myString.length() > 0)
  {
    validCommand = false;
    Serial.println("   command string is invalid: '" + myString + "'");
    btSerial.println("   command string is invalid: '" + myString + "'");
  }
  myString = "";
}

void processCommand()
{
  justRunX = false;
  justRunY = false;
  if (axis == 'X' || axis == 'B') stepperX.enableOutputs();
  if (axis == 'Y' || axis == 'B') stepperY.enableOutputs();
  switch (command)
  {
  case '0':
    Serial.println("STOP immediately");
    btSerial.println("STOP immediately");
    if (axis == 'X' || axis == 'B') stopNow(stepperX);
    if (axis == 'Y' || axis == 'B') stopNow(stepperY);
    break;
  case '1':
    Serial.println("step clockwise");
    btSerial.println("step clockwise");
    if (axis == 'X' || axis == 'B') moveSteps(stepperX, mySteps);
    if (axis == 'Y' || axis == 'B') moveSteps(stepperY, mySteps);
    break;
  case '2':
    Serial.println("step anti-clockwise");
    btSerial.println("step anti-clockwise");
    if (axis == 'X' || axis == 'B') moveSteps(stepperX, -mySteps);
    if (axis == 'Y' || axis == 'B') moveSteps(stepperY, -mySteps);
    break;
  case '3':
    Serial.println("run clockwise");
    btSerial.println("run clockwise");
    if (axis == 'X' || axis == 'B') runAtSpeed(stepperX, mySpeed, justRunX);
    if (axis == 'Y' || axis == 'B') runAtSpeed(stepperY, mySpeed, justRunY);
    break;
  case '4':
    Serial.println("run anti-clockwise");
    btSerial.println("run anti-clockwise");
    if (axis == 'X' || axis == 'B') runAtSpeed(stepperX, -mySpeed, justRunX);
    if (axis == 'Y' || axis == 'B') runAtSpeed(stepperY, -mySpeed, justRunY);
    break;
  case 'Z':
    Serial.println("Bluetooth Disconnected; STOP immediately");
    btSerial.println("Bluetooth Disconnected; STOP immediately");
    if (axis == 'X' || axis == 'B') stopNow(stepperX);
    if (axis == 'Y' || axis == 'B') stopNow(stepperY);
    break;
  default:
    Serial.print(command);
    Serial.println(" ....is not in valid command range");
    btSerial.print(command);
    btSerial.println(" ....is not in valid command range");
    break;
  }
}

void moveSteps(AccelStepper &stepper, long steps)
{
  stepper.setAcceleration(10.0);
  stepper.setSpeed(5.0);
  stepper.setCurrentPosition(0);
  stepper.moveTo(steps);
}

void runAtSpeed(AccelStepper &stepper, float cSpeed, boolean &justRunFlag)
{
  stepper.setSpeed(cSpeed);
  justRunFlag = true;
}

void stopNow(AccelStepper &stepper)
{
  stepper.stop();
  stepper.setCurrentPosition(0);
  stepper.run();
  stepper.disableOutputs();
}
