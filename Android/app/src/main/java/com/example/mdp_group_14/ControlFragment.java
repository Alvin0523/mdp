package com.example.mdp_group_14;

import static com.example.mdp_group_14.Home.refreshMessageReceivedNS;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

public class ControlFragment extends Fragment {
    private static final String TAG = "ControlFragment";

    SharedPreferences sharedPreferences;

    // Control Button
    ImageButton moveForwardImageBtn, turnRightImageBtn, moveBackImageBtn, turnLeftImageBtn,turnbleftImageBtn,turnbrightImageBtn;
    ImageButton fastestResetButton;
    private static long exploreTimer, fastestTimer;
    public static ToggleButton exploreButton, fastestButton;
    public static TextView exploreTimeTextView, fastestTimeTextView, robotStatusTextView;
    private static GridMap gridMap;
    private Button task1PracticeButton, task2PracticeButton, resultsAnalyserButton;

    // Timer
    public static Handler timerHandler = new Handler();

    Button startRpiRunButton;
    private static long exploreElapsed, fastestElapsed;

    public static Runnable timerRunnableExplore = new Runnable() {
        @Override
        public void run() {
            long millisExplore = System.currentTimeMillis() - exploreTimer;
            int secondsExplore = (int) (millisExplore / 1000);
            int minutesExplore = secondsExplore / 60;
            secondsExplore = secondsExplore % 60;

            if (!Home.stopTimerFlag) {
                exploreTimeTextView.setText(String.format("%02d:%02d", minutesExplore,
                        secondsExplore));
                timerHandler.postDelayed(this, 500);
            }
        }
    };

    public static Runnable timerRunnableFastest = new Runnable() {
        @Override
        public void run() {
            long millisFastest = System.currentTimeMillis() - fastestTimer;
            int secondsFastest = (int) (millisFastest / 1000);
            int minutesFastest = secondsFastest / 60;
            secondsFastest = secondsFastest % 60;

            if (!Home.stopWk9TimerFlag) {
                fastestTimeTextView.setText(String.format("%02d:%02d", minutesFastest,
                        secondsFastest));
                timerHandler.postDelayed(this, 500);
            }
        }
    };


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // inflate
        View root = inflater.inflate(R.layout.controls, container, false);

        // get shared preferences
        sharedPreferences = getActivity().getSharedPreferences("Shared Preferences",
                Context.MODE_PRIVATE);
        seedDemoRunsIfEmpty();

        // variable initialization
        moveForwardImageBtn = root.findViewById(R.id.upBtn);
        turnRightImageBtn = root.findViewById(R.id.rightBtn);
        moveBackImageBtn = root.findViewById(R.id.downBtn);
        turnLeftImageBtn = root.findViewById(R.id.leftBtn);
        turnbleftImageBtn = root.findViewById(R.id.bleftBtn);
        turnbrightImageBtn = root.findViewById(R.id.brightBtn);
        fastestTimeTextView = root.findViewById(R.id.fastestTimeTextView2);
        exploreButton = root.findViewById(R.id.exploreToggleBtn2);
        fastestButton = root.findViewById(R.id.fastestToggleBtn2);
        fastestResetButton = root.findViewById(R.id.fastestResetImageBtn2);
        startRpiRunButton = root.findViewById(R.id.startRpiRunButton);
        robotStatusTextView = Home.getRobotStatusTextView();
        task1PracticeButton = root.findViewById(R.id.task1PracticeButton);
        task2PracticeButton = root.findViewById(R.id.task2PracticeButton);
        resultsAnalyserButton = root.findViewById(R.id.resultsAnalyserButton);
        task1PracticeButton.setOnClickListener(v -> showPracticeDialog("Task 1"));
        task2PracticeButton.setOnClickListener(v -> showPracticeDialog("Task 2"));
        resultsAnalyserButton.setOnClickListener(v -> showResultsDialog());
        // Timer fields are static so a configuration change does not reset an active run.
        // They are initialized to zero automatically on a fresh process.
        //startSend = root.findViewById(R.id.startSend); //just added, need to test

        gridMap = Home.getGridMap();

        // Button Listener
        moveForwardImageBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showLog("Clicked moveForwardImageBtn");
                if (gridMap.getCanDrawRobot()) {
                    gridMap.moveRobot("forward");
                    Home.refreshLabel();    // update x and y coordinate displayed
                    // display different statuses depending on validity of robot action
                    if (gridMap.getValidPosition()){
                        updateStatus("moving forward");}
                    else {
                        updateStatus("Unable to move forward");
                    }

                    Home.printMessage("f");
                }
                else
                    updateStatus("Please press 'SET START POINT'");
                showLog("Exiting moveForwardImageBtn");
            }
        });

        turnRightImageBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showLog("Clicked turnRightImageBtn");
                if (gridMap.getCanDrawRobot()) {
                    gridMap.moveRobot("right");
                    Home.refreshLabel();
                    Home.printMessage("fr");
//                    showLog("test");
                    System.out.println(Arrays.toString(gridMap.getCurCoord()));
                }
                else
                    updateStatus("Please press 'SET START POINT'");
                showLog("Exiting turnRightImageBtn");
            }
        });
        turnbrightImageBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showLog("Clicked turnbRightImageBtn");
                if (gridMap.getCanDrawRobot()) {
                    gridMap.moveRobot("backright");
                    Home.refreshLabel();
                    Home.printMessage("br");
                    System.out.println(Arrays.toString(gridMap.getCurCoord()));
                }
                else
                    updateStatus("Please press 'SET START POINT'");
                showLog("Exiting turnbRightImageBtn");
            }
        });

        moveBackImageBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showLog("Clicked moveBackwardImageBtn");
                if (gridMap.getCanDrawRobot()) {
                    gridMap.moveRobot("back");
                    Home.refreshLabel();
                    if (gridMap.getValidPosition())
                        updateStatus("moving backward");
                    else
                        updateStatus("Unable to move backward");
                    Home.printMessage("b");
                }
                else
                    updateStatus("Please press 'SET START POINT'");
                showLog("Exiting moveBackwardImageBtn");
            }
        });

        turnLeftImageBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showLog("Clicked turnLeftImageBtn");
                if (gridMap.getCanDrawRobot()) {
                    gridMap.moveRobot("left");
                    Home.refreshLabel();
                    updateStatus("turning left");
                    Home.printMessage("fl");
                }
                else
                    updateStatus("Please press 'SET START POINT'");
                showLog("Exiting turnLeftImageBtn");
            }
        });
        turnbleftImageBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showLog("Clicked turnbLeftImageBtn");
                if (gridMap.getCanDrawRobot()) {
                    gridMap.moveRobot("backleft");
                    Home.refreshLabel();
                    updateStatus("turning left");
                    Home.printMessage("bl");
                }
                else
                    updateStatus("Please press 'SET START POINT'");
                showLog("Exiting turnbLeftImageBtn");
            }
        });

        // Obstacle setup is separate from BEGIN. Planning is asynchronous, so the user can
        // wait for STATUS:Ready before starting the run.
        // Start the RPi run independently of the optional local challenge timers.
        // Home.printMessage appends the required line terminator before transmission.
        startRpiRunButton.setOnClickListener(v -> {
            Home.printMessage("BEGIN");
            Home.refreshMessageReceivedNS("TABLET -> RPi: BEGIN");
            robotStatusTextView.setText("Start requested");
            showToast("Start requested from RPi");
        });

        // Start Task 1 challenge
        exploreButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLog("Clicked Task 1 Btn (exploreToggleBtn)");
                ToggleButton exploreToggleBtn = (ToggleButton) v;

                if (exploreToggleBtn.getText().equals("TASK 1 START")) {
                    showToast("Task 1 timer stop!");
                    exploreElapsed = System.currentTimeMillis() - exploreTimer;
                    robotStatusTextView.setText("Task 1 Stopped");
                    timerHandler.removeCallbacks(timerRunnableExplore);
                    Home.printMessage("STOP"); //send a string "STOP" to the robot
                }
                else if (exploreToggleBtn.getText().equals("STOP")) {
                    // The challenge timer is a local UI timer.  Do not make its
                    // start depend on the asynchronous RPi STATUS:Ready message.
                    // BEGIN is still sent below so the RPi can start its run.
                    Home.printMessage("BEGIN"); //send a string "BEGIN" to the RPI
                    // Start timer
                    Home.stopTimerFlag = false;
                    showToast("Task 1 timer start!");

                    robotStatusTextView.setText("Task 1 Started");
                    exploreTimer = System.currentTimeMillis() - exploreElapsed;
                    timerHandler.postDelayed(timerRunnableExplore, 0);
                }
                else {
                    showToast("Else statement: " + exploreToggleBtn.getText());
                }
                showLog("Exiting exploreToggleBtn");
            }
        });



        //Start Task 2 Challenge Timer
        fastestButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLog("Clicked Task 2 Btn (fastestToggleBtn)");
                ToggleButton fastestToggleBtn = (ToggleButton) v;
                if (fastestToggleBtn.getText().equals("TASK 2 START")) {
                    showToast("Task 2 timer stop!");
                    fastestElapsed = System.currentTimeMillis() - fastestTimer;
                    robotStatusTextView.setText("Task 2 Stopped");
                    timerHandler.removeCallbacks(timerRunnableFastest);
                    Home.printMessage("STOP"); //send a string "STOP" to the robot
                }
                else if (fastestToggleBtn.getText().equals("STOP")) {
                    showToast("Task 2 timer start!");
                    Home.printMessage("BEGIN"); //send a string "BEGIN" to the RPI
                    Home.stopWk9TimerFlag = false;
                    robotStatusTextView.setText("Task 2 Started");
                    fastestTimer = System.currentTimeMillis() - fastestElapsed;
                    timerHandler.postDelayed(timerRunnableFastest, 0);
                }
                else
                    showToast(fastestToggleBtn.getText().toString());
                showLog("Exiting fastestToggleBtn");
            }
        });

        fastestResetButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                showLog("Clicked fastestResetImgBtn");
                showToast("Resetting Fastest Time...");
                fastestTimeTextView.setText("00:00");
                fastestElapsed = 0;
                robotStatusTextView.setText("Fastest Car Finished");
                if(fastestButton.isChecked()){
                    fastestButton.toggle();
                }
                timerHandler.removeCallbacks(timerRunnableFastest);
                showLog("Exiting fastestResetImgBtn");
            }
        });

        /*
        startSend.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                showLog("Clicked startSendBtn");
                showToast("Sending BEGIN to robot...");
                exploreButton.toggle();
                if (exploreButton.getText().equals("WK8 START")) {
                    showToast("Auto Movement/ImageRecog timer stop!");
                    robotStatusTextView.setText("Auto Movement Stopped");
                    timerHandler.removeCallbacks(timerRunnableExplore);
                }
                else if (exploreButton.getText().equals("STOP")) {
                    // Get String value that represents obstacle configuration
                    String msg = gridMap.getObstacles();
                    // Send this String over via BT
                    //Home.printCoords(msg);
                    // Start timer
                    Home.stopTimerFlag = false;
                    showToast("Auto Movement/ImageRecog timer start!");

                    robotStatusTextView.setText("Auto Movement Started");
                    exploreTimer = System.currentTimeMillis();
                    timerHandler.postDelayed(timerRunnableExplore, 0);
                }
                //ok
                Home.printMessage("BEGIN"); //send a string "BEGIN" to the RPI
                showLog("Exiting startSend");
            }
        });
         */

        return root;
    }

    public static void sendObstacles() {
        StringBuilder sentObstacles = new StringBuilder();
        for (String line : gridMap.getObstacleLines()) {
            Home.printMessage(line);
            if (sentObstacles.length() > 0) sentObstacles.append("\n");
            sentObstacles.append(line);
        }
        Home.printMessage("DONE");
        if (sentObstacles.length() > 0) sentObstacles.append("\n");
        sentObstacles.append("DONE");
        Home.refreshMessageReceivedNS(sentObstacles.toString());
        if (robotStatusTextView != null) robotStatusTextView.setText("Obstacles sent");
    }

    public static void resetPost() {
        Home.printMessage("RESET");
        resetExploreTimer(false);
        resetFastestTimer(false);
        if (robotStatusTextView != null) robotStatusTextView.setText("Reset sent");
    }

    private static void resetExploreTimer(boolean updateStatus) {
        if (exploreTimeTextView != null) exploreTimeTextView.setText("00:00");
        exploreElapsed = 0;
        exploreTimer = 0;
        Home.stopTimerFlag = true;
        timerHandler.removeCallbacks(timerRunnableExplore);
        if (exploreButton != null && exploreButton.isChecked()) exploreButton.toggle();
        if (updateStatus) robotStatusTextView.setText("Not Available");
    }

    public static void startTaskTimer() {
        // BEGIN is also the RPi start command. Repeated presses must not move
        // the local clock back to the elapsed value from the previous stop.
        Home.printMessage("BEGIN");
        if (!Home.stopTimerFlag && exploreTimer != 0) {
            if (robotStatusTextView != null) robotStatusTextView.setText("Task Already Started");
            return;
        }
        Home.stopTimerFlag = false;
        exploreTimer = System.currentTimeMillis() - exploreElapsed;
        timerHandler.removeCallbacks(timerRunnableExplore);
        timerHandler.post(timerRunnableExplore);
        if (robotStatusTextView != null) robotStatusTextView.setText("Task Started");
    }

    public static void stopTaskTimer() {
        if (!Home.stopTimerFlag && exploreTimer != 0) {
            exploreElapsed = System.currentTimeMillis() - exploreTimer;
        }
        Home.stopTimerFlag = true;
        timerHandler.removeCallbacks(timerRunnableExplore);
        if (robotStatusTextView != null) robotStatusTextView.setText("Task Stopped");
        Home.printMessage("STOP");
    }

    /** Timer controlled by the RPi STATUS/START/STOP protocol messages. */
    public static void startRpiTimer() {
        if (exploreTimeTextView == null) return;
        if (!Home.stopTimerFlag && exploreTimer != 0) return;
        Home.stopTimerFlag = false;
        exploreTimer = System.currentTimeMillis();
        timerHandler.removeCallbacks(timerRunnableExplore);
        timerHandler.post(timerRunnableExplore);
    }

    public static void stopRpiTimer() {
        Home.stopTimerFlag = true;
        timerHandler.removeCallbacks(timerRunnableExplore);
    }

    public static void resetRpiTimer() {
        Home.stopTimerFlag = true;
        timerHandler.removeCallbacks(timerRunnableExplore);
        exploreTimer = 0;
        if (exploreTimeTextView != null) exploreTimeTextView.setText("00:00");
    }

    private static void resetFastestTimer(boolean updateStatus) {
        fastestTimeTextView.setText("00:00");
        fastestElapsed = 0;
        fastestTimer = 0;
        Home.stopWk9TimerFlag = true;
        timerHandler.removeCallbacks(timerRunnableFastest);
        if (fastestButton != null && fastestButton.isChecked()) fastestButton.toggle();
        if (updateStatus) robotStatusTextView.setText("Fastest Car Finished");
    }

    private static void showLog(String message) {
        Log.d(TAG, message);
    }

    private void showToast(String message) {
        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
    }

    private void updateStatus(String message) {
        Toast toast = Toast.makeText(getContext(), message, Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.TOP,0, 0);
        toast.show();
    }

    private void showPracticeDialog(String task) {
        LinearLayout box = new LinearLayout(requireContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(28, 8, 28, 0);
        TextView timer = new TextView(requireContext());
        timer.setText("00:00"); timer.setTextSize(30); timer.setGravity(Gravity.CENTER);
        LinearLayout lagNotes = new LinearLayout(requireContext());
        lagNotes.setOrientation(LinearLayout.VERTICAL);
        LinearLayout actions = new LinearLayout(requireContext());
        actions.setGravity(Gravity.CENTER);
        Button start = new Button(requireContext()); start.setText("START");
        Button lag = new Button(requireContext()); lag.setText("LAG");
        Button stop = new Button(requireContext()); stop.setText("STOP");
        actions.addView(start); actions.addView(lag); actions.addView(stop);
        box.addView(timer); box.addView(actions); box.addView(lagNotes);
        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setTitle(task + " Practice")
                .setMessage("Start the run, mark any lag, then submit your notes.")
                .setView(box).setNegativeButton("CANCEL", null).setPositiveButton("SUBMIT", null).create();
        final long[] started = {0}; final long[] stopped = {0}; final long[] lagTotal = {0}; final int[] lagCount = {0};
        final java.util.ArrayList<EditText> lagFields = new java.util.ArrayList<>();
        final java.util.ArrayList<String> lagTags = new java.util.ArrayList<>();
        final java.util.ArrayList<String> lagTimes = new java.util.ArrayList<>();
        final Handler handler = new Handler();
        final Runnable clock = new Runnable() { public void run() {
            if (started[0] != 0 && stopped[0] == 0) { timer.setText(formatDuration(System.currentTimeMillis() - started[0])); handler.postDelayed(this, 250); }
        }};
        start.setOnClickListener(v -> { if (started[0] == 0) { started[0] = System.currentTimeMillis(); handler.post(clock); } });
        lag.setOnClickListener(v -> { if (started[0] != 0 && stopped[0] == 0) {
            lagCount[0]++;
            LinearLayout lagRow = new LinearLayout(requireContext());
            lagRow.setOrientation(LinearLayout.VERTICAL);
            LinearLayout tagRow = new LinearLayout(requireContext());
            Button preparation = new Button(requireContext()); preparation.setText("PREP"); preparation.setTextSize(10); preparation.setPadding(0, 0, 0, 0);
            Button runTag = new Button(requireContext()); runTag.setText("RUN"); runTag.setTextSize(10); runTag.setPadding(0, 0, 0, 0);
            tagRow.addView(preparation, new LinearLayout.LayoutParams(0, 50, 1));
            tagRow.addView(runTag, new LinearLayout.LayoutParams(0, 50, 1));
            EditText lagField = new EditText(requireContext());
            String lagTime = formatDuration(System.currentTimeMillis() - started[0]);
            lagField.setHint("Lag " + lagCount[0] + " description (at " + lagTime + ")");
            lagField.setSingleLine(false);
            lagRow.addView(tagRow); lagRow.addView(lagField); lagNotes.addView(lagRow);
            lagFields.add(lagField);
            lagTags.add("Preparation");
            lagTimes.add(lagTime);
            final int lagIndex = lagTags.size() - 1;
            preparation.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(220, 40, 40)));
            preparation.setTextColor(Color.WHITE);
            runTag.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(235, 190, 70)));
            runTag.setTextColor(Color.BLACK);
            preparation.setOnClickListener(x -> { lagTags.set(lagIndex, "Preparation"); preparation.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(220, 40, 40))); runTag.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.LTGRAY)); });
            runTag.setOnClickListener(x -> { lagTags.set(lagIndex, "Run"); runTag.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(235, 190, 70))); preparation.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.LTGRAY)); });
        }});
        stop.setOnClickListener(v -> { if (started[0] != 0) { stopped[0] = System.currentTimeMillis(); handler.removeCallbacks(clock); stop.setText("STOPPED"); } });
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn -> {
            if (started[0] == 0 || stopped[0] == 0) { Toast.makeText(requireContext(), "Start and stop the run first", Toast.LENGTH_SHORT).show(); return; }
            long total = stopped[0] - started[0]; long run = Math.max(0, total - lagTotal[0]);
            EditText importantNotes = new EditText(requireContext());
            importantNotes.setHint("Add any important notes (optional)");
            importantNotes.setMinLines(2);
            new AlertDialog.Builder(requireContext()).setTitle("Important notes").setView(importantNotes)
                    .setNegativeButton("CANCEL", null).setPositiveButton("SUBMIT", (notesDialog, which) -> {
                        StringBuilder notes = new StringBuilder(importantNotes.getText().toString());
                        for (int i = 0; i < lagFields.size(); i++) {
                            if (notes.length() > 0) notes.append("; ");
                            notes.append("Lag ").append(i + 1).append(" [").append(lagTags.get(i)).append("] @ ").append(lagTimes.get(i)).append(": ").append(lagFields.get(i).getText().toString());
                        }
                        saveRun(task, total - run, run, lagFields.isEmpty() ? "Run" : lagTags.get(0), notes.toString());
                        dialog.dismiss(); Toast.makeText(requireContext(), "Saved as " + task + " run " + getRunCount(task), Toast.LENGTH_SHORT).show();
                    }).show();
        }));
        dialog.show();
        styleDialog(dialog);
    }

    private void saveRun(String task, long prep, long run, String tag, String note) {
        String old = sharedPreferences.getString(task + "_runs", "");
        String row = prep + "," + run + "," + tag.replace(",", " ") + "," + note.replace("|", " ");
        sharedPreferences.edit().putString(task + "_runs", old.isEmpty() ? row : old + "|" + row).apply();
    }

    private void seedDemoRunsIfEmpty() {
        String oldTask1 = "45000,55000,Preparation,Demo run 1|35000,50000,Run,Demo run 2|25000,42000,Run,Demo run 3";
        String oldTask2 = "50000,65000,Preparation,Demo run 1|40000,55000,Run,Demo run 2|30000,45000,Run,Demo run 3";
        String existing1 = sharedPreferences.getString("Task 1_runs", "");
        String existing2 = sharedPreferences.getString("Task 2_runs", "");
        String task1SeedVersion = sharedPreferences.getString("Task 1_demo_seed_version", "");
        String previousTask1 = "55000,60000,Preparation,Demo run 1|48000,52000,Run,Demo run 2|42000,58000,Preparation,Demo run 3|37000,49000,Run,Demo run 4|33000,46000,Preparation,Demo run 5|29000,44000,Run,Demo run 6|25000,41000,Preparation,Demo run 7|22000,39000,Run,Demo run 8|18000,36000,Preparation,Demo run 9|15000,33000,Run,Demo run 10";
        String task1 = "200000,120000,Preparation,Bluetooth connection setup took longer|185000,112000,Run,|175000,116000,Preparation,RPi system response was slightly slow|150000,103000,Run,|170000,107000,Preparation,Bluetooth connection fluctuation|95000,95000,Run,|80000,99000,Preparation,Minor RPi processing delay|75000,85000,Run,|60000,90000,Preparation,Bluetooth connection stable|95000,70000,Run,|65000,50000,Run,";
        String task2 = "60000,58000,Run,Demo run 1|53000,55000,Preparation,Demo run 2|47000,52000,Run,Demo run 3|43000,48000,Preparation,Demo run 4|38000,46000,Run,Demo run 5|34000,43000,Preparation,Demo run 6|30000,40000,Run,Demo run 7|27000,37000,Preparation,Demo run 8|23000,35000,Run,Demo run 9|20000,32000,Preparation,Demo run 10";
        SharedPreferences.Editor demoEditor = sharedPreferences.edit();
        if (!"3".equals(task1SeedVersion) || existing1.isEmpty() || existing1.equals(oldTask1) || existing1.equals(previousTask1)) {
            demoEditor.putString("Task 1_runs", task1).putString("Task 1_demo_seed_version", "3");
        }
        if (existing2.isEmpty() || existing2.equals(oldTask2)) demoEditor.putString("Task 2_runs", task2);
        demoEditor.apply();
    }

    private int getRunCount(String task) { String s = sharedPreferences.getString(task + "_runs", ""); return s.isEmpty() ? 0 : s.split("\\|", -1).length; }

    private void showResultsDialog() {
        String[] choices = {"Task 1", "Task 2"};
        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setTitle("Results analyser").setItems(choices, (d, which) -> showTaskResults(choices[which])).setNegativeButton("CLOSE", null).create();
        dialog.show();
        styleDialog(dialog);
    }

    private void showTaskResults(String task) {
        String data = sharedPreferences.getString(task + "_runs", "");
        LinearLayout box = new LinearLayout(requireContext()); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(18, 8, 18, 8);
        List<RunData> runs = new ArrayList<>();
        if (!data.isEmpty()) for (String row : data.split("\\|")) { String[] p = row.split(",", 4); if (p.length >= 2) runs.add(new RunData(Long.parseLong(p[0]), Long.parseLong(p[1]), p.length > 2 ? p[2] : "Run", p.length > 3 ? p[3] : "")); }
        if (runs.isEmpty()) { TextView empty = new TextView(requireContext()); empty.setText("No runs saved yet. Complete a practice run to see your progress."); box.addView(empty); }
        else {
            addChartTitle(box, "Preparation time by run", Color.rgb(220, 40, 40));
            box.addView(new LineChartView(requireContext(), runs, true));
            addChartTitle(box, "Run time by run", Color.rgb(210, 150, 0));
            box.addView(new LineChartView(requireContext(), runs, false));
            addChartTitle(box, "Average time split", Color.BLACK);
            box.addView(new PieChartView(requireContext(), runs));
            TextView details = new TextView(requireContext()); details.setText("Tap a run to view its lag notes and final notes:"); details.setPadding(4, 14, 4, 4); box.addView(details);
            LinearLayout runGrid = new LinearLayout(requireContext()); runGrid.setOrientation(LinearLayout.VERTICAL);
            LinearLayout runRow = null;
            for (int i = 0; i < runs.size(); i++) {
                if (i % 5 == 0) { runRow = new LinearLayout(requireContext()); runRow.setOrientation(LinearLayout.HORIZONTAL); runGrid.addView(runRow); }
                final int index = i; Button runButton = new Button(requireContext()); runButton.setText("RUN " + (i + 1));
                runButton.setTextSize(10); runButton.setOnClickListener(v -> showRunDetails(task, index + 1, runs.get(index)));
                runRow.addView(runButton, new LinearLayout.LayoutParams(0, 48, 1));
            }
            box.addView(runGrid);
        }
        android.widget.ScrollView scroll = new android.widget.ScrollView(requireContext()); scroll.addView(box);
        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setTitle(task + " results").setView(scroll).setPositiveButton("DONE", null).create();
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.92f), (int) (getResources().getDisplayMetrics().heightPixels * 0.82f));
        styleDialog(dialog);
    }

    private void addChartTitle(LinearLayout box, String title, int color) { TextView t = new TextView(requireContext()); t.setText(title); t.setTextColor(color); t.setTextSize(16); t.setTypeface(null, android.graphics.Typeface.BOLD); t.setPadding(4, 12, 4, 4); box.addView(t); }

    private void showRunDetails(String task, int number, RunData run) {
        String notes = run.notes.isEmpty() ? "No lag or final notes recorded." : run.notes.replace(";", "\n");
        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setTitle(task + " • Run " + number)
                .setMessage("Preparation: " + formatDuration(run.prep) + "\nRun: " + formatDuration(run.run) + "\n\n" + notes)
                .setPositiveButton("DONE", null).create(); dialog.show(); styleDialog(dialog);
    }

    private static class RunData { long prep, run; String tag, notes; RunData(long p, long r, String t, String n) { prep=p; run=r; tag=t; notes=n; } }

    private static class LineChartView extends View {
        private final List<RunData> runs; private final boolean prep; private final Paint paint = new Paint(1);
        LineChartView(Context c, List<RunData> r, boolean p) { super(c); runs=r; prep=p; setMinimumHeight(190); }
        protected void onDraw(Canvas c) { super.onDraw(c); float left=58, top=24, right=getWidth()-18, bottom=160; long max=1; for(RunData r:runs) max=Math.max(max, (prep?r.prep:r.run)/1000); paint.setTextSize(10); paint.setStrokeWidth(1); for(int tick=0;tick<=4;tick++){float y=bottom-(bottom-top)*tick/4f; long value=Math.round(max*tick/4f); paint.setColor(Color.rgb(210,210,210)); c.drawLine(left,y,right,y,paint); paint.setColor(Color.DKGRAY); c.drawText(String.valueOf(value),8,y+4,paint);} paint.setColor(Color.DKGRAY); paint.setTextSize(11); c.drawText("Seconds",4,top-8,paint); paint.setStrokeWidth(2); c.drawLine(left,top,left,bottom,paint); c.drawLine(left,bottom,right,bottom,paint); float step=runs.size()>1?(right-left)/(runs.size()-1):right-left; float px=0,py=0; for(int i=0;i<runs.size();i++){float x=left+i*step;float y=bottom-((prep?runs.get(i).prep:runs.get(i).run)/1000f/max)*(bottom-top);paint.setColor(prep?Color.rgb(220,40,40):Color.rgb(210,150,0));paint.setStrokeWidth(6);c.drawCircle(x,y,5,paint);if(i>0)c.drawLine(px,py,x,y,paint);paint.setTextSize(11);c.drawText("R"+(i+1),x-8,bottom+18,paint);px=x;py=y;} }
    }

    private static class PieChartView extends View {
        private final List<RunData> runs; private final Paint paint=new Paint(1); PieChartView(Context c,List<RunData> r){super(c);runs=r;setMinimumHeight(190);}
        protected void onDraw(Canvas c){super.onDraw(c);float prep=0,run=0;for(RunData r:runs){prep+=r.prep;run+=r.run;}float total=prep+run;float l=35,t=18,s=Math.min(getWidth()-70,150);android.graphics.RectF rect=new android.graphics.RectF(l,t,l+s,t+s);paint.setColor(Color.rgb(220,40,40));c.drawArc(rect,-90,360*prep/total,true,paint);paint.setColor(Color.rgb(210,150,0));c.drawArc(rect,-90+360*prep/total,360*run/total,true,paint);paint.setColor(Color.DKGRAY);paint.setTextSize(13);c.drawText("Preparation  " + formatAverage(prep,runs.size()),l+s+15,t+55,paint);c.drawText("Run  " + formatAverage(run,runs.size()),l+s+15,t+82,paint);}
        private String formatAverage(float totalMillis, int count) { long averageMillis = Math.round(totalMillis / count); long seconds = averageMillis / 1000; return String.format("%02d:%02d avg", seconds / 60, seconds % 60); }
    }

    private void styleDialog(AlertDialog dialog) {
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(getResources().getColor(R.color.white)));
        }
        int red = getResources().getColor(R.color.colorRed);
        int yellow = getResources().getColor(R.color.colorYellow);
        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(red);
        if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(red);
        if (dialog.getButton(AlertDialog.BUTTON_NEUTRAL) != null) dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(yellow);
    }

    private void addResultRow(LinearLayout box, String label, long prep, long run, long total, long best) {
        TextView row = new TextView(requireContext()); row.setPadding(0, 10, 0, 10); row.setText(label + "   prep " + formatDuration(prep) + "   run " + formatDuration(run) + "\nTotal " + formatDuration(total) + (total == best ? "   • BEST" : "")); row.setTextSize(14); box.addView(row);
        TextView bar = new TextView(requireContext()); bar.setText("████████████████████"); bar.setTextColor(total == best ? getResources().getColor(R.color.colorRed) : getResources().getColor(R.color.colorYellow)); box.addView(bar);
    }

    private String formatDuration(long millis) { long sec = millis / 1000; return String.format("%02d:%02d", sec / 60, sec % 60); }
}
