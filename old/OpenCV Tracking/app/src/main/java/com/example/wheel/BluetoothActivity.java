package com.example.wheel;

import static com.example.wheel.BtThread.ConnectThread.bluetoothSocket;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import com.example.wheel.BtThread.ConnectThread;
import com.example.wheel.BtThread.ConnectedThread;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class  BluetoothActivity extends AppCompatActivity {

    public static UUID MY_UUID= UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");//符合UUID格式就行。
    Button back=null;
    ListView btList=null;
    Intent intent=null;

    BluetoothAdapter bluetoothAdapter=null;
    List<String> devicesNames=new ArrayList<>();
    ArrayList<BluetoothDevice> readyDevices=null;
    ArrayAdapter<String> btNames=null;


    static ConnectThread connectThread=null;
    static ConnectedThread connectedThread=null;

    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth);

        back=(Button) findViewById(R.id.back);
        btList=(ListView) findViewById(R.id.btList);

        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(bluetoothSocket!=null&&bluetoothSocket.isConnected()){//先判断连接上了
                    connectedThread=new ConnectedThread(bluetoothSocket);
                    connectedThread.start();
                    Toast.makeText(BluetoothActivity.this,"已开启数据线程",Toast.LENGTH_SHORT).show();
                }
                intent=new Intent(BluetoothActivity.this,MainActivity.class);
                startActivity(intent);
            }
        });

        bluetoothAdapter=BluetoothAdapter.getDefaultAdapter();
        if(!bluetoothAdapter.isEnabled()){
            intent=new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent,1);
        }
        @SuppressLint("MissingPermission")
        Set<BluetoothDevice> pairedDevices=bluetoothAdapter.getBondedDevices();
        readyDevices=new ArrayList();
        if(pairedDevices!=null&&pairedDevices.size()>0){
            for(BluetoothDevice device:pairedDevices){
                readyDevices.add(device);
                devicesNames.add(device.getName());
                btNames=new ArrayAdapter<>(this, android.R.layout.simple_list_item_1,devicesNames);
            }
            btList.setAdapter(btNames);
        }else{
            Toast.makeText(this, "没有设备已经配对！", Toast.LENGTH_SHORT).show();
        }

        btList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                if(connectThread!=null){
                    connectThread.cancel();
                    connectThread=null;
                }

                connectThread=new ConnectThread(readyDevices.get(position));
                connectThread.start();
                Toast.makeText(BluetoothActivity.this, "已连接"+readyDevices.get(position).getName(), Toast.LENGTH_SHORT).show();
            }
        });

    }
}