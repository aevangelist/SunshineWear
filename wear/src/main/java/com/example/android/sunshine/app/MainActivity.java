package com.example.android.sunshine.app;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final String WEARABLE_DATA_PATH = "/wearable_data";
    private static final String MIN_TEMP = "min";
    private static final String MAX_TEMP = "max";

    private TextView mTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mTextView = (TextView) stub.findViewById(R.id.text);
            }
        });

        // Register the local broadcast receiver
        IntentFilter messageFilter = new IntentFilter(Intent.ACTION_SEND);
        MessageReceiver messageReceiver = new MessageReceiver();
        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver, messageFilter);

        //Toast.makeText(this, "dhg;ksdhgsdg.", Toast.LENGTH_LONG).show();
        Log.i("MainActivity", "Running the watch");
    }

    public class MessageReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {

            Toast.makeText(context, "Intent Detected.", Toast.LENGTH_LONG).show();

            String message = intent.getStringExtra(MIN_TEMP);
            Log.i("MainActivity", "Broadcast received on watch: " + message);

            Bundle data = intent.getBundleExtra(MIN_TEMP);

            // Display received data in UI
            String display = "Received from the data Layer\n" +
                    "Hole: " + data.getString("hole") + "\n" +
                    "Front: " + data.getString("front") + "\n" +
                    "Middle: "+ data.getString("middle") + "\n" +
                    "Back: " + data.getString("back");
            mTextView.setText(display);
        }
    }

}
