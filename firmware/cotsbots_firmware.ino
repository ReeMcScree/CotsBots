//Robot Variable Control
//V1.0 Tristan Talbott 5/14/2026
//setup: open Arduino IDE and upload to robot via micro USB cable plugged into microcontroller
//open serial monitor and set pull downs to newline and 115200 baud
//with cord plugged in type commands into serial monitor and hit enter to send

//COMMANDS
//Z = Instructions
//Structure: left,right,duration
//range from -255 to 255, negative means backward
//duration is in milliseconds | 0 = continuous
//multiple commands may be chained on one line, whitespace tolerant

#include <ctype.h>

// -------------------------------------------------------------
// Command structure: holds a single parsed instruction
// -------------------------------------------------------------
struct Command {
  int leftSpeed;              // Motor 1 speed (-255 to 255) | negative = backward
  int rightSpeed;            // Motor 2 speed (-255 to 255) | negative = backward
  unsigned long durationMs;  // how long to run in ms | 0 = continuous
};

const byte numChars = 64;
char inputBuffer[numChars];
bool newData = false;

// Motor control pins
const int E1 = 5;
const int E2 = 6;
const int M1 = 4;
const int M2 = 7;

void setMotors(int leftSpeed, int rightSpeed) {
  digitalWrite(M1, leftSpeed >= 0 ? HIGH : LOW);
  analogWrite(E1, abs(leftSpeed));
  digitalWrite(M2, rightSpeed >= 0 ? HIGH : LOW);
  analogWrite(E2, abs(rightSpeed));
}

static inline void skipWs(const char *s, size_t &pos) {
  while (s[pos] && isspace(static_cast<unsigned char>(s[pos]))) pos++;
}

bool nextCommand(const char *s, size_t &pos, Command &out) {
  skipWs(s, pos);
  if (!s[pos]) return false;

  if (tolower(s[pos]) == 'z') {
    pos++;
    Serial.println(F("Instructions:"));
    Serial.println(F("  left,right,duration"));
    Serial.println(F("  left/right: -255 to 255 (negative = backward)"));
    Serial.println(F("  duration: milliseconds | 0 = continuous"));
    return false;
  }

  bool leftNeg = false;
  if (s[pos] == '-') { leftNeg = true; pos++; }
  if (!isdigit((unsigned char)s[pos])) { Serial.println(F("Error: expected left speed")); return false; }
  int left = 0;
  while (isdigit((unsigned char)s[pos])) { left = left * 10 + (s[pos] - '0'); pos++; }
  if (left > 255) { Serial.println(F("Error: left speed out of range (-255 to 255)")); return false; }
  if (leftNeg) left = -left;

  skipWs(s, pos);
  if (s[pos] != ',') { Serial.println(F("Error: expected ',' after left speed")); return false; }
  pos++;

  skipWs(s, pos);
  bool rightNeg = false;
  if (s[pos] == '-') { rightNeg = true; pos++; }
  if (!isdigit((unsigned char)s[pos])) { Serial.println(F("Error: expected right speed")); return false; }
  int right = 0;
  while (isdigit((unsigned char)s[pos])) { right = right * 10 + (s[pos] - '0'); pos++; }
  if (right > 255) { Serial.println(F("Error: right speed out of range (-255 to 255)")); return false; }
  if (rightNeg) right = -right;

  skipWs(s, pos);
  if (s[pos] != ',') { Serial.println(F("Error: expected ',' after right speed")); return false; }
  pos++;

  skipWs(s, pos);
  if (!isdigit((unsigned char)s[pos])) { Serial.println(F("Error: expected duration")); return false; }
  unsigned long duration = 0;
  while (isdigit((unsigned char)s[pos])) { duration = duration * 10 + (s[pos] - '0'); pos++; }

  skipWs(s, pos);
  out.leftSpeed  = left;
  out.rightSpeed = right;
  out.durationMs = duration;
  return true;
}

void executeInstruction(int leftSpeed, int rightSpeed, unsigned long duration) {
  setMotors(leftSpeed, rightSpeed);
  if (duration > 0) {
    if (duration > 30000UL) duration = 30000UL;
    delay(duration);
    setMotors(0, 0);
  }
}

void recvCommandLine() {
  static byte index = 0;
  while (Serial.available() > 0) {
    char rc = Serial.read();
    if (rc == '\n' || rc == '\r') {
      while (Serial.peek() == '\n' || Serial.peek() == '\r') { Serial.read(); }
      inputBuffer[index] = '\0';
      index = 0;
      newData = true;
      return;
    } else if (index < numChars - 1) {
      inputBuffer[index++] = rc;
    }
  }
}

void parseAndExecute(char *input) {
  size_t pos = 0;
  Command c;
  while (nextCommand(input, pos, c)) {
    executeInstruction(c.leftSpeed, c.rightSpeed, c.durationMs);
  }
}

void setup() {
  for (int i = 4; i <= 7; i++) pinMode(i, OUTPUT);
  Serial.begin(115200);
  Serial.println(F("CONNECTED"));
  Serial.println(F("Z = Instructions"));
}

void loop() {
  recvCommandLine();
  if (newData) {
    Serial.print(F("Received: "));
    Serial.println(inputBuffer);
    parseAndExecute(inputBuffer);
    newData = false;
  }
}
