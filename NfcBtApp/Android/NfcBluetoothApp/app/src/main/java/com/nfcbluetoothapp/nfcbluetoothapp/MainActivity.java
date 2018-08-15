package com.nfcbluetoothapp.nfcbluetoothapp;

import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;
import android.widget.Button;
import android.view.View;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final Button pairingButton = (Button)findViewById(R.id.pairingButton);
        pairingButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                pairing();
            }
        });

        final Button rButton = (Button)findViewById(R.id.sendActivity);
        rButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                sendFiles();
            }
        });
    }

    public void sendFiles() {
        startActivity(new Intent(this, SendActivity.class));
    }

    public void pairing() {
        startActivity(new Intent(this, PairingActivity.class));
    }
}
