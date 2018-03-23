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
import java.util.concurrent.Delayed;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
//import com.devadvance.circularseekbar.CircularSeekBar;

public class gui extends AppCompatActivity {

    public static final int INPUT_MESSAGE = 5;
    public static final int SPEED_MESSAGE = 4;
    private ScheduledExecutorService scheduler;
    private ScheduledExecutorService constSpeed;
    String address = null;
    private ProgressDialog progress;
    private SpeedThread speeder;
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
    SeekBar steering;
    TextView diagnostics;
    TextView txtSteering;
    TextView txtPower;
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
        txtSteering = findViewById(R.id.txtSteering);
        txtPower = findViewById(R.id.txtPower);

        //determine if this is a test run
        if(address.equals("Test")){
            test = true;
        }
        steering = findViewById(R.id.steering);

        //begin connection and start output thread
        if(!test) {
            new ConnectBT().execute();
            streamThread = new Stream();
        }

        mainHandler = new Handler(Looper.getMainLooper()){
            @Override
            public void handleMessage(Message input) {
                if(input.what == INPUT_MESSAGE){
                    updateText(input.obj.toString());
                }
            }
        };

        String setup;
        setup = "Steering: " + steering.getProgress();
        txtSteering.setText(setup);
        setup = "Power: " + power.getProgress();
        txtPower.setText(setup);

        //listens for changes in the power meter
        power.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            //send the new value to the car every time it is changed
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                if(b && !test){
                    String send;
                    send =String.valueOf(i) + ":";
                    Message next = Message.obtain(streamThread.handler, 1, send);
                    Message speed = Message.obtain(speeder.speedHandler, SPEED_MESSAGE, send);
                    next.sendToTarget();
                    speed.sendToTarget();
                }
                String text = ("Power: " + i);
                txtPower.setText(text);
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
                String text = ("Power: " + seekBar.getProgress());
                txtPower.setText(text);
            }
        });

        //listens for changes in the steering bar
        steering.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                if(!test && b){
                    String send = String.valueOf(i + 100) + ":";
                    Message next = Message.obtain(streamThread.handler, 1, send);
                    next.sendToTarget();
                }
                String text = ("Steering: " + i);
                txtSteering.setText(text);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                steering.setProgress(50);
                if(!test) {
                    Message next = Message.obtain(streamThread.handler, 1, String.valueOf(steering.getProgress() + 100) + ":");
                    next.sendToTarget();
                }
                String text = ("Steering: " + seekBar.getProgress());
                txtSteering.setText(text);
            }
        });

        //listen and update vehicle on forward and reverse status
        fwd.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if(!test) {
                    if (b) {
                        Message send = Message.obtain(streamThread.handler, 1, "0:");
                        Message speed = Message.obtain(speeder.speedHandler, SPEED_MESSAGE, "0:");
                        power.setProgress(0);
                        send.sendToTarget();
                        speed.sendToTarget();
                        send = Message.obtain(streamThread.handler, 1, "F:");
                        send.sendToTarget();
                    } else {
                        Message send = Message.obtain(streamThread.handler, 1, "0:");
                        Message speed = Message.obtain(speeder.speedHandler, SPEED_MESSAGE, "0:");
                        power.setProgress(0);
                        send.sendToTarget();
                        speed.sendToTarget();
                        send = Message.obtain(streamThread.handler, 1, "R:");
                        send.sendToTarget();
                    }
                }
            }
        });

        //this button immediately sets the power of the vehicle to 0
        eStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(!test){
                    Message send = Message.obtain(streamThread.handler, 1, "0:");
                    Message speed = Message.obtain(speeder.speedHandler, SPEED_MESSAGE, "0:");
                    send.sendToTarget();
                    speed.sendToTarget();
                }
                power.setProgress(0);
            }
        });

        //reconnnects to the device it was previously connected to
        btnRec.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(!test) {
                    try {
                        btSocket.close();
                        isBtConnected = false;
                        new ConnectBT().execute();
                    } catch (IOException e) {

                    }
                }else{
                    msg("Test mode: nothing to reconnect");
                }
            }
        });

        String initial = "RPMs: INITIAL";
        diagnostics.setText(initial);
    }

    //updates the diagnostics text on the app
    public void updateText(String s){
        String send = "RPMs: " + s;
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

    //used to periodically pulse the current speed to car
    private class SpeedThread extends Thread{
        String theSpeed = "";
        @SuppressLint("HandlerLeak")
        Handler speedHandler = new Handler(){
            @Override
            public void handleMessage(Message input) {
                if(input.what == SPEED_MESSAGE){
                    theSpeed = input.obj.toString();
                }
            }
        };
        public void run(){
            Message send = Message.obtain(streamThread.handler, SPEED_MESSAGE, theSpeed);
            send.sendToTarget();
        }
    }

    //used to periodically read in diagnostic data from the car
    private class inStream extends Thread{
        String command;
        private inStream(){

        }

        @Override
        public void run(){
            try {
                command = "";
                if(btSocket.getInputStream().available() == 0) {
                    int rand = (int)(100*Math.random());
                    Message send = Message.obtain(mainHandler, INPUT_MESSAGE, String.valueOf(rand));
                    send.sendToTarget();
                }else {
                    while (btSocket.getInputStream().available() > 0) {
                        msg("Reading data");
                        command += (char) btSocket.getInputStream().read();
                    }
                    Message send = Message.obtain(mainHandler, INPUT_MESSAGE, command);
                    send.sendToTarget();
                }
            }catch(Exception e){
                msg("Error caught: " + e.getCause().toString());
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
                inThread = new inStream();
                try {
                    scheduler = Executors.newScheduledThreadPool(1);
                    scheduler.scheduleWithFixedDelay(inThread, 0, 1, TimeUnit.SECONDS);
                }catch(Exception e){
                    msg("Exception: " + e.getCause().toString());
                }
                speeder = new SpeedThread();
                constSpeed = Executors.newScheduledThreadPool(1);
                constSpeed.scheduleWithFixedDelay(speeder, 0, 500, TimeUnit.MILLISECONDS);
            }
            progress.dismiss();
        }
    }
}
