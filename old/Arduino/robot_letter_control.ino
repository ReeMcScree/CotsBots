#include <Servo.h>
#include <EEPROM.h>

unsigned long lastDistanceCheck = 0;
unsigned long distanceInterval = 100; // ms

Servo myservo;
int sweepFlag = 1;
unsigned long lastSweep = 0;
unsigned long sweepInterval = 30; // ms

int URPWM = 3; // PWM Output 0－25000US，Every 50US represent 1cmk
int URTRIG = 10; // PWM trigger pin
uint8_t EnPwmCmd[4] = {0x44, 0x02, 0xbb, 0x01}; // distance measure command

unsigned long actualDistance = 0;
int safeDistance = 5; // cm
int safeDistanceAddress = 3;


//Standard PWM DC control
int E1 = 5;  //M1 Speed Control
int E2 = 6;  //M2 Speed Control
int M1 = 4;  //M1 Direction Control
int M2 = 7;  //M2 Direction Control
int duration = 1000; //one second is 1000 ms

char currentCommand = 'x';          // current active command
unsigned long commandStartTime = 0; // when a duration-based command started
bool hasDuration = false;          // is this command using duration?
bool newCommandReceived = false;

int neutralPosition = 90;
int neutralAddress = 0; 
int leftPosition = 90;
int leftAddress = 1;
int rightPosition = 90;    
int rightAddress = 2; 


void stop(void)  //Stop
{
  digitalWrite(E1, LOW);
  digitalWrite(E2, LOW);
}
void advance(char a, char b)  //Move forward
{
  analogWrite(E1, a);  //PWM Speed Control
  digitalWrite(M1, HIGH);
  analogWrite(E2, b);
  digitalWrite(M2, HIGH);
}
void back_off(char a, char b)  //Move backward
{
  analogWrite(E1, a);
  digitalWrite(M1, LOW);
  analogWrite(E2, b);
  digitalWrite(M2, LOW);
}
void turn_L(char a, char b)  //Turn Left
{
  analogWrite(E1, a);
  digitalWrite(M1, LOW);
  analogWrite(E2, b);
  digitalWrite(M2, HIGH);
}
void turn_R(char a, char b)  //Turn Right
{
  analogWrite(E1, a);
  digitalWrite(M1, HIGH);
  analogWrite(E2, b);
  digitalWrite(M2, LOW);
}

void setup(void) {
  int i;
  for (i = 4; i <= 7; i++)
    pinMode(i, OUTPUT);
  digitalWrite(E1, LOW);
  digitalWrite(E2, LOW);

  leftPosition = EEPROM.read(leftAddress);
  rightPosition = EEPROM.read(rightAddress);
  neutralPosition = EEPROM.read(neutralAddress);

  safeDistance = EEPROM.read(safeDistanceAddress);

  Serial.begin(115200);  //Set Baud Rate
  Serial.println("CONNNECTED");
  Serial.println("z = Instructions");

  SensorSetup();
}
void readInput() {
  currentCommand = Serial.read();
  newCommandReceived = true;

  delay(10);  // Wait briefly for rest of input to arrive

  if (Serial.available()) {
    char c = Serial.peek();
    if (isDigit(c)) {
      duration = Serial.parseInt();
      duration = (duration > 0) ? duration : 0;
      hasDuration = true;
      commandStartTime = millis();
    } else {
      hasDuration = false;
      duration = 0;
    }
  }
}
bool isMoveCommand(char c){
  return c != 'x' &&
         c != 'X' &&
         c != 'z' &&
         c != 'Z' &&
         c != 'c' &&
         c != 'C' ;
}
void loop(void) {
  // 1. Check if movement is still safe
  if (!isSafeToMoveForward() && isMoveCommand(currentCommand)) {
    stop();
    currentCommand = 'x';
    Serial.print("Obstacle detected stopping movement! ");
    Serial.println(actualDistance);
    return;
  }
  // 2. Get input
  if (Serial.available()) {
    readInput();
  }
  // 3. Execute command
  if (newCommandReceived){
    switch (currentCommand) {
      case 'w': case 'W':  // Forward
        moveServo(neutralPosition);
        advance(255, 255);
        break;

      case 's': case 'S':  // Backward
        moveServo(neutralPosition);
        back_off(255, 255);
        break;

      case 'a': case 'A':  // Turn Left
        moveServo(leftPosition);
        turn_L(255, 255);
        break;

      case 'd': case 'D':  // Turn Right
        moveServo(rightPosition);
        turn_R(255, 255);
        break;

      case 'x': case 'X':  // Stop
        stop();
        break;
      case 'c': case 'C':
        stop();
        calibration();
        break;
      case 'z': case 'Z':  // Help Instructions
        Serial.println("w = forward (duration), s = backward (duration), a = left (duration), d = right (duration), x = stop, c = calibration Servo, z = instructions");
        break;
      
      default:
        stop();
        break;
    }
    newCommandReceived = false;
  }
  // 4. If command has a duration, stop it after time elapsed
  if (hasDuration && millis() - commandStartTime >= duration) {
    stop();
    currentCommand = 'x';
    hasDuration = false;
    Serial.println("Duration complete stopping.");
  }
}

void calibration() {
  Serial.println("Entering calibration mode. 'd' for safe distance, 's' servo position, 'p' print current calibrations, 'z' help, 'x' to exit:");
  while(true){
    if (Serial.available()) {
      char input = Serial.read();
      switch (input){
        case 'd': case 'D':
          safeDistanceCalibration();
          return;
        case 's': case 'S':
          servoCalibration();
          return;
        case 'X': case 'x':
          Serial.println("Exiting Calibration.");
          return;
        case 'p': case 'P':
          Serial.print("Left Servo position: ");
          Serial.println(leftPosition);
          Serial.print("Right Servo position: ");
          Serial.println(rightPosition);
          Serial.print("Neutral Servo position: ");
          Serial.println(neutralPosition);
          Serial.print("Safe distance: ");
          Serial.println(safeDistance);
          break;
        case 'z': case 'Z':
          Serial.println("'d' for safe distance, 's' servo position, 'p' print current calibrations, 'z' help, 'x' to exit:");
          break;
      }
    }
  }
}
void safeDistanceCalibration(){
  Serial.println("Safe distance calibration mode. Enter a distance");

  while (true) {
    delay(20);
    if (Serial.available()) {
      char c = Serial.peek();
      if (isDigit(c)) {
        safeDistance = Serial.parseInt();
        EEPROM.write(safeDistanceAddress, safeDistance);  // Save position to EEPROM
        Serial.print("Safe distance saved. distance: ");
        Serial.println(safeDistance);
        Serial.println("Exiting Calibration.");

      }else{
        Serial.read();
      }
    }
  }
}
void servoCalibration(){
  Serial.println("Servo calibration mode. Enter a position (0-180). Then 'n'eutral 'l'eft 'r'ight to save calibration value.  'X' to exit:");
  int position = 0;
  while(true){
    if (Serial.available()) {
      char input = Serial.read();  // Read the input
      delay(10);

      switch(input){
        case 'X': case 'x':
          Serial.println("Exiting Calibration.");
          return;
        case 'l': case 'L':
          leftPosition = position;
          EEPROM.write(leftAddress, leftPosition);  // Save position to EEPROM
          Serial.print("Left Calibration saved. Servo position: ");
          Serial.println(leftPosition);
          break;
        case 'r': case 'R':
          rightPosition = position;
          EEPROM.write(rightAddress, rightPosition);  // Save position to EEPROM
          Serial.print("Right Calibration saved. Servo position: ");
          Serial.println(rightPosition);
          break;
        case 'n': case 'N':
          neutralPosition = position;
          EEPROM.write(neutralAddress, neutralPosition);  // Save position to EEPROM
          Serial.print("Neutral Calibration saved. Servo position: ");
          Serial.println(neutralPosition);
          break;
        default:
          if (isDigit(input)) {
            String numStr = String(input);
            
            // Read additional digits to form the full position number
            while (Serial.available()) {
              input = Serial.read();
              if (isDigit(input)) {
                numStr += input;
              } else {
                break;
              }
            }

            position = numStr.toInt();  // Convert string to integer

            // If the position is within the valid range, move the servo
            if (position >= 0 && position <= 180) {
              myservo.write(position);  // Move the servo
              Serial.print("Servo moved to position: ");
              Serial.println(position);
            } else {
              Serial.println("Invalid position. Enter a number between 0 and 180.");
            }
          }
      }
    }
  }
}

void moveServo(int angle){
  myservo.write(angle); 
  delay(500); 
}
//Servo and Sensor code
void SensorSetup() {
  myservo.attach(9);
  pinMode(URTRIG, OUTPUT);                    // A low pull on pin COMP/TRIG
  digitalWrite(URTRIG, HIGH);                 // Set to HIGH
  pinMode(URPWM, INPUT);                      // Sending Enable PWM mode command
  for (int i = 0; i < 4; i++) {
    Serial.write(EnPwmCmd[i]);
  }
  moveServo(neutralPosition);
}

int MeasureDistance() { // a low pull on pin COMP/TRIG  triggering a sensor reading
  digitalWrite(URTRIG, LOW);
  digitalWrite(URTRIG, HIGH);               // reading Pin PWM will output pulses
  unsigned long distance = pulseIn(URPWM, LOW);
  if (distance == 1000) {          // the reading is invalid.
    Serial.print("Invalid");
  } else {
    distance = distance / 50;       // every 50us low level stands for 1cm
  }
  return distance;
}

bool isSafeToMoveForward() {
  unsigned long now = millis();
  if (now - lastDistanceCheck >= distanceInterval) {
    lastDistanceCheck = now;
    actualDistance = MeasureDistance();
  }
  return actualDistance > safeDistance;
}