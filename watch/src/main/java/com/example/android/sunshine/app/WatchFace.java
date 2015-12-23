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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class WatchFace extends CanvasWatchFaceService {

    private static final long UPDATE_PERIOD_MILLIS = TimeUnit.SECONDS.toMillis(1);
    private static final int UPDATE_ID = 666;

    @Override
    public Engine onCreateEngine() {
        /* provide your watch face implementation */
        return new Engine();
    }

    /* implement service callback methods */
    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, ResultCallback<DataItemBuffer> {

        private Calendar calendar;

        private int weather_id = -1;
        private String max_temp;
        private String min_temp;

        private Paint timePaint;
        private Paint timePaintSecondary;
        private Paint datePaint;
        private Paint dividerPaint;
        private Paint temperaturePaint;
        private Paint temperaturePaintSecondary;

        private SimpleDateFormat timeFormat;
        private SimpleDateFormat dateFormat;

        private boolean lowBitAmbient = false;
        private boolean antialias = true;
        private boolean receiverRegistered = false;

        private GoogleApiClient client;

        final Handler updateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case UPDATE_ID:
                        invalidate();
                        if(isVisible() && !isInAmbientMode()) {
                            long delay = UPDATE_PERIOD_MILLIS - System.currentTimeMillis() % UPDATE_PERIOD_MILLIS;
                            updateTimeHandler.sendEmptyMessageDelayed(UPDATE_ID, delay);
                        }
                        break;
                }
            }
        };

        final BroadcastReceiver timeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                calendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(WatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setAmbientPeekMode(WatchFaceStyle.AMBIENT_PEEK_MODE_HIDDEN)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            timePaint = new Paint();
            timePaint.setColor(Color.WHITE);
            timePaint.setAntiAlias(true);
            timePaint.setTextAlign(Paint.Align.RIGHT);
            timePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

            timePaintSecondary = new Paint();
            timePaintSecondary.setColor(Color.WHITE);
            timePaintSecondary.setAntiAlias(true);
            timePaintSecondary.setTextAlign(Paint.Align.LEFT);

            datePaint = new Paint();
            datePaint.setColor(Color.WHITE);
            datePaint.setAntiAlias(true);
            datePaint.setTextAlign(Paint.Align.CENTER);
            datePaint.setAlpha(150);

            dividerPaint = new Paint();
            dividerPaint.setColor(Color.WHITE);
            dividerPaint.setAntiAlias(false);
            dividerPaint.setAlpha(150);
            dividerPaint.setStrokeWidth(1);

            temperaturePaint = new Paint();
            temperaturePaint.setColor(Color.WHITE);
            temperaturePaint.setAntiAlias(true);
            temperaturePaint.setTextAlign(Paint.Align.CENTER);

            temperaturePaintSecondary = new Paint();
            temperaturePaintSecondary.setColor(Color.WHITE);
            temperaturePaintSecondary.setAntiAlias(true);
            temperaturePaintSecondary.setTextAlign(Paint.Align.LEFT);
            temperaturePaintSecondary.setAlpha(150);

            timeFormat = new SimpleDateFormat("HH:mm");
            dateFormat = new SimpleDateFormat("EEE, LLL d yyyy");

            calendar = Calendar.getInstance();

            client = new GoogleApiClient.Builder(WatchFace.this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(Wearable.API)
                    .build();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);

            lowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);

            if(inAmbientMode && lowBitAmbient) antialias = false;
            else antialias = true;

            invalidate();

            updateTimeHandler.removeMessages(UPDATE_ID);
            if(isVisible() && !isInAmbientMode()) updateTimeHandler.sendEmptyMessage(UPDATE_ID);
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            calendar.setTimeInMillis(System.currentTimeMillis());

            if(isInAmbientMode()) canvas.drawColor(Color.BLACK);
            else canvas.drawColor(Color.parseColor("#2196F3"));

            timePaint.setTextSize(bounds.height() / 5);
            timePaintSecondary.setTextSize(bounds.height() / 5);
            datePaint.setTextSize(bounds.height() / 15);
            temperaturePaint.setTextSize(bounds.height()/10);
            temperaturePaintSecondary.setTextSize(bounds.height()/10);
            timePaint.setAntiAlias(antialias);
            timePaintSecondary.setAntiAlias(antialias);
            datePaint.setAntiAlias(antialias);
            temperaturePaint.setAntiAlias(antialias);
            temperaturePaintSecondary.setAntiAlias(antialias);

            Date d = calendar.getTime();

            String time = timeFormat.format(d);
            String date = dateFormat.format(d).toUpperCase();
            canvas.drawText(time, 0, 3, bounds.width() / 2, bounds.height() / 3, timePaint);
            canvas.drawText(time, 3, time.length(), bounds.width() / 2, bounds.height() / 3, timePaintSecondary);
            canvas.drawText(date, 0, date.length(), bounds.width() / 2, (int) (bounds.height() / 2.2), datePaint);

            canvas.drawLine(bounds.width() / 2 - bounds.width() / 10, (int) (bounds.height() / 1.8), bounds.width() / 2 + bounds.width() / 10, (int) (bounds.height() / 1.8), dividerPaint);

            if(min_temp != null) canvas.drawText(min_temp, 0, min_temp.length(), bounds.width()/2, 3*bounds.height()/4, temperaturePaint);
            if(max_temp != null) canvas.drawText(max_temp, 0, min_temp.length(), bounds.width()/2+bounds.width()/10, 3*bounds.height()/4, temperaturePaintSecondary);
            if(getWeatherIconId() != -1) {
                Bitmap bitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), getWeatherIconId()), bounds.height() / 8, bounds.height() / 8, false);
                canvas.drawBitmap(bitmap, bounds.width()/2-bounds.width()/8-bitmap.getWidth(), 3*bounds.height()/4-bounds.height()/10, null);
            }
        }
        
        private int getWeatherIconId() {
            if (weather_id >= 200 && weather_id <= 232) {
                return R.drawable.ic_storm;
            } else if (weather_id >= 300 && weather_id <= 321) {
                return R.drawable.ic_light_rain;
            } else if (weather_id >= 500 && weather_id <= 504) {
                return R.drawable.ic_rain;
            } else if (weather_id == 511) {
                return R.drawable.ic_snow;
            } else if (weather_id >= 520 && weather_id <= 531) {
                return R.drawable.ic_rain;
            } else if (weather_id >= 600 && weather_id <= 622) {
                return R.drawable.ic_snow;
            } else if (weather_id >= 701 && weather_id <= 761) {
                return R.drawable.ic_fog;
            } else if (weather_id == 761 || weather_id == 781) {
                return R.drawable.ic_storm;
            } else if (weather_id == 800) {
                return R.drawable.ic_clear;
            } else if (weather_id == 801) {
                return R.drawable.ic_light_clouds;
            } else if (weather_id >= 802 && weather_id <= 804) {
                return R.drawable.ic_cloudy;
            }
            return -1;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if(visible) {
                if(!receiverRegistered) {
                    receiverRegistered = true;
                    WatchFace.this.registerReceiver(timeZoneReceiver, new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED));
                }

                client.connect();

                calendar.setTimeZone(TimeZone.getDefault());
            } else {
                if(receiverRegistered) {
                    receiverRegistered = false;
                    WatchFace.this.unregisterReceiver(timeZoneReceiver);
                }

                if(client != null && client.isConnected()) client.disconnect();
            }
        }

        @Override
        public void onDestroy() {
            updateTimeHandler.removeMessages(UPDATE_ID);
            super.onDestroy();
        }

        @Override
        public void onConnected(Bundle bundle) {
            Wearable.DataApi.addListener(client, this);
            Wearable.DataApi.getDataItems(client).setResultCallback(this);
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.e(getPackageName(), "Connection suspended!");
        }

        @Override
        public void onDataChanged(DataEventBuffer data) {
            for(DataEvent event: data) {
                if(event.getType() == DataEvent.TYPE_CHANGED) {
                    DataItem item = event.getDataItem();
                    if (item.getUri().getPath().equals("/weather")) {
                        DataMap map = DataMapItem.fromDataItem(item).getDataMap();
                        min_temp = map.getString("min_temp");
                        max_temp = map.getString("max_temp");
                        weather_id = map.getInt("weather_id");
                    }
                }
            }
            data.release();
            if(isVisible() && !isInAmbientMode()) invalidate();
        }

        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {
            Log.e(getPackageName(), "Connection failed!");
        }

        @Override
        public void onResult(DataItemBuffer dataItemBuffer) {
            for(DataItem item: dataItemBuffer) {
                if (item.getUri().getPath().equals("/weather")) {
                    DataMap map = DataMapItem.fromDataItem(item).getDataMap();
                    min_temp = map.getString("min_temp");
                    max_temp = map.getString("max_temp");
                    weather_id = map.getInt("weather_id");
                }
            }
            dataItemBuffer.release();
            if(isVisible() && !isInAmbientMode()) invalidate();
        }
    }
}
