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
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
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

    private class Engine extends CanvasWatchFaceService.Engine implements
            DataApi.DataListener,
            GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;
        boolean mAmbient;
        Time mTime;

        //All the Paints
        Paint mBackgroundPaint;
        Paint mWhitePaint;
        Paint mGreyPaint;
        Paint mWeatherIconPaint;

        //to receive data from the handset
        GoogleApiClient mGoogleApiClient;
        //constants and path
        String LOW_TEMP = "LOW_TEMP";
        String HIGH_TEMP = "HIGH_TEMP";
        String WEATHER_ICON = "WEATHER_IMG";
        String WEATHER_PATH = "/sunshine";

        String mHighTemp = "";
        String mLowTemp = "";
        Bitmap mWeatherIconBitmap;

        //all the position
        float mTimeXOffset;
        float mTimeYOffset;
        float mDateXOffset;
        float mDateYOffset;
        float mSeparatorXOffset;
        float mSeparatorYOffset;
        float mIconXOffset;
        float mIconYOffset;
        float mHighXOffset;
        float mHighYOffset;
        float mLowXOffset;
        float mLowYOffset;
        float mSeparatorLength;
        //all the sizes
        float mTimeTextSize;
        float mDateTextSize;
        float mHighTempTextSize;
        float mLowTempTextSize;
        int iconSize;

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };


        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setHotwordIndicatorGravity(Gravity.TOP | Gravity.CENTER) // hotword protection
                    .setViewProtectionMode(WatchFaceStyle.PROTECT_HOTWORD_INDICATOR) // hotword protection
                    .build());
            //instantiate google api client
            mGoogleApiClient = new GoogleApiClient.Builder(getApplicationContext())
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(Engine.this)
                    .addOnConnectionFailedListener(Engine.this)
                    .build();

            Resources resources = SunshineWatchFace.this.getResources();
            mTimeYOffset = resources.getDimension(R.dimen.time_y_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background_3));

            mWhitePaint = new Paint();
            mWhitePaint = createTextPaint(resources.getColor(R.color.text_1));

            mGreyPaint = new Paint();
            mGreyPaint = createTextPaint(resources.getColor(R.color.text_2));

            mWeatherIconPaint = new Paint();

            mDateXOffset = resources.getDimension(R.dimen.date_x_offset);
            mDateYOffset = resources.getDimension(R.dimen.date_y_offset);
            mSeparatorXOffset = resources.getDimension(R.dimen.separator_x_offset);
            mSeparatorYOffset = resources.getDimension(R.dimen.separator_y_offset);
            mIconXOffset = resources.getDimension(R.dimen.icon_x_offset);
            mIconYOffset = resources.getDimension(R.dimen.icon_y_offset);
            mHighXOffset = resources.getDimension(R.dimen.high_x_offset);
            mHighYOffset = resources.getDimension(R.dimen.high_y_offset);
            mLowXOffset = resources.getDimension(R.dimen.low_x_offset);
            mLowYOffset = resources.getDimension(R.dimen.low_y_offset);

            mSeparatorLength = resources.getDimension(R.dimen.separator_length);

            iconSize = resources.getInteger(R.integer.icon_size);
            mDateTextSize = resources.getDimension(R.dimen.date_text_size);
            mHighTempTextSize = resources.getDimension(R.dimen.high_temp_size);
            mLowTempTextSize = resources.getDimension(R.dimen.low_temp_text_size);
            mTime = new Time();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            //disconnect the google client api
            Wearable.DataApi.removeListener(mGoogleApiClient, Engine.this);
            if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                mGoogleApiClient.disconnect();
            }
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                //connect google client api
                mGoogleApiClient.connect();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
                //disconnect google client api
                Wearable.DataApi.removeListener(mGoogleApiClient, Engine.this);
                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    mGoogleApiClient.disconnect();
                }
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
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mTimeXOffset = resources.getDimension(isRound
                    ? R.dimen.time_x_offset_round : R.dimen.time_x_offset);


            mTimeTextSize = resources.getDimension(isRound
                    ? R.dimen.text_size_round : R.dimen.text_size);

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
                    mWhitePaint.setAntiAlias(!inAmbientMode);
                    mGreyPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            mTime.setToNow();

            String text = String.format("%d:%02d", mTime.hour, mTime.minute);
            //canvas the time
            mWhitePaint.setTextSize(mTimeTextSize);
            canvas.drawText(text, mTimeXOffset, mTimeYOffset, mWhitePaint);
            //
            if (!mAmbient) {
                // Draw date
                String date = Util.convertDate(mTime.weekDay, mTime.monthDay, mTime.month, mTime.year);
                mGreyPaint.setTextSize(mDateTextSize);
                canvas.drawText(date, mDateXOffset, mDateYOffset, mGreyPaint);

                // Draw weather information if available
                if (mHighTemp != null && mLowTemp != null) {
                    // Draw high
                    mWhitePaint.setTextSize(mHighTempTextSize);
                    canvas.drawText(mHighTemp, mHighXOffset, mHighYOffset, mWhitePaint);
                    // Draw low
                    mGreyPaint.setTextSize(mLowTempTextSize);
                    canvas.drawText(mLowTemp, mLowXOffset, mLowYOffset, mGreyPaint);
                    // Draw separator
                    mGreyPaint.setStrokeWidth(0);
                    canvas.drawLine(mSeparatorXOffset, mSeparatorYOffset,
                            mSeparatorXOffset + mSeparatorLength, mSeparatorYOffset, mGreyPaint);
                }
                if (mWeatherIconBitmap != null) {
                    // Scale and draw icon
                    float ratio = iconSize / (float) mWeatherIconBitmap.getWidth();
                    float middleX = iconSize / 2.0f;
                    float middleY = iconSize / 2.0f;

                    Matrix scaleMatrix = new Matrix();
                    scaleMatrix.setScale(ratio, ratio, mIconXOffset + middleX, mIconYOffset + middleY);
                    canvas.setMatrix(scaleMatrix);
                    mWeatherIconPaint.setFilterBitmap(true);

                    canvas.drawBitmap(mWeatherIconBitmap, mIconXOffset * ratio,
                            mIconYOffset * ratio, mWeatherIconPaint);
                }
            }
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
        public void onConnected(@Nullable Bundle bundle) {
            Log.d("WATCH", "Watch: on connected");
            getInitialData();
            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.d("WATCH", "Watch: on connection suspended");
        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            Log.d("WATCH", "Watch: on connection failed");
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            Log.d("WATCH", "Watch: on data changed");
            for (DataEvent dataEvent : dataEventBuffer) {
                if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                    DataItem item = dataEvent.getDataItem();
                    if (item.getUri().getPath().compareTo(WEATHER_PATH) == 0) {
                        DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                        mLowTemp = dataMap.getString(LOW_TEMP);
                        mHighTemp = dataMap.getString(HIGH_TEMP);
                        Asset weatherIconAsset = dataMap.getAsset(WEATHER_ICON);
                        getBitmapFromAsset(weatherIconAsset);
                    }
                } else {
                    Log.d("WATCH", "Watch:" + dataEvent.getType());
                }
            }

        }

        void getInitialData() {
            Log.d("WATCH", "Watch: I am in the get Initial data");
            Wearable.NodeApi.getConnectedNodes(mGoogleApiClient)
                    .setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
                        @Override
                        public void onResult(@NonNull NodeApi.GetConnectedNodesResult nodes) {
                            Node node = null;
                            Log.d("WATCH", "Watch:" + nodes.getNodes().size());
                            for (Node n : nodes.getNodes()) {
                                node = n;
                            }
                            if (node == null) {
                                return;
                            }
                            Uri uri = new Uri.Builder()
                                    .scheme(PutDataRequest.WEAR_URI_SCHEME)
                                    .path(WEATHER_PATH)
                                    .authority(node.getId())
                                    .build();
                            Wearable.DataApi.getDataItem(mGoogleApiClient, uri)
                                    .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                                        @Override
                                        public void onResult(@NonNull DataApi.DataItemResult dataItemResult) {
                                            Log.d("WATCH", "Watch: on result of call");

                                            if (dataItemResult.getStatus().isSuccess() &&
                                                    dataItemResult.getDataItem() != null) {
                                                Log.d("WATCH", "Watch: on result of call and success");
                                                DataMap dataMap = DataMapItem.fromDataItem(dataItemResult.getDataItem()).getDataMap();
                                                mLowTemp = dataMap.getString(LOW_TEMP);
                                                mHighTemp = dataMap.getString(HIGH_TEMP);
                                                Asset weatherIconAsset = dataMap.getAsset(WEATHER_ICON);
                                                getBitmapFromAsset(weatherIconAsset);
                                            }
                                        }
                                    });
                        }
                    });
        }

        private void getBitmapFromAsset(Asset weatherIconAsset) {
            if (weatherIconAsset != null) {
                Wearable.DataApi.getFdForAsset(mGoogleApiClient, weatherIconAsset)
                        .setResultCallback(new ResultCallback<DataApi.GetFdForAssetResult>() {
                            @Override
                            public void onResult(@NonNull DataApi.GetFdForAssetResult getFdForAssetResult) {
                                InputStream inputStream = getFdForAssetResult.getInputStream();
                                if (inputStream != null) {
                                    mWeatherIconBitmap = BitmapFactory.decodeStream(inputStream);
                                }
                            }
                        });
            }
            return;
        }
    }
}
