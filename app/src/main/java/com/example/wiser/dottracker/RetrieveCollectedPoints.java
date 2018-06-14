package com.example.wiser.dottracker;

import android.os.Environment;
import android.util.Log;

import com.mapbox.mapboxsdk.geometry.LatLng;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class RetrieveCollectedPoints {
    //Note that odd numbered entries will be starting points and even numbered entries will be end points
    List<LatLng> pointList = new ArrayList<>();
    List<Integer> taggedFloors = new ArrayList<>();

    RetrieveCollectedPoints(){
        getPoints();
    }
    void getPoints(){
        List<String> fileNames = new ArrayList<>();
        File sdCard = Environment.getExternalStorageDirectory();
        File[] directory = new File(sdCard.getAbsolutePath() + "/DataCollectSinglePoint").listFiles();
        //Adds all file names to a list
        for(int i =0; i < directory.length;i++){
            if(directory[i].isFile()){
                fileNames.add(directory[i].toString());
            }
        }
        //File names are formatted: BUILDING(lat,lon).txt
        for(int i = 0; i < fileNames.size();i++){
            String point;
            int openBracket = fileNames.get(i).indexOf("(");
            int closeBracket = fileNames.get(i).indexOf(")");
            point = fileNames.get(i).substring(openBracket+1, closeBracket);
            //0th index is lat, 1st index is lon
            String[] latAndLon = point.split(",");
            taggedFloors.add(Integer.parseInt(Character.toString(fileNames.get(i).charAt(openBracket-1))));
            pointList.add(new LatLng(Double.parseDouble(latAndLon[0]), Double.parseDouble(latAndLon[1])));
        }
    }
}
