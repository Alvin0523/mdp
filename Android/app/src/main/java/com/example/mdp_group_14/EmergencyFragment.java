package com.example.mdp_group_14;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

// NOTE: THIS HAS BEEN REMOVED - NOT IN USE IN FINAL APP
// This was made for redundancy - in case an obstacle can't be placed properly, this is a slightly faster way to get the right syntax to manually send to RPi
public class EmergencyFragment extends DialogFragment {
    private static final String TAG = "EmergencyFragment";
    View rootView;
    private SharedPreferences.Editor editor;
    Button addManualBtn;
    GridMap gridMap;
    LinearLayout obstacleListContainer;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        showLog("Entering onCreateView");
        rootView = inflater.inflate(R.layout.activity_manual_input, container, false);
        super.onCreate(savedInstanceState);

        SharedPreferences sharedPreferences = getActivity().getSharedPreferences("Shared Preferences", Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();
        gridMap = Home.getGridMap();
        // buttons
        addManualBtn = rootView.findViewById(R.id.addManualBtn);

        obstacleListContainer = rootView.findViewById(R.id.obstacleListContainer);

        // selecting 0 - 19 for x, y
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                rootView.getContext(), R.array.obstID_array,
                android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        final Spinner xValSpinner = rootView.findViewById(R.id.xDropdownSpinner);
        final Spinner yValSpinner = rootView.findViewById(R.id.yDropdownSpinner);
        xValSpinner.setAdapter(adapter);
        yValSpinner.setAdapter(adapter);

        // Selecting "North", "South", "East", "West" for dir of obstacle
        ArrayAdapter<CharSequence> dirAdapter = ArrayAdapter.createFromResource(
                rootView.getContext(), R.array.obstDir_array,
                android.R.layout.simple_spinner_item);
        dirAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        final Spinner dirValSpinner = rootView.findViewById(R.id.directionDropdownSpinner);
        dirValSpinner.setAdapter(dirAdapter);

        // Initialise selection for all spinners to the 1st element in the given array
        xValSpinner.setSelection(0); yValSpinner.setSelection(0); dirValSpinner.setSelection(0);

        addManualBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showLog("Clicked addManualBtn");
//                EditText chatInput = BluetoothCommunications.getTypeBoxEditText();
//                String old = chatInput.getText().toString();
//                if(old.equals("")) old = "ALG";
                int col = Integer.parseInt(xValSpinner.getSelectedItem().toString());
                int row = Integer.parseInt(yValSpinner.getSelectedItem().toString());
                showLog("Col = " + col  + ", Row = " + row);
                // obstDir
                String dir = dirValSpinner.getSelectedItem().toString();

                if (!gridMap.addManualObstacle(col, row, dir)) {
                    showToast("An obstacle already exists at this location");
                    return;
                }

                // obstID is acquired based on the obstID set for the PREVIOUS obstacle (prev obst MUST be added in this way)
//                int obstID = 0;
//                if(!old.equals("ALG")) {
//                    obstID = Integer.parseInt(old.substring(old.lastIndexOf(",") + 1)) + 1;
//                }
//                if(!isObstacle) obstID = -1;
//
//                String obstString = "|" + (col * 10 + 5) + "," + (row * 10 + 5) + "," + obstDir + "," + obstID;
//                String newString = old + obstString;
//                chatInput.setText(newString);
//                getDialog().dismiss();
                gridMap.invalidate();
                refreshObstacleList();
                showToast("Obstacle added");
                showLog("Exiting addManualBtn");
            }
        });

        refreshObstacleList();

        return rootView;
    }

    private void refreshObstacleList() {
        if (obstacleListContainer == null || gridMap == null) return;
        obstacleListContainer.removeAllViews();
        for (int i = 0; i < gridMap.getObstaclesList().size(); i++) {
            int[] obstacle = gridMap.getObstaclesList().get(i);
            Button row = new Button(requireContext());
            String direction = gridMap.imageBearings.get(obstacle[1])[obstacle[0]];
            row.setText("Obstacle " + gridMap.getObstacleId(i) + "  (X: " + obstacle[0] + ", Y: " + obstacle[1] + ", " + direction + ")");
            final int index = i;
            row.setOnClickListener(v -> showObstacleEditor(index));
            obstacleListContainer.addView(row);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // The map is shared with the MAP CONFIG tab. Rebuild this list every time
        // the tab becomes visible so additions, moves, rotations, and deletions
        // made on the grid are reflected immediately.
        gridMap = Home.getGridMap();
        refreshObstacleList();
    }

    private void showObstacleEditor(int index) {
        if (index < 0 || index >= gridMap.getObstaclesList().size()) return;
        int[] obstacle = gridMap.getObstaclesList().get(index);
        LinearLayout editor = new LinearLayout(requireContext());
        editor.setOrientation(LinearLayout.HORIZONTAL);
        Spinner x = createSpinner(R.array.obstID_array, obstacle[0]);
        Spinner y = createSpinner(R.array.obstID_array, obstacle[1]);
        Spinner direction = createSpinner(R.array.obstDir_array, gridMap.imageBearings.get(obstacle[1])[obstacle[0]]);
        editor.addView(x); editor.addView(y); editor.addView(direction);
        new AlertDialog.Builder(requireContext()).setTitle("Edit obstacle " + (index + 1))
                .setView(editor)
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Delete", (d, w) -> { gridMap.deleteObstacle(index); refreshObstacleList(); })
                .setPositiveButton("Update", (d, w) -> {
                    boolean updated = gridMap.updateManualObstacle(index, Integer.parseInt(x.getSelectedItem().toString()), Integer.parseInt(y.getSelectedItem().toString()), direction.getSelectedItem().toString());
                    if (updated) refreshObstacleList();
                    else showToast("That grid cell is already occupied");
                }).show();
    }

    private Spinner createSpinner(int arrayId, int selection) {
        Spinner spinner = new Spinner(requireContext());
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(requireContext(), arrayId, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(selection);
        return spinner;
    }

    private Spinner createSpinner(int arrayId, String selection) {
        Spinner spinner = createSpinner(arrayId, 0);
        ArrayAdapter adapter = (ArrayAdapter) spinner.getAdapter();
        int position = adapter.getPosition(selection);
        if (position >= 0) spinner.setSelection(position);
        return spinner;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        showLog("Entering onSaveInstanceState");
        super.onSaveInstanceState(outState);
        showLog("Exiting onSaveInstanceState");
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        showLog("Entering onDismiss");
        super.onDismiss(dialog);
        showLog("Exiting onDismiss");
    }

    private void showLog(String message) {
        Log.d(TAG, message);
    }

    private void showToast(String message) { Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show(); }
}
