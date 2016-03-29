/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public class SunshineWatchFace extends CanvasWatchFaceService {

    private static final String LOG_TAG = ".SunshineWatchFace";

    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);
    private static final int MSG_UPDATE_TIME = 0;

    private final String TIME_FORMAT_DISPLAYED = "kk:mm";
    private final String DAY_FORMAT_DISPLAYED = "EEEE";
    private final String DATE_FORMAT_DISPLAYED = "MMM dd yyyy";

    private GoogleApiClient googleApiClient;
    private static final long TIMEOUT_MS = (3 * 60000);

    /**
     * Variables from OPENWeatherAPI
     **/
    private static final String WEARABLE_DATA_PATH = "/wearable_data";
    private static final String MIN_TEMP = "min";
    private static final String MAX_TEMP = "max";
    private static final String WEATHER_ICON = "icon";

    //Sunshine object
    SunshineObj sunshineObj = new SunshineObj("", "", null);
    //Element values
    private String mTime;
    private String mDate;
    private String mDay;
    private String mMinTemp;
    private String mMaxTemp;
    private Bitmap mBitmap;


    @Override
    public Engine onCreateEngine() {
        Log.i(LOG_TAG, "Attempting to create engine");
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchFace.Engine> mWeakReference;

        public EngineHandler(SunshineWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    /**
     * Canvas Watch Face Engine
     * - Listens for broadcasts from the mobile app
     * - Implements Google API client to load image
     */
    private class Engine extends CanvasWatchFaceService.Engine implements
            GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener{

        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        boolean mAmbient;

        //Paint items
        Paint backgroundPaint;
        Paint timePaint;
        Paint dayPaint;
        Paint datePaint;
        Paint minTempPaint;
        Paint maxTempPaint;
        Paint iconPaint;

        //Dimension values;
        float mXTime, mYTime, mXDay, mYDay, mXDate, mYDate, mXMax, mYMax, mXMin, mYMin, mXIcon, mYIcon;
        //Size values;
        float mTimeSize, mDaySize, mDateSize, mMaxSize, mMinSize, mIconSize;

        //Offset values
        float mXOffset;
        float mYOffsetTime;
        float mYOffsetDate;

        //Watch Face Items
        Bitmap mBackgroundBitmap;

        int mTapCount;

        private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                mTime = new SimpleDateFormat(TIME_FORMAT_DISPLAYED)
                        .format(Calendar.getInstance().getTime());

                mDay = new SimpleDateFormat(DAY_FORMAT_DISPLAYED)
                        .format(Calendar.getInstance().getTime());

                mDate = new SimpleDateFormat(DATE_FORMAT_DISPLAYED)
                        .format(Calendar.getInstance().getTime());

                mMinTemp = intent.getStringExtra(MIN_TEMP);
                mMaxTemp = intent.getStringExtra(MAX_TEMP);
                Asset iconAsset = intent.getParcelableExtra(WEATHER_ICON);

                //Requires a new thread to avoid blocking the UI
                new LoadBitmapThread(iconAsset).start();

                Log.i(LOG_TAG, "##Broadcast received on watch: " + mMinTemp + " " + mMaxTemp + " " + mBitmap);
            }
        };

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;


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
                        googleApiClient.blockingConnect(TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (!result.isSuccess()) {
                    Log.w(LOG_TAG, "Unable to connect to GoogleAPIClient");
                }
                // convert asset into a file descriptor and block until it's ready
                InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                        googleApiClient, asset).await().getInputStream();
                googleApiClient.disconnect();

                if (assetInputStream == null) {
                    Log.w(LOG_TAG, "Requested an unknown Asset.");
                }
                // decode the stream into a bitmap
                Bitmap b =  BitmapFactory.decodeStream(assetInputStream);
                mBitmap = b;
                sunshineObj.setIcon(mBitmap);
            }
        }

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());

            Resources resources = SunshineWatchFace.this.getResources();

            //Set up Google API client
            googleApiClient = new GoogleApiClient.Builder(SunshineWatchFace.this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();

            //Register the broadcast receiver
            registerReceiver();

            setUpPaintItems(resources);

            Log.d(LOG_TAG, "Created the watch face engine");

        }


        private void setUpPaintItems(Resources resources){

            //Paint
            timePaint = new Paint();
            dayPaint = new Paint();
            datePaint = new Paint();
            minTempPaint = new Paint();
            maxTempPaint = new Paint();
            iconPaint = new Paint();

            //Sizes
            mTimeSize = resources.getDimension(R.dimen.time_text_size);
            mDaySize = resources.getDimension(R.dimen.day_text_size);
            mDateSize = resources.getDimension(R.dimen.date_text_size);
            mMaxSize = resources.getDimension(R.dimen.max_temp_text_size);
            mMinSize = resources.getDimension(R.dimen.min_temp_text_size);
            mIconSize = resources.getDimension(R.dimen.icon_size);

            int white = resources.getColor(R.color.white);

            //Configure Paint item size
            timePaint.setTextSize(mTimeSize);
            timePaint.setColor(white);
            timePaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            timePaint.setAntiAlias(true);

            dayPaint.setTextSize(mDaySize);
            dayPaint.setAntiAlias(true);
            dayPaint.setColor(white);
            dayPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));

            datePaint.setTextSize(mDateSize);
            datePaint.setAntiAlias(true);
            datePaint.setColor(white);
            datePaint.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));

            maxTempPaint.setTextSize(mMaxSize);
            maxTempPaint.setColor(white);
            maxTempPaint.setAntiAlias(true);

            minTempPaint.setTextSize(mMinSize);
            minTempPaint.setColor(white);
            minTempPaint.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
            minTempPaint.setAntiAlias(true);

            // Load the background image
            backgroundPaint = new Paint();
            backgroundPaint.setColor(resources.getColor(R.color.background));
            Drawable backgroundDrawable = resources.getDrawable(R.drawable.background, null);
            mBackgroundBitmap = ((BitmapDrawable) backgroundDrawable).getBitmap();

        }


        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private void releaseGoogleApiClient() {
            if (googleApiClient != null && googleApiClient.isConnected()) {
                googleApiClient.disconnect();
            }
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                googleApiClient.connect();
            } else {
                unregisterReceiver();
                releaseGoogleApiClient();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();

        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;

            IntentFilter filter = new IntentFilter(Intent.ACTION_SEND);
            LocalBroadcastManager.getInstance(SunshineWatchFace.this).registerReceiver(broadcastReceiver, filter);

            Log.d(LOG_TAG, "Registered the broadcast receiver");
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            LocalBroadcastManager.getInstance(SunshineWatchFace.this).unregisterReceiver(broadcastReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchFace.this.getResources();
            /*boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);*/

            //Coordinates
            mXTime = resources.getDimension(R.dimen.time_x);
            mYTime = resources.getDimension(R.dimen.time_y);
            mXDay = resources.getDimension(R.dimen.day_x);
            mYDay = resources.getDimension(R.dimen.day_y);
            mXDate = resources.getDimension(R.dimen.date_x);
            mYDate = resources.getDimension(R.dimen.date_y);
            mXIcon = resources.getDimension(R.dimen.icon_x);
            mYIcon = resources.getDimension(R.dimen.icon_y);
            mXMax = resources.getDimension(R.dimen.max_temp_x);
            mYMax = resources.getDimension(R.dimen.max_temp_y);
            mXMin = resources.getDimension(R.dimen.min_temp_x);
            mYMin = resources.getDimension(R.dimen.min_temp_y);

        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    timePaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            Resources resources = SunshineWatchFace.this.getResources();
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    mTapCount++;
                    backgroundPaint.setColor(resources.getColor(mTapCount % 2 == 0 ?
                            R.color.background : R.color.background2));
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {

            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.WHITE);
            } else {
                //canvas.drawRect(0, 0, bounds.width(), bounds.height(), backgroundPaint);
                mBackgroundBitmap = scaleBitmap(mBackgroundBitmap, 320, 320);
                backgroundPaint.setAntiAlias(true);
                canvas.drawBitmap(mBackgroundBitmap, 5, 5, backgroundPaint);
            }

            //Draw elements
            if(mTime != null){
                canvas.drawText(mTime, mXTime, mYTime, timePaint);
            }

            if(mDay != null){
                canvas.drawText(mDay.toUpperCase(), mXDay, mYDay, dayPaint);
            }

            if(mDate != null){
                canvas.drawText(mDate, mXDate, mYDate, datePaint);
            }

            if(mMinTemp != null){
                canvas.drawText(mMinTemp, mXMin, mYMin, minTempPaint);
            }

            if(mMaxTemp != null){
                canvas.drawText(mMaxTemp, mXMax, mYMax, maxTempPaint);
            }

            if(mBitmap != null){
                //Resize Bitmap
                int size = Math.round(mIconSize);
                mBitmap= scaleBitmap(mBitmap, size, size);
                canvas.drawBitmap(mBitmap, mXIcon, mYIcon, iconPaint);
            }
        }


        private Bitmap scaleBitmap(Bitmap bitmap, int wantedWidth, int wantedHeight) {

            Bitmap output = Bitmap.createBitmap(wantedWidth, wantedHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(output);
            Matrix m = new Matrix();
            m.setScale((float) wantedWidth / bitmap.getWidth(), (float) wantedHeight / bitmap.getHeight());
            canvas.drawBitmap(bitmap, m, new Paint());

            return output;
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        @Override
        public void onConnected(Bundle bundle) {
            Log.d(LOG_TAG, "Connected to GoogleAPI");
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.d(LOG_TAG, "Suspended GoogleAPI");
        }

        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {
            Log.d(LOG_TAG, "Connection Failed to GoogleAPI");
            super.onDestroy();
        }

    }



}
