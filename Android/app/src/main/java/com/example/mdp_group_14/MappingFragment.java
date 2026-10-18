package com.example.mdp_group_14;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import java.util.Arrays;

public class MappingFragment extends Fragment {
    private static final String TAG = "MapFragment";

    SharedPreferences mapPref;
    private static SharedPreferences.Editor editor;

    Button updateButton;
    Button resetMapBtn, deleteObstacleBtn;
    ImageButton obstacleImageBtn;
    GridMap gridMap;

    Switch dragSwitch;

    static String imageID="";
    static String imageBearing="North";
    static String path="LL";
    static boolean dragStatus;

    String direction = "";
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.activity_map_config, container,  false);
        SharedPreferences sharedPreferences = getActivity().getSharedPreferences("Shared Preferences", Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();
        direction = sharedPreferences.getString("direction","");

        if (savedInstanceState != null)
            direction = savedInstanceState.getString("direction");
        gridMap = Home.getGridMap();
        final DirectionsFragment directionFragment = new DirectionsFragment();

        resetMapBtn = root.findViewById(R.id.resetBtn);
        deleteObstacleBtn = root.findViewById(R.id.deleteObstacleBtn);
        obstacleImageBtn = root.findViewById(R.id.addObstacleBtn);
//        updateButton = root.findViewById(R.id.updateMapBtn);
        dragSwitch = root.findViewById(R.id.dragSwitch);

        // Sync initial UI with gridMap state
        deleteObstacleBtn.setText(gridMap.getDeleteObstacleStatus() ? "Tap obstacle to delete" : "DELETE OBSTACLE");
        obstacleImageBtn.setBackgroundResource(gridMap.getSetObstacleStatus() ? R.drawable.border_black_pressed : R.drawable.border_black);
        dragSwitch.setChecked(dragStatus);

        resetMapBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showLog("Clicked resetMapBtn");
                new android.app.AlertDialog.Builder(requireContext())
                        .setTitle("Reset Map")
                        .setMessage("Are you sure you want to clear all obstacles?")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Clear", (dialog, which) -> {
                            gridMap.resetMap();
                            resetEditModes();
                        })
                        .show();

            }
        });

        deleteObstacleBtn.setOnClickListener(v -> {
            boolean nextState = !gridMap.getDeleteObstacleStatus();
            gridMap.setDeleteObstacleStatus(nextState);
            deleteObstacleBtn.setText(nextState ? "Tap obstacle to delete" : "DELETE OBSTACLE");

            if (nextState) {
                // Mutually exclusive: turn off Add Obstacle and Drag
                gridMap.setSetObstacleStatus(false);
                obstacleImageBtn.setBackgroundResource(R.drawable.border_black);
                if (dragSwitch.isChecked()) {
                    dragSwitch.setChecked(false);
                }
                dragStatus = false;
            }
        });

        // switch for dragging
        dragSwitch.setOnCheckedChangeListener( new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton toggleButton, boolean isChecked) {
                showToast("Dragging is " + (isChecked ? "on" : "off"));
                dragStatus = isChecked;
                if (dragStatus) {
                    // Mutually exclusive: turn off Add Obstacle and Delete Obstacle
                    gridMap.setSetObstacleStatus(false);
                    obstacleImageBtn.setBackgroundResource(R.drawable.border_black);
                    gridMap.setDeleteObstacleStatus(false);
                    deleteObstacleBtn.setText("DELETE OBSTACLE");
                }
            }
        });

        /* load action removed */
        /*
        loadMapObstacle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showLog("Clicked loadMapObstacle");
                mapPref = getContext().getSharedPreferences("Shared Preferences", Context.MODE_PRIVATE);
                String obsPos = mapPref.getString("maps","");
                if(!obsPos.equals("")){

                    String[] obstaclePosition = obsPos.split("\n");
                    for (String s : obstaclePosition) {

                        String[] coords = s.split(",");
//                        BluetoothCommunications.getMessageReceivedTextView().append(Arrays.toString(coords));

//                        String direction2 = "";
//                        switch (coords[2]) {
//                            case "N":
//                                direction2 = "NORTH";
//                                break;
//                            case "E":
//                                direction2 = "EAST";
//                                break;
//                            case "W":
//                                direction2 = "WEST";
//                                break;
//                            case "S":
//                                direction2 = "SOUTH";
//                                break;
//                            default:
//                                direction2 = "null";
//                        }
//
////                        BluetoothCommunications.getMessageReceivedTextView().append(coords[0]);
////                        BluetoothCommunications.getMessageReceivedTextView().append(coords[1]);
////                        BluetoothCommunications.getMessageReceivedTextView().append(direction2);
//
//                        gridMap.imageBearings.get(Integer.parseInt(coords[1]))[Integer.parseInt(coords[0])] = direction2;



//                        gridMap.setObstacleCoord(Integer.parseInt(coords[0]) + 1, Integer.parseInt(coords[1]) + 1, "","");
                        String direction = "";
                        switch (coords[2]) {
                            case "N":
                                direction = "North";
                                break;
                            case "E":
                                direction = "East";
                                break;
                            case "W":
                                direction = "West";
                                break;
                            case "S":
                                direction = "South";
                                break;
                            default:
                                direction = "";
                        }
                        gridMap.imageBearings.get(Integer.parseInt(coords[1]))[Integer.parseInt(coords[0])] = direction;
                        gridMap.setObstacleCoord(Integer.parseInt(coords[0]) + 1, Integer.parseInt(coords[1]) + 1);
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    gridMap.invalidate();
                    showLog("Exiting Load Button");
                    showToast("Loaded saved map");
                }
                showToast("Empty saved map!");
            }
        }); */

        // To place obstacles
        obstacleImageBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showLog("Clicked obstacleImageBtn");

                if (!gridMap.getSetObstacleStatus()) {  // if setObstacleStatus is false
                    showToast("Please plot obstacles");
                    gridMap.setSetObstacleStatus(true);
                    gridMap.toggleCheckedBtn("obstacleImageBtn");
                    obstacleImageBtn.setBackgroundResource(R.drawable.border_black_pressed);

                    // Mutually exclusive: turn off Delete Obstacle and Drag
                    gridMap.setDeleteObstacleStatus(false);
                    deleteObstacleBtn.setText("DELETE OBSTACLE");
                    dragSwitch.setChecked(false);
                    dragStatus = false;
                }
                else if (gridMap.getSetObstacleStatus()) {  // if setObstacleStatus is true
                    gridMap.setSetObstacleStatus(false);
                    obstacleImageBtn.setBackgroundResource(R.drawable.border_black);
                }
                showLog("obstacle status = " + gridMap.getSetObstacleStatus());
                showLog("Exiting obstacleImageBtn");
            }
        });

        //preload defaults button
//        updateButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                showLog("Clicked updateButton");
//
//                gridMap.imageBearings.get(9)[5] = "South";
//                gridMap.imageBearings.get(15)[15] = "South";
//                gridMap.imageBearings.get(14)[7] = "West";
//                gridMap.imageBearings.get(4)[15] = "West";
//                gridMap.imageBearings.get(9)[12] = "East";
//                gridMap.setObstacleCoord(5+1, 9+1);
//                gridMap.setObstacleCoord(15+1, 15+1);
//                gridMap.setObstacleCoord(7+1, 14+1);
//                gridMap.setObstacleCoord(15+1, 4+1);
//                gridMap.setObstacleCoord(12+1, 9+1);
//                gridMap.invalidate();
//                updateStatus("i say dont click right why u still click????");
//                showLog("Exiting updateButton");
//            }
//        });
        return root;
    }

    private void resetEditModes() {
        if (gridMap != null) {
            gridMap.setSetObstacleStatus(false);
            gridMap.setDeleteObstacleStatus(false);
        }
        if (obstacleImageBtn != null) {
            obstacleImageBtn.setBackgroundResource(R.drawable.border_black);
        }
        if (deleteObstacleBtn != null) {
            deleteObstacleBtn.setText("DELETE OBSTACLE");
        }
        if (dragSwitch != null) {
            dragSwitch.setChecked(false);
        }
        dragStatus = false;
    }

    private void showLog(String message) {
        Log.d(TAG, message);
    }

    private void showToast(String message) {
        // Toasts intentionally disabled for faster repeated map editing.
    }
    private void updateStatus(String message) {
        // Status feedback is kept in the UI instead of transient Toasts.
    }
}
