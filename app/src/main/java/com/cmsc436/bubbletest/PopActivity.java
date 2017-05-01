package com.cmsc436.bubbletest;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

import edu.umd.cmsc436.sheets.Sheets;

import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Random;

public class PopActivity extends Activity implements Sheets.Host {

    //int totalBubbles = 0;
    int poppedBubbles = 0;
    long timeOfBirth;
    long timeOfDeath;

    // distance between old and new bubble to be tapped
    final int BUBBLE_RADIUS = 500;
    final int CORRECTION_FACTOR = 300;

    // to store response times
    private ArrayList<Double> lifespans;

    int oldBubbleX;
    int oldBubbleY;
    int newBubbleX;
    int newBubbleY;

    Button startTrial;
    Button bubble;
    TextView debugNarrator;

    private String today;

    public static final int LIB_ACCOUNT_NAME_REQUEST_CODE = 1001;
    public static final int LIB_AUTHORIZATION_REQUEST_CODE = 1002;
    public static final int LIB_PERMISSION_REQUEST_CODE = 1003;
    public static final int LIB_PLAY_SERVICES_REQUEST_CODE = 1004;

    // main spreadsheet information
    private Sheets centralSheet;
    private Sheets teamSheet;

    private String centralSpreadsheetId = "1YvI3CjS4ZlZQDYi5PaiA7WGGcoCsZfLoSFM0IdvdbDU";
    private String teamSpreadsheetId = "1jus0ktF2tQw2sOjsxVb4zoDeD1Zw90KAMFNTQdkFiJQ";

    private static String USER_ID;
    private static Sheets.TestType APPENDAGE;

    // indicates if test should write to central spreadsheet
    private static boolean WRITE_TO_CENTRAL = false;
    private static boolean IN_PRACTICE_MODE = false;

    //private long secs,mins,hrs;
    //private String minutes,seconds;
    private long startTime;
    private long elapsedTime;
    private Handler mHandler = new Handler();
    private final int REFRESH_RATE = 100;

    private Runnable startTimer = new Runnable() {
        public void run() {
            elapsedTime = System.currentTimeMillis() - startTime;
            //updateTimer(elapsedTime);
            if(elapsedTime < 10000) {
                mHandler.postDelayed(this, REFRESH_RATE);
            } else {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        bubble.setVisibility(View.GONE);
                        if(!writtenToSheets) {
                            completeTrial();
                        }
                    }
                });
            }
        }
    };

    boolean writtenToSheets = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bubble);

        RelativeLayout rl = (RelativeLayout)findViewById(R.id.activity_bubble);

        debugNarrator = (TextView) findViewById(R.id.debugNarrator);
        debugNarrator.setVisibility(View.INVISIBLE);

        // initialize sheet

        centralSheet = new Sheets(this, this, getString(R.string.app_name), centralSpreadsheetId, centralSpreadsheetId);
        teamSheet = new Sheets(this, this, getString(R.string.app_name), teamSpreadsheetId, teamSpreadsheetId);
        showInstructions(rl);

        bubble = (Button) findViewById(R.id.bubble);

        // the bubble should not be visible until the trial has started
        bubble.setVisibility(View.GONE);

        Intent intent = getIntent();
        String action = intent.getAction();

        if (action == null) {
            //TODO: Determine what to do for null intent Action
        }

        Log.d("ACTION", action);

        if (action.equals("edu.umd.cmsc436.pop.action.TRIAL")) {
            //the intent is the TRIAL, so we gotta send data to the main sheet
            WRITE_TO_CENTRAL = true;
            IN_PRACTICE_MODE = false;

            Bundle extras = intent.getExtras();
            if (extras != null) {
                USER_ID = extras.getString("patient id");
                switch (extras.getString("appendage")) {
                    case "RH_POP":
                        APPENDAGE = Sheets.TestType.RH_POP;
                        break;
                    case "LH_POP":
                        APPENDAGE = Sheets.TestType.LH_POP;
                        break;
                }
                Log.d("APPENDAGE", String.valueOf(APPENDAGE));
                //TODO: Determine what to do for invalid appendage argument
            }
        } else if (action.equals("edu.umd.cmsc436.pop.action.PRACTICE")) {
            WRITE_TO_CENTRAL = false;
            IN_PRACTICE_MODE = true;
        } else if (action.equals("edu.umd.cmsc436.pop.action.HELP")) {
            WRITE_TO_CENTRAL = false;
            IN_PRACTICE_MODE = true;
        }

        startTrial = (Button) findViewById(R.id.startTrial);
        startTrial.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startTime = System.currentTimeMillis();
                startTimer.run();
                startTest();
                today = USER_ID + " " + (new Timestamp(System.currentTimeMillis())).toString();
                // remove the start trial button
                startTrial.setVisibility(View.INVISIBLE);
                findViewById(R.id.helpButton).setVisibility(View.GONE);
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        lifespans= new ArrayList<Double>();
    }

    @Override
    protected void onStop() {
        super.onStop();
        bubble.setVisibility(View.GONE);
        if(!writtenToSheets){
            Log.i("Test", "PARTIAL trial");
            WRITE_TO_CENTRAL = false;
            today = "PARTIAL TRIAL " + today;
            completeTrial();
        }
    }

    public void startTest() {
        initialLocation();
        bubble.setVisibility(View.VISIBLE);
    }

    /*
    Given the start point and a fixed radius, generate a random x,y pair
     */
    public void randomEuclideanDistancePointsGenerator() {
        int rangeMin = 0;
        int rangeMax = 360;

        Random r = new Random(); // generate a random angle value
        double randomAngle = rangeMin + (rangeMax - rangeMin) * r.nextDouble();


        double x = oldBubbleX + BUBBLE_RADIUS * Math.cos(randomAngle);
        double y = oldBubbleY + BUBBLE_RADIUS * Math.sin(randomAngle);

        // make sure that a bubble set at the new x and y would fit in the layout
        bubble = (Button) findViewById(R.id.bubble);

        // get screen dimensions
        RelativeLayout.LayoutParams scene = (RelativeLayout.LayoutParams) bubble.getLayoutParams();
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);

        int layoutWidth = metrics.widthPixels;
        int layoutHeight = metrics.heightPixels;

        boolean legalBubbleLocation = (x + bubble.getWidth() <= layoutWidth-CORRECTION_FACTOR) &&
                (y + bubble.getHeight() <= layoutHeight-CORRECTION_FACTOR) &&
                (x >= 0) &&
                (y >= 0);

        while (!legalBubbleLocation) {
            //Log.i("BubbleAct","illegalBubbleLocation");
            randomAngle = rangeMin + (rangeMax - rangeMin) * r.nextDouble();
            x = oldBubbleX + BUBBLE_RADIUS * Math.cos(randomAngle);
            y = oldBubbleY + BUBBLE_RADIUS * Math.sin(randomAngle);

            // check location again
            legalBubbleLocation = (x + bubble.getWidth() <= layoutWidth-CORRECTION_FACTOR) &&
                    (y + bubble.getHeight() <= layoutHeight-CORRECTION_FACTOR) &&
                    (x >= 0) &&
                    (y >= 0);
        }

        //Log.i("BubbleAct",layoutWidth +", " + layoutHeight);
        //Log.i("BubbleAct",x +", " + y);
        //Log.i("BubbleAct",bubble.getWidth() +", " + bubble.getHeight()+"\n");

        scene.leftMargin = (int) x;
        scene.topMargin = (int) y;

        // set bubble at new location
        bubble.setLayoutParams(scene);
        bubble.setVisibility(View.VISIBLE);

        // save time of appearance as time of birth
        timeOfBirth = System.nanoTime();

        // increment trialNum
        //totalBubbles++;
        //Log.i("BubbleAct",totalBubbles + " bubbles popped");

        // TODO figure out a way to preserve accuracy by not having to cast these values to ints
        oldBubbleX = (int)x;
        oldBubbleY = (int)y;

        //moveBubble();
    }

    /*
    The bubble should be moved to a constant distance of 50 pixels away from the current bubble.
    The distance can be the radius of a circle drawn around the most recent bubble.
    The next bubble will pop up instantaneously.
    Trial lasts for 10 seconds.
     */
    public void moveBubble() {
        bubble.setVisibility(View.GONE);
        randomEuclideanDistancePointsGenerator();
    }

    private void completeTrial() {
        //totalBubbles = 100;

        double result = 0.0;
        DecimalFormat precision = new DecimalFormat("0.00");

        if(bubble.getVisibility() == View.VISIBLE){
            bubble.setVisibility(View.GONE);
        }

        if (poppedBubbles > 0) {
            double totalReactionTime = 0;
            for (int i = 0; i < lifespans.size(); i++) {
                Log.i("Lifespan", "" + lifespans.get(i));
                totalReactionTime += lifespans.get(i);
                //LH_POP WILL HAVE LIFESPANS
                teamSheet.writeData(Sheets.TestType.LH_POP, today, new Float(lifespans.get(i)));
            }
            result = totalReactionTime / poppedBubbles;
            double stdDev = standardDeviation(lifespans, totalReactionTime/lifespans.size());
            Log.i("stdDev", "" + stdDev);
            teamSheet.writeData(Sheets.TestType.LH_CURL, today, (float) stdDev);

        } else {
            result = 0.0;
        }
        //RH_POP FINAL RESULTS
        Log.i("result", "" + result);
        teamSheet.writeData(Sheets.TestType.RH_POP, today, (float) result);


        Intent data = new Intent();
        data.putExtra("float", result);

        Log.d("MODES", IN_PRACTICE_MODE + " + " + WRITE_TO_CENTRAL);

        if (WRITE_TO_CENTRAL) {
            //Only write to central sheet if intent is TRIAL
            centralSheet.writeData(APPENDAGE, USER_ID, (float) result);
            setResult(Activity.RESULT_OK, data);
        } else {
            //this means the user either closed the program early, or they did PRACTICE
            setResult(Activity.RESULT_CANCELED, data);
        }

        writtenToSheets = true;

        TextView resultScreen = (TextView) findViewById(R.id.showResult);

        resultScreen.setText("You hit " + poppedBubbles + " bubbles.\n"
                        + "Your average tap response time was " + precision.format(result)
                        + " seconds.\n"
                //+ detailData
        );
        resultScreen.setTextSize(40);
        resultScreen.setVisibility(View.VISIBLE);

        //A button to let the user finish the activity
        (findViewById(R.id.done_button)).setVisibility(View.VISIBLE);
    }

    public void initialLocation() {
        //TODO: FIX
        bubble = (Button) findViewById(R.id.bubble);

        // get screen dimensions
        RelativeLayout.LayoutParams scene = (RelativeLayout.LayoutParams) bubble.getLayoutParams();
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);

        // set initial location for bubble
        int fullWidth = metrics.widthPixels;
        int fullHeight = metrics.heightPixels;
        int x = bubble.getWidth()
                + new Random()
                .nextInt(fullWidth - (5 * bubble.getWidth()));
        int y = bubble.getHeight()
                + new Random()
                .nextInt(fullHeight - (5 * bubble.getHeight()));


        boolean legalBubbleLocation = (x + bubble.getWidth() <= fullWidth-CORRECTION_FACTOR) &&
                (y + bubble.getHeight() <= fullHeight-CORRECTION_FACTOR) &&
                (x >= 0) &&
                (y >= 0);

        while (!legalBubbleLocation) {
            Log.i("BubbleAct", "illegalBubbleLocation");
            x = bubble.getWidth()
                    + new Random()
                    .nextInt(fullWidth - (5 * bubble.getWidth()));
            y = bubble.getHeight()
                    + new Random()
                    .nextInt(fullHeight - (5 * bubble.getHeight()));

            // check location again
            legalBubbleLocation = (x + bubble.getWidth() <= fullWidth-CORRECTION_FACTOR) &&
                    (y + bubble.getHeight() <= fullHeight-CORRECTION_FACTOR) &&
                    (x >= 0) &&
                    (y >= 0);
        }

        /*
        // FOR DEBUG PURPOSES
        debugNarrator.setText("full: " + fullWidth + " x " + fullHeight + "\n"
                + "location: " + x + " x " + y);
        */

        scene.leftMargin = x;
        scene.topMargin = y;

        oldBubbleX = x;
        oldBubbleY = y;
        newBubbleX = x;
        newBubbleY = y;

        bubble.setLayoutParams(scene);

        // save time of appearance as time of birth
        timeOfBirth =  System.nanoTime();


        bubble.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                poppedBubbles++;
                timeOfDeath = System.nanoTime();
                saveLife();
                moveBubble();
            }
        });
    }

    private void saveLife() {
        double lifespan = ((timeOfDeath - timeOfBirth)/1000000000.0);
        lifespans.add(lifespan);
    }

    // the following four methods for Sheet implementation have been copied directly from the
    // class example app
    @Override
    public void onRequestPermissionsResult (int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        centralSheet.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        centralSheet.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public int getRequestCode(Sheets.Action action) {
        switch (action) {
            case REQUEST_ACCOUNT_NAME:
                return LIB_ACCOUNT_NAME_REQUEST_CODE;
            case REQUEST_AUTHORIZATION:
                return LIB_AUTHORIZATION_REQUEST_CODE;
            case REQUEST_PERMISSIONS:
                return LIB_PERMISSION_REQUEST_CODE;
            case REQUEST_PLAY_SERVICES:
                return LIB_PLAY_SERVICES_REQUEST_CODE;
            default:
                return -1;
        }
    }

    @Override
    public void notifyFinished(Exception e) {
        if (e != null) {
            System.out.println(e.getClass());
            throw new RuntimeException(e);
        }
        Log.i(getClass().getSimpleName(), "Done");
    }

    public void showInstructions(View view) {
        AlertDialog.Builder builder = new AlertDialog.Builder(PopActivity.this);
        if (IN_PRACTICE_MODE)
            builder.setTitle("Pop Practice Instructions");
        else
            builder.setTitle("Pop Test Instructions");
        builder.setMessage("Try to hit as many bubbles as you can once the test starts");

        builder.setPositiveButton("Okay", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // if this button is clicked, close current activity
            }
        });
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
        TextView textView = (TextView) alertDialog.findViewById(android.R.id.message);
        textView.setTextSize(40);
    }

    //The user hit the done_button, so we can finish the activity and send data back to Front End
    public void sendResultsToFrontEnd(View view) {
        finish();
    }

    private double standardDeviation(ArrayList<Double> arr, double average){

        if(arr.size() < 2){
            return 0;
        }

        double stdDev = 0;
        for(Double d: arr){
            stdDev += ((d - average)*(d - average)) / (arr.size()-1);
        }
        return Math.sqrt(stdDev);
    }


}
