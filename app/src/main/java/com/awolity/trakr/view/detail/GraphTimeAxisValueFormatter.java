package com.awolity.trakr.view.detail;

import com.awolity.trakr.data.entity.TrackpointEntity;
import com.awolity.trakr.utils.MyLog;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;

import java.util.List;

public class GraphTimeAxisValueFormatter implements IAxisValueFormatter {

    private static final String LOG_TAG = GraphTimeAxisValueFormatter.class.getSimpleName();
    private String[] mValues;

    public GraphTimeAxisValueFormatter(long durationInSeconds) {
        Duration duration;



        mValues = new String[(int)durationInSeconds];

        if (durationInSeconds < 60) {// track shorter than a minute
            duration = GraphTimeAxisValueFormatter.Duration.Seconds;
        } else if (durationInSeconds < 3600) { //track shorter then an hour
            duration = GraphTimeAxisValueFormatter.Duration.Minutes;
        } else {
            duration = GraphTimeAxisValueFormatter.Duration.Hours;
        }

        for (int i = 0; i < durationInSeconds; i++) {
            String s = "";
            if (duration.equals(Duration.Seconds)) {
                s = String.valueOf(i);
            } else if (duration.equals(Duration.Minutes)) {
                s = String.valueOf(i / 60)
                        + ":"
                        + String.valueOf(i % 60);
            } else if (duration.equals(Duration.Hours)) {
                s = String.valueOf(i / 3600)
                        + ":"
                        + String.valueOf((i % 3600) / 60)
                        + ":"
                        + String.valueOf(i % 60);
            }
            mValues[i] = s;
        }
    }

    @Override
    public String getFormattedValue(float value, AxisBase axis) {
        MyLog.d(LOG_TAG, "getFormattedValue - value: " + value);
        if (value >= 0) {
            String result = mValues[(int) value % mValues.length];
            MyLog.d(LOG_TAG, "getFormattedValue - mValue: " + result);
            return mValues[(int) value % mValues.length];
        } else
            return "";

    }

    public enum Duration {Seconds, Minutes, Hours,}
}