package com.example.wiser.dottracker;

import android.content.Context;
import android.hardware.SensorManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.example.wiser.ILOSDataCollection.WifiInfo;
import com.mapbox.mapboxsdk.geometry.LatLng;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SinglePointUtils extends AppCompatActivity {
    public static double collectionLat;
    public static double collectionLong;
    public static int FLOOR_NUMBER;
    public static String BUILDING_NAME;
    public static boolean doServerUpload;
    Switch aSwitch;
    TextView pointDataText;
    volatile boolean switchChecked = false;
    final int UPDATE_INTERVAL = 100;
    Handler mRepeatHandler;
    Runnable mRepeatRunnable;
    WifiManager wifiManager;
    volatile List<String> outputData = new ArrayList<>();
    volatile int clickCounter = 0;
    public volatile boolean pulling = true;
    public volatile boolean scanning = true;
    volatile String titleNum = "1";
    int maxClicks = 50;
    public volatile CountDownLatch latch;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_single_point_utils);
        pointDataText = (TextView)findViewById(R.id.pointText);
        aSwitch = (Switch)findViewById(R.id.startSwitch);
        mRepeatHandler = new Handler();
        aSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, final boolean isChecked) {
                aSwitch.setClickable(false);
                if(!isChecked){
                    //Once switch becomes unchecked
                    switchChecked = false;
                }
                else{
                    switchChecked = true;
                    //Change this to have it change the thread it runs on
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
                if(clickCounter!=0){
                    saveData();
                    if(doServerUpload){
                        writeToServer();
                    }
                }
            }
        }).start();
    }
    void saveData(){
        Looper.prepare();
        try {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getBaseContext(), "Storing Data", Toast.LENGTH_LONG).show();
                }
            });
            //save data
            File sdCard = Environment.getExternalStorageDirectory();
            File directory = new File(sdCard.getAbsolutePath() + "/DataCollectSinglePoint");
            Log.i("Save Dir", sdCard.getAbsolutePath() + "/DataCollectSinglePoint");
            directory.mkdirs();
            String filename = BUILDING_NAME + FLOOR_NUMBER + "(" + collectionLat + "," + collectionLong + ")" + ".txt";
            File file = new File(directory, filename);
            PrintWriter out = new PrintWriter(file);
            for (int i = 0; i<outputData.size();i++) {
                out.write(outputData.get(i));
                out.write("\r\n");
            }
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    void scanWifi(){
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiManager.startScan();
        if(wifiManager.isWifiEnabled()){
            StringBuffer stringBuffer = new StringBuffer();
            List<ScanResult> list = wifiManager.getScanResults();
            long tsLong = System.currentTimeMillis();
            for(ScanResult scanResult:list){
                outputData.add("SCAN#"+ Integer.toString(clickCounter) + "," + Long.toString(tsLong)+ "," + Double.toString(collectionLat) + "," + Double.toString(collectionLong) + "," + BUILDING_NAME + FLOOR_NUMBER + "," + scanResult.BSSID + "," + scanResult.SSID + "," +  scanResult.level);
                stringBuffer.append(scanResult.SSID + "     " + scanResult.BSSID + "     " + scanResult.level + "\n");
            }
            if(clickCounter <= maxClicks) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        pointDataText.setText("Collecting 50 scans for best results!" + "\n\n" + "Scan #" + (clickCounter) + ":\t" + list.size() + " networks scanned " + "\nCoordinates: " + "\n" + Double.toString(collectionLat) + " " + Double.toString(collectionLong) + "\n" + BUILDING_NAME + " " + FLOOR_NUMBER + "\n\n" + stringBuffer);
                    }
                });
            }
            else{
                if(doServerUpload) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            pointDataText.setText("Uploading Data to Server...");
                        }
                    });
                }
                else{
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            pointDataText.setText("Data Stored!" + "\n" + "Press back to select a new point of data collection");
                        }
                    });
                }
                switchChecked = false;
            }
        }
        else{
            Toast.makeText(getBaseContext(), "Enabling WiFi...", Toast.LENGTH_LONG).show();
            wifiManager.setWifiEnabled(true);
        }
    }
    void writeToServer(){
        pullFromServer("http://18.188.107.179:8000/point-data/");
        //Makes thread wait while pulling form server
        try {
            while (pulling) {
                TimeUnit.SECONDS.sleep(1);
            }
        }
        catch(Exception e){}
        String alexTempServer = "http://18.188.107.179:8000/single-point-data/";
        List<String> JSONList = new ArrayList<>();
        for(int i = 0; i < outputData.size(); i++){
            JSONList.add(createJSONString(outputData.get(i)));
        }
        sendDeviceDetails(alexTempServer, JSONList);
        //Make thread wait
        latch = new CountDownLatch(1);
        try {
            Log.i("WAITING FOR THREAD:", "TRUE");
            latch.await();
        }
        catch (Exception e){}
        savePointData();
        if(doServerUpload) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    pointDataText.setText("UPLOAD COMPLETE!" + "\n" + "Press back to select a new point of data collection");
                }
            });
        }
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
    //This function just posts the start and end coordinates to the server
    public void savePointData(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                String data = "";
                HttpURLConnection httpURLConnection = null;
                String pathDataServer = "http://18.188.107.179:8000/point-data/";
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
                            ",\"latitude\":" + Double.toString(collectionLat) +
                            ",\"longitude\":" + Double.toString(collectionLong)+
                            "}");
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
                    Log.i("LENGTHHHHHHH", Integer.toString(lines.length));
                    numEntries = lines.length;
                    br.close();
                    if(numEntries > 0){
                        titleNum = Integer.toString(numEntries +1);
                    }
                    pulling = false;
                }
                catch(Exception e){
                    pointDataText.setText("ERROR FETCHING SERVER DATA!");
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
                long startTime = System.nanoTime();
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
    }
    */
}
