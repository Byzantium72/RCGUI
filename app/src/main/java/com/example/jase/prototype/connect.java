package com.example.jase.prototype;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Set;

public class connect extends AppCompatActivity {

    Button connect;
    ListView list;
    BluetoothAdapter myBluetooth = null;
    Set<BluetoothDevice> pairedDevices;
    public static String EXTRA_ADDRESS;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect);
        connect = findViewById(R.id.connect);
        list = findViewById(R.id.deviceList);

        myBluetooth = BluetoothAdapter.getDefaultAdapter();

        //if bluetooth is not turned on, request to turn it on
        if(myBluetooth == null){
            Toast.makeText(getApplicationContext(), "No Bluetooth Adapter", Toast.LENGTH_SHORT).show();
            finish();
        }else{
            if(!myBluetooth.isEnabled()){
                Intent btOn = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(btOn, 1);
            }
        }

        //turns displays paired devices when "Connect" is pressed
        connect.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                pairedDevicesList();
            }
        });
    }

    //Begins connection based on what paired device is tapped
    private AdapterView.OnItemClickListener myListClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
            String info = ((TextView) view).getText().toString();
            String address = info.substring(info.length() - 17);
            Intent intent = new Intent(connect.this, gui.class);
            intent.putExtra(EXTRA_ADDRESS, address);
            startActivity(intent);
        }
    };

    //displays list of paired devices
    public void pairedDevicesList(){
        pairedDevices = myBluetooth.getBondedDevices();
        ArrayList l = new ArrayList();

        if(pairedDevices.size() > 0){
            for(BluetoothDevice bt : pairedDevices){
                l.add(bt.getName() + "\n" + bt.getAddress());
            }
        }else{
            Toast.makeText(getApplicationContext(), "No paired Bluetooth devices", Toast.LENGTH_SHORT);
        }
        final ArrayAdapter adapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1,l);
        list.setAdapter(adapter);
        list.setOnItemClickListener(myListClickListener);
    }
}
