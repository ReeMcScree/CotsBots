import serial
import time

# Replace with your actual Bluetooth COM port

bluetooth_port = "COM10"  
baud_rate = 9600

try:
    # Open Bluetooth serial connection
    bt_serial = serial.Serial(bluetooth_port, baud_rate)
    time.sleep(2)
    # Give some time to establish connection

    print("Connected to Bluetooth module. Type 'exit' to quit.")
    
    while True:
        user_input = input("Enter a 1(turn on) or 0(turn off) to send: ")
        
        if user_input.lower() == "exit":
            print("Exiting program...")
            break
        
        bt_serial.write(user_input.encode())  # Send input via Bluetooth
        print(f"Sent: {user_input}")

    bt_serial.close()
except Exception as e:
    print(f"Error: {e}")

#small bluetooth program to get a whichever led in arduino code to turn on (lily had the built-in one on pin 13 and led on pin 7)
