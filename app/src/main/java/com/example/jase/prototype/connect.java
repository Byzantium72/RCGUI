package com.example.jase.prototype;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import java.util.Set;

public class connect extends AppCompatActivity {

    Button connect = findViewById(R.id.connect);
    ListView list = findViewById(R.id.deviceList);

    BluetoothAdapter myBluetooth = null;
    Set<BluetoothDevice> pairedDevices;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect);

        myBluetooth = BluetoothAdapter.getDefaultAdapter();

        if(myBluetooth == null){
            Toast.makeText(getApplicationContext(), "No Bluetooth Adapter", Toast.LENGTH_SHORT).show();
            finish();
        }else{
            if(!myBluetooth.isEnabled()){
                Intent btOn = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(btOn, 1);
            }
        }

        connect.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                pairedDevicesList();
            }
        });
    }

    public void pairedDevicesList(){

    }
}
