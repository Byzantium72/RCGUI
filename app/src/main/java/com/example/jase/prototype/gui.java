package com.example.jase.prototype;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.SeekBar;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

public class gui extends AppCompatActivity {

    String address = null;
    private ProgressDialog progress;
    BluetoothAdapter myBluetooth = null;
    BluetoothSocket btSocket = null;
    private boolean isBtConnected = false;
    static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    BufferedOutputStream bufOut;
    //Queue<String> stream;
    SeekBar power;
    SeekBar steering;
    Stream streamThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gui);

        Intent newint = getIntent();
        address = newint.getStringExtra(connect.EXTRA_ADDRESS);
        //stream = new LinkedList<>();

        power = findViewById(R.id.power);
        steering = findViewById(R.id.steering);
        new ConnectBT().execute();
        /*
        try {
            bufOut = new BufferedOutputStream(btSocket.getOutputStream());
        }catch(IOException e){

        }*/
        streamThread = new Stream();
        //streamThread.run();
        power.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                if(b){
                    String send = "P-" + String.valueOf(i) + ":";
                    Message next = Message.obtain(streamThread.handler, 1, send);
                    next.sendToTarget();
                    //stream.add(send);
                    //send();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        steering.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                if(b){
                    String send = "S-" + String.valueOf(i) + ":";
                    Message next = Message.obtain(streamThread.handler, 1, send);
                    next.sendToTarget();
                    //stream.add(send);
                    //send();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }

    /*
    public void createStream(){
        try{
            bufOut = new BufferedOutputStream(btSocket.getOutputStream());
        }catch(IOException e){

        }
    }
    public void send(){
        try {
            //bufOut.write(s.getBytes());
            if(stream.peek().length() > 6){
                btSocket.getOutputStream().write("Here is the problem".getBytes());
            }
            btSocket.getOutputStream().write(stream.remove().getBytes());
        }catch (IOException e){

        }
    }*/
    private void msg(String s){
        Toast.makeText(getApplicationContext(), s, Toast.LENGTH_SHORT).show();
    }

    private class Stream extends Thread{
        Queue<String> stream = new LinkedList<>();

        @SuppressLint("HandlerLeak")
        Handler handler = new Handler(){
            @Override
            public void handleMessage(Message input){
                boolean empty = false;
                if(stream.size() == 0){
                    empty = true;
                }
                stream.add(input.obj.toString());

                if(empty){
                    run();
                }
            }
        };

        public void run(){
            while(stream.size() > 0){
                try {
                    btSocket.getOutputStream().write(stream.remove().getBytes());
                }catch(IOException e){
                }
            }
        }
    }

    private class ConnectBT extends AsyncTask<Void, Void, Void> {
        private boolean ConnectSuccess = true;


        @Override
        protected void onPreExecute()
        {
            progress = ProgressDialog.show(gui.this, "Connecting...", "Please wait!!!");  //show a progress dialog
        }

        @Override
        protected Void doInBackground(Void... devices) //while the progress dialog is shown, the connection is done in background
        {
            try
            {
                if (btSocket == null || !isBtConnected)
                {
                    myBluetooth = BluetoothAdapter.getDefaultAdapter();//get the mobile bluetooth device
                    BluetoothDevice dispositivo = myBluetooth.getRemoteDevice(address);//connects to the device's address and checks if it's available
                    btSocket = dispositivo.createInsecureRfcommSocketToServiceRecord(myUUID);
                    btSocket.connect();//start connection
                }
            }
            catch (IOException e)
            {
                ConnectSuccess = false;
            }
            return null;
        }
        @Override
        protected void onPostExecute(Void result)
        {
            super.onPostExecute(result);

            if (!ConnectSuccess)
            {
                msg("Connection Failed.");
                finish();
            }
            else
            {
                msg("Connected to " + myBluetooth.getRemoteDevice(address).getName());
                isBtConnected = true;
            }
            progress.dismiss();
            //createStream();
        }
    }
}
