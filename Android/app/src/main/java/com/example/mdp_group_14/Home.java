package com.example.mdp_group_14;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.viewpager.widget.ViewPager;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;
import android.widget.FrameLayout;


import com.google.android.material.tabs.TabLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public class Home extends Fragment {

    final Handler handler = new Handler();
    // Declaration Variables
    private static SharedPreferences sharedPreferences;
    private static SharedPreferences.Editor editor;
    private static Context context;
    public static Handler timerHandler = new Handler();

    private static GridMap gridMap;
    static TextView xAxisTextView, yAxisTextView, directionAxisTextView;
    static TextView robotStatusTextView, bluetoothStatus, bluetoothDevice;
    static TextView planStatusTextView, resetStatusTextView;
    static ImageButton upBtn, downBtn, leftBtn, rightBtn,bleftBtn,brightBtn;

    BluetoothDevice mBTDevice;
    private static UUID myUUID;
    ProgressDialog myDialog;
    Bitmap bm, mapscalable;
    String obstacleID;

    private static final String TAG = "Main Activity";
    public static boolean stopTimerFlag = false;
    public static boolean stopWk9TimerFlag = false;

    public static boolean trackRobot = true;
    private static volatile boolean robotReady = false;

    public static boolean isRobotReady() {
        return robotReady;
}
    private int g_coordX;
    private int g_coordY;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // inflate
        View root = inflater.inflate(R.layout.home, container, false);
        View statusPanel = root.findViewById(R.id.homeRobotStatusPanel);
        registerStatusViews(root);

        // get shared preferences
        sharedPreferences = getActivity().getSharedPreferences("Shared Preferences",
                Context.MODE_PRIVATE);



        SectionsPagerAdapter sectionsPagerAdapter = new SectionsPagerAdapter(getActivity().getSupportFragmentManager(),
                FragmentPagerAdapter.BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);

        sectionsPagerAdapter.addFragment(new MappingFragment(),"MAP CONFIG");
        sectionsPagerAdapter.addFragment(new BluetoothCommunications(),"CHAT");
        sectionsPagerAdapter.addFragment(new ControlFragment(),"CHALLENGE");

        ViewPager viewPager = root.findViewById(R.id.view_pager);
        viewPager.setAdapter(sectionsPagerAdapter);
        viewPager.setOffscreenPageLimit(2);

        GridMap mapView = root.findViewById(R.id.mapView);
        LinearLayout mapArea = root.findViewById(R.id.map_area);
        FrameLayout tabArea = root.findViewById(R.id.tab_area);
        configureMapConfigLayout(mapArea, tabArea, true);
        viewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                configureMapConfigLayout(mapArea, tabArea, position == 0);
                statusPanel.setVisibility(position == 0 ? View.GONE : View.VISIBLE);
            }
        });


        TabLayout tabs = root.findViewById(R.id.tabs);
        tabs.setupWithViewPager(viewPager);



        LocalBroadcastManager
                .getInstance(getContext())
                .registerReceiver(messageReceiver, new IntentFilter("incomingMessage"));

        // Set up sharedPreferences
        Home.context = getContext();
        sharedPreferences();
        // Preserve state across configuration changes. Clearing these values here made
        // a simple rotation look like the Bluetooth session and chat had failed.
        if (!sharedPreferences.contains("direction")) editor.putString("direction", "None");
        if (!sharedPreferences.contains("connStatus")) editor.putString("connStatus", "Disconnected");

        editor.commit();

        // Map
        gridMap = mapView;
        if (robotStatusTextView != null) {
            robotStatusTextView.setText(sharedPreferences.getString("robotStatus", "Ready to Start"));
        }
        if (planStatusTextView != null) {
            planStatusTextView.setText(sharedPreferences.getString("planStatus", "WAITING"));
        }
        if (resetStatusTextView != null) {
            resetStatusTextView.setText(sharedPreferences.getString("resetStatus", "WAITING"));
        }

        // initialize ITEM_LIST and imageBearings strings
        for (int i = 0; i < 20; i++) {
            for (int j = 0; j < 20; j++) {
                gridMap.ITEM_LIST.get(i)[j] = "";
                GridMap.imageBearings.get(i)[j] = "";
            }
        }
        // Recover the last automatically saved map after a crash or relaunch.
        gridMap.restoreObstacleMap();

        myDialog = new ProgressDialog(getContext());
        myDialog.setMessage("Waiting for other device to reconnect...");
        myDialog.setCancelable(false);
        myDialog.setButton(
                DialogInterface.BUTTON_NEGATIVE,
                "Cancel",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                }
        );
        PathTranslator pathTranslator = new PathTranslator(gridMap);
//        pathTranslator.translatePath("MOVE,FORWARD,30");
        return root;
    }

    /**
     * Give Map Config a taller map while preserving the existing Chat and
     * Challenge proportions. GridMap recalculates its cell size from the new
     * measured dimensions, so taps and dragged obstacles stay aligned.
     */
    private void configureMapConfigLayout(LinearLayout mapArea, FrameLayout tabArea,
                                          boolean mapConfigSelected) {
        LinearLayout.LayoutParams mapParams = (LinearLayout.LayoutParams) mapArea.getLayoutParams();
        LinearLayout.LayoutParams tabParams = (LinearLayout.LayoutParams) tabArea.getLayoutParams();

        // Map Config gets the extra vertical room. Chat and Challenge retain the original split.
        mapParams.weight = mapConfigSelected ? 1.35f : 1.0f;
        tabParams.weight = mapConfigSelected ? 0.65f : 1.0f;

        mapArea.setLayoutParams(mapParams);
        tabArea.setLayoutParams(tabParams);
    }

    public static GridMap getGridMap() {
        return gridMap;
    }
    public static TextView getRobotStatusTextView() {  return robotStatusTextView; }

    /** Connects the live status model to the status card on the Bluetooth tab. */
    public static void registerStatusViews(View root) {
        bluetoothStatus = root.findViewById(R.id.bluetoothStatus);
        bluetoothDevice = root.findViewById(R.id.bluetoothConnectedDevice);
        robotStatusTextView = root.findViewById(R.id.robotStatus);
        planStatusTextView = root.findViewById(R.id.planStatus);
        resetStatusTextView = root.findViewById(R.id.resetStatus);
        xAxisTextView = root.findViewById(R.id.xAxisTextView);
        yAxisTextView = root.findViewById(R.id.yAxisTextView);
        directionAxisTextView = root.findViewById(R.id.directionAxisTextView);
        if (BluetoothConnectionService.BluetoothConnectionStatus && bluetoothStatus != null) {
            bluetoothStatus.setText("connected");
            bluetoothStatus.setTextColor(android.graphics.Color.GREEN);
        }
    }

    public static ImageButton getUpBtn() { return upBtn; }
    public static ImageButton getDownBtn() { return downBtn; }
    public static ImageButton getLeftBtn() { return leftBtn; }
    public static ImageButton getRightBtn() { return rightBtn; }

    public static ImageButton getbLeftBtn() { return bleftBtn; }
    public static ImageButton getbRightBtn() { return brightBtn; }


    public static TextView getBluetoothStatus() { return bluetoothStatus; }
    public static TextView getConnectedDevice() { return bluetoothDevice; }
    // For week 8 only
    public static boolean getTrackRobot() { return trackRobot; }
    public static void toggleTrackRobot() { trackRobot = !trackRobot; }

    public static void sharedPreferences() {
        sharedPreferences = Home.getSharedPreferences(Home.context);
        editor = sharedPreferences.edit();
    }

    private static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences("Shared Preferences", Context.MODE_PRIVATE);
    }

    // Send Coordinates to alg
    public static void printCoords(String message){
        showLog("Displaying Coords untranslated and translated");
        showLog(message);
        
        String[] strArr = message.split("_",2);


        //ensure strArr[1] exists and ends with newline character before sending
        if (strArr.length > 1) {

            if(!strArr[1].endsWith("\n")){
                strArr[1]+= "\n";
                showLog("Appended newline to strArr[1]"); //logs for Task C1
            }
        }



        // Translated ver is sent
        if (BluetoothConnectionService.BluetoothConnectionStatus == true){


            byte[] bytes = strArr[1].getBytes(Charset.defaultCharset());
            BluetoothConnectionService.write(bytes);
        }
        showLog("C1 strArr[0]: " + strArr[0]);
        // Display both untranslated and translated coordinates on CHAT (for debugging)
        refreshMessageReceivedNS("Untranslated Coordinates: " + strArr[0] + "\n");
        refreshMessageReceivedNS("Translated Coordinates: "+strArr[1]);
        showLog("Exiting printCoords");
    }

    // Send message to bluetooth (not shown on chat box)
    public static void printMessage(String message) {
        showLog("Entering printMessage");
        editor = sharedPreferences.edit();

        if (BluetoothConnectionService.BluetoothConnectionStatus) {


            if (!message.endsWith("\n")) {
                message += "\n";                  // NEW
                showLog("Appended newline to message"); // logs for task c1
            }





            byte[] bytes = message.getBytes(Charset.defaultCharset());
            BluetoothConnectionService.write(bytes);
            android.util.Log.d("BT_TX", java.util.Arrays.toString(bytes)); // for debugging android bluetooth transmission
        }
        showLog("C1 message: " + message);
        //showLog(message);


        showLog("Exiting printMessage");
    }

    // Send message to bluetooth (not shown on chat box)
    public static void printMessage(JSONArray message) {
        showLog("Entering printMessage");
        editor = sharedPreferences.edit();

    }


//        if (BluetoothConnectionService.BluetoothConnectionStatus) {
    ////            JSONObject jsonObj = message.getJSONObject("data");
//        JSONObject js=new JSONObject();
//        try {
//            JSONArray ja=new JSONArray();
//            js.put("key", "floor");
//            ja.put(js);
//            BluetoothConnectionService.write(ja);
//
//        }
//        catch (JSONException e) {
//            showLog("lol!");
//
//
//            //BluetoothConnectionService.write({"key":"test","value":"hello"});
//        }
//        //showLog(message);
//        showLog("Exiting printMessage");
//    }

    // Purely to display a message on the chat box - NOT SENT via BT
    public static void refreshMessageReceivedNS(String message){
        BluetoothCommunications.updateMessageLog(context, message);
    }

    public static void refreshMessageReceivedNS(int message){
        BluetoothCommunications.updateMessageLog(context, String.valueOf(message));
    }

    public static void refreshDirection(String direction) {
        // Note: this used to also echo a "ROBOT,x,y,DIR" line back over Bluetooth here, but the
        // bluetooth_bridge_node.cpp / task1_runner.py protocol has no tablet->robot ROBOT message
        // (ROBOT only ever flows robot->tablet), so that outbound send was removed.
        gridMap.setRobotDirection(direction);
        if (directionAxisTextView != null)
            directionAxisTextView.setText(sharedPreferences.getString("direction","")); //changes the UI direction display as well
    }

    public static void refreshLabel() {
        if (xAxisTextView != null) xAxisTextView.setText(String.valueOf(gridMap.getCurCoord()[0]-1));
        if (yAxisTextView != null) yAxisTextView.setText(String.valueOf(gridMap.getCurCoord()[1]-1));
        if (directionAxisTextView != null) directionAxisTextView.setText(sharedPreferences.getString("direction",""));
    }

    private static void showLog(String message) {
        Log.d(TAG, message);
    }

    private final BroadcastReceiver mBroadcastReceiver5 = new BroadcastReceiver() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @Override
        public void onReceive(Context context, Intent intent) {
            BluetoothDevice mDevice = intent.getParcelableExtra("Device");
            String status = intent.getStringExtra("Status");
            sharedPreferences();

            if(status.equals("connected")){
                try {
                    myDialog.dismiss();
                } catch(NullPointerException e){
                    e.printStackTrace();
                }

                Log.d(TAG, "mBroadcastReceiver5: Device now connected to "+mDevice.getName());
                updateStatus("Device now connected to "
                        + mDevice.getName());
                editor.putString("connStatus", "Connected to " + mDevice.getName());
            }
            else if(status.equals("disconnected")){
                Log.d(TAG, "mBroadcastReceiver5: Disconnected from "+mDevice.getName());
                updateStatus("Disconnected from "
                        + mDevice.getName());

                editor.putString("connStatus", "Disconnected");

                editor.commit();


                myDialog.show();
            }
            editor.commit();
        }
    };

    // Message handler (Receiving)
    // RPi relays the EXACT SAME stm commands sent by algo back to android: Starts with "Algo|"
    // RPi sends the image id as "TARGET~<obID>~<ImValue>"
    // Other specific strings are to clear checklist
    BroadcastReceiver messageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String message = intent.getStringExtra("receivedMessage");
            if (message == null) return;
            message = message.trim();
            if (message.isEmpty()) return;
            showLog("receivedMessage: message --- " + message);

//            if (message.contains(" "))
//            {
//                message= Arrays.toString(message.split(" "));
//            }
//            showLog("cmd1 --- " + cmdd[1]);
//            showLog("cmd2 --- " + cmdd[2]);


            int[] global_store = gridMap.getCurCoord();
            g_coordX = global_store[0];
            g_coordY = global_store[1];
            ArrayList<String> mapCoord = new ArrayList<>();

            // STATUS:<input>. Match the command prefix exactly: status text must not be parsed
            // as another command merely because it happens to contain one of these words.
            if (message.startsWith("STATUS:")) {
                String status = message.substring(7).trim();
                robotReady = "Ready".equalsIgnoreCase(status);
                if (robotStatusTextView != null) robotStatusTextView.setText(status);
                sharedPreferences.edit().putString("robotStatus", status).apply();
                if (isRunActiveStatus(status)) {
                    ControlFragment.startRpiTimer();
                } else if ("Finished".equalsIgnoreCase(status)
                        || "Stopped".equalsIgnoreCase(status)) {
                    ControlFragment.stopRpiTimer();
                }
                return;
            }
            //ROBOT,<x>,<y>,<N|E|S|W> from bluetooth_bridge_node.cpp / task1_runner.py
            if(message.startsWith("ROBOT,")) {
                String[] cmd = message.split(",", -1);
                if (cmd.length != 4) {
                    showLog("Malformed ROBOT line: " + message);
                    return;
                }
                int sentX;
                int sentY;
                char facing;
                try {
                    sentX = Integer.parseInt(cmd[1].trim());
                    sentY = Integer.parseInt(cmd[2].trim());
                    String facingField = cmd[3].trim();
                    if (facingField.length() != 1) throw new IllegalArgumentException("direction is not one letter");
                    facing = facingField.charAt(0);
                } catch (IllegalArgumentException e) {
                    showLog("Malformed ROBOT line: " + message);
                    return;
                }
                String direction;
                switch (facing) {
                    case 'E':
                        direction = "right";
                        break;
                    case 'N':
                        direction = "up";
                        break;
                    case 'W':
                        direction = "left";
                        break;
                    case 'S':
                        direction = "down";
                        break;
                    default:
                        direction = "";
                }
                if (direction.isEmpty()) {
                    showLog("Invalid ROBOT direction: " + cmd[3]);
                    return;
                }
                gridMap.setRobotBottomLeftCell(sentX, sentY, direction);
            }
            //image format from RPI is "TARGET~<obID>~<ImValue>" eg TARGET~3~7
            else if(message.startsWith("TARGET,")) {
                try {
                    String[] cmd = message.split(",", -1);
                    if (cmd.length != 3) throw new IllegalArgumentException("Malformed TARGET line");
                    int obstacleNumber = Integer.parseInt(cmd[1].trim());
                    BluetoothCommunications.addDetectedImage(context, message);

//                    if (cmd[2].contains("STOP"))
//                    {
//                        String temp=cmd[2];
//                        String[] temp1=temp.split(" ");
//                        temp2=temp1[0];
//
//                    }

                    gridMap.updateIDFromRpi(String.valueOf(obstacleNumber - 1), cmd[2].trim());
                    obstacleID = String.valueOf(obstacleNumber - 1);


//                    int ob= Integer.parseInt(obstacleID);

                }
                catch(Exception e)
                {
                    e.printStackTrace();
                }
            }
            else if(message.startsWith("ARROW,")){
                String[] cmd = message.split(",");
//                BluetoothCommunications.getMessageReceivedTextView().append("Obstacle no: " + cmd[1]+ "TARGET ID: " + cmd[2] + "\n");

                Home.refreshMessageReceivedNS("TASK2"+"\n");
                Home.refreshMessageReceivedNS("obstacle id: "+cmd[1]+", ARROW: "+cmd[2]);


//                updateStatus(cmd[0]+" "+ cmd[1]+" "+cmd[2]);
            }
            // OLD VER: Expects a syntax of e.g. Algo|f010. Commented out and implemented new version below
/*            if(message.contains("Algo")) {
                // translate the message after Algo|
                if(trackRobot)
                    pathTranslator.translatePath(message.split("\\|")[1]);
//                pathTranslator.altTranslation(message.split("\\|")[1]);   // last min addition - untested
            }*/

            //PLAN:<WAITING|PLANNING|DONE> from task1_runner.py
            else if(message.startsWith("PLAN:")) {
                String state = message.substring(5).trim();
                if (planStatusTextView != null) planStatusTextView.setText(state);
                sharedPreferences.edit().putString("planStatus", state).apply();
                Home.refreshMessageReceivedNS("PLAN: " + state);
            }
            //RESET:<WAITING|DONE> from task1_runner.py
            else if(message.startsWith("RESET:")) {
                String state = message.substring(6).trim();
                if (resetStatusTextView != null) resetStatusTextView.setText(state);
                sharedPreferences.edit().putString("resetStatus", state).apply();
                Home.refreshMessageReceivedNS("RESET: " + state);
            }
            else if (message.equalsIgnoreCase("START") || message.equalsIgnoreCase("BEGIN")) {
                ControlFragment.startRpiTimer();
                if (robotStatusTextView != null) robotStatusTextView.setText("Going");
            }
            else if(message.equalsIgnoreCase("STOP"))
            {
                Home.refreshMessageReceivedNS("STOP received");
                ControlFragment.stopRpiTimer();
                if (robotStatusTextView != null) robotStatusTextView.setText("Stopped");
            }
            else if (message.equalsIgnoreCase("RESET")) {
                ControlFragment.resetRpiTimer();
                if (robotStatusTextView != null) robotStatusTextView.setText("Reset");
            }
            else{
                BluetoothCommunications.updateMessageLog(context, "unknown message received");
                showLog("unknown message received");
            }
        }
    };

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data){
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode){
            case 1:
                if(resultCode == Activity.RESULT_OK){
                    mBTDevice = data.getExtras().getParcelable("mBTDevice");
                    myUUID = (UUID) data.getSerializableExtra("myUUID");
                }
        }
    }

    private static boolean isRunActiveStatus(String status) {
        String value = status.toLowerCase(java.util.Locale.ROOT);
        return value.startsWith("going") || value.startsWith("scanning");
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        try{
            LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(messageReceiver);
            LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mBroadcastReceiver5);
        } catch(IllegalArgumentException e){
            e.printStackTrace();
        }
    }

    @Override
    public void onPause(){
        super.onPause();
        try{
            LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mBroadcastReceiver5);
        } catch(IllegalArgumentException e){
            e.printStackTrace();
        }
    }

    @Override
    public void onResume(){
        super.onResume();
        try{
            IntentFilter filter2 = new IntentFilter("ConnectionStatus");
            LocalBroadcastManager.getInstance(getContext()).registerReceiver(mBroadcastReceiver5, filter2);
        } catch(IllegalArgumentException e){
            e.printStackTrace();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        showLog("Entering onSaveInstanceState");
        super.onSaveInstanceState(outState);

        outState.putString(TAG, "onSaveInstanceState");
        showLog("Exiting onSaveInstanceState");
    }
    private void updateStatus(String message) {
        Toast toast = Toast.makeText(getContext(), message, Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.TOP,0, 0);
        toast.show();
    }

}
