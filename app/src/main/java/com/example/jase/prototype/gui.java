package com.example.jase.prototype;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VerticalSeekBar;

import com.marcinmoskala.arcseekbar.ArcSeekBar;
import com.marcinmoskala.arcseekbar.ProgressListener;
import com.triggertrap.seekarc.SeekArc;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;
//import com.devadvance.circularseekbar.CircularSeekBar;

public class gui extends AppCompatActivity {

    public static final int INPUT_MESSAGE = 5;
    String address = null;
    private ProgressDialog progress;
    BluetoothAdapter myBluetooth = null;
    BluetoothSocket btSocket = null;
    private boolean isBtConnected = false;
    static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    Handler mainHandler;
    Button btnRec;
    Button eStop;
    Stream streamThread;
    inStream inThread;
    Switch fwd;
    SeekBar power;
    ArcSeekBar Asteering;
    TextView diagnostics;
    boolean test = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //initialize some of the necessary variables
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_gui);
        Intent newint = getIntent();
        address = newint.getStringExtra(connect.EXTRA_ADDRESS);

        power = findViewById(R.id.power);
        fwd = findViewById(R.id.fwd);
        btnRec = findViewById(R.id.reconnect);
        eStop = findViewById(R.id.Estop);
        diagnostics = findViewById(R.id.diagnostics);

        //determine if this is a test run
        if(address.equals("Test")){
            test = true;
        }
        Asteering = findViewById(R.id.Asteering);
        Asteering.setMaxProgress(100);

        //begin connection and start output thread
        if(!test) {
            new ConnectBT().execute();
            streamThread = new Stream();
            inThread = new inStream();
        }

        mainHandler = new Handler(Looper.getMainLooper()){
            @Override
            public void handleMessage(Message input) {
                if(input.what == INPUT_MESSAGE){
                    updateText(input.obj.toString());
                }
            }
        };

        //listens for changes in the power meter
        power.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            //send the new value to the car every time it is changed
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                if(b && !test){
                    String send;
                    send =String.valueOf(i) + ":";
                    Message next = Message.obtain(streamThread.handler, 1, send);
                    next.sendToTarget();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            //resend the last value when user take thumb off the slider
            //for extra data validation
            public void onStopTrackingTouch(SeekBar seekBar) {
                if(!test) {
                    String send;
                    send = String.valueOf(seekBar.getProgress());
                    Message next = Message.obtain(streamThread.handler, 1, send);
                    next.sendToTarget();
                }
            }
        });

        //listens for changes in the steering bar
        Asteering.setOnProgressChangedListener(new ProgressListener() {
            @Override
            public void invoke(int i) {
                if(!test) {
                    String send = String.valueOf(i + 100) + ":";
                    Message next = Message.obtain(streamThread.handler, 1, send);
                    next.sendToTarget();
                }
            }
        });

        Asteering.setOnStopTrackingTouch(new ProgressListener() {
            @Override
            public void invoke(int i) {
                Asteering.setProgress(50);
            }
        });

        //listen and update vehicle on forward and reverse status
        fwd.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if(!test) {
                    if (b) {
                        Message send = Message.obtain(streamThread.handler, 1, "0:");
                        power.setProgress(0);
                        send.sendToTarget();
                        send = Message.obtain(streamThread.handler, 1, "F:");
                        send.sendToTarget();
                    } else {
                        Message send = Message.obtain(streamThread.handler, 1, "0:");
                        power.setProgress(0);
                        send.sendToTarget();
                        send = Message.obtain(streamThread.handler, 1, "R:");
                        send.sendToTarget();
                    }
                }
            }
        });

        btnRec.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(!test) {
                    try {
                        btSocket.close();
                        btSocket = null;
                        new ConnectBT().execute();
                    } catch (IOException e) {

                    }
                }else{
                    msg("Test mode: nothing to reconnect");
                }
            }
        });

        eStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(!test){
                    Message send = Message.obtain(streamThread.handler, 1, "0:");
                    power.setProgress(0);
                    send.sendToTarget();
                }
            }
        });
    }

    public void updateText(String s){
        String send = "RPMS: " + s;
        diagnostics.setText(send);
    }
    //easy method for displaying messages to user
    private void msg(String s){
        Toast.makeText(getApplicationContext(), s, Toast.LENGTH_SHORT).show();
    }

    //this is a background thread that will constantly send data to the bluetooth module
    //without interrupting normal device function
    private class Stream extends Thread{
        Queue<String> stream = new LinkedList<>();  //this queue holds all information to be sent

        //handler required for thread to communicate with main UI thread
        @SuppressLint("HandlerLeak")
        Handler handler = new Handler(){
            @Override
            public void handleMessage(Message input){
                boolean empty = false;
                if(stream.size() == 0){
                    empty = true;
                }
                stream.add(input.obj.toString());

                //if wasn't any data being sent, restart the sending process
                if(empty){
                    run();
                }
            }
        };

        //once initialized, this method will continue to send data to the bluetooth device until
        //out of data to send
        public void run(){
            while(stream.size() > 0){
                try {
                    btSocket.getOutputStream().write(stream.remove().getBytes());
                }catch(IOException e){
                }
            }
        }
    }

    private class inStream extends Thread{
        String command;
        public void run(){
            while(btSocket != null){
                try {
                    command = "";
                    while (btSocket.getInputStream().available() == 0) {
                        Thread.sleep(500);
                    }

                    while(btSocket.getInputStream().available()>0){
                        command += (char)btSocket.getInputStream().read();
                    }
                    Message send = Message.obtain(mainHandler, INPUT_MESSAGE, command);
                    send.sendToTarget();
                }catch(Exception e){

                }
            }
        }
    }

    //an asynchronous task that establishes connection to the bluetooth module
    private class ConnectBT extends AsyncTask<Void, Void, Void> {
        private boolean ConnectSuccess = true;


        @Override
        protected void onPreExecute()
        {
            progress = ProgressDialog.show(gui.this, "Connecting...",
                    "Please wait!!!");  //show a progress dialog
        }

        @Override
        //while the progress dialog is shown, the connection is done in background
        protected Void doInBackground(Void... devices)
        {
            try
            {
                if (btSocket == null || !isBtConnected)
                {
                    myBluetooth = BluetoothAdapter.getDefaultAdapter();
                    BluetoothDevice module = myBluetooth.getRemoteDevice(address);
                    btSocket = module.createInsecureRfcommSocketToServiceRecord(myUUID);
                    btSocket.connect();//start connection
                }
            }
            //if there was an error, the connection failed
            catch (IOException e)
            {
                ConnectSuccess = false;
            }
            return null;
        }
        @Override
        //if successful, show the GUI
        //if not, return to the previous screen with a fail message
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
        }
    }
}
