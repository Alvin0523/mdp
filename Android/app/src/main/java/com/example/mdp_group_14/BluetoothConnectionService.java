package com.example.mdp_group_14;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.widget.TextView;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * RFCOMM (SPP) link between the tablet and the RPi/Mac bridge.
 *
 * The tablet is the SERVER: it listens on the SPP UUID and the bridge connects to it.
 * Use BluetoothConnectionService.getInstance(context) everywhere - there must only ever be
 * one instance, otherwise several listeners/health loops fight over the same static state.
 */
public class BluetoothConnectionService {
    private static final String TAG = "Debugging Tag";
    private static final String APP_NAME = "MDP_Grp_14";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final long ACCEPT_RETRY_MS = 1000L;
    private static final long LINK_TIMEOUT_MS = 3000L;
    // Health state is still published on changes; this periodic check only detects silence.
    // A slower check reduces main-thread wakeups without changing connection behavior.
    private static final long LINK_HEALTH_CHECK_MS = 1000L;

    // ---- singleton -------------------------------------------------------------------------
    private static volatile BluetoothConnectionService instance;

    public static synchronized BluetoothConnectionService getInstance(Context context) {
        if (instance == null) {
            instance = new BluetoothConnectionService(context.getApplicationContext());
        }
        return instance;
    }

    // ---- shared state ----------------------------------------------------------------------
    public static volatile boolean BluetoothConnectionStatus = false;
    // A socket may stay connected while this is false if the peer has stopped sending data.
    public static volatile boolean BluetoothLinkOk = false;


    private final BluetoothAdapter mBluetoothAdapter;
    private final Context mContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private AcceptThread mAcceptThread;
    private ConnectThread mConnectThread;
    private volatile ConnectedThread mConnectedThread;

    private volatile long lastSeenElapsedMs = 0L;
    private boolean linkHealthPublished = false;

    private final Runnable acceptRetryRunnable = new Runnable() {
        @Override
        public void run() {
            if (!BluetoothConnectionStatus) {
                startAcceptThread();
            }
        }
    };

    private final Runnable linkHealthRunnable = new Runnable() {
        @Override
        public void run() {
            final long lastSeen = lastSeenElapsedMs;
            final boolean linkOk = BluetoothConnectionStatus
                    && lastSeen > 0L
                    && SystemClock.elapsedRealtime() - lastSeen < LINK_TIMEOUT_MS;
            publishLinkHealth(linkOk);
            mainHandler.postDelayed(this, LINK_HEALTH_CHECK_MS);
        }
    };

    BluetoothConnectionService(Context context) {
        this.mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.mContext = context;
        startAcceptThread();
        mainHandler.post(linkHealthRunnable);
    }

    public static void getConnectedDeviceName() {
    }

    // ---- server side: waits for the bridge to connect --------------------------------------
    private class AcceptThread extends Thread {
        private final BluetoothServerSocket serverSocket;

        AcceptThread() {
            BluetoothServerSocket tmp = null;
            try {
                if (mBluetoothAdapter != null) {
                    // Secure RFCOMM authenticates and encrypts the already-bonded peer.
                    tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(APP_NAME, MY_UUID);
                    Log.d(TAG, "Accept Thread: Setting up secure RFCOMM server using: " + MY_UUID);
                }
            } catch (IOException | SecurityException e) {
                // SecurityException = BLUETOOTH_CONNECT not granted yet. We retry every second,
                // so the listener comes up as soon as the permission is granted.
                Log.e(TAG, "Accept Thread: could not listen: " + e.getMessage());
            }
            serverSocket = tmp;
        }

        @Override
        public void run() {
            Log.d(TAG, "run: AcceptThread Running.");
            if (serverSocket == null) {
                Log.e(TAG, "run: RFCOMM server socket was not created");
                acceptThreadFinished(this);
                return;
            }

            BluetoothSocket socket = null;
            try {
                Log.d(TAG, "run: RFCOMM server socket start here...");
                socket = serverSocket.accept();
            } catch (IOException e) {
                Log.e(TAG, "run: accept IOException: " + e.getMessage());
            }

            if (socket != null) {
                try {
                    connected(socket, socket.getRemoteDevice());
                } catch (RuntimeException e) {
                    Log.e(TAG, "run: connected() failed", e);
                    closeQuietly(socket);
                    scheduleAcceptRetry();
                }
            }
            Log.i(TAG, "END AcceptThread");
            acceptThreadFinished(this);
        }

        void cancel() {
            Log.d(TAG, "cancel: Cancelling AcceptThread");
            if (serverSocket != null) {
                try {
                    serverSocket.close();   // does NOT close an already-accepted socket
                } catch (IOException e) {
                    Log.e(TAG, "cancel: Failed to close server socket " + e.getMessage());
                }
            }
        }
    }

    // ---- client side: only used by the Connect button ---------------------------------------
    private class ConnectThread extends Thread {
        private final BluetoothDevice device;
        private final UUID uuid;
        private volatile BluetoothSocket socket;
        private volatile boolean handedOff = false;

        ConnectThread(BluetoothDevice device, UUID uuid) {
            this.device = device;
            this.uuid = uuid;
        }

        @Override
        public void run() {
            try {
                // Secure RFCOMM requires the paired/bonded peer and negotiates encryption.
                socket = device.createRfcommSocketToServiceRecord(uuid);
                if (mBluetoothAdapter != null) mBluetoothAdapter.cancelDiscovery();
                socket.connect();
                Log.d(TAG, "RUN: ConnectThread connected.");
                handedOff = true;
                connected(socket, device);
            } catch (IOException | SecurityException e) {
                Log.e(TAG, "RUN: ConnectThread could not connect: " + e.getMessage());
                closeQuietly(socket);
                scheduleAcceptRetry();
            } finally {
                synchronized (BluetoothConnectionService.this) {
                    if (mConnectThread == this) mConnectThread = null;
                }
            }
        }

        void cancel() {
            if (!handedOff) closeQuietly(socket);   // never close a socket ConnectedThread owns
        }
    }

    public synchronized void startAcceptThread() {
        Log.d(TAG, "startAcceptThread");
        if (BluetoothConnectionStatus) return;      // already connected, nothing to listen for
        if (mAcceptThread == null || !mAcceptThread.isAlive()) {
            mAcceptThread = new AcceptThread();
            mAcceptThread.start();
        }
    }

    private synchronized void acceptThreadFinished(AcceptThread finishedThread) {
        if (mAcceptThread != finishedThread) {
            return;     // connected() already replaced/cleared it
        }
        mAcceptThread = null;
        if (!BluetoothConnectionStatus) {
            Log.d(TAG, "AcceptThread ended; retrying listener in " + ACCEPT_RETRY_MS + " ms");
            scheduleAcceptRetry();
        }
    }

    public synchronized void startClientThread(BluetoothDevice device, UUID uuid) {
        Log.d(TAG, "startClientThread");
        if (mConnectThread != null) mConnectThread.cancel();
        mConnectThread = new ConnectThread(device, uuid);
        mConnectThread.start();
    }

    // ---- an established connection ----------------------------------------------------------
    private class ConnectedThread extends Thread {
        private final BluetoothSocket socket;
        private final BluetoothDevice device;
        private final InputStream inStream;
        private final OutputStream outStream;
        private final StringBuilder pending = new StringBuilder();

        // Runs on the accept/connect worker thread: no UI work and nothing that can throw here.
        ConnectedThread(BluetoothSocket socket, BluetoothDevice device) {
            this.socket = socket;
            this.device = device;
            InputStream in = null;
            OutputStream out = null;
            try {
                in = socket.getInputStream();
                out = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "ConnectedThread: could not get streams: " + e.getMessage());
            }
            inStream = in;
            outStream = out;
        }

        @Override
        public void run() {
            Log.d(TAG, "ConnectedThread: running");
            broadcastConnection("connected", device);
            updateStatusViews(true, deviceLabel(device));

            if (inStream == null || outStream == null) {
                onLost();
                return;
            }

            byte[] buffer = new byte[1024];
            while (true) {
                try {
                    int bytes = inStream.read(buffer);
                    if (bytes < 0) {
                        throw new IOException("Bluetooth stream closed by peer");
                    }
                    if (bytes == 0) {
                        continue;
                    }
                    lastSeenElapsedMs = SystemClock.elapsedRealtime();
                    publishLinkHealth(true);
                    handleIncoming(new String(buffer, 0, bytes, StandardCharsets.UTF_8));
                } catch (IOException e) {
                    Log.e(TAG, "Error reading input stream. " + e.getMessage());
                    onLost();
                    break;
                }
            }
        }

        // Reads can return a message in pieces (sometimes 1 char at a time). Re-assemble on
        // '\n' and deliver only complete protocol lines.  A quiet timeout is deliberately not
        // used: treating an incomplete frame as a command can start or stop a robot incorrectly.
        private void handleIncoming(String chunk) {
            List<String> lines = new ArrayList<>();
            synchronized (pending) {
                pending.append(chunk);
                int nl;
                while ((nl = pending.indexOf("\n")) >= 0) {
                    String line = pending.substring(0, nl);
                    pending.delete(0, nl + 1);
                    if (line.endsWith("\r")) line = line.substring(0, line.length() - 1);
                    if (!line.isEmpty()) lines.add(line);
                }
            }
            for (String line : lines) {
                broadcastIncoming(line);
            }
        }

        // Every outgoing message ends with '\n' so line-based readers on the other side see it.
        void write(byte[] bytes) {
            if (outStream == null) return;
            String text = new String(bytes, StandardCharsets.UTF_8);
            if (!text.endsWith("\n")) {
                text = text + "\n";
                bytes = text.getBytes(StandardCharsets.UTF_8);
            }
            Log.d(TAG, "write: Writing to output stream: " + text);
            try {
                synchronized (outStream) {
                    outStream.write(bytes);
                    outStream.flush();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error writing to output stream. " + e.getMessage());
            }
        }

        private void onLost() {
            cancel();   // release the dead socket so the peer sees EOF promptly
            synchronized (BluetoothConnectionService.this) {
                if (mConnectedThread != this) {
                    return;     // replaced by a newer connection; that one owns the state
                }
                mConnectedThread = null;
                BluetoothConnectionStatus = false;
            }
            synchronized (pending) {
                pending.setLength(0); // an unfinished line belongs to the lost connection
            }
            lastSeenElapsedMs = 0L;
            publishLinkHealth(false);
            broadcastConnection("disconnected", device);
            updateStatusViews(false, null);
            scheduleAcceptRetry();
        }

        void cancel() {
            Log.d(TAG, "cancel: Closing connected socket");
            closeQuietly(socket);
        }
    }

    private synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        Log.d(TAG, "connected: Starting.");
        if (mAcceptThread != null) {
            mAcceptThread.cancel();
            mAcceptThread = null;
        }
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        ConnectedThread thread = new ConnectedThread(socket, device);
        mConnectedThread = thread;
        BluetoothConnectionStatus = true;
        lastSeenElapsedMs = SystemClock.elapsedRealtime();
        publishLinkHealth(true);
        thread.start();
    }

    // ---- helpers ----------------------------------------------------------------------------
    private void scheduleAcceptRetry() {
        mainHandler.removeCallbacks(acceptRetryRunnable);
        mainHandler.postDelayed(acceptRetryRunnable, ACCEPT_RETRY_MS);
    }

    private void broadcastConnection(String status, BluetoothDevice device) {
        Intent intent = new Intent("ConnectionStatus");
        intent.putExtra("Status", status);
        intent.putExtra("Device", device);
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
    }

    private void broadcastIncoming(String message) {
        Log.d(TAG, "InputStream: " + message);
        Intent intent = new Intent("incomingMessage");
        intent.putExtra("receivedMessage", message);
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
    }

    private synchronized void publishLinkHealth(boolean linkOk) {
        if (linkHealthPublished && BluetoothLinkOk == linkOk) {
            return;
        }
        linkHealthPublished = true;
        BluetoothLinkOk = linkOk;
        Intent linkStatus = new Intent("BluetoothLinkStatus");
        linkStatus.putExtra("link_ok", linkOk);
        linkStatus.putExtra("last_seen_elapsed_ms", lastSeenElapsedMs);
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(linkStatus);
    }

    // Views may only be touched on the main thread, and Home's views may not exist right now.
    private void updateStatusViews(final boolean connected, final String deviceName) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                TextView status = Home.getBluetoothStatus();
                if (status != null) {
                    status.setText(connected ? "Connected" : "Disconnected");
                    status.setTextColor(connected ? Color.GREEN : Color.RED);
                }
                if (connected && deviceName != null) {
                    TextView device = Home.getConnectedDevice();
                    if (device != null) {
                        device.setText(deviceName);
                    }
                }
            }
        });
    }

    private static String deviceLabel(BluetoothDevice device) {
        if (device == null) return null;
        try {
            String name = device.getName();
            return name != null ? name : device.getAddress();
        } catch (SecurityException e) {
            Log.e(TAG, "Missing BLUETOOTH_CONNECT permission: " + e.getMessage());
            return null;
        }
    }

    private static void closeQuietly(BluetoothSocket socket) {
        if (socket == null) return;
        try {
            socket.close();
        } catch (IOException e) {
            Log.e(TAG, "closeQuietly: " + e.getMessage());
        }
    }

    public static void write(byte[] out) {
        Log.d(TAG, "write: Write is called.");
        BluetoothConnectionService service = instance;
        ConnectedThread thread = (service == null) ? null : service.mConnectedThread;
        if (thread == null || !BluetoothConnectionStatus) {
            Log.w(TAG, "write: no active Bluetooth connection, dropping message");
            return;
        }
        thread.write(out);
    }
}
