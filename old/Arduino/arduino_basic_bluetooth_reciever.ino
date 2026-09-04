char Incoming_value = 0;
int LED_PIN = 7;

//Set pins and serial to be ready for inputs/outputs
void setup() {
  Serial.begin(9600);
  pinMode(LED_PIN,OUTPUT);
  pinMode(LED_BUILTIN,OUTPUT);
}

void  loop() {
  //Check if there is a command ready
  if (Serial.available()  > 0)
    {
      //Read command sent
      Incoming_value = Serial.read();
      Serial.print(Incoming_value);

      //Turn on when 1
      if (Incoming_value == '1'){
        digitalWrite(LED_PIN,HIGH);
        digitalWrite(LED_BUILTIN,HIGH);
      }
      
      //Turn off when 0
      else if(Incoming_value == '0'){
        digitalWrite(LED_PIN,LOW);
        digitalWrite(LED_BUILTIN,LOW);
      }
        
    }
}