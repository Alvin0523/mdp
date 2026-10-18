package com.example.mdp_group_14;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class BluetoothCommunications extends Fragment {
    private static final String TAG = "BluetoothComms";

    private SharedPreferences sharedPreferences;
    private TextView messageReceivedTextView;
    private TextView detectedImagesTextView;
    private EditText typeBoxEditText;
    private static final String DETECTED_IMAGES_PREF = "detectedImages";

    public static void updateMessageLog(Context context, String message) {
        SharedPreferences prefs = context.getSharedPreferences("Shared Preferences", Context.MODE_PRIVATE);
        String previous = prefs.getString("message", "");
        String next = previous + (previous.isEmpty() ? "" : "\n") + message;
        // Bound diagnostic storage so a day-long run cannot exhaust preferences.
        if (next.length() > 32768) next = next.substring(next.length() - 32768);
        prefs.edit().putString("message", next).apply();
        Intent intent = new Intent("uiMessage");
        intent.putExtra("receivedMessage", message);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        IntentFilter filter = new IntentFilter();
        filter.addAction("incomingMessage");
        filter.addAction("uiMessage");
        filter.addAction("uiImageDetected");
        filter.addAction("imagesCleared");
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(mReceiver, filter);
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.activity_communications, container, false);
        ImageButton send;
        send = root.findViewById(R.id.messageButton);

        // Message Box
        messageReceivedTextView = root.findViewById(R.id.messageReceivedTitleTextView);
        messageReceivedTextView.setMovementMethod(new ScrollingMovementMethod());
        detectedImagesTextView = root.findViewById(R.id.detectedImagesTextView);
        detectedImagesTextView.setMovementMethod(new ScrollingMovementMethod());
        Button chatTab = root.findViewById(R.id.chatTabButton);
        Button imagesTab = root.findViewById(R.id.imagesDetectedTabButton);
        Button clearImages = root.findViewById(R.id.clearImagesButton);
        chatTab.setOnClickListener(v -> showChatTab(true));
        imagesTab.setOnClickListener(v -> showChatTab(false));
        clearImages.setOnClickListener(v -> clearDetectedImages(requireContext()));
        typeBoxEditText = root.findViewById(R.id.typeBoxEditText);
        ControlFragment.exploreTimeTextView = root.findViewById(R.id.chatTaskTimerTextView);

        Button startTask = root.findViewById(R.id.chatTaskStartButton);
        Button stopTask = root.findViewById(R.id.chatTaskStopButton);
        Button sendObstacles = root.findViewById(R.id.chatSendObstaclesButton);
        Button resetPost = root.findViewById(R.id.chatResetPostButton);
        startTask.setOnClickListener(v -> ControlFragment.startTaskTimer());
        stopTask.setOnClickListener(v -> ControlFragment.stopTaskTimer());
        sendObstacles.setOnClickListener(v -> ControlFragment.sendObstacles());
        resetPost.setOnClickListener(v -> ControlFragment.resetPost());

        // get shared preferences
        sharedPreferences = requireActivity().getSharedPreferences("Shared Preferences", Context.MODE_PRIVATE);

        String savedMessage = sharedPreferences.getString("message", "");
        if (!savedMessage.isEmpty()) {
            messageReceivedTextView.setText(savedMessage);
            messageReceivedTextView.post(() ->
                    messageReceivedTextView.scrollTo(0, messageReceivedTextView.getBottom()));
        }
        String savedImages = sharedPreferences.getString(DETECTED_IMAGES_PREF, "");
        if (!savedImages.isEmpty()) detectedImagesTextView.setText(savedImages);

        send.setOnClickListener(view -> {
            showLog("Clicked sendTextBtn");
            String sentText = typeBoxEditText.getText().toString();

            updateMessageLog(requireContext(), sentText);
            typeBoxEditText.setText("");

            Home.printMessage(sentText);
            showLog("Exiting sendTextBtn");
        });

        return root;
    }

    private void showChatTab(boolean showChat) {
        messageReceivedTextView.setVisibility(showChat ? View.VISIBLE : View.GONE);
        detectedImagesTextView.setVisibility(showChat ? View.GONE : View.VISIBLE);
        getView().findViewById(R.id.clearImagesButton).setVisibility(showChat ? View.GONE : View.VISIBLE);
    }

    public static boolean isImageDetectionMessage(String message) {
        String value = message == null ? "" : message.trim().toUpperCase(java.util.Locale.ROOT);
        return value.startsWith("TARGET,") || value.startsWith("TARGET~")
                || value.startsWith("IMAGE,") || value.startsWith("IMAGE~")
                || value.startsWith("IMAGE_DETECTED") || value.startsWith("DETECTED_IMAGE");
    }

    public static String formatImageDetectionMessage(String message) {
        String value = message == null ? "" : message.trim();
        if (value.regionMatches(true, 0, "TARGET,", 0, 7)) {
            String[] fields = value.split(",", -1);
            if (fields.length >= 3) return "Obstacle no: " + fields[1].trim() + " TARGET ID: " + fields[2].trim();
        }
        if (value.regionMatches(true, 0, "TARGET~", 0, 7)) {
            String[] fields = value.split("~", -1);
            if (fields.length >= 3) return "Obstacle no: " + fields[1].trim() + " TARGET ID: " + fields[2].trim();
        }
        return value;
    }

    public static void addDetectedImage(Context context, String message) {
        if (context == null || message == null || message.trim().isEmpty()) return;
        SharedPreferences prefs = context.getSharedPreferences("Shared Preferences", Context.MODE_PRIVATE);
        String formatted = formatImageDetectionMessage(message);
        String previous = prefs.getString(DETECTED_IMAGES_PREF, "");
        for (String line : previous.split("\\n", -1)) if (line.equals(formatted)) return;
        String next = previous.isEmpty() ? formatted : previous + "\n" + formatted;
        prefs.edit().putString(DETECTED_IMAGES_PREF, next).apply();
        Intent intent = new Intent("uiImageDetected");
        intent.putExtra("receivedMessage", formatted);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    public static void clearDetectedImages(Context context) {
        context.getSharedPreferences("Shared Preferences", Context.MODE_PRIVATE)
                .edit().remove(DETECTED_IMAGES_PREF).apply();
        LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent("imagesCleared"));
    }

    private void bindMovementControls(View root) {
        ImageButton left = root.findViewById(R.id.leftBtn);
        ImageButton forward = root.findViewById(R.id.upBtn);
        ImageButton right = root.findViewById(R.id.rightBtn);
        ImageButton backLeft = root.findViewById(R.id.bleftBtn);
        ImageButton back = root.findViewById(R.id.downBtn);
        ImageButton backRight = root.findViewById(R.id.brightBtn);

        left.setOnClickListener(v -> moveRobot("left", "fl"));
        forward.setOnClickListener(v -> moveRobot("forward", "f"));
        right.setOnClickListener(v -> moveRobot("right", "fr"));
        backLeft.setOnClickListener(v -> moveRobot("backleft", "bl"));
        back.setOnClickListener(v -> moveRobot("back", "b"));
        backRight.setOnClickListener(v -> moveRobot("backright", "br"));
    }

    private void moveRobot(String direction, String command) {
        GridMap map = Home.getGridMap();
        if (map != null && map.getCanDrawRobot()) {
            map.moveRobot(direction);
            Home.refreshLabel();
            Home.printMessage(command);
        } else if (Home.getRobotStatusTextView() != null) {
            Home.getRobotStatusTextView().setText("Please press 'SET START POINT'");
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(mReceiver);
    }

    private static void showLog(String message) {
        Log.d(TAG, message);
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String text = intent.getStringExtra("receivedMessage");
            if ("imagesCleared".equals(intent.getAction())) {
                if (detectedImagesTextView != null) detectedImagesTextView.setText("");
            } else if ("uiImageDetected".equals(intent.getAction())) {
                if (detectedImagesTextView != null && text != null) {
                    String current = detectedImagesTextView.getText().toString();
                    if (!current.contains(text)) detectedImagesTextView.append((current.isEmpty() ? "" : "\n") + text);
                }
            } else if ("incomingMessage".equals(intent.getAction()) && isImageDetectionMessage(text)) {
                addDetectedImage(context, text);
            } else if (messageReceivedTextView != null && text != null) {
                messageReceivedTextView.append(text + "\n");
            }
        }
    };
}
