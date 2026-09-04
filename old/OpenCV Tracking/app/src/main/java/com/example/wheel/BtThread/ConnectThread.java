package com.example.wheel.BtThread;

import static com.example.wheel.BluetoothActivity.MY_UUID;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import java.io.IOException;

public class ConnectThread extends Thread{

    BluetoothDevice bluetoothDevice=null;
    public static BluetoothSocket bluetoothSocket=null;
    public ConnectThread(BluetoothDevice bluetoothDevice){
        this.bluetoothDevice=bluetoothDevice;
    }

    @SuppressLint("MissingPermission")
    @Override
    public void run() {
        super.run();
        try {
            bluetoothSocket=this.bluetoothDevice.createInsecureRfcommSocketToServiceRecord(MY_UUID);
            //连接
            bluetoothSocket.connect();
        } catch (IOException e) {
            try {
                bluetoothSocket.close();
            } catch (IOException ex) {}
        }
    }
    public void cancel(){
        if(bluetoothSocket!=null){
            try {
                bluetoothSocket.close();
            } catch (IOException e) {}
            bluetoothSocket=null;
        }
    }
}
