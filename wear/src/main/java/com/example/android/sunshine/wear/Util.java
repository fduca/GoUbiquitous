package com.example.android.sunshine.wear;

import android.content.res.Resources;

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
