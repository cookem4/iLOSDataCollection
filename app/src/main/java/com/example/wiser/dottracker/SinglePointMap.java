package com.example.wiser.dottracker;

import android.content.Intent;
import android.graphics.Color;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;

import static com.mapbox.mapboxsdk.style.layers.Property.NONE;
import static com.mapbox.mapboxsdk.style.layers.Property.VISIBLE;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.visibility;

public class SinglePointMap extends AppCompatActivity {

    int currentFloor = 1;
    LatLng collectionPoint;
    MapView mapView;
    int mapClickCount = 0;
    EditText buildingName;
    LinearLayout buildingLinLay;
    MapboxMap map;
    public final static String MAPBOX_LAYER_STRING = "layer";
    public final static String MAPBOX_ROOM_STRING = "rooms";
    public final static String MAPBOX_LABELS_STRING = "labels";
    public final static String MAPBOX_ELEVATOR = "elevator";
    public final static int MAPBOX_LAYER_CHOICE_ROOM = 0;
    public final static int MAPBOX_LAYER_CHOICE_LABELS = 1;
    public final static int MAPBOX_LAYER_CHOICE_FILL = 2;
    public final static int MAPBOX_LAYER_CHOICE_STAIR = 3;
    public final static int MAPBOX_LAYER_CHOICE_WASHROOM = 4;
    public final static int MAPBOX_LAYER_CHOICE_ELEVATOR = 5;
    public final static String MAPBOX_FILL_STRING = "fill";
    public final static String MAPBOX_WASHROOM = "washroom";
    public final static String MAPBOX_STAIRCASE = "staircase";
    public boolean isCheckBoxChecked = false;
    public boolean serverUpload = false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_single_point_map);
        CheckBox checkBox = (CheckBox)findViewById(R.id.serverCheckBox);
        checkBox.setBackgroundColor(Color.WHITE);
        checkBox.setAlpha((float)0.8);
        buildingName = (EditText)findViewById(R.id.buildingText);
        buildingLinLay = (LinearLayout)findViewById(R.id.buildingLinLays);
        buildingLinLay.setBackgroundColor(Color.WHITE);
        buildingLinLay.setAlpha((float)0.8);
        Mapbox.getInstance(this, "pk.eyJ1Ijoid2lzZXIiLCJhIjoiY2o1ODUyNjZuMTR5cjJxdGc1em9qaWV4YyJ9.x-VFKxrs3xr7Zy8NnpKs6A");
        mapView = (MapView) findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(MapboxMap mapboxMap) {
                map = mapboxMap;
                drawCollectedPoints();
                mapboxMap.addOnMapLongClickListener(new MapboxMap.OnMapLongClickListener() {
                    @Override
                    public void onMapLongClick(@NonNull LatLng point) {
                        mapClickCount++;
                        if(mapClickCount == 1) {
                            collectionPoint = point;
                            mapboxMap.addMarker(new MarkerOptions()
                                    .position(new LatLng(point.getLatitude(), point.getLongitude()))
                                    .title("Collection Point")
                                    .snippet(Double.toString(point.getLatitude()) + "," + Double.toString(point.getLongitude())));
                        }
                    }
                });
            }
        });
    }
    public void increaseMapBoxLayer(View v){
        if(currentFloor < 8) {
            currentFloor++;
            map.clear();
            drawCollectedPoints();
            mapClickCount = 0;
            loopLayers();
        }
    }
    public void decreaseMapBoxLayer(View v){
        if(currentFloor < 8) {
            currentFloor--;
            map.clear();
            drawCollectedPoints();
            mapClickCount = 0;
            loopLayers();
        }
    }
    void loopLayers(){
        for (int i = -1; i < 8; i++) {
            if (i == currentFloor) {
                map.getLayer(getLayerName(MAPBOX_LAYER_CHOICE_FILL, currentFloor)).setProperties(visibility(VISIBLE));
                map.getLayer(getLayerName(MAPBOX_LAYER_CHOICE_ROOM, currentFloor)).setProperties(visibility(VISIBLE));
                map.getLayer(getLayerName(MAPBOX_LAYER_CHOICE_LABELS, currentFloor)).setProperties(visibility(VISIBLE));
                map.getLayer(getLayerName(MAPBOX_LAYER_CHOICE_WASHROOM, currentFloor)).setProperties(visibility(VISIBLE));
                map.getLayer(getLayerName(MAPBOX_LAYER_CHOICE_STAIR, currentFloor)).setProperties(visibility(VISIBLE));
                map.getLayer(getLayerName(MAPBOX_LAYER_CHOICE_ELEVATOR, currentFloor)).setProperties(visibility(VISIBLE));
            } else {
                map.getLayer(getLayerName(MAPBOX_LAYER_CHOICE_FILL, i)).setProperties(visibility(NONE));
                map.getLayer(getLayerName(MAPBOX_LAYER_CHOICE_ROOM, i)).setProperties(visibility(NONE));
                map.getLayer(getLayerName(MAPBOX_LAYER_CHOICE_LABELS, i)).setProperties(visibility(NONE));
                map.getLayer(getLayerName(MAPBOX_LAYER_CHOICE_WASHROOM, i)).setProperties(visibility(NONE));
                map.getLayer(getLayerName(MAPBOX_LAYER_CHOICE_STAIR, i)).setProperties(visibility(NONE));
                map.getLayer(getLayerName(MAPBOX_LAYER_CHOICE_ELEVATOR, i)).setProperties(visibility(NONE));
            }
        }
    }
    String getLayerName(int choice, int floor){
        switch (choice) {
            case MAPBOX_LAYER_CHOICE_ROOM:
                return MAPBOX_LAYER_STRING + floor + MAPBOX_ROOM_STRING;
            case MAPBOX_LAYER_CHOICE_LABELS:
                return MAPBOX_LAYER_STRING + floor + MAPBOX_LABELS_STRING;
            case MAPBOX_LAYER_CHOICE_FILL:
                return MAPBOX_LAYER_STRING + floor + MAPBOX_FILL_STRING;
            case MAPBOX_LAYER_CHOICE_WASHROOM:
                return MAPBOX_LAYER_STRING + floor + MAPBOX_WASHROOM;
            case MAPBOX_LAYER_CHOICE_STAIR:
                return MAPBOX_LAYER_STRING + floor + MAPBOX_STAIRCASE;
            case MAPBOX_LAYER_CHOICE_ELEVATOR:
                return MAPBOX_LAYER_STRING + floor + MAPBOX_ELEVATOR;
            default:
                return MAPBOX_LAYER_STRING + "1" + MAPBOX_ROOM_STRING;
        }
    }
    public void goToCollection(View v){
        //TODO CLEAN UP THESE IF STATEMENTS
        if(collectionPoint!=null && buildingName.getText().toString().length() != 0) {
            Intent Intent = new Intent(this, SinglePointUtils.class);
            SinglePointUtils.collectionLat = collectionPoint.getLatitude();
            SinglePointUtils.collectionLong = collectionPoint.getLongitude();
            SinglePointUtils.FLOOR_NUMBER = currentFloor;
            SinglePointUtils.BUILDING_NAME = buildingName.getText().toString();
            SinglePointUtils.doServerUpload = serverUpload;
            startActivity(Intent);
        }
        else if (collectionPoint==null){
            Toast.makeText(getBaseContext(), "Please select collection point", Toast.LENGTH_LONG).show();
        } else if (buildingName.getText().toString().length() == 0) {
            Toast.makeText(getBaseContext(), "Please input a building", Toast.LENGTH_LONG).show();
        }
    }
    public void clearMarkers(View v){
        mapClickCount = 0;
        map.clear();
        drawCollectedPoints();
    }
    void drawCollectedPoints(){
        RetrieveCollectedPoints allPoints = new RetrieveCollectedPoints();
        for (int i = 0; i < allPoints.pointList.size(); i++){
            if(allPoints.taggedFloors.get(i) == currentFloor) {
                map.addMarker(new MarkerOptions()
                        .position(allPoints.pointList.get(i))
                        .title("Previous Collection")
                        .snippet(Double.toString(allPoints.pointList.get(i).getLatitude()) + "," + Double.toString(allPoints.pointList.get(i).getLongitude())));
            }
        }
    }
    public void enableServerUpload(View v){
        if(!isCheckBoxChecked){
            serverUpload = true;
            isCheckBoxChecked = true;
        }
        else{
            serverUpload = false;
            isCheckBoxChecked = false;
        }
    }
    @Override
    public void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }
}
