package com.example.wiser.ILOSDataCollection;

import android.os.Environment;
import android.util.Log;

import com.mapbox.mapboxsdk.annotations.Polyline;
import com.mapbox.mapboxsdk.annotations.PolylineOptions;
import com.mapbox.mapboxsdk.geometry.LatLng;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class RetrieveCollectedPaths {
    //Note that odd numbered entries will be starting points and even numbered entries will be end points
    List<LatLng> pointList = new ArrayList<>();
    List<Integer> taggedFloors = new ArrayList<>();
    //Poly line list for drawing paths
    List<PolylineOptions> polyLinesList = new ArrayList<>();
    RetrieveCollectedPaths(){
        getPoints();
    }
    void getPoints(){
        List<String> fileNames = new ArrayList<>();
        File sdCard = Environment.getExternalStorageDirectory();
        File[] directory = new File(sdCard.getAbsolutePath() + "/DataCollect").listFiles();
        //Adds all file names to a list
        for(int i =0; i < directory.length;i++){
            if(directory[i].isFile()){
                fileNames.add(directory[i].toString());
            }
        }
        //File names are formatted: BUILDING(startLat,startLon)to(endLat,endLon).txt
        for(int i = 0; i < fileNames.size();i++){
            String startPoint;
            String endPoint;
            int openBracket = fileNames.get(i).indexOf("(");
            int closeBracket = fileNames.get(i).indexOf(")");
            startPoint = fileNames.get(i).substring(openBracket+1, closeBracket);
            endPoint = fileNames.get(i).substring(closeBracket+4, fileNames.get(i).length()-5);
            //0th index is lat, 1st index is lon
            String[] start = startPoint.split(",");
            String[] end = endPoint.split(",");
            taggedFloors.add(Integer.parseInt(Character.toString(fileNames.get(i).charAt(openBracket-1))));
            taggedFloors.add(Integer.parseInt(Character.toString(fileNames.get(i).charAt(openBracket-1))));
            pointList.add(new LatLng(Double.parseDouble(start[0]), Double.parseDouble(start[1])));
            pointList.add(new LatLng(Double.parseDouble(end[0]), Double.parseDouble(end[1])));
            //A single polyItem is added to two different numbered entries corresponding to the start and ending numbers
            PolylineOptions polyItem = new PolylineOptions();
            polyLinesList.add(polyItem);
            polyLinesList.add(polyItem);
        }
    }
}
