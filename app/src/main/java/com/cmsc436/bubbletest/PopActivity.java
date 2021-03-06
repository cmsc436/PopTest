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
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Random;

import edu.umd.cmsc436.frontendhelper.TrialMode;
import edu.umd.cmsc436.sheets.Sheets;

public class PopActivity extends Activity implements Sheets.Host {

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
    private Sheets sheet;

    private String centralSpreadsheetId = "1YvI3CjS4ZlZQDYi5PaiA7WGGcoCsZfLoSFM0IdvdbDU";
    private String teamSpreadsheetId = "1jus0ktF2tQw2sOjsxVb4zoDeD1Zw90KAMFNTQdkFiJQ";

    private static String USER_ID = "PRACTICE";
    private static Sheets.TestType APPENDAGE;
    private static int TRIAL_NUM;
    private static int TRIAL_OUT_OF;
    private static int DIFFICULTY;

    // indicates if test should write to central spreadsheet
    //private static boolean WRITE_TO_CENTRAL = false;
    private static boolean IN_PRACTICE_MODE = false;

    private long startTime;
    private long elapsedTime;
    private Handler mHandler = new Handler();
    private final int REFRESH_RATE = 100;

    private boolean errored = false;

    private Runnable startTimer = new Runnable() {
        public void run() {
            elapsedTime = System.currentTimeMillis() - startTime;
            // length of trial
            if(elapsedTime < 25000) {
                mHandler.postDelayed(this, REFRESH_RATE);
            } else {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        errored = false;
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
        setContentView(R.layout.activity_pop);

        RelativeLayout rl = (RelativeLayout)findViewById(R.id.activity_bubble);

        debugNarrator = (TextView) findViewById(R.id.debugNarrator);
        debugNarrator.setVisibility(View.INVISIBLE);

        // initialize sheet
        sheet = new Sheets(this, this, getString(R.string.app_name), centralSpreadsheetId, teamSpreadsheetId);

        bubble = (Button) findViewById(R.id.bubble);

        // the bubble should not be visible until the trial has started
        bubble.setVisibility(View.GONE);

        Intent intent = getIntent();
        String action = intent.getAction();

        if (action == null) {
        }

        if (action.equals("edu.umd.cmsc436.pop.action.TRIAL")) {
            // the intent is the TRIAL
            IN_PRACTICE_MODE = false;

            USER_ID = TrialMode.getPatientId(intent);
            APPENDAGE = TrialMode.getAppendage(intent);
            DIFFICULTY = TrialMode.getDifficulty(intent);
            TRIAL_NUM = TrialMode.getTrialNum(intent);
            TRIAL_OUT_OF = TrialMode.getTrialOutOf(intent);

        } else if (action.equals("edu.umd.cmsc436.pop.action.PRACTICE")) {
            IN_PRACTICE_MODE = true;
        } else if (action.equals("edu.umd.cmsc436.pop.action.HELP")) {
            IN_PRACTICE_MODE = true;
        }

        startTrial = (Button) findViewById(R.id.startTrial);
        startTrial.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startTime = System.currentTimeMillis();
                startTimer.run();
                startTest();
                today = USER_ID;
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
            errored = true;
            today = "PARTIAL TRIAL " + today;
            completeTrial();
        }
    }

    /*
    This method initiates the test
     */
    public void startTest() {
        initialLocation();
        bubble.setVisibility(View.VISIBLE);
    }

    /*
    This method generates a new x and y location for the bubble to appear based off of the location
    of the previous bubble
     */
    public void randomEuclideanDistancePointsGenerator() {
        int rangeMin = 0;
        int rangeMax = 360;

        // generate a random angle value
        Random r = new Random();
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

        // ensure the location is legal
        boolean legalBubbleLocation = (x + bubble.getWidth() <= layoutWidth-CORRECTION_FACTOR) &&
                (y + bubble.getHeight() <= layoutHeight-CORRECTION_FACTOR) &&
                (x >= 0) &&
                (y >= 0);

        // if the location is illegal, produce a new location
        while (!legalBubbleLocation) {
            randomAngle = rangeMin + (rangeMax - rangeMin) * r.nextDouble();
            x = oldBubbleX + BUBBLE_RADIUS * Math.cos(randomAngle);
            y = oldBubbleY + BUBBLE_RADIUS * Math.sin(randomAngle);

            // check location again
            legalBubbleLocation = (x + bubble.getWidth() <= layoutWidth-CORRECTION_FACTOR) &&
                    (y + bubble.getHeight() <= layoutHeight-CORRECTION_FACTOR) &&
                    (x >= 0) &&
                    (y >= 0);
        }

        scene.leftMargin = (int) x;
        scene.topMargin = (int) y;

        // set bubble at new location
        bubble.setLayoutParams(scene);
        bubble.setVisibility(View.VISIBLE);

        // save time of appearance as time of birth
        timeOfBirth = System.nanoTime();

        oldBubbleX = (int)x;
        oldBubbleY = (int)y;
    }

    /*
    This method generates a new bubble.
    The bubble should be moved to a constant distance away from the current bubble.
    The distance can be the radius of a circle drawn around the most recent bubble.
    The new bubble will pop up instantaneously.
     */
    public void moveBubble() {
        bubble.setVisibility(View.GONE);
        randomEuclideanDistancePointsGenerator();
    }

    /*
    This method writes all necessary data to their respective spreadsheets and presents the user
    with their singular score
     */
    private void completeTrial() {
        double result = 0.0;
        DecimalFormat precision = new DecimalFormat("0.00");

        // remove bubble
        if(bubble.getVisibility() == View.VISIBLE){
            bubble.setVisibility(View.GONE);
        }

        float[] ls = new float[lifespans.size()];

        if (poppedBubbles > 0) {
            double totalReactionTime = 0;
            for (int i = 0; i < lifespans.size(); i++) {
                totalReactionTime += lifespans.get(i);
                ls[i] = new Float(lifespans.get(i));
            }
            sheet.writeTrials(Sheets.TestType.LH_POP,today,ls);
            result = totalReactionTime / poppedBubbles;
            Log.i("if", result + " " + poppedBubbles);
            double stdDev = standardDeviation(lifespans, totalReactionTime/lifespans.size());
            float[] stD = {(float) stdDev};
            // TODO: why is this writing to LH_CURL?
            sheet.writeTrials(Sheets.TestType.LH_CURL, today,stD);
        } else {
            result = 0.0;
            Log.i("else", "" + poppedBubbles);
        }

        // score should be altered based on difficulty setting
        double scoreMultiplier;
        switch (DIFFICULTY) {
            case 1: scoreMultiplier = 1;
                break;
            case 2: scoreMultiplier = 1.2;
                break;
            case 3: scoreMultiplier = 1.5;
                break;
            default: scoreMultiplier = 1;
                break;
        }
        float scoreMultiplied = (float) (result * scoreMultiplier);
        Log.i("result", "" + scoreMultiplied);
        float[] res = {scoreMultiplied};

        sheet.writeTrials(Sheets.TestType.RH_POP, today, res);

        if(errored){
            //this means the user closed the program early
            setResult(Activity.RESULT_CANCELED, TrialMode.getResultIntent(scoreMultiplied));
        } else if (!IN_PRACTICE_MODE) {
            //Only write to central sheet if intent is TRIAL
            setResult(Activity.RESULT_OK, TrialMode.getResultIntent(scoreMultiplied));
        }

        writtenToSheets = true;

        // present the singular score to the user
        TextView resultScreen = (TextView) findViewById(R.id.showResult);
        resultScreen.setText("You hit " + poppedBubbles + " bubbles.\n"
                        + "Your average tap response time was " + precision.format(result)
                        + " seconds.\n"
                        + "Your score was " + precision.format(scoreMultiplied)
                        + "\n"
                //+ detailData
        );
        resultScreen.setTextSize(40);
        resultScreen.setVisibility(View.VISIBLE);

        //A button to let the user finish the activity
        (findViewById(R.id.done_button)).setVisibility(View.VISIBLE);
    }

    /*
    This method calculates an initial location to place the bubble and an initial size of the bubble
    based on the difficulty setting
     */
    public void initialLocation() {
        bubble = (Button) findViewById(R.id.bubble);

        Log.i("Bubble",bubble.getWidth() + " " + bubble.getHeight());
        ViewGroup.LayoutParams params = bubble.getLayoutParams();

        // alter size of bubble based on difficulty
        switch (DIFFICULTY) {
            case 1:
                params.width = dpToPx(100);
                params.height = dpToPx(100);
                break;
            case 2:
                params.width = dpToPx(70);
                params.height = dpToPx(70);
                break;
            case 3:
                params.width = dpToPx(50);
                params.height = dpToPx(50);
                break;
            default:
                params.width = dpToPx(100);
                params.height = dpToPx(100);
                break;
        }
        bubble.setLayoutParams(params);

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

        // ensure the location is legal
        boolean legalBubbleLocation = (x + bubble.getWidth() <= fullWidth-CORRECTION_FACTOR) &&
                (y + bubble.getHeight() <= fullHeight-CORRECTION_FACTOR) &&
                (x >= 0) &&
                (y >= 0);

        // if the location is illegal, produce a new legal location
        while (!legalBubbleLocation) {
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

        scene.leftMargin = x;
        scene.topMargin = y;

        oldBubbleX = x;
        oldBubbleY = y;
        newBubbleX = x;
        newBubbleY = y;

        bubble.setLayoutParams(scene);

        // save time of appearance as time of birth
        timeOfBirth =  System.nanoTime();

        // popping a bubble should record its lifespan and generate another bubble
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

    /*
    This method records the lifespan of the recently popped bubble and adds the lifespan to the
    list of lifespans being recorded for this trial
     */
    private void saveLife() {
        double lifespan = ((timeOfDeath - timeOfBirth)/1000000000.0);
        lifespans.add(lifespan);
    }

    /*
    The following four methods for Sheet implementation have been copied directly from the cmsc436
    Sheets examples
     */
    @Override
    public void onRequestPermissionsResult (int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        sheet.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        sheet.onActivityResult(requestCode, resultCode, data);
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
    }

    /*
    This method presents instructions for the user
     */
    public void showInstructions(View view) {
        AlertDialog.Builder builder = new AlertDialog.Builder(PopActivity.this);
        if (IN_PRACTICE_MODE) {
            builder.setTitle("Pop Practice Instructions");
        } else {
            builder.setTitle("Pop Test Instructions");
        }
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

    /*
    This method is called when the user has notified of completion and results can be sent to the
    Front End
     */
    public void sendResultsToFrontEnd(View view) {
        finish();
    }

    /*
    This method is used to calculate detailed data to be recorded in spreadsheets
     */
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

    /*
    This method is used to convert DP to PX
     */
    public int dpToPx(int dp) {
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        return Math.round(dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
    }

}
