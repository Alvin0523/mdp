package com.example.mdp_group_14;

import android.Manifest;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.viewpager.widget.ViewPager;

import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;


public class BluetoothSetUp extends Fragment {

    private static final String TAG = "Bluetooth Debug";

    private String connStatus;
    BluetoothAdapter mBluetoothAdapter;
    public ArrayList<BluetoothDevice> mNewBTDevices = new ArrayList<>();
    public ArrayList<BluetoothDevice> mPairedBTDevices = new ArrayList<>();
    public DeviceListAdapter mNewDeviceListAdapter;
    public DeviceListAdapter mPairedDeviceListAdapter;
    public static TextView connStatusTextView;
    ListView lvNewDevices;  // otherDevicesListView
    ListView lvPairedDevices;   // pairedDevicesListView
    Button connectBtn;
    Button btnSearch;
    ProgressDialog myDialog;

    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_LOCATION_EXTRA_COMMANDS,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
    };
    private static String[] PERMISSIONS_LOCATION = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_LOCATION_EXTRA_COMMANDS,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
    };

    SharedPreferences sharedPreferences;
    SharedPreferences.Editor editor;

    BluetoothConnectionService mBluetoothConnection;
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    public static BluetoothDevice mBTDevice;

    boolean retryConnection = false;
    Handler reconnectionHandler = new Handler();
    Context mContext;
//    public BluetoothSetUp(Context context) {
//
//        this.mContext = context;
//
//    }
    Runnable reconnectionRunnable = new Runnable() {
        @Override
        public void run() {
            // Magic here
            try {
                if (BluetoothConnectionService.BluetoothConnectionStatus == false) {
                    showLog("Reconnecting...");
                    startBTConnection(mBTDevice, MY_UUID);
                    updateStatus("Reconnection Success");

                }
                reconnectionHandler.removeCallbacks(reconnectionRunnable);
                retryConnection = false;
            } catch (Exception e) {
                showLog("Reconnection failed");
                e.printStackTrace();
                updateStatus("Failed to reconnect, trying in 5 second");
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        // inflate
        View root = inflater.inflate(R.layout.bluetooth, container, false);
        // get shared preferences
        sharedPreferences = getActivity().getSharedPreferences("Shared Preferences",
                Context.MODE_PRIVATE);


        DisplayMetrics dm = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(dm);

        lvNewDevices = root.findViewById(R.id.otherDevicesListView);
        lvPairedDevices = root.findViewById(R.id.pairedDevicesListView);
        mNewBTDevices = new ArrayList<>();
        mPairedBTDevices = new ArrayList<>();

        connectBtn = root.findViewById(R.id.connectBtn);
        btnSearch = root.findViewById(R.id.scanButton);

        int width = dm.widthPixels;
        int height = dm.heightPixels;

        // Get bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        getActivity().registerReceiver(mBroadcastReceiver4, filter);

        IntentFilter filter2 = new IntentFilter("ConnectionStatus");
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mBroadcastReceiver5, filter2);

//        checkBTPermissions(); // might help with the 1st time crashing when clicking 'Scan'

        lvNewDevices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @RequiresPermission(allOf = {Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT})
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if (!canUseBluetooth()) return;
                cancelDiscoverySafely();
                if (i < 0 || i >= mNewBTDevices.size()) return;
                lvPairedDevices.setAdapter(mPairedDeviceListAdapter);

                BluetoothDevice selectedDevice = mNewBTDevices.get(i);
                if (selectedDevice == null) return;
                String deviceName = safeDeviceName(selectedDevice);
                String deviceAddress = safeDeviceAddress(selectedDevice);
                Log.d(TAG, "onItemClick: A device is selected.");
                Log.d(TAG, "onItemClick: DEVICE NAME: " + deviceName);
                Log.d(TAG, "onItemClick: DEVICE ADDRESS: " + deviceAddress);

                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    Log.d(TAG, "onItemClick: Initiating pairing with " + deviceName);
//                    mNewBTDevices.get(i).createBond();
                    mBTDevice = selectedDevice;
                    updateStatus("Pair this device in Android Settings first, then pick it from the paired list");
                    mBluetoothConnection = BluetoothConnectionService.getInstance(requireContext());
                    mBTDevice = selectedDevice;
                }
            }
        });

        lvPairedDevices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @RequiresPermission(allOf = {Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT})
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if (!canUseBluetooth()) return;
                cancelDiscoverySafely();
                if (i < 0 || i >= mPairedBTDevices.size()) return;
                lvNewDevices.setAdapter(mNewDeviceListAdapter);

                BluetoothDevice selectedDevice = mPairedBTDevices.get(i);
                if (selectedDevice == null) return;
                String deviceName = safeDeviceName(selectedDevice);
                String deviceAddress = safeDeviceAddress(selectedDevice);
                Log.d(TAG, "onItemClick: A device is selected.");
                Log.d(TAG, "onItemClick: DEVICE NAME: " + deviceName);
                Log.d(TAG, "onItemClick: DEVICE ADDRESS: " + deviceAddress);

                mBluetoothConnection = BluetoothConnectionService.getInstance(requireContext());
                mBTDevice = selectedDevice;
            }
        });

        connectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(mBTDevice ==null)
                {
                    updateStatus("Please Select a Device before connecting.");
                }
                else {
                    startConnection();
                }
            }
        });
        //ONCLICKLISTENER FOR SEARCH BUTTON
        btnSearch.setOnClickListener(new View.OnClickListener() {
            @RequiresPermission(allOf = {Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT})
            public void onClick(View view) {
                Log.d(TAG, "onClick: search button");
                if (mBluetoothAdapter == null) {
                    updateStatus("Bluetooth is not supported on this device");
                    return;
                }
                toggleButtonScan(view);
            }
        });

        Button backBtn = root.findViewById(R.id.backBtn);

        connStatusTextView = root.findViewById(R.id.connStatusTextView);
        connStatus ="Disconnected";
        sharedPreferences = getActivity().getSharedPreferences("Shared Preferences", Context.MODE_PRIVATE);
        if (sharedPreferences.contains("connStatus"))
            connStatus = sharedPreferences.getString("connStatus", "");
        if (mBluetoothAdapter == null)
            connStatus = "Bluetooth not supported on this device";

        connStatusTextView.setText(connStatus);

        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editor = sharedPreferences.edit();
                editor.putString("connStatus", connStatusTextView.getText().toString());
                editor.commit();
                // This SF-24 logo is the Bluetooth screen's back button. Return
                // to Home without destroying the activity or Bluetooth service.
                ViewPager viewPager = requireActivity().findViewById(R.id.view_pager2);
                if (viewPager != null) viewPager.setCurrentItem(0, true);
            }
        });

        myDialog = new ProgressDialog(getContext());
        myDialog.setMessage("Waiting for other device to reconnect...");
        myDialog.setCancelable(false);
        myDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });









        return root;
    }

//
//    private void checkBTPermissions() {
//        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP){
//            int permissionCheck = 0;
//            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
//                permissionCheck = this.checkSelfPermission("Manifest.permission.ACCESS_FINE_LOCATION");
//            }
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//                permissionCheck += this.checkSelfPermission("Manifest.permission.ACCESS_COARSE_LOCATION");
//            }
//            if (permissionCheck != 0) {
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//                    this.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1001);
//                }
//            }
//        } else {
//            Log.d(TAG, "checkBTPermissions: No need to check permissions. SDK version < LOLLIPOP.");
//        }
//    }

                             private void checkBTPermissions(){
        int permission1 = ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int permission2 = ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.BLUETOOTH_SCAN);
        if (permission1 != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    getActivity(),
                    PERMISSIONS_STORAGE,
                    1
            );
        } else if (permission2 != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(
                    getActivity(),
                    PERMISSIONS_LOCATION,
                    1
            );
        }
    }

    @RequiresPermission(allOf = {Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT})
    public void Scanning() {
        Log.d(TAG, "toggleButton: Scanning for unpaired devices.");
        if (!canUseBluetooth()) return;
        mNewBTDevices.clear();
        if (mBluetoothAdapter != null) {
            if (!mBluetoothAdapter.isEnabled()) {
                updateStatus( "Please turn on Bluetooth first!");
                return;
            }
            //If discovering, cancel discovery and start again
            if (mBluetoothAdapter.isDiscovering()) {
                mBluetoothAdapter.cancelDiscovery();
//                checkBTPermissions();

                mBluetoothAdapter.startDiscovery();
                IntentFilter discoverDevicesIntent = new IntentFilter(BluetoothDevice.ACTION_FOUND);
                getActivity().registerReceiver(mBroadcastReceiver3, discoverDevicesIntent);
            }
            // If not discovering, start discovery
            else if (!mBluetoothAdapter.isDiscovering()) {
//                checkBTPermissions();

                mBluetoothAdapter.startDiscovery();
                IntentFilter discoverDevicesIntent = new IntentFilter(BluetoothDevice.ACTION_FOUND);
                getActivity().registerReceiver(mBroadcastReceiver3, discoverDevicesIntent);
            }
            mPairedBTDevices.clear();
            Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
            Log.d(TAG, "toggleButton: Number of paired devices found: "+ pairedDevices.size());
            for(BluetoothDevice d : pairedDevices){
                Log.d(TAG, "Paired Devices: "+ d.getName() +" : " + d.getAddress());
                mPairedBTDevices.add(d);
                mPairedDeviceListAdapter = new DeviceListAdapter(getContext(), R.layout.device_adapter_view, mPairedBTDevices);
                lvPairedDevices.setAdapter(mPairedDeviceListAdapter);
            }
        }
    }

    private boolean canUseBluetooth() {
        if (mBluetoothAdapter == null) {
            updateStatus("Bluetooth is not supported on this device");
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED)) {
            updateStatus("Bluetooth permission is required");
            checkBTPermissions();
            return false;
        }
        return true;
    }

    private void cancelDiscoverySafely() {
        try {
            if (mBluetoothAdapter != null && mBluetoothAdapter.isDiscovering()) {
                mBluetoothAdapter.cancelDiscovery();
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Unable to stop Bluetooth discovery", e);
        }
    }

    private String safeDeviceName(BluetoothDevice device) {
        try {
            String name = device.getName();
            return name == null || name.trim().isEmpty() ? "Unknown device" : name;
        } catch (SecurityException e) {
            return "Unknown device";
        }
    }

    private String safeDeviceAddress(BluetoothDevice device) {
        try {
            return device.getAddress();
        } catch (SecurityException e) {
            return "Address unavailable";
        }
    }

    @RequiresPermission(allOf = {Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT})
    public void toggleButtonScan(View view){
        Scanning();
    }

    private final BroadcastReceiver mBroadcastReceiver1 = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(mBluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,mBluetoothAdapter.ERROR);
                //Turning on or off Bluetooth on Device
                switch(state){
                    case BluetoothAdapter.STATE_OFF:
                        Log.d(TAG, "onReceive: STATE OFF");
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        Log.d(TAG, "mBroadcastReceiver1: onReceive: STATE OFF");
                        break;
                    case BluetoothAdapter.STATE_ON:
                        Log.d(TAG, "onReceive: STATE ON");
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        Log.d(TAG, "mBroadcastReceiver1: onReceive: STATE ON");
                        break;
                }
            }
        }
    };

    private final BroadcastReceiver mBroadcastReceiver2 = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)) {
                final int mode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.ERROR);

                switch (mode) {
                    //Device is in discoverable mode. i.e. other devices can find this device.
                    case BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE:
                        Log.d(TAG, "mBroadcastReceiver2: Discoverability Enabled.");
                        break;
                    //Device is not discoverable.
                    case BluetoothAdapter.SCAN_MODE_CONNECTABLE:
                        Log.d(TAG, "mBroadcastReceiver2: Discoverability Disabled. Able to receive connections.");
                        break;
                    case BluetoothAdapter.SCAN_MODE_NONE:
                        Log.d(TAG, "mBroadcastReceiver2: Discoverability Disabled. Not able to receive connections.");
                        break;
                    case BluetoothAdapter.STATE_CONNECTING:
                        Log.d(TAG, "mBroadcastReceiver2: Connecting...");
                        break;
                    case BluetoothAdapter.STATE_CONNECTED:
                        Log.d(TAG, "mBroadcastReceiver2: Connected.");
                        break;
                }
            }
        }
    };

        private final BroadcastReceiver mBroadcastReceiver3 = new BroadcastReceiver() {
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                Log.d(TAG, "onReceive: ACTION FOUND.");

                if(action.equals(BluetoothDevice.ACTION_FOUND)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if(device != null && device.getBondState()!=BluetoothDevice.BOND_BONDED) {
                        mNewBTDevices.add(device);
                        Log.d(TAG, "onReceive: " + safeDeviceName(device) + " : " + safeDeviceAddress(device));
                        mNewDeviceListAdapter = new DeviceListAdapter(context, R.layout.device_adapter_view, mNewBTDevices);
                        lvNewDevices.setAdapter(mNewDeviceListAdapter);
                    }
                }
            }
        };

    private final BroadcastReceiver mBroadcastReceiver4 = new BroadcastReceiver() {
        @RequiresPermission(allOf = {Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT})
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if(action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)){
                BluetoothDevice mDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (mDevice == null) return;
                // 3 ACTIONS
                if(mDevice.getBondState() == BluetoothDevice.BOND_BONDED){
                    Log.d(TAG, "BOND_BONDED.");
                    updateStatus("Successfully paired with " + safeDeviceName(mDevice));
                    //mBTDevice = mDevice;
                    Scanning();
                }
                if(mDevice.getBondState() == BluetoothDevice.BOND_BONDING){
                    Log.d(TAG, "BOND_BONDING.");
                }
                if(mDevice.getBondState() == BluetoothDevice.BOND_NONE){
                    Log.d(TAG, "BOND_NONE.");
                }
            }
        }
    };

    private final BroadcastReceiver mBroadcastReceiver5 = new BroadcastReceiver() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @Override
        public void onReceive(Context context, Intent intent) {

            BluetoothDevice mDevice = intent.getParcelableExtra("Device");
            String status = intent.getStringExtra("Status");
            if (mDevice == null || status == null || connStatusTextView == null) return;
            sharedPreferences = getActivity().getSharedPreferences("Shared Preferences", Context.MODE_PRIVATE);
            editor = sharedPreferences.edit();

            if(status.equals("connected")){
                try {
                    myDialog.dismiss();
                } catch(NullPointerException e){
                    e.printStackTrace();
                }

                Log.d(TAG, "mBroadcastReceiver5: Device now connected to "+safeDeviceName(mDevice));
                updateStatus( "Device now connected to "+safeDeviceName(mDevice));
                editor.putString("connStatus", "Connected to " + safeDeviceName(mDevice));
                connStatusTextView.setText("Connected to " + safeDeviceName(mDevice));

            }
            else if(status.equals("disconnected") && retryConnection == false){
                Log.d(TAG, "mBroadcastReceiver5: Disconnected from "+safeDeviceName(mDevice));
                updateStatus("Disconnected from "+safeDeviceName(mDevice));
                mBluetoothConnection = BluetoothConnectionService.getInstance(requireContext());
                //mBluetoothConnection.startAcceptThread();


                sharedPreferences = getActivity().getSharedPreferences("Shared Preferences", Context.MODE_PRIVATE);
                editor = sharedPreferences.edit();
                editor.putString("connStatus", "Disconnected");

               connStatusTextView.setText("Disconnected");

                editor.commit();

                try {
                    myDialog.show();
                }catch (Exception e){
                    Log.d(TAG, "BluetoothPopUp: mBroadcastReceiver5 Dialog show failure");
                }
                retryConnection = true;
                reconnectionHandler.postDelayed(reconnectionRunnable, 5000);

            }
            editor.commit();
        }
    };

    public void startConnection(){
        if (mBTDevice == null || mBluetoothConnection == null) {
            updateStatus("Please select a paired device first");
            return;
        }
        startBTConnection(mBTDevice,MY_UUID);
    }

    public void startBTConnection(BluetoothDevice device, UUID uuid){
        Log.d(TAG, "startBTConnection: Initializing RFCOMM Bluetooth Connection");
        mBluetoothConnection.startClientThread(device, uuid);
    }


    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy: called");
        super.onDestroy();
        unregisterReceiversSafely();
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause: called");
        super.onPause();
        unregisterReceiversSafely();
    }

    private void unregisterReceiversSafely() {
        if (getActivity() == null) return;
        unregisterReceiverSafely(mBroadcastReceiver1);
        unregisterReceiverSafely(mBroadcastReceiver2);
        unregisterReceiverSafely(mBroadcastReceiver3);
        unregisterReceiverSafely(mBroadcastReceiver4);
        try {
            LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(mBroadcastReceiver5);
        } catch (IllegalArgumentException ignored) {
            // Receiver was already removed or was never registered.
        }
    }

    private void unregisterReceiverSafely(BroadcastReceiver receiver) {
        try {
            requireActivity().unregisterReceiver(receiver);
        } catch (IllegalArgumentException ignored) {
            // Receiver was already removed or was never registered.
        }
    }

    private void showLog(String message) {
        Log.d(TAG, message);
    }
    private void updateStatus(String message) {
        Toast toast = Toast.makeText(getContext(), message, Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.TOP,0, 0);
        toast.show();
    }
}
