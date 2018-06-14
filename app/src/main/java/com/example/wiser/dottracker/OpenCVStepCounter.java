package com.example.wiser.dottracker;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Environment;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import java.lang.Math;

import com.example.wiser.ILOSDataCollection.MapData;
import com.example.wiser.ILOSDataCollection.Object;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;

import java.io.File;
import java.io.FileReader;
import java.security.Policy;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.round;

public class OpenCVStepCounter extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {


    // Used for logging success or failure messages
    private static final String TAG = "OCVSample::Activity";

    // Loads camera view of OpenCV for us to use. This lets us see using OpenCV
    private CameraBridgeViewBase mOpenCvCameraView;

    // Used in Camera selection from menu (when implemented)
    private boolean mIsJavaCamera = true;
    private MenuItem mItemSwitchCamera = null;

    // These variables are used (at the moment) to fix camera orientation from 270degree to 0degree
    Mat mRgba;
    Mat mRgbaF;
    Mat mRgbaT;
    boolean collectData = false;
    public volatile TextView coordText;
    public volatile TextView stepText;
    int clickCount = 0;
    List<com.example.wiser.ILOSDataCollection.Object> redList = new ArrayList<Object>();
    List<com.example.wiser.ILOSDataCollection.Object> blueList  = new ArrayList<com.example.wiser.ILOSDataCollection.Object>();
    List<com.example.wiser.ILOSDataCollection.Object> greenList  = new ArrayList<com.example.wiser.ILOSDataCollection.Object>();
    int counter = 1;
    public volatile int blueNumSteps = 0;
    public volatile int redNumSteps = 0;
    long blueGone = 0;
    long blueStart = 1;
    long blueEnd = 0;
    long redGone = 0;
    long redStart = 1;
    long redEnd = 0;
    boolean redGreenLine = false;
    boolean blueGreenLine = false;
    final double peakThresh = 5; //Number of frames for minimum peak distances
    final int MIN_AREA = 1000;
    public static String USER_NAME;
    public static double startLat;
    public static double startLong;
    public static double endLat;
    public static double endLong;
    public static String BUILDING_NAME;
    public static String FLOOR_NUMBER;
    double STEP_TIME_ORIGINAL;
    double STEP_LENGTH;
    boolean realTimeIncreaseRed = false;
    boolean realTimeIncreaseBlue = false;
    boolean steppedOnceRed = false;
    boolean steppedOnceBlue = false;
    int stepCount = 0;
    EditText nameText;
    double totalNumSteps;
    double xCompMotion;
    double yCompMotion;
    double pathDistance;
    double degToMRatio;
    boolean finishedCollection;
    static {
        OpenCVLoader.initDebug();
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_open_cvstep_counter);
        getStepProfile();
        getMotionInfo();
        mOpenCvCameraView = (JavaCameraView) findViewById(R.id.show_camera_activity_java_surface_view);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        coordText = (TextView)findViewById(R.id.coordText);
        coordText.setText("Press the record button!");
        stepText = (TextView)findViewById(R.id.stepRecorder);
        stepText.setText(USER_NAME + "'s step length: " + String.format("%.3f", STEP_LENGTH) + "m");
        //This speeds things up
        mOpenCvCameraView.setMaxFrameSize(400,400);
        mOpenCvCameraView.setCvCameraViewListener(this);
        mOpenCvCameraView.enableView();
        displayToast();
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_1_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {

        mRgba = new Mat(height, width, CvType.CV_8UC3);
        mRgbaF = new Mat(height, width, CvType.CV_8UC3);
        mRgbaT = new Mat(width, width, CvType.CV_8UC3);
    }

    public void onCameraViewStopped() {
        mRgba.release();
    }

    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        // TODO Auto-generated method stub
        mRgba = inputFrame.rgba();
        Imgproc.resize(mRgba, mRgba, new Size(100,100));
        // Rotate mRgba 90 degrees
        Core.transpose(mRgba, mRgbaT);
        Imgproc.resize(mRgbaT, mRgbaF, mRgbaF.size(), 0,0, 0);
        Core.flip(mRgbaF, mRgba, 1 );


        Imgproc.blur(mRgba, mRgba, new Size(8,8));

        Object blue = new Object("blue", counter);
        Object green = new Object("green", counter);
        Object red = new Object("red", counter);

        Mat threshold = new Mat();
        Mat HSV = new Mat();

        Imgproc.cvtColor(mRgba,HSV,Imgproc.COLOR_RGB2HSV);

        Core.inRange(HSV, blue.HSVMin, blue.HSVMax, threshold);
        morphOps(threshold);
        trackFilteredObject(blue,threshold,HSV,mRgba, blue.type, counter);

        Imgproc.cvtColor(mRgba,HSV,Imgproc.COLOR_BGR2HSV);
        Core.inRange(HSV, green.HSVMin, green.HSVMax, threshold);
        morphOps(threshold);
        trackFilteredObject(green,threshold,HSV,mRgba, green.type, counter);

        Imgproc.cvtColor(mRgba,HSV,Imgproc.COLOR_BGR2HSV);
        Core.inRange(HSV, red.HSVMin, red.HSVMax, threshold);
        morphOps(threshold);
        trackFilteredObject(red,threshold,HSV,mRgba, red.type, counter);

        if(redGreenLine || blueGreenLine){
            Imgproc.line(mRgba, new Point(0, 150), new Point(500, 150), new Scalar(0, 255, 0), 2);
        }
        else if(!redGreenLine && !redGreenLine){
            Imgproc.line(mRgba, new Point(0, 150), new Point(500, 150), new Scalar(255, 0, 0), 2);
        }

        counter++;

        return mRgba; // This function must return
    }
    void morphOps(Mat thresh) {
        Imgproc.blur(thresh, thresh, new Size(10,10));
        Mat erodeElement = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2,2));
        Mat dilateElement = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(7,7));
        Imgproc.erode(thresh, thresh, erodeElement);
        Imgproc.dilate(thresh, thresh, dilateElement);
    }
    void trackFilteredObject(com.example.wiser.ILOSDataCollection.Object theObject, Mat threshold, Mat HSV, Mat cameraFeed, String colour, int counter) {
        ArrayList<com.example.wiser.ILOSDataCollection.Object> Objects = new ArrayList<>();
        Mat temp = new Mat();
        threshold.copyTo(temp);
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        boolean redFound = false;
        boolean blueFound = false;

        Imgproc.findContours(temp,contours,hierarchy,Imgproc.RETR_EXTERNAL,Imgproc.CHAIN_APPROX_SIMPLE);
        boolean objectFound = false;
        if (!hierarchy.empty()) {
            for (int i = 0; i >=0; i = (int)hierarchy.get(i, 0)[1]) {
                MatOfPoint contour = contours.get(i);
                Moments moment = Imgproc.moments(contour);
                double area = moment.m00;
                if(area > MIN_AREA) {
                    com.example.wiser.ILOSDataCollection.Object object = new com.example.wiser.ILOSDataCollection.Object(counter);

                    object.setXPos(moment.m10/area);
                    object.setYPos(moment.m01/area);
                    object.type = theObject.type;
                    object.colour = theObject.colour;
                    if(object.colour == new Scalar(255,0,0)){
                        redFound = true;
                    }
                    if(object.colour == new Scalar(0,255,0)){
                        blueFound = true;
                    }
                    Objects.add(object);

                    objectFound = true;

                }else {objectFound = false;}
            }
            if(!redFound){
                if(redStart==1) {
                    redStart = System.currentTimeMillis();
                }
                else{
                    redEnd = System.currentTimeMillis();
                }
                if(redStart!=1 && redEnd!=0) {
                    redGone = redEnd - redStart;
                    if(redGone > 300) {
                        System.out.println("RED OFF SCREEN: " + redGone);
                    }
                    redStart = 1;
                    redEnd = 0;
                }

            }
            if(!blueFound){
                if(blueStart==1) {
                    blueStart = System.currentTimeMillis();
                }
                else{
                    blueEnd = System.currentTimeMillis();
                }
                if(blueStart!=1 && blueEnd!=0) {
                    blueGone = blueEnd - blueStart;
                    if(blueGone > 300) {
                        System.out.println("BLUE OFF SCREEN" + blueGone);
                    }
                    blueStart = 1;
                    blueEnd = 0;
                }
            }

            if(objectFound == true && collectData) {
                drawObject(Objects, mRgba, temp, contours, hierarchy);
            }
            if(!collectData){
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        coordText.setText("Press back to select a new path!");
                    }
                });
             }
        }
    }
    void drawObject(ArrayList<com.example.wiser.ILOSDataCollection.Object> theObjects, Mat finalFrame, Mat temp, List<MatOfPoint> contours, Mat hierarchy) {
        boolean isBlue = false;
        boolean isRed = false;
        boolean isGreen = false;
        for (int i = 0; i < theObjects.size(); i++) {
            if(theObjects.get(i).colour.equals(new Scalar(255.0,0.0,0.0,0.0))) {
                redList.add(theObjects.get(i));
                if(theObjects.get(i).getYPos()<150){
                    redGreenLine = true;
                }
                else{
                    redGreenLine = false;
                }
                isRed = true;
            }
            if(theObjects.get(i).colour.equals(new Scalar(0.0,255.0,0.0,0.0))) {
                greenList.add(theObjects.get(i));
                isGreen = true;
            }
            if(theObjects.get(i).colour.equals(new Scalar(0.0,0.0,255.0,0.0))) {
                blueList.add(theObjects.get(i));
                if(theObjects.get(i).getYPos()<150){
                    blueGreenLine = true;
                }
                else{
                    blueGreenLine = false;
                }
                isBlue = true;
            }
            //For the following if statements, an index of two less than the length gives the last item added on
            //Checks that the next point drawn is within 50 pixels between then x and y distances
            if(isBlue && blueList.size() > 2) {
                if ((theObjects.get(i).getXPos() - blueList.get(blueList.size()-2).getXPos() < 50 && theObjects.get(i).getYPos() - blueList.get(blueList.size()-2).getYPos() < 50)) {
                    Imgproc.drawContours(mRgba, contours, i, theObjects.get(i).colour,3);
                    Imgproc.circle(mRgba, new Point(theObjects.get(i).getXPos(), theObjects.get(i).getYPos()), 3, theObjects.get(i).colour, 10);
                }
            }
            if(isRed && redList.size() > 2) {
                if ((theObjects.get(i).getXPos() - redList.get(redList.size()-2).getXPos() < 50 && theObjects.get(i).getYPos() - redList.get(redList.size()-2).getYPos() < 50)) {
                    Imgproc.drawContours(mRgba, contours, i, theObjects.get(i).colour,3);
                    Imgproc.circle(mRgba, new Point(theObjects.get(i).getXPos(), theObjects.get(i).getYPos()), 3, theObjects.get(i).colour, 10);
                }
            }
            if(isGreen && greenList.size() > 2) {
                if (theObjects.get(i).getYPos() - greenList.get(greenList.size()-2).getYPos() < 50) {
                    Imgproc.drawContours(mRgba, contours, i, theObjects.get(i).colour,3);
                    Imgproc.circle(mRgba, new Point(theObjects.get(i).getXPos(), theObjects.get(i).getYPos()), 3, theObjects.get(i).colour, 10);
                }
            }
        }
        if(redList.size()>1 && blueList.size()>1 && (redGreenLine || blueGreenLine) && collectData) {
            realTimeProcess();
            showText();
        }
        if (!redGreenLine) {
            steppedOnceRed = false;
        }
        if(!blueGreenLine){
            steppedOnceBlue = false;
        }
    }
    List<double[]> cmwa(List<double[]> points){
        List<double[]> outputList = new ArrayList<>();
        //output list tags the y values with the frame
        outputList.add(new double[]{(points.get(0)[0]+points.get(1)[0])/2, points.get(0)[1]});
        for (int i = 1; i < points.size() - 1; i++) {
            outputList.add(new double[]{(points.get(i-1)[0] + points.get(i)[0] + points.get(i+1)[0])/3, points.get(i)[1] });
        }
        outputList.add(new double[]{(points.get(points.size() - 1)[0] + points.get(points.size()-2)[0])/2, points.get(points.size()-1)[1]});
        return outputList;
    }
    int findMax(List<double[]> x){
        List<double[]> maxInfo = new ArrayList<>();
        boolean increasing  = false;
        double maxNum = x.get(0)[0];
        double maxFrame = 1;
        double slope = 0;
        for(int i = 0; i<x.size()-1;i++){
            System.out.println(x.get(i)[0]);
            if(x.get(i+1)[0] < x.get(i)[0]){
                increasing = true;
                slope = x.get(i+1)[0] - x.get(i)[0];
                maxNum = x.get(i+1)[0];
                maxFrame = x.get(i+1)[1];
            }
            else if(x.get(i+1)[0] > x.get(i)[0] && increasing && maxNum<150){
                System.out.println("PEAK: " + maxNum + "," + maxFrame);
                increasing = false;
                maxInfo.add(new double[]{maxNum, maxFrame});
                maxNum = 0;
                maxFrame = 0;
            }
        }
        //removes false peaks with close frame tag near each other
        for(int i = 0; i < maxInfo.size()-1;i++){
            if(maxInfo.get(i+1)[1] - maxInfo.get(i)[1] <=10){
                System.out.println("REMOVED " + maxInfo.get(i+1)[0]);
                maxInfo.remove(i+1);
            }
        }
        return maxInfo.size();
    }
    public void showText(){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(collectData) {
                    String redText;
                    String blueText;
                    redText = String.format("%.1f", (redList.get(redList.size() - 1).getXPos())) + "," + String.format("%.1f", (redList.get(redList.size() - 1).getYPos()));
                    blueText = String.format("%.1f", (blueList.get(blueList.size() - 1).getXPos())) + "," + String.format("%.1f", (blueList.get(blueList.size() - 1).getYPos()));
                    coordText.setText("Red Circle Coordinate: " +  redText+ "\n" + "Blue Circle Coordinates: " + blueText);
                    }
                }
        });
    }
    public void collectDataBtn(View v){
        clickCount++;
        if(clickCount%2 == 1 && !finishedCollection) {
            collectData = true;
        }
        else{
            finishedCollection = true;
            collectData = false;
        }
        /*
        if(clickCount%2 == 0 && clickCount>1 && redList.size() > 0 && blueList.size() > 0){
            //This is post processing of steps
            postProcessData();
        }
        */
    }
    void realTimeProcess(){
        //For red list
        if(redList.get(redList.size()-1).getYPos() < redList.get(redList.size()-2).getYPos() && !steppedOnceRed && redGreenLine){
            realTimeIncreaseRed = true;
        }
        else if(redList.get(redList.size()-1).getYPos() > redList.get(redList.size()-2).getYPos() && realTimeIncreaseRed && !steppedOnceRed && redGreenLine){
            stepCount++;
            steppedOnceRed =true;
            System.out.println("STEP: " + redList.get(redList.size()-1).getYPos());
            realTimeIncreaseRed = false;
        }
        //for blue list
        if(blueList.get(blueList.size()-1).getYPos() < blueList.get(blueList.size()-2).getYPos() && !steppedOnceBlue && blueGreenLine){
            realTimeIncreaseBlue = true;
        }
        else if(blueList.get(blueList.size()-1).getYPos() > blueList.get(blueList.size()-2).getYPos() && realTimeIncreaseBlue && !steppedOnceBlue && blueGreenLine){
            stepCount++;
            steppedOnceBlue = true;
            System.out.println("STEP: " + blueList.get(blueList.size()-1).getYPos());
            realTimeIncreaseBlue = false;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                stepText.setText(USER_NAME + "'s step length: " + String.format("%.3f", STEP_LENGTH) + "m" + "\n" + "Number of Steps: " + stepCount);
            }
        });
        Log.i("NUMBER OF STEPS", Double.toString(totalNumSteps));
        if(stepCount >= totalNumSteps){
            postProcessData();
        }
    }
    void postProcessData(){
        //Just working with blue marker
        collectData = false;
        blueNumSteps = 0;
        List<double[]> inputListBlue = new ArrayList<>();
        List<double[]> outputListBlue = new ArrayList<>();
        for(int i = 0; i < blueList.size();i++){
            inputListBlue.add(new double[]{blueList.get(i).getYPos(), blueList.get(i).frame});
        }
        //Reset to zero
        while(blueList.size()>0){
            blueList.remove(0);
        }
        //Reset to zero
        for (int i = 0; i < outputListBlue.size(); i++) {
            outputListBlue.remove(0);
        }


        outputListBlue = cmwa(inputListBlue);
        outputListBlue = cmwa(outputListBlue);
        outputListBlue = cmwa(outputListBlue);
        outputListBlue = cmwa(outputListBlue);
        outputListBlue = cmwa(outputListBlue);


        blueNumSteps = findMax(outputListBlue);

        //Reset to zero
        for (int i = 0; i < inputListBlue.size(); i++) {
            inputListBlue.remove(0);
        }

        //Process red steps
        redNumSteps = 0;
        List<double[]> outputListRed = new ArrayList<>();
        List<double[]> inputListRed = new ArrayList<>();

        for(int i = 0; i < redList.size();i++){
            inputListRed.add(new double[]{redList.get(i).getYPos(), redList.get(i).frame});
        }
        //Reset to zero
        while(redList.size()>0){
            redList.remove(0);
        }
        //Reset to zero
        for (int i = 0; i < outputListRed.size(); i++) {
            outputListRed.remove(0);
        }

        outputListRed = cmwa(inputListRed);
        outputListRed = cmwa(outputListRed);
        outputListRed = cmwa(outputListRed);
        outputListRed = cmwa(outputListRed);
        outputListRed = cmwa(outputListRed);

        redNumSteps = findMax(outputListRed);

        //Reset to zero
        for (int i = 0; i < inputListRed.size(); i++) {
            inputListRed.remove(0);
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                System.out.println("RED STEPS:"+ redNumSteps);
                System.out.println("BLUE STEPS:"+ blueNumSteps);
                if(Math.abs(blueNumSteps - redNumSteps) < 2 && Math.abs(stepCount - (blueNumSteps + redNumSteps))<2) {
                    stepText.setText(USER_NAME + "'s step length: " + String.format("%.3f", STEP_LENGTH) + "m" + "\n" + "Number of Steps: " + (blueNumSteps + redNumSteps) + "\n" + "Collection Complete!");
                }
                else{
                    stepText.setText(USER_NAME + "'s step length: " + String.format("%.3f", STEP_LENGTH) + "m"+"\n" + "Data Collection Error: Steps do not Match");
                }
            }
        });
    }
    void displayToast(){
        Toast.makeText(getBaseContext(), "Walk Such That Step Peaks Cross Line!", Toast.LENGTH_LONG).show();
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
        totalNumSteps = Math.round(totalNumSteps);
    }
    public void getStepProfile() {
        try {
            Toast.makeText(getBaseContext(), "Reading Data", Toast.LENGTH_LONG).show();
            //save data
            File sdCard = Environment.getExternalStorageDirectory();
            File directory = new File(sdCard.getAbsolutePath() + "/DataCollectProfiles");
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

}
