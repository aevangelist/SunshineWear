package com.example.android.sunshine.app;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener{

    private static final String LOG_TAG = "WEAR MainActivity";


    private static final long TIMEOUT_MS = (3 * 60000);

    private static final String WEARABLE_DATA_PATH = "/wearable_data";
    private static final String MIN_TEMP = "min";
    private static final String MAX_TEMP = "max";
    private static final String WEATHER_ICON = "icon";

    private Bitmap bitmap;

    private TextView mMinTemp;
    private TextView mMaxTemp;
    private ImageView mWeatherIcon;

    private TextView mWearTime;
    private TextView mWearDate;

    //Google API Client
    GoogleApiClient googleAPIClient;

    private final static IntentFilter INTENT_FILTER;
    static {
        INTENT_FILTER = new IntentFilter();
        INTENT_FILTER.addAction(Intent.ACTION_TIME_TICK);
        INTENT_FILTER.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        INTENT_FILTER.addAction(Intent.ACTION_TIME_CHANGED);
    }

    private final String TIME_FORMAT_DISPLAYED = "kk:mm";
    private final String DATE_FORMAT_DISPLAYED = "EEE, MMM dd yyyy";


    private BroadcastReceiver mTimeInfoReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context arg0, Intent intent) {
            mWearTime.setText(
                    new SimpleDateFormat(TIME_FORMAT_DISPLAYED)
                            .format(Calendar.getInstance().getTime()));
            mWearDate.setText(
                    new SimpleDateFormat(DATE_FORMAT_DISPLAYED)
                            .format(Calendar.getInstance().getTime()));
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mMinTemp = (TextView) stub.findViewById(R.id.wearTempMin);
                mMaxTemp = (TextView) stub.findViewById(R.id.wearTempMax);
                mWeatherIcon = (ImageView) stub.findViewById(R.id.weatherIcon);

                mWearTime = (TextView) stub.findViewById(R.id.wearTime);
                mWearDate = (TextView) stub.findViewById(R.id.wearDate);

                mTimeInfoReceiver.onReceive(MainActivity.this, registerReceiver(null, INTENT_FILTER));
                //  Here, we're just calling our onReceive() so it can set the current time.
                registerReceiver(mTimeInfoReceiver, INTENT_FILTER);

            }
        });

        // Register the local broadcast receiver
        IntentFilter messageFilter = new IntentFilter(Intent.ACTION_SEND);
        MessageReceiver messageReceiver = new MessageReceiver();
        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver, messageFilter);

        Log.i("MainActivity", "Running the watch");

        //Build your Google API Client
        googleAPIClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        Intent i = new Intent(this, SunshineWatchFace.class);
        startService(i);
    }

    @Override
    protected void onStart() {
        super.onStart();
        googleAPIClient.connect();
        Log.d("", "Connected to Google API Client");
    }


    // Disconnect from the data layer when the Activity stops
    @Override
    protected void onStop() {
        if (null != googleAPIClient && googleAPIClient.isConnected()) {
            googleAPIClient.disconnect();
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mTimeInfoReceiver);
    }

    @Override
    public void onConnected(Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

    public class MessageReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {

            //Toast.makeText(context, "Intent Detected.", Toast.LENGTH_LONG).show();

            String minTemp = intent.getStringExtra(MIN_TEMP);
            String maxTemp = intent.getStringExtra(MAX_TEMP);
            Asset iconAsset = intent.getParcelableExtra(WEATHER_ICON);

            //Requires a new thread to avoid blocking the UI
            new LoadBitmapThread(iconAsset).start();

            Log.i("MainActivity", "Broadcast received on MAIN: " + minTemp + " " + maxTemp + " " + bitmap);

            //Display received data in UI
            mMinTemp.setText(minTemp);
            mMaxTemp.setText(maxTemp);
            mWeatherIcon.setImageBitmap(bitmap);

        }
    }

    /**
     * Inner class to send  data object to all nodes currently connected to the data layer
     * Runs on a new thread
     */
    class LoadBitmapThread extends Thread {
        Asset asset;

        // Constructor
        LoadBitmapThread(Asset a) {
            asset = a;
        }

        public void run() {
            if (asset == null) {
                throw new IllegalArgumentException("Asset must be non-null");
            }

            ConnectionResult result =
                    googleAPIClient.blockingConnect(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!result.isSuccess()) {
                Log.w(LOG_TAG, "Unable to connect to GoogleAPIClient");
            }
            // convert asset into a file descriptor and block until it's ready
            InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                    googleAPIClient, asset).await().getInputStream();
            googleAPIClient.disconnect();

            if (assetInputStream == null) {
                Log.w(LOG_TAG, "Requested an unknown Asset.");
            }
            // decode the stream into a bitmap
            Bitmap b =  BitmapFactory.decodeStream(assetInputStream);
            bitmap = b;
        }
    }

}
