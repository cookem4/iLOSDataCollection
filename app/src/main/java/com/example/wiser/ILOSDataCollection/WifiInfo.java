package com.example.wiser.ILOSDataCollection;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.StrictMode;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Adapter;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.SimpleAdapter;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.example.wiser.dottracker.R;
import com.mapbox.mapboxsdk.geometry.LatLng;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.Object;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.spec.ECField;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


public class WifiInfo extends AppCompatActivity implements SensorEventListener, StepListener {

    volatile Switch aSwitch;
    volatile TextView scanText;
    volatile TextView titleText;
    private Sensor myMagnetometer;
    float azimuth = 0f;
    float firstAzimuth = 0f;
    //Used to sum and average values of heading since it does not stabablize values well
    float firstAzimuthSum = 0f;
    int azimuthCount = 0;
    boolean displayAlertDialog = false;
    //This is not being used now but gets angle from start and end coordinates.
    public static float expectedHeading = 0f;
    float[] mGravity = new float[3];
    float[] mGeomagnetic = new float[3];
    private SensorManager SM;
    private SensorManager SMgyro;
    SensorEventListener SMgyroListener;
    volatile WifiManager wifiManager;
    volatile int clickCounter = 0;
    final int UPDATE_INTERVAL = 100;
    private Handler mRepeatHandler;
    private Runnable mRepeatRunnable;
    volatile List<String> outputData = new ArrayList<>();
    volatile List<String> replacedOutput = new ArrayList<>();
    volatile boolean switchChecked = false;
    public static double startLat;
    public static double startLong;
    public static double endLat;
    public static double endLong;
    public static boolean doServerUpload;
    double xCompMotion = 0;
    double yCompMotion = 0;
    double pathDistance = 0;
    double totalNumSteps = 0;
    double degToMRatio = 0;
    //For men multiply height in cm by 0.415
    //For women multiply height in cm by 0.413
    public static double STEP_LENGTH = 0;
    double STEP_TIME_ORIGINAL = 0;
    //Adaptive time is based on the last 5 steps the user takes
    volatile double STEP_TIME_ADAPTIVE = 0;
    volatile boolean walkingTooFast = false;
    volatile boolean walkingTooSlow = false;
    volatile ArrayList<Long> adaptiveTimeList = new ArrayList<Long>();
    volatile ArrayList<Long> scanTimeStampList = new ArrayList<Long>();
    volatile int stepCount = 0;
    private StepDetector simpleStepDetector;
    private Sensor accel;
    private Sensor rotation;
    public static String FLOOR_NUMBER = null;
    public static String BUILDING_NAME = null;
    public static String USER_NAME = null;
    double magX = 0;
    double magY = 0;
    double magZ = 0;
    double lat = 0;
    double lon = 0;
    double displayLat = 0;
    double dispalyLon = 0;
    boolean xIncreasing = false;
    boolean yIncreasing = false;
    //TitleNum is used for bookkeeping to keep track of how many paths have been collected for the server
    volatile String titleNum = "1";
    public volatile boolean pulling  = true;
    public volatile CountDownLatch latch;
    static Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wifi_info);
        getStepProfile();
        getMotionInfo();
        lat = startLat;
        lon = startLong;
        displayLat = startLat;
        dispalyLon = startLong;
        if(endLat - startLat > 0){
            xIncreasing = true;
        }
        else{
            xIncreasing = false;
        }
        if(endLong - startLong > 0){
            yIncreasing = true;
        }
        else{
            yIncreasing = false;
        }
        aSwitch = findViewById(R.id.collectSwitch);
        scanText = findViewById(R.id.scanText);
        titleText = findViewById(R.id.displayTitle);
        titleText.setText("Wifi Data");
        scanText.setText("Flip switch to begin data collection" + "\n" + "Please ensure phone is oriented in same direcion as collection path");
        SM = (SensorManager) getSystemService(SENSOR_SERVICE);
        accel = SM.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        rotation = SM.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        simpleStepDetector = new StepDetector();
        simpleStepDetector.registerListener(this);
        myMagnetometer = SM.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        SM.registerListener(this, myMagnetometer, SensorManager.SENSOR_DELAY_NORMAL);
        SM.registerListener(this, rotation, SensorManager.SENSOR_DELAY_NORMAL);
        mRepeatHandler = new Handler();
        aSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, final boolean isChecked) {
                aSwitch.setClickable(false);
                if(!isChecked){
                    if(stepCount == totalNumSteps) {
                        //Once switch becomes unchecked
                        //First get correct coordinates for each wifi scan based on Dr. Zheng's algorithm
                        //TODO enable this
                        processScanCoordinates();
                        storeData();
                        if (doServerUpload) {
                            writeToServer();
                        }
                        switchChecked = false;
                        //scanText.setText("Flip switch to continue collection");
                        clickCounter = 0;
                    }
                    //User has done something to warrant errenous data collection
                    else{
                        scanText.setText("Press back to select a new data collection path" + "\n" + "User collected data became innacurate");
                        clickCounter = 0;
                    }
                }
                else{
                    //To start off sets the adaptive step time as the average step time over collection path
                    STEP_TIME_ADAPTIVE = STEP_TIME_ORIGINAL;
                    //Timestamps when the program starts onto the list of step locations
                    adaptiveTimeList.add(System.nanoTime());
                    stepCount = 0;
                    SM.registerListener(WifiInfo.this, accel, SensorManager.SENSOR_DELAY_FASTEST);
                    switchChecked = true;
                    //Replace this to go back to original
                    //mRepeatRunnable.run();
                    makeWifiThread();
                }
            }
        });
        mRepeatRunnable = new Runnable() {
            @Override
            public void run() {
                if(switchChecked) {
                    clickCounter++;
                    scanWifi();
                    mRepeatHandler.postDelayed(mRepeatRunnable, UPDATE_INTERVAL);
                }
            }
        };
        mRepeatHandler.postDelayed(mRepeatRunnable, UPDATE_INTERVAL);

    }
    void makeWifiThread(){
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        setFrequencyBand2Hz(true, wifiManager);
        WifiManager.WifiLock wifilock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_SCAN_ONLY,"MyLock");
        wifilock.setReferenceCounted(true);
        wifilock.acquire();
        if(!wifilock.isHeld()) {

            wifilock.acquire();
        }
        //Separate thread for collecting wifi data
        new Thread(new Runnable() {
            @Override
            public void run() {
                while(switchChecked) {
                    long start = System.currentTimeMillis();
                    clickCounter++;
                    scanWifi();
                    long end = System.currentTimeMillis();
                    System.out.println("TIMEEEE" + (end - start));
                    try {
                        Thread.sleep(UPDATE_INTERVAL - (end - start));
                    } catch (Exception e) {
                    }
                }
            }
        }).start();
    }
    public void setFrequencyBand2Hz(boolean enable, WifiManager mWifiManager) {
        int band; //WIFI_FREQUENCY_BAND_AUTO = 0,  WIFI_FREQUENCY_BAND_2GHZ = 2
        try {
            Field field = Class.forName(WifiManager.class.getName())
                    .getDeclaredField("mService");
            field.setAccessible(true);
            Object obj = field.get(mWifiManager);
            Class myClass = Class.forName(obj.getClass().getName());

            Method method = myClass.getDeclaredMethod("setFrequencyBand", int.class, boolean.class);
            method.setAccessible(true);
            if (enable) {
                band = 2;
            } else {
                band = 0;
            }
            method.invoke(obj, band, false);

        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    //Handles wifi data
    void scanWifi(){
        if(wifiManager.isWifiEnabled()){
            wifiManager.startScan();
            StringBuffer stringBuffer = new StringBuffer();
            List<ScanResult> list = wifiManager.getScanResults();
            updateScanTimes();
            long tsLong = System.nanoTime();
            for(ScanResult scanResult:list){
                //Replace any commas in SSID with a space
                String ssid = scanResult.SSID;
                ssid.replace(",", " ");
                outputData.add("SCAN#"+ Integer.toString(clickCounter) + "," + Long.toString(tsLong)+ "," + Double.toString(lat) + "," + Double.toString(lon) + "," + BUILDING_NAME + FLOOR_NUMBER + "," + scanResult.BSSID + "," + scanResult.SSID + "," +  scanResult.level);
                stringBuffer.append(scanResult.SSID + "     " + scanResult.BSSID + "     " + scanResult.level + "\n");
            }
            if(stepCount < totalNumSteps) {
                if(firstAzimuth == 0f) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            scanText.setText(USER_NAME + "'s" + " Step Length: " + String.format("%.3f", STEP_LENGTH) + "m" + "\n" + "Heading: " + String.format("%.0f", azimuth) + "\n" + "Step Time: " + String.format("%.3f", STEP_TIME_ORIGINAL) + "s Adaptive: " + String.format("%.3f", STEP_TIME_ADAPTIVE) + "s" + "\n" + "Scan #" + (clickCounter) + ":\t" + list.size() + " networks scanned " + "\nCoordinates: " + "\n" + Double.toString(displayLat) + " " + Double.toString(dispalyLon) + "\n" + BUILDING_NAME + " " + FLOOR_NUMBER + "\n" + "Number of Steps: " + Integer.toString(stepCount) + "\n\n" + stringBuffer);
                        }
                    });
                }
                else{
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            scanText.setText(USER_NAME + "'s" + " Step Length: " + String.format("%.3f", STEP_LENGTH) + "m" + "\n" + "Expected heading: " + String.format("%.0f",firstAzimuth)+"\n"+  "Actual heading: " +String.format("%.0f",azimuth)+ "\n"+ "Step Time: " + String.format("%.3f", STEP_TIME_ORIGINAL)+ "s Adaptive: " + String.format("%.3f", STEP_TIME_ADAPTIVE) + "s"+ "\n" + "Scan #" + (clickCounter) + ":\t" + list.size() + " networks scanned " + "\nCoordinates: " + "\n" + Double.toString(displayLat) + " " + Double.toString(dispalyLon) + "\n" + BUILDING_NAME + " " + FLOOR_NUMBER + "\n" + "Number of Steps: " + Integer.toString(stepCount) + "\n\n" + stringBuffer);
                        }
                    });
                }
            }
            else{
                if(doServerUpload) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            scanText.setText("Collection Complete!" + "\n" + "Uploading Data to Server...");
                        }
                    });
                }
            }
        }
        else{
            Toast.makeText(getBaseContext(), "Enabling WiFi...", Toast.LENGTH_LONG).show();
            wifiManager.setWifiEnabled(true);
        }
    }
    void getMotionInfo(){
        //Finds distance between two coordinates in metres
        double degreeDistance = Math.sqrt(Math.pow((startLat - endLat),2) + Math.pow((startLong - endLong),2));
        double R = 6378.137; // Radius of earth in KM
        double dLat = startLat * Math.PI / 180 - endLat* Math.PI / 180;
        dLat = Math.abs(dLat);
        double dLon = startLong * Math.PI / 180 - endLong* Math.PI / 180;
        dLon = Math.abs(dLon);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(endLat * Math.PI / 180) * Math.cos(startLat* Math.PI / 180) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance;
        distance = R * c;
        distance = distance * 1000f;
        pathDistance = distance;
        degToMRatio = degreeDistance/distance;
        double angle = Math.atan(dLat/dLon);
        xCompMotion = Math.cos(angle);
        yCompMotion = Math.sin(angle);
        totalNumSteps = pathDistance/STEP_LENGTH;
        //Rounds number of steps to nearest whole number
        //TODO should this round down or up????
        totalNumSteps = Math.round(totalNumSteps);
    }
    void updateScanTimes(){
        scanTimeStampList.add(System.nanoTime());
    }
    //TODO will have to process the position of each access point after each step. will process the points between the most recent step
    void updateCoordinates(int stepType){
        //For a full step
        if(stepType == 0){
            if(xIncreasing) {
                lat = lat + STEP_LENGTH * yCompMotion * degToMRatio;
                displayLat = displayLat + STEP_LENGTH*yCompMotion*degToMRatio;
            }
            else{
                lat = lat - STEP_LENGTH * yCompMotion * degToMRatio;
                displayLat = displayLat - STEP_LENGTH*yCompMotion*degToMRatio;
            }
            if(yIncreasing) {
                lon = lon + STEP_LENGTH * xCompMotion * degToMRatio;
                dispalyLon= dispalyLon + STEP_LENGTH*xCompMotion*degToMRatio;
            }
            else{
                lon = lon - STEP_LENGTH * xCompMotion * degToMRatio;
                dispalyLon= dispalyLon - STEP_LENGTH*xCompMotion*degToMRatio;
            }
        }
        //For finding positions of scans that occur between steps
        else if(stepType == 1){
            /*
            fractionTime= fractionTime/1000;
            lat = lat + STEP_LENGTH*yCompMotion*degToMRatio*(fractionTime/STEP_TIME_ADAPTIVE);
            lon = lon + STEP_LENGTH*xCompMotion*degToMRatio*(fractionTime/STEP_TIME_ADAPTIVE);
            */
        }
        /*
        Log.i("STEP TIME ADAPTIVE", Double.toString(STEP_TIME_ADAPTIVE));
        Log.i("TIME FRACTION", Double.toString(fractionTime));
        Log.i("STEP FRACTION", Double.toString(fractionTime/STEP_TIME_ADAPTIVE));
        */
    }
    void processScanCoordinates(){
        //Runs after the path is walked along. Rewrites the coordinates in outputData
        //processes the coordinates of each scan based on the list of timestamps for scans and timestamps for steps
        //TODO make sure the accuracy of this is correct. Check to see why an additional step is required to trigger this screen
        Toast.makeText(getBaseContext(), "Processing Scan Coordinates", Toast.LENGTH_SHORT).show();
        //Make a list to store coordinates of each scan. The index number represents the scan number minus 1
        List<LatLng> scanCoordinates = new ArrayList<>();
        for(int i = 0; i < adaptiveTimeList.size()-1;i++){
            long upperTimeBound = adaptiveTimeList.get((i+1));
            long lowerTimeBound = adaptiveTimeList.get(i);
            List<Integer> scanIndexWithinStep = new ArrayList<>();
            //Searches through the list of timestamps and adds the scan numbers that fall within the step to a list
            for(int j = 0; j < scanTimeStampList.size();j++){
                if(scanTimeStampList.get(j) <= upperTimeBound && scanTimeStampList.get(j)> lowerTimeBound){
                    scanIndexWithinStep.add(j);
                }
            }
            //Can loop through them as they will be sequential
            for(int k = scanIndexWithinStep.get(0); k <= scanIndexWithinStep.get(scanIndexWithinStep.size()-1); k++){
                //Run the algorithm for each scan that falls within the time range for each step
                double tempLat;
                double tempLong;
                //Formula from Dr. Zheng's paper
                // i here represents the number of steps as the index number in adaptiveTimeList is the step number
                if(xIncreasing) {
                    tempLat = startLat + STEP_LENGTH * (i + (double) (scanTimeStampList.get(k) - lowerTimeBound) / (double) (upperTimeBound - lowerTimeBound)) * yCompMotion * degToMRatio;
                }
                else{
                    tempLat = startLat - STEP_LENGTH * (i + (double) (scanTimeStampList.get(k) - lowerTimeBound) / (double) (upperTimeBound - lowerTimeBound)) * yCompMotion * degToMRatio;
                }
                if(yIncreasing) {
                    tempLong = startLong + STEP_LENGTH * (i + (double) (scanTimeStampList.get(k) - lowerTimeBound) / (double) (upperTimeBound - lowerTimeBound)) * xCompMotion * degToMRatio;
                }
                else{
                    tempLong = startLong - STEP_LENGTH * (i + (double) (scanTimeStampList.get(k) - lowerTimeBound) / (double) (upperTimeBound - lowerTimeBound)) * xCompMotion * degToMRatio;
                }
                scanCoordinates.add(new LatLng(tempLat, tempLong));
            }

        }
        //Re-writes coordinates in outputdata
        for(int i = 0; i < outputData.size(); i++){
            String line = outputData.get(i);
            String[] entries = line.split(",");
            int scanNum = Integer.parseInt(entries[0].substring(5,entries[0].length()));
            //Entries at indices 2 and 3 represent latitude and longitude
            for(int j = 0; j<entries.length;j++){
                if(scanNum < scanCoordinates.size()) {
                    if (j == 2) {
                        entries[j] = Double.toString(scanCoordinates.get(scanNum - 1).getLatitude());
                    } else if (j == 3) {
                        entries[j] = Double.toString(scanCoordinates.get(scanNum - 1).getLongitude());
                    }
                }
            }
            String output = "";
            for(int j = 0; j < entries.length;j++){
                if(j == entries.length - 1){
                    output = output + entries[j];
                }
                else {
                    output = output + entries[j] + ",";
                }
            }
            //Creates new output list by replacing the latitude and longitude in the original
            replacedOutput.add(output);
        }

    }
    //Currently no implementation for magnetic field data
    @Override
    public void onSensorChanged(SensorEvent event) {
        //For heading values, step counting, and magnetic field information
        final float alpha = 0.97f;
        float rotationSpeed = 0f;
        synchronized (this){
            if (switchChecked && event.sensor.getType() == Sensor.TYPE_ACCELEROMETER){
                mGravity[0] = alpha * mGravity[0] + (1-alpha) * event.values[0];
                mGravity[1] = alpha * mGravity[1] + (1-alpha) * event.values[1];
                mGravity[2] = alpha * mGravity[2] + (1-alpha) * event.values[2];
                simpleStepDetector.updateAccel(
                        event.timestamp, event.values[0], event.values[1], event.values[2]);
            }
            if (switchChecked && event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD){
                mGeomagnetic[0] = alpha * mGeomagnetic[0] + (1-alpha) * event.values[0];
                mGeomagnetic[1] = alpha * mGeomagnetic[1] + (1-alpha) * event.values[1];
                mGeomagnetic[2] = alpha * mGeomagnetic[2] + (1-alpha) * event.values[2];
                magX = event.values[0];
                magY = event.values[1];
                magZ = event.values[2];
            }
            if(switchChecked && event.sensor.getType() == Sensor.TYPE_GYROSCOPE){
                rotationSpeed = event.values[2];
                Log.i("ROTATION SPEED", Float.toString(rotationSpeed));
            }
            if(rotationSpeed > 1.5){
                displayAlertDialog = true;
                SM.unregisterListener(this);
            }
            float R [] = new float [9];
            float I [] = new float [9];
            boolean success = SensorManager.getRotationMatrix(R,I,mGravity,mGeomagnetic);
            if (success){
                float [] orientation = new float[3];
                //Give a few seconds for magnetic reading to stabilize then take an average for a few seconds to find acceptable orientation
                if(clickCounter < 60 && clickCounter > 30){
                    azimuthCount++;
                    firstAzimuthSum += azimuth;
                }
                if(clickCounter == 60){
                    //Gives average from a few seconds since reading does not stabalize when walking
                    firstAzimuth = firstAzimuthSum/azimuthCount;
                }
                SM.getOrientation(R,orientation);
                azimuth = (float) (Math.toDegrees(orientation[0]));
                azimuth = removeMagLoops(azimuth);
                if(Math.abs(firstAzimuth - azimuth) > 35 && clickCounter>60){
                    //TODO disable this to allow for easier testing without orientation
                    //displayAlertDialog = true;
                    //SM.unregisterListener(this);
                }
            }
        }
        if (displayAlertDialog) {
            AlertDialog.Builder builder1 = new AlertDialog.Builder(this);
            builder1.setMessage("Phone orientation no longer matches path orientation!");
            builder1.setCancelable(true);
            switchChecked = false;
            builder1.setPositiveButton(
                    "Restart Collection",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            aSwitch.setChecked(false);
                            dialog.cancel();
                        }
                    });
            AlertDialog alert11 = builder1.create();
            alert11.show();
            displayAlertDialog = false;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    //Override for the class
    @Override
    public void onResume(){
        super.onResume();
        SM.registerListener((SensorEventListener)this,SM.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),SensorManager.SENSOR_DELAY_GAME);
        SM.registerListener((SensorEventListener)this,SM.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),SensorManager.SENSOR_DELAY_GAME);
    }
    //Override for the class
    @Override
    public void onPause(){
        super.onPause();
        SM.unregisterListener(this);
    }
    //Removed full multiples of 360deg rotations on magnetic orientation
    float removeMagLoops(float val){
        while(val < 0){
            val+=360;
        }
        val%=360;
        return val;
    }
    //Used to store sensor and wifi data
    void storeData(){
        try {
            Toast.makeText(getBaseContext(), "Storing Data", Toast.LENGTH_SHORT).show();
            //save data
            File sdCard = Environment.getExternalStorageDirectory();
            File directory = new File(sdCard.getAbsolutePath() + "/DataCollect");
            Log.i("Save Dir", sdCard.getAbsolutePath() + "/DataCollect");
            directory.mkdirs();
            String filename = BUILDING_NAME + FLOOR_NUMBER + "(" + startLat + "," + startLong + ")" + "to" + "(" + endLat + "," + endLong + ")"+ ".txt";
            File file = new File(directory, filename);
            PrintWriter out = new PrintWriter(file);
            for (int i = 0; i<replacedOutput.size();i++) {
                out.write(replacedOutput.get(i));
                out.write("\r\n");
            }
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    void writeToServer(){
        pullFromServer("http://18.188.107.179:8000/path-data/");
        //Makes thread wait while pulling form server
        try {
            while (pulling) {
                TimeUnit.SECONDS.sleep(1);
            }
        }
        catch(Exception e){}
        String alexTempServer = "http://18.188.107.179:8000/signal-data/";
        List<String> JSONList = new ArrayList<>();
        for(int i = 0; i < outputData.size(); i++){
            JSONList.add(createJSONString(outputData.get(i)));
        }
        sendDeviceDetails(alexTempServer, JSONList);
        //  Make thread wait
        latch = new CountDownLatch(1);
        try {
            Log.i("WAITING FOR THREAD:", "TRUE");
            latch.await();
        }
        catch (Exception e){}
        savePathData();
        scanText.setText("UPLOAD COMPLETE!" + "\n" + "Press back to select a new path of data collection");

    }
    String createJSONString(String s){
        String returnObj = new String();
        String[] stringList  = s.split(",");
        try {
            returnObj+="{";
            returnObj+="\"title\":";
            returnObj = returnObj + "\"" + stringList[4] + "path#" + titleNum + "\"";
            returnObj+=",\"scan_num\":";
            returnObj+=stringList[0].substring(5,stringList[0].length());
            returnObj+=",\"timestamp\":";
            returnObj+=stringList[1];
            returnObj+=",\"longitude\":";
            returnObj+=stringList[3];
            returnObj+=",\"latitude\":";
            returnObj+=stringList[2];
            returnObj+=",\"building_floor\":";
            returnObj+="\"" + stringList[4] + "\"";
            returnObj+=",\"mac_id\":";
            returnObj+="\"" + stringList[5] + "\"";
            returnObj+=",\"ssid\":";
            returnObj+= "\"" +stringList[6] + "\"";
            returnObj+=",\"signal_strength\":";
            returnObj+=stringList[7];
            returnObj+="}";
        }
        catch(Exception e){}
        return returnObj;
    }
    @Override
    public void step(long timeNs) {
        if(stepCount < totalNumSteps && switchChecked == true) {
            stepCount++;
            if(stepCount <=3 && stepCount>=1){
                adaptiveTimeList.add(System.nanoTime());
            }
            else if(stepCount >3){
                STEP_TIME_ADAPTIVE = (System.nanoTime() - adaptiveTimeList.get(adaptiveTimeList.size()-3))/3;
                adaptiveTimeList.add(System.nanoTime());
                STEP_TIME_ADAPTIVE/=1000000000;
            }
            updateCoordinates(0);
            //0.1 here is a threshold in seconds
            if((STEP_TIME_ADAPTIVE  - STEP_TIME_ORIGINAL) > 0.1 && stepCount > 3){
                //TODO disable this to allow for easier testing without step speed notifications
                //walkingTooFast = true;
                //SM.unregisterListener(this);

            }
            else if(STEP_TIME_ADAPTIVE - STEP_TIME_ORIGINAL < -0.1 && stepCount > 3){
                //TODO diable this to allow for easier testing without step speed notifications
                //walkingTooSlow = true;
                //SM.unregisterListener(this);

            }
        }
        else if(clickCounter!=0){
            if(!doServerUpload){
                scanText.setText("Collection Complete!" + "\n" + "Data stored on local storage");
            }
            aSwitch.setChecked(false);
            //scanText.setText("Scan complete, flip switch to begin new data collection");
            stepCount = 0;
        }
        if(walkingTooFast){
            AlertDialog.Builder builder1 = new AlertDialog.Builder(this);
            builder1.setMessage("User walking speed of " + String.format("%.3f",STEP_TIME_ADAPTIVE) + "s exceeded acceptable range!"+ "\n" + "This can change step length");
            builder1.setCancelable(true);
            switchChecked = false;
            builder1.setPositiveButton(
                    "Restart Collection",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            aSwitch.setChecked(false);
                            dialog.cancel();
                        }
                    });
            AlertDialog alert11 = builder1.create();
            alert11.show();
            walkingTooFast = false;
        }
        else if(walkingTooSlow){
            AlertDialog.Builder builder1 = new AlertDialog.Builder(this);
            builder1.setMessage("User walking speed of " + String.format("%.3f",STEP_TIME_ADAPTIVE) + "s exceeded acceptable range!"+ "\n" + "This can change step length");
            builder1.setCancelable(true);
            switchChecked = false;
            builder1.setPositiveButton(
                    "Restart Collection",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            aSwitch.setChecked(false);
                            dialog.cancel();
                        }
                    });
            AlertDialog alert11 = builder1.create();
            alert11.show();
            walkingTooSlow = false;
        }
    }
    //This function is called from the button press "STEP" as it allows the user to manually track steps if the pedometer is not working properly
    public void takeAStep(View v){
        if(stepCount < totalNumSteps && switchChecked == true) {
            stepCount++;
            updateCoordinates(0);
        }
        else if(clickCounter!=0){
            aSwitch.setChecked(false);
            //scanText.setText("Scan complete, flip switch to begin new data collection");
            stepCount = 0;
        }
    }
    public void getStepProfile() {
        try {
            Toast.makeText(getBaseContext(), "Reading Data", Toast.LENGTH_LONG).show();
            //save data
            File sdCard = Environment.getExternalStorageDirectory();
            File directory = new File(sdCard.getAbsolutePath() + "/DataCollectProfiles");
            Log.i("Save Dir", sdCard.getAbsolutePath() + "/DataCollect");
            directory.mkdirs();
            File file = new File(directory, USER_NAME + ".txt");
            FileReader fr = new FileReader(file);
            boolean readStepTime = false;
            String stepTime = "";
            String fileInfo = "";
            int intNextChar;
            char nextChar;
            //Numbers correspond the ASCII table values
            intNextChar = fr.read();
            while (intNextChar < 58 && intNextChar > 43) {
                if(intNextChar == ','){
                    intNextChar = fr.read();
                    readStepTime = true;
                }
                if(readStepTime){
                    nextChar = (char) intNextChar;
                    stepTime = stepTime + nextChar;
                    intNextChar = fr.read();
                }
                else {
                    nextChar = (char) intNextChar;
                    fileInfo = fileInfo + nextChar;
                    intNextChar = fr.read();
                }
            }
            STEP_TIME_ORIGINAL = Double.parseDouble(stepTime);
            STEP_LENGTH = Double.parseDouble(fileInfo);
        } catch (Exception e) {
        }

    }
    //This function just posts the start and end coordinates to the server
    public void savePathData(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                String data = "";
                HttpURLConnection httpURLConnection = null;
                String pathDataServer = "http://18.188.107.179:8000/path-data/";
                try {
                    long startTime = System.nanoTime();
                    httpURLConnection = (HttpURLConnection) new URL(pathDataServer).openConnection();
                    httpURLConnection.setUseCaches(false);
                    httpURLConnection.setDoOutput(true);
                    httpURLConnection.setReadTimeout(3000);
                    httpURLConnection.setConnectTimeout(3000);
                    httpURLConnection.setRequestMethod("POST");
                    httpURLConnection.setRequestProperty("Content-Type", "application/json");
                    httpURLConnection.connect();
                    DataOutputStream wr = new DataOutputStream(httpURLConnection.getOutputStream());
                    wr.writeBytes("{\"floor_number\":"+FLOOR_NUMBER +
                                ",\"start_latitude\":" + Double.toString(startLat) +
                                ",\"start_longitude\":" + Double.toString(startLong)+
                                ",\"end_latitude\":" + Double.toString(endLat) +
                                ",\"end_longitude\":" + Double.toString(endLong) + "}");
                    wr.flush();
                    wr.close();

                    InputStream in = httpURLConnection.getInputStream();
                    InputStreamReader inputStreamReader = new InputStreamReader(in);
                    int inputStreamData = inputStreamReader.read();
                    while (inputStreamData != -1) {
                        char current = (char) inputStreamData;
                        inputStreamData = inputStreamReader.read();
                        data += current;
                    }
                    long endTime = System.nanoTime();
                    Log.i("TIME TO POST:", Long.toString((endTime-startTime)/1000000));
                } catch (Exception e) {
                    scanText.setText("ERROR UPLOADING DATA!");
                    e.printStackTrace();
                } finally {
                    if (httpURLConnection != null) {
                        httpURLConnection.disconnect();
                    }
                }
            }
        }).start();
    }
    //Pulls from server
    void pullFromServer(String url){
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    int numEntries = 0;
                    HttpURLConnection httpURLConnection = null;
                    URL URL = new URL(url);
                    httpURLConnection = (HttpURLConnection) URL.openConnection();
                    httpURLConnection.setRequestMethod("GET");
                    httpURLConnection.setReadTimeout(10000);
                    httpURLConnection.setConnectTimeout(15000);
                    httpURLConnection.setDoOutput(true);
                    httpURLConnection.connect();
                    BufferedReader br = new BufferedReader(new InputStreamReader(URL.openStream()));
                    String[] lines;
                    String line;
                    line = br.readLine();
                    Log.i("HEREEEEEEEEEEEE", line);
                    lines = line.split("id");
                    Log.i("ONE ENTRYYYYYY", lines[0]);
                    Log.i("ANOTHER ONEEEEEEEEEEE", lines[2]);
                    Log.i("LENGTHHHHHHH", Integer.toString(lines.length));
                    numEntries = lines.length;
                    br.close();
                    if(numEntries > 0){
                        titleNum = Integer.toString(numEntries + 1);
                    }
                    pulling = false;
                }
                catch(Exception e){
                    Log.i("SERVER PULLING ERROR", e.toString());
                    pulling = false;
                }
            }
        }).start();
    }
    void sendDeviceDetails(String serverURL, List<String> outputInfo){

        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection httpURLConnection = null;
                try {
                    for(int i = 0; i < outputInfo.size();i++) {
                        long startTime = System.nanoTime();
                        httpURLConnection = (HttpURLConnection) new URL(serverURL).openConnection();
                        httpURLConnection.setUseCaches(false);
                        httpURLConnection.setDoOutput(true);
                        httpURLConnection.setReadTimeout(3000);
                        httpURLConnection.setConnectTimeout(3000);
                        httpURLConnection.setRequestMethod("POST");
                        httpURLConnection.setRequestProperty("Content-Type", "application/json");
                        httpURLConnection.connect();
                        OutputStream out = httpURLConnection.getOutputStream();
                        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(out));
                        bw.write(outputInfo.get(i));
                        bw.flush();
                        out.close();
                        bw.close();
                        if(httpURLConnection.getResponseCode() == HttpURLConnection.HTTP_CREATED || httpURLConnection.getResponseCode() == HttpURLConnection.HTTP_CREATED){
                            InputStream in = httpURLConnection.getInputStream();
                            BufferedReader br = new BufferedReader(new InputStreamReader(in));
                            String str = null;
                            StringBuffer buffer = new StringBuffer();
                            while ((str = br.readLine()) != null) {
                                buffer.append(str);
                            }
                            in.close();
                            br.close();
                        }
                        //Flag variable to tell us that we are on the last thread call

                        long endTime = System.nanoTime();
                        Log.i("TIME TO POST:", Long.toString((endTime - startTime) / 1000000));
                    }
                    latch.countDown();

                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (httpURLConnection != null) {
                        httpURLConnection.disconnect();
                    }
                }

            }
        }).start();
    }
    /*
    //Runs a separate thread that posts to the server all scans
    private class SendDeviceDetails extends AsyncTask<String, Void, String>  {

        @Override
        protected String doInBackground(String... params) {
            String data = "";
            HttpURLConnection httpURLConnection = null;
            try {

                httpURLConnection = (HttpURLConnection) new URL(params[0]).openConnection();
                httpURLConnection.setUseCaches(false);
                httpURLConnection.setDoOutput(true);
                httpURLConnection.setReadTimeout(3000);
                httpURLConnection.setConnectTimeout(3000);
                httpURLConnection.setRequestMethod("POST");
                httpURLConnection.setRequestProperty("Content-Type", "application/json");
                httpURLConnection.connect();

                DataOutputStream wr = new DataOutputStream(httpURLConnection.getOutputStream());
                wr.writeBytes(params[1]);
                wr.flush();
                wr.close();
                long startTime = System.nanoTime();
                InputStream in = httpURLConnection.getInputStream();
                InputStreamReader inputStreamReader = new InputStreamReader(in);
                int inputStreamData = inputStreamReader.read();
                while (inputStreamData != -1) {
                    char current = (char) inputStreamData;
                    inputStreamData = inputStreamReader.read();
                    data += current;
                }

                long endTime = System.nanoTime();

                Log.i("TIME TO POST:", Long.toString((endTime-startTime)/1000000));

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (httpURLConnection != null) {
                    httpURLConnection.disconnect();
                }
            }
            //Flag variable to tell us that we are on the last thread call
            if(params[1].charAt(params[1].length()-1)=='$'){
                scanning = false;
            }
            return data;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            Log.e("TAG", "IS SCAN FINISHED:" + scanning + " " + result); // this is expecting a response code to be sent from your server upon receiving the POST data
        }

        @Override
        protected void onPreExecute(){
        }


    }
    */

}