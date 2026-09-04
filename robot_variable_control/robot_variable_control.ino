//Robot Variable Control
//V1.0 Tristan Talbott 5/14/2026
//setup: open Arduino IDE and upload to robot via micro USB cable plugged into microcontroller
//open serial monitor and set pull downs to newline and 115200 baud
//with cord plugged in type commands into serial monitor and hit enter to send

//OLD COMMANDS
//commands: w = forward, s = backward, a = left, d = right, x = stop, z = instructions
//add a number after w/a/s/d to move for that many milliseconds (e.g. w1000 = move forward for 1 second)

//OLD KNOWN ERRORS
// -- when testing on a android device connected via bluetooth the transmission would send extra whitespace
// -- to test we connected andriod device via bluetooth serial and sent commands using the bluno app given on DFrobots website
//Sending commands via the serial monitor works perfectly fine on its own
//put attempts at bridging the gap between a phones serial bluetooth input and the board itself were not successful
//we believe the extra whitespace is causing issues with parsing the commands correctly 

//NEW COMMANDS
//Z = Instructions
//Structure: left,right,duration
//range from -255 to 255, negative means backward
//duration is in milliseconds | 0 = continuous



#include <ctype.h>
// -------------------------------------------------------------
// Command structure: holds a single parsed instruction
// -------------------------------------------------------------
struct Command {
  int leftSpeed;              // Motor 1 speed (-255 to 255) | negative = backward
  int rightSpeed;             // Motor 2 speed (-255 to 255) | negative = backward
  unsigned long durationMs;   // how long to run in ms | 0 = continuous
};

const byte numChars = 64;   // maximum characters per serial input line
char inputBuffer[numChars]; // stores the text line typed into Serial Monitor
bool newData = false;       // becomes true when a full line of text is received

// Motor control pins
const int E1 = 5;
const int E2 = 6;
const int M1 = 4;
const int M2 = 7;

// -------------------------------------------------------------
// Basic motor control helper functions
// -------------------------------------------------------------
void setMotors(int leftSpeed, int rightSpeed) {
  digitalWrite(M1, leftSpeed >= 0 ? HIGH : LOW);
  analogWrite(E1, abs(leftSpeed));
  digitalWrite(M2, rightSpeed >= 0 ? HIGH : LOW);
  analogWrite(E2, abs(rightSpeed));
}
// -------------------------------------------------------------
// Parsing utilities
// -------------------------------------------------------------
// Skips over spaces and tabs
static inline void skipWs(const char *s, size_t &pos) {
  while (s[pos] && isspace(static_cast<unsigned char>(s[pos]))) pos++;
}
// Reads one command (leftPower,rightPower,duration) from the input string.
// Returns true if a command was found, false if no more commands.
bool nextCommand(const char *s, size_t &pos, Command &out) {
  skipWs(s, pos);
  if (!s[pos]) return false;

  // --- check for z (instructions) ---
  if (tolower(s[pos]) == 'z') {
    pos++;
    Serial.println(F("Instructions:"));
    Serial.println(F("  left,right,duration"));
    Serial.println(F("  left/right: -255 to 255 (negative = backward)"));
    Serial.println(F("  duration: milliseconds | 0 = continuous"));
    return false; // nothing more to execute
  }

  // --- left speed ---
  bool leftNeg = false;
  if (s[pos] == '-') { leftNeg = true; pos++; }
  if (!isdigit((unsigned char)s[pos])) { Serial.println(F("Error: expected left speed")); return false; }
  int left = 0;
  while (isdigit((unsigned char)s[pos])) {
    left = left * 10 + (s[pos] - '0');
    pos++;
  }
  if (left > 255) { Serial.println(F("Error: left speed out of range (-255 to 255)")); return false; }
  if (leftNeg) left = -left;

  // --- comma separator ---
  skipWs(s, pos);
  if (s[pos] != ',') { Serial.println(F("Error: expected ',' after left speed")); return false; }
  pos++;

  // --- right speed ---
  skipWs(s, pos);
  bool rightNeg = false;
  if (s[pos] == '-') { rightNeg = true; pos++; }
  if (!isdigit((unsigned char)s[pos])) { Serial.println(F("Error: expected right speed")); return false; }
  int right = 0;
  while (isdigit((unsigned char)s[pos])) {
    right = right * 10 + (s[pos] - '0');
    pos++;
  }
  if (right > 255) { Serial.println(F("Error: right speed out of range (-255 to 255)")); return false; }
  if (rightNeg) right = -right;

  // --- comma separator ---
  skipWs(s, pos);
  if (s[pos] != ',') { Serial.println(F("Error: expected ',' after right speed")); return false; }
  pos++;

  // --- duration ---
  skipWs(s, pos);
  if (!isdigit((unsigned char)s[pos])) { Serial.println(F("Error: expected duration")); return false; }
  unsigned long duration = 0;
  while (isdigit((unsigned char)s[pos])) {
    duration = duration * 10 + (s[pos] - '0');
    pos++;
  }

  skipWs(s, pos);
  out.leftSpeed  = left;
  out.rightSpeed = right;
  out.durationMs = duration;
  return true;
}

// -------------------------------------------------------------
// Execute the parsed commands
// -------------------------------------------------------------
void executeInstruction(int leftSpeed, int rightSpeed, unsigned long duration) {
  setMotors(leftSpeed, rightSpeed);

  if (duration > 0) {
    if (duration > 30000UL) duration = 30000UL; // time cap
    delay(duration);
    setMotors(0, 0);
  }
}
// -------------------------------------------------------------
// Serial input handling
// -------------------------------------------------------------
void recvCommandLine() {
  static byte index = 0;

  while (Serial.available() > 0) {
    char rc = Serial.read();
    // If we hit Enter (newline or carriage return), the command line is done
    if (rc == '\n' || rc == '\r') {
      // consume any paired newline/carriage return characters
      while (Serial.peek() == '\n' || Serial.peek() == '\r') {
        Serial.read();
      }
      inputBuffer[index] = '\0'; // terminate the string
      index = 0;
      newData = true;            // mark new data ready
      return;
    } else if (index < numChars - 1) {
      // store the character in our buffer
      inputBuffer[index++] = rc;
    }
  }
}
// -------------------------------------------------------------
// Parse and execute all commands in one line
// -------------------------------------------------------------
void parseAndExecute(char *input) {
  size_t pos = 0;
  Command c;
  while (nextCommand(input, pos, c)) {
    executeInstruction(c.leftSpeed, c.rightSpeed, c.durationMs);
  }
}
// -------------------------------------------------------------
// Arduino setup and loop
// -------------------------------------------------------------
void setup() {
  // configure all motor pins as outputs
  for (int i = 4; i <= 7; i++) pinMode(i, OUTPUT);
  Serial.begin(115200);
  Serial.println(F("CONNECTED"));
  Serial.println(F("Z = Instructions"));
}
void loop() {
  recvCommandLine();  // check if a line of text was received
  if (newData) {
    Serial.print(F("Received: "));
    Serial.println(inputBuffer);
    parseAndExecute(inputBuffer); // run each command in order
    newData = false;
  }
}
