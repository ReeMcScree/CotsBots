//Motor Definitions
int E1 = 5;     //M1 Speed Control
int E2 = 6;     //M2 Speed Control
int M1 = 4;     //M1 Direction Control
int M2 = 7;     //M2 Direction Control

//DIRECTIONS

//STOP
void stop(void)
{
  digitalWrite(E1, 0);
  digitalWrite(M1, LOW);
  digitalWrite(E2, 0);
  digitalWrite(M2, LOW);
}

//ADVANCE
void advance(char a, char b)
{
  analogWrite (E1, a);
  digitalWrite(M1, HIGH);
  analogWrite (E2, b);
  digitalWrite(M2, HIGH);
}

//MOVE BACKWARDS
void back_off (char a, char b)
{
  analogWrite (E1, a);
  digitalWrite(M1, LOW);
  analogWrite (E2, b);
  digitalWrite(M2, LOW);
}


//TURN LEFT
void turn_L (char a, char b)
{
  analogWrite (E1, a);
  digitalWrite(M1, LOW);
  analogWrite (E2, b);
  digitalWrite(M2, HIGH);
}

//TURN RIGHT
void turn_R (char a, char b)
{
  analogWrite (E1, a);
  digitalWrite(M1, HIGH);
  analogWrite (E2, b);
  digitalWrite(M2, LOW);
}

void setup(void) {
  int i;
  for (i = 4; i <= 7; i++)
    pinMode(i, OUTPUT);
  Serial.begin(115200);       //Set Baud Rate
  digitalWrite(E1, LOW);
  digitalWrite(E2, LOW);
}
void loop(void) {
  if (Serial.available()) {
    char val = Serial.read();
    if (val != -1)
    {
      switch (val)
      {
        case 'w'://Move Forward
          Serial.println("going forward");
          advance (255, 255);  //move forward at max speed
          delay (1000);
          stop();
          break;
        case 's'://Move Backward
          Serial.println("going backward");
          back_off (255, 255);  //move backwards at max speed
          delay (1000);
          stop();
          break;
        case 'a'://Turn Left
          Serial.println("turning left");
          turn_L (255, 255);
          delay (1000);
          stop();
          break;
        case 'd'://Turn Right
          Serial.println("turning right");
          turn_R (255, 255);
          delay (1000);
          stop();
          break;
        case 'h':
          Serial.println("w = forward, d = turn right, a = turn left, s = backward, h = this info");
          break;
      }
    }
    else stop();
  }
}