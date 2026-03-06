//Robot Letter Control w/ good parsing :P
//V1.1 Casey Jensen 10/09/2025
//setup: upload onto arduino nano, open serial monitor, set pull downs to newline and 115200 baud

#include <ctype.h>
// -------------------------------------------------------------
// Command structure: holds a single parsed instruction
// -------------------------------------------------------------
struct Command {
  char cmd;                 // letter
  bool hasDuration;         // true if a number followed the command (timed move)
  unsigned long durationMs; // how long to move in milliseconds (if any) | delay requires unsigned long argument
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
void stop() {
  // stop both motors
  digitalWrite(E1, LOW);
  digitalWrite(E2, LOW);
}
//uint8_t = unsigned 8-bit integer | standard type from stdint.h/ arduino.h
void advance(uint8_t a, uint8_t b) {
  analogWrite(E1, a);   
  digitalWrite(M1, HIGH);
  analogWrite(E2, b);   
  digitalWrite(M2, HIGH);
}

void back_off(uint8_t a, uint8_t b) {
  analogWrite(E1, a);   
  digitalWrite(M1, LOW);
  analogWrite(E2, b);   
  digitalWrite(M2, LOW);
}

void turn_L(uint8_t a, uint8_t b) {
  analogWrite(E1, a);   
  digitalWrite(M1, HIGH);
  analogWrite(E2, b);   
  digitalWrite(M2, LOW);
}

void turn_R(uint8_t a, uint8_t b) {
  analogWrite(E1, a);   
  digitalWrite(M1, LOW);
  analogWrite(E2, b);   
  digitalWrite(M2, HIGH);
}
// -------------------------------------------------------------
// Parsing utilities
// -------------------------------------------------------------
// Skips over spaces and tabs
static inline void skipWs(const char *s, size_t &pos) {
  while (s[pos] && isspace(static_cast<unsigned char>(s[pos]))) pos++;
}
// Reads one command (letter + optional number) from the input string.
// Returns true if a command was found, false if no more commands.
bool nextCommand(const char *s, size_t &pos, Command &out) {
  skipWs(s, pos);                // skip spaces
  char c = s[pos];
  if (!c) return false;          // end of string
  if (!isalpha((unsigned char)c)) return false; // not a letter → stop

  out.cmd = c;                   
  out.hasDuration = false;
  out.durationMs = 0;
  pos++;                         // move past the letter

  skipWs(s, pos);                // skip spaces before optional number
  if (isdigit((unsigned char)s[pos])) {
    // read number following command (like "w1000 or w 1000") | converts from a character to a number 
    unsigned long val = 0;
    while (isdigit((unsigned char)s[pos])) {  //for each decimal place it takes previous * 10 and adds current number
      val = val * 10 + (s[pos] - '0');
      pos++;
    }
    out.hasDuration = true;
    out.durationMs = val;
  }
  skipWs(s, pos);                // skip any trailing spaces
  return true;
}

// -------------------------------------------------------------
// Execute the parsed commands
// -------------------------------------------------------------
void executeInstruction(char cmd, bool hasDuration, unsigned long duration) {

  switch (tolower(cmd)) {
    case 'w': advance(255, 255); break;
    case 's': back_off(255, 255); break;
    case 'a': turn_L(255, 255);  break;
    case 'd': turn_R(255, 255);  break;
    case 'x': stop();                        break;
    case 'z':
      Serial.println(F("Instructions:"));
      Serial.println(F("  w/a/s/d # = does a command for a duration (ms)"));
      Serial.println(F("  w/a/s/d = forward/backward/left/right continuously"));
      Serial.println(F("  x = stop"));
      return;
    default:
      Serial.print(F("Unknown command: "));
      Serial.println(cmd);
      return;
  }
  // if there's a duration -> run for that time then stop
  if (hasDuration) {
    if (duration > 30000UL) duration = 30000UL; // time cap
    delay(duration);
    stop();
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
   executeInstruction(c.cmd, c.hasDuration, c.durationMs);
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
