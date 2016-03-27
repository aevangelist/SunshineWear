package com.example.android.sunshine.app;

import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * Created by aevangelista on 16-03-26.
 */
public class ListenerService extends WearableListenerService {

    private static final String LOG_TAG = "ListenerService";

    private static final String WEARABLE_DATA_PATH = "/wearable_data";
    private static final String MIN_TEMP = "min";
    private static final String MAX_TEMP = "max";
    private static final String WEATHER_ICON = "icon";


    @Override
    public void onCreate()
    {
        Log.i(LOG_TAG, "##DataService created");
        super.onCreate();

    }

    @Override
    public void onDestroy()
    {
        Log.i(LOG_TAG, "##DataService destroyed");
        super.onDestroy();
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent)
    {
        Log.i(LOG_TAG, "##DataService received " + messageEvent.getPath());
    }

    @Override
    public void onPeerConnected(Node peer) {
        Log.i(LOG_TAG, "##Peer connected ");
    }


    @Override
    public void onDataChanged(DataEventBuffer dataEvents){

        Log.i(LOG_TAG, "The data to be sent has changed");

        DataMap dataMap;

        for(DataEvent dataEvent : dataEvents){

            //Check data type
            if(dataEvent.getType() == DataEvent.TYPE_CHANGED){

                //Check data path
                String path = dataEvent.getDataItem().getUri().getPath();
                if(path.equals(WEARABLE_DATA_PATH)){
                    dataMap = DataMapItem.fromDataItem(dataEvent.getDataItem()).getDataMap();

                    String minTemp = dataMap.getString(MIN_TEMP);
                    String maxTemp = dataMap.getString(MAX_TEMP);
                    Asset iconAsset = dataMap.getAsset(WEATHER_ICON);


                    // Broadcast message to wearable activity for display
                    Intent dataIntent = new Intent();
                    dataIntent.setAction(Intent.ACTION_SEND);
                    dataIntent.putExtra(MIN_TEMP, minTemp);
                    dataIntent.putExtra(MAX_TEMP, maxTemp);
                    dataIntent.putExtra(WEATHER_ICON, iconAsset);
                    LocalBroadcastManager.getInstance(this).sendBroadcast(dataIntent);

                    Log.d(LOG_TAG, "DataMap received on watch: " + minTemp + " " + maxTemp);

                }
            }
        }
    }
}
