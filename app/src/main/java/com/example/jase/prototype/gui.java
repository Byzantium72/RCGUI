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
import android.support.constraint.ConstraintLayout;
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
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class gui extends AppCompatActivity {

    //Constants
    public static final int GOOFY_MESSAGE = 6;
    public static final int INPUT_MESSAGE = 5;
    public static final int SPEED_MESSAGE = 4;
    public static final int TURN_MESSAGE = 3;
    public static final int DIRECTION_MESSAGE = 2;
    public static final int TOAST = 1;
    public static final int STEERING_DEFAULT = 50;
    public static final int NO_POWER = 0;

    String address = null;
    private ProgressDialog progress;
    private Stream streamThread;
    BluetoothAdapter myBluetooth = null;
    BluetoothSocket btSocket = null;
    private boolean isBtConnected = false;
    static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    Handler textHandler;
    Button eStop;
    Button btnDis;
    Button btnRec;
    inStream inThread;
    Switch fwd;
    Switch goofy;
    SeekBar power;
    SeekBar steering;
    TextView diagnostics;
    TextView txtSteering;
    TextView txtPower;
    ConstraintLayout layout;
    boolean test = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //initialize some of the necessary variables
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_gui);
        Intent newint = getIntent();
        address = newint.getStringExtra(connect.EXTRA_ADDRESS);

        steering = findViewById(R.id.steering);
        power = findViewById(R.id.power);
        fwd = findViewById(R.id.fwd);
        goofy = findViewById(R.id.goofy);
        eStop = findViewById(R.id.Estop);
        diagnostics = findViewById(R.id.diagnostics);
        txtSteering = findViewById(R.id.txtSteering);
        txtPower = findViewById(R.id.txtPower);
        layout = findViewById(R.id.layout);
        btnDis = findViewById(R.id.disconnect);
        btnRec = findViewById(R.id.reconnect);

        //determine if this is a test run
        if(address.equals("Test")){
            test = true;
        }

        //begin connection and start output thread
        if(!test) {
            new ConnectBT().execute();
        }

        //handles information read in from the car
        textHandler = new Handler(Looper.getMainLooper()){
            @Override
            public void handleMessage(Message input) {
                if(input.what == INPUT_MESSAGE){
                    updateText(input.obj.toString());
                }else if(input.what == TOAST){
                    msg(input.obj.toString());
                }
            }
        };

        //Initialize the labels
        String setup;
        setup = "Steering: " + steering.getProgress();
        txtSteering.setText(setup);
        setup = "Power: " + power.getProgress();
        txtPower.setText(setup);
        String initial = "RPMs: INITIAL";
        diagnostics.setText(initial);

        //*********************************LISTENERS************************************************
        //listens for changes in the power meter
        power.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            //send the new value to the car every time it is changed
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                if(b && !test){
                    String send;
                    send =String.valueOf(i) + ":";
                    updateSpeed(send);
                }
                String text = ("Power: " + i);
                txtPower.setText(text);
                //tran.startTransition(5000);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            //resend the last value when user take thumb off the slider
            //for extra data validation
            public void onStopTrackingTouch(SeekBar seekBar) {
                power.setProgress(NO_POWER);
                if(!test) {
                    String send = String.valueOf(power.getProgress()) + ":";
                    updateSpeed(send);
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
                    String send = String.valueOf(i) + ":";
                    updateTurn(send);
                }
                String text = ("Steering: " + i);
                txtSteering.setText(text);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            //recenter the car's steering when released
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                steering.setProgress(STEERING_DEFAULT);
                if(!test) {
                    String send = String.valueOf(steering.getProgress()) + ":";
                    updateTurn(send);
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
                        power.setProgress(NO_POWER);
                        updateSpeed("0:");
                        updateDirection("F:");
                    } else {
                        power.setProgress(NO_POWER);
                        updateSpeed("0:");
                        updateDirection("R:");
                    }
                }

                //changes color of background depending on being in fwd or reverse
                if(b){
                    layout.setBackground(getDrawable(R.drawable.appbackground));
                }else{
                    layout.setBackground(getDrawable(R.drawable.rev_background));
                }
            }
        });

        //this checks the steering mode, goofy or normal
        goofy.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if(!test){
                    if(b){
                        updateGoofy("1");
                    }else{
                        updateGoofy("0");
                    }
                }
            }
        });

        //this button immediately sets the power of the vehicle to 0
        eStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                power.setProgress(NO_POWER);
                if(!test){
                    updateSpeed("0:");
                }
            }
        });

        //disconnects device and returns to opening screen
        btnDis.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(!test){
                    try{
                        inThread.close();
                        streamThread.close();
                        btSocket.close();
                        isBtConnected = false;
                        finish();
                    }catch(IOException e){
                        msg(e.toString());
                    }
                }
            }
        });

        //reconnects to bluetooth device without having to exit
        btnRec.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try{
                    inThread.close();
                    streamThread.close();
                    btSocket.close();
                    isBtConnected = false;
                    new ConnectBT().execute();

                    //make sure car is back in start-up state
                    appReset();
                }catch(IOException e){
                    msg(e.toString());
                }
            }
        });
        //******************************************************************************************
    }

    //**********************************SIMPLE FUNCTIONS********************************************
    //updates the direction to be sent to the car
    public void updateDirection(String s){
        Message send = Message.obtain(streamThread.handler, DIRECTION_MESSAGE, s);
        send.sendToTarget();
    }
    //updates the speed to be sent to the car
    public void updateSpeed(String s){
        Message send = Message.obtain(streamThread.handler, SPEED_MESSAGE, s);
        send.sendToTarget();
    }

    //updates the turning to be sent to the car
    public void updateTurn(String s){
        Message send = Message.obtain(streamThread.handler, TURN_MESSAGE, s);
        send.sendToTarget();
    }

    //updates the diagnostics text on the app
    public void updateText(String s){
        String send = "RPMs: " + s;
        diagnostics.setText(send);
    }

    //updates the status of goofy steering
    public void updateGoofy(String s){
        Message send = Message.obtain(streamThread.handler, GOOFY_MESSAGE, s);
        send.sendToTarget();
    }

    //easy method for displaying messages to user
    private void msg(String s){
        Toast.makeText(getApplicationContext(), s, Toast.LENGTH_SHORT).show();
    }

    //ensures app is in start-up condition
    public void appReset(){
        steering.setProgress(STEERING_DEFAULT);
        updateTurn(STEERING_DEFAULT + ":");
        power.setProgress(NO_POWER);
        updateSpeed(NO_POWER + ":");
        goofy.setChecked(false);
        updateGoofy("0:");
        fwd.setChecked(true);
        updateDirection("F:");

        String setup;
        setup = "Steering: " + steering.getProgress();
        txtSteering.setText(setup);
        setup = "Power: " + power.getProgress();
        txtPower.setText(setup);
        String initial = "RPMs: INITIAL";
        diagnostics.setText(initial);
    }
    //**********************************************************************************************

    //***************************************THREADS************************************************
    //this is a background thread that will periodically send data to the bluetooth module
    //without interrupting normal device function
    private class Stream extends Thread{
        String theSpeed = "0:";
        String theTurn = "50:";
        String direction = "F:";
        String goofy = "0:";

        //a handler that sorts data that's being sent to it
        @SuppressLint("HandlerLeak")
        Handler handler = new Handler(){
            @Override
            public void handleMessage(Message input) {
                if(input.what == SPEED_MESSAGE){
                    theSpeed = input.obj.toString();
                }else if(input.what == TURN_MESSAGE){
                    theTurn = input.obj.toString();
                }else if(input.what == DIRECTION_MESSAGE){
                    direction = input.obj.toString();
                }else if(input.what == GOOFY_MESSAGE){
                    goofy = input.obj.toString();
                }
            }
        };

        //sends the data to the output stream
        public void run(){
            String command = "G" + goofy + "D" + direction + "P" + theSpeed + "S" + theTurn;
            try {
                btSocket.getOutputStream().write(command.getBytes());
            }catch(IOException e){
                msg(e.toString());
            }
        }

        //closes the stream
        public void close(){
            try {
                btSocket.getOutputStream().close();
            }catch(IOException e){
                Message send = Message.obtain(textHandler, TOAST, e.getMessage());
                send.sendToTarget();
            }
        }
    }

    //used to periodically read in diagnostic data from the car
    private class inStream extends Thread{
        String command;

        @Override
        public void run(){
            try {
                command = "";
                if(btSocket.getInputStream().available() == 0) {
                    int rand = (int)(100*Math.random());
                    Message send = Message.obtain(textHandler, INPUT_MESSAGE, String.valueOf(rand));
                    send.sendToTarget();
                }else {
                    while (btSocket.getInputStream().available() > 0) {
                        command += (char) btSocket.getInputStream().read();
                    }
                    Message send = Message.obtain(textHandler, INPUT_MESSAGE, command);
                    send.sendToTarget();
                }
            }catch(Exception e){
                msg("Error caught: " + e.getCause().toString());
            }
        }

        //closes the stream
        public void close(){
            try {
                btSocket.getInputStream().close();
            }catch(IOException e){
                Message send = Message.obtain(textHandler, TOAST, e.getMessage());
                send.sendToTarget();
            }
        }
    }
    //**********************************************************************************************

    //an asynchronous task that establishes connection to the bluetooth module
    private class ConnectBT extends AsyncTask<Void, Void, Void> {
        private boolean ConnectSuccess = true;
        String error = null;

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
                if (!isBtConnected)
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
                error = e.getMessage();
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
                msg(error);
                finish();
            }
            else
            {
                msg("Connected to " + myBluetooth.getRemoteDevice(address).getName());
                isBtConnected = true;
                inThread = new inStream();
                streamThread = new Stream();

                //starts the scheduled tasks to send and read information with the car
                ScheduledExecutorService startIn;
                ScheduledExecutorService startOut;

                //check for diagnostic info once every second
                startIn = Executors.newScheduledThreadPool(1);
                startIn.scheduleWithFixedDelay(inThread, 0, 1, TimeUnit.SECONDS);

                //send commands to car every 1/10th of a second
                startOut = Executors.newScheduledThreadPool(1);
                startOut.scheduleWithFixedDelay(streamThread, 0, 100, TimeUnit.MILLISECONDS);
            }
            progress.dismiss();
        }
    }
}