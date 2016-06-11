package com.example.android.sunshine.app;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.annotation.NonNull;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.text.DateFormatSymbols;

/**
 * Created by cudaf on 03/06/2016.
 */

public class Util {

    public static String convertDate(int weekDay, int day, int month, int year){
        String weekday = new DateFormatSymbols().getShortWeekdays()[weekDay];
        String monthName = new DateFormatSymbols().getShortMonths()[month];
        return String.format("%s,%s %d %d", weekday, monthName, day, year);
    }

}
