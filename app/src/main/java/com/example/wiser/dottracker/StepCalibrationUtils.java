package com.example.wiser.dottracker;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Environment;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.example.wiser.ILOSDataCollection.StepDetector;
import com.example.wiser.ILOSDataCollection.StepListener;
import com.example.wiser.ILOSDataCollection.WifiInfo;
import com.mapbox.mapboxsdk.geometry.LatLng;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

public class StepCalibrationUtils extends AppCompatActivity implements SensorEventListener, StepListener {


    public static double startLat;
    public static double startLong;
    public static double endLat;
    public static double endLong;
    public static String userName = null;
    boolean switchChecked = false;
    double lat = 0;
    double lon = 0;
    int stepCount = 0;
    Handler mRepeatHandler;
    Runnable mRepeatRunnable;
    SensorManager SM;
    StepDetector simpleStepDetector;
    Sensor accel;
    Switch aSwitch;
    TextView stepText;
    double pathDistance;
    final int UPDATE_INTERVAL = 100;
    double stepLength = 0;
    long startTime;
    long endTime;
    double stepTime;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startTime = System.currentTimeMillis();
        setContentView(R.layout.activity_step_calibration_utils);
        lat = startLat;
        lon = startLat;
        aSwitch = (Switch)findViewById(R.id.startStepSwitch);
        stepText = (TextView) findViewById(R.id.stepText);
        SM = (SensorManager) getSystemService(SENSOR_SERVICE);
        accel = SM.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        simpleStepDetector = new StepDetector();
        simpleStepDetector.registerListener(this);
        getPathDistance();
        mRepeatHandler = new Handler();
        aSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, final boolean isChecked) {
                if(!isChecked){
                    //Once switch becomes unchecked
                    switchChecked = false;
                    saveProfile();
                }
                else{
                    stepCount = 0;
                    SM.registerListener(StepCalibrationUtils.this, accel, SensorManager.SENSOR_DELAY_FASTEST);
                    switchChecked = true;
                    mRepeatRunnable.run();
                }
            }
        });
        mRepeatRunnable = new Runnable() {
            @Override
            public void run() {
                if(switchChecked) {
                    updateText();
                    mRepeatHandler.postDelayed(mRepeatRunnable, UPDATE_INTERVAL);
                }
            }
        };
        mRepeatHandler.postDelayed(mRepeatRunnable, UPDATE_INTERVAL);

    }
    void updateText(){
        stepText.setText(userName + "\n" + "Number of Steps: " + stepCount + "\n" + "Path Distance: " + pathDistance + "\n" +"Flip Switch Once Path is Completed");
    }
    void getPathDistance(){
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
    }
    void saveProfile(){
        try {
            endTime = System.currentTimeMillis();
            stepTime = (endTime - startTime)/stepCount;
            stepTime/=1000;
            stepLength = pathDistance/stepCount;
            stepText.setText("Calculated Step Length is: " + stepLength + "\n" + "Average time for one step is: " + stepTime +"\n" + "Flip switch to redo step calibration");

            Log.i("START TIME", Long.toString(startTime));
            Log.i("END TIME", Long.toString(endTime));

            Toast.makeText(getBaseContext(), "Storing Data", Toast.LENGTH_LONG).show();
            File sdCard = Environment.getExternalStorageDirectory();
            File directory = new File(sdCard.getAbsolutePath() + "/DataCollectProfiles");
            Log.i("Save Dir", sdCard.getAbsolutePath() + "/DataCollectProfiles");
            directory.mkdirs();
            String filename = userName + ".txt";
            File file = new File(directory, filename);
            PrintWriter out = new PrintWriter(file);
            out.write(Double.toString(stepLength) + "," + Double.toString(stepTime));
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (switchChecked && event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            simpleStepDetector.updateAccel(
                    event.timestamp, event.values[0], event.values[1], event.values[2]);
        }
    }
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
    @Override
    public void step(long timeNs) {
        if(switchChecked == true) {
            stepCount++;
        }
        else{
            stepText.setText("Scan complete, flip switch to begin new data collection");
            stepCount = 0;
        }
    }
}
