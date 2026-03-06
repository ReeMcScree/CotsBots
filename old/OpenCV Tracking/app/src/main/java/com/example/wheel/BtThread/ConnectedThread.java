package com.example.wheel.BtThread;

import android.bluetooth.BluetoothSocket;

import com.example.wheel.MainActivity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

public class  ConnectedThread extends Thread{

    BluetoothSocket bluetoothSocket=null;
    InputStream inputStream=null;//get input
    OutputStream outputStream=null;//get output
    int[] lastData=new int[]{0,0};
    public ConnectedThread(BluetoothSocket bluetoothSocket){
        this.bluetoothSocket=bluetoothSocket;

        InputStream inputTemp=null;
        OutputStream outputTemp=null;
        try {
            inputTemp=this.bluetoothSocket.getInputStream();
            outputTemp=this.bluetoothSocket.getOutputStream();
        } catch (IOException e) {
            try {
                bluetoothSocket.close();
            } catch (IOException ex) {}
        }
        inputStream=inputTemp;
        outputStream=outputTemp;
    }
    public void write(String input) {
        try {
            outputStream.write(input.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        super.run();
        while(true){
            //btWriteString("hello I love you!\r\n");
            if(!Arrays.toString(MainActivity.wheelData).equals(Arrays.toString(lastData))){
                btWriteInt(MainActivity.wheelData);
            }
            lastData=MainActivity.wheelData;
        }
    }

    public void btWriteInt(int[] intData){
        for(int sendInt:intData){
            try {
                outputStream.write(sendInt);
            } catch (IOException e) {}
        }
    }

    public void btWriteString(String string){
        for(byte sendData:string.getBytes()){
            try {
                outputStream.write(sendData);
            } catch (IOException e) {}
        }
    }

    public void cancel(){
        try {
            bluetoothSocket.close();
        } catch (IOException e) {}
    }
}
