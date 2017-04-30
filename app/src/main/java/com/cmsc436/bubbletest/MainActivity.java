package com.cmsc436.bubbletest;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.os.*;

import edu.umd.cmsc436.sheets.Sheets;


public class MainActivity extends AppCompatActivity {

    // remove these later
    private Button trialButton;
    private Button practiceButton;
    private Button helpButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // remove this line
        setContentView(R.layout.activity_main);

        final Context context = this;

        /* Add these lines back in */
//        Intent intent = new Intent(context, BubbleActivity.class);
//        startActivity(intent);

        // remove everything below here
        trialButton = (Button) findViewById(R.id.trial_button);
        practiceButton = (Button) findViewById(R.id.practice_button);
        helpButton = (Button) findViewById(R.id.help_button);

        setListeners();
    }

    private void setListeners() {

        trialButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Intent intent = new Intent("edu.umd.cmsc436.pop.action.TRIAL");
                intent.addCategory(Intent.CATEGORY_DEFAULT);

                // hard coded tag names from
                intent.putExtra("appendage", "LH_POP");
                intent.putExtra("trial num", 1);
                intent.putExtra("trial out of", 3);
                intent.putExtra("difficulty", 1);
                intent.putExtra("patient id", "t04p03");

                startActivity(intent);
            }
        });

        practiceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent("edu.umd.cmsc436.pop.action.PRACTICE");
                intent.addCategory(Intent.CATEGORY_DEFAULT);
                startActivity(intent);
            }
        });

        helpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent("edu.umd.cmsc436.pop.action.HELP");
                intent.addCategory(Intent.CATEGORY_DEFAULT);
                startActivity(intent);
            }
        });
    }
}
