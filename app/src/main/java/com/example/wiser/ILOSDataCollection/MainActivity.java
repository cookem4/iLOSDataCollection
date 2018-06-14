package com.example.wiser.ILOSDataCollection;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;


import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import com.example.wiser.dottracker.OpenCVPath;
import com.example.wiser.dottracker.OpenCVStepCounter;
import com.example.wiser.dottracker.R;
import com.example.wiser.dottracker.SinglePointMap;
import com.example.wiser.dottracker.StepCalibrationPath;


public class MainActivity extends AppCompatActivity {

    Button dataCollect;
    Button openCVCam;
    Button calibrateStep;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dataCollect = (Button)findViewById(R.id.wifiBtn);
        openCVCam = (Button)findViewById(R.id.openCVCamBtn);
        calibrateStep= (Button)findViewById(R.id.calibrateBtn);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(com.example.wiser.dottracker.R.layout.activity_main);
    }

    public void goToWifiInfo(View v){
        Intent Intent = new Intent(this, MapData.class);
        startActivity(Intent);
    }
    public void goToStepCalibration(View v){
        Intent Intent = new Intent(this, StepCalibrationPath.class);
        startActivity(Intent);
    }
    public void goToOpenCVCam(View v){
        Intent Intent = new Intent(this, OpenCVPath.class);
        startActivity(Intent);
    }
    public void goToSinglePoint(View v){
        Intent Intent = new Intent(this, SinglePointMap.class);
        startActivity(Intent);
    }
}


