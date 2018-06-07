package com.awolity.trakr.view.detail;

import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Spinner;

import com.awolity.trakr.R;
import com.awolity.trakr.customviews.PrimaryPropertyViewIcon;
import com.awolity.trakr.data.entity.TrackWithPoints;
import com.awolity.trakr.data.entity.TrackpointEntity;
import com.awolity.trakr.utils.MyLog;
import com.awolity.trakr.utils.StringUtils;
import com.awolity.trakr.viewmodel.TrackViewModel;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.LargeValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TrackDetailActivityChartsFragment extends Fragment
        implements AdapterView.OnItemSelectedListener {

    private static final String ARG_TRACK_ID = "track_id";
    private static final String LOG_TAG = TrackDetailActivityChartsFragment.class.getSimpleName();

    private long trackId;
    private TrackViewModel trackViewModel;
    private PrimaryPropertyViewIcon maxSpeedPpvi, avgSpeedPpvi, ascentPpvi, descentPpvi,
            maxAltitudePpvi, minAltitudePpvi, maxPacePpvi, avgPacePpvi;
    private CheckBox paceCheckBox, speedCheckBox;
    private LineChart speedChart, elevationChart;
    private Spinner xAxisSpinner;
    private int xAxis = 0;
    private boolean isSpeed = true;

    public static TrackDetailActivityChartsFragment newInstance(long trackId) {
        TrackDetailActivityChartsFragment fragment = new TrackDetailActivityChartsFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_TRACK_ID, trackId);
        fragment.setArguments(args);
        return fragment;
    }

    public TrackDetailActivityChartsFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            trackId = getArguments().getLong(ARG_TRACK_ID);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.activity_track_detail_fragment_charts, container, false);
        setupWidgets(view);
        setupCharts();
        resetWidgets();
        setupViewModel();
        return view;
    }

    private void setupWidgets(View view) {
        xAxisSpinner = view.findViewById(R.id.spinner_xaxis);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(getContext(),
                R.array.x_axis_label_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        xAxisSpinner.setAdapter(adapter);
        xAxisSpinner.setOnItemSelectedListener(this);
        xAxisSpinner.setSelection(0);

        maxSpeedPpvi = view.findViewById(R.id.ppvi_max_speed);
        maxPacePpvi = view.findViewById(R.id.ppvi_max_pace);
        avgSpeedPpvi = view.findViewById(R.id.ppvi_avg_speed);
        avgPacePpvi = view.findViewById(R.id.ppvi_avg_pace);
        speedChart = view.findViewById(R.id.chart_speed);
        elevationChart = view.findViewById(R.id.chart_elevation);
        ascentPpvi = view.findViewById(R.id.ppvi_ascent);
        descentPpvi = view.findViewById(R.id.ppvi_descent);
        maxAltitudePpvi = view.findViewById(R.id.ppvi_max_altitude);
        minAltitudePpvi = view.findViewById(R.id.ppvi_min_altitude);
        paceCheckBox = view.findViewById(R.id.cb_pace);
        speedCheckBox = view.findViewById(R.id.cb_speed);
        paceCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                speedCheckBox.setChecked(!b);
                if (b && trackWithPoints != null) {
                    isSpeed = false;
                    if (xAxis == 0) {
                        setPaceChartDataByTime(trackWithPoints);
                    } else if (xAxis == 1) {
                        setPaceChartDataByDistance(trackWithPoints);
                    }
                }
            }
        });
        speedCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                paceCheckBox.setChecked(!b);
                if (b && trackWithPoints != null) {
                    isSpeed = true;
                    if (xAxis == 0) {
                        setSpeedChartDataByTime(trackWithPoints);
                    } else if (xAxis == 1) {
                        setSpeedChartDataByDistance(trackWithPoints);
                    }
                }
            }
        });
    }

    private void resetWidgets() {
        // TODO: extract
        maxSpeedPpvi.setup("Max.Speed", "km/h", "0", R.drawable.ic_max_speed);
        avgSpeedPpvi.setup("Avg.Speed", "km/h", "-", R.drawable.ic_avg_speed);
        maxPacePpvi.setup("Max.Pace", "min/km", "0", R.drawable.ic_max_speed);
        avgPacePpvi.setup("Avg.Pace", "min/km", "-", R.drawable.ic_avg_speed);
        ascentPpvi.setup("Ascent", "m", "0", R.drawable.ic_ascent);
        descentPpvi.setup("Descent", "m", "0", R.drawable.ic_descent);
        maxAltitudePpvi.setup("Max.Altitude", "m", "-", R.drawable.ic_max_altitude);
        minAltitudePpvi.setup("Min.Altitude", "m", "-", R.drawable.ic_min_altitude);

        paceCheckBox.setChecked(false);
        speedCheckBox.setChecked(true);
    }

    private void setupCharts() {
        Description description = new Description();
        description.setText("");
        speedChart.setDescription(description);
        speedChart.getAxisRight().setEnabled(false);
        speedChart.setTouchEnabled(true);
        speedChart.setDragEnabled(true);
        speedChart.setScaleEnabled(true);
        speedChart.setPinchZoom(false);
        Legend l = speedChart.getLegend();
        l.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        l.setHorizontalAlignment(Legend.LegendHorizontalAlignment.LEFT);
        l.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        l.setDrawInside(false);

        elevationChart.setDescription(description);
        elevationChart.getAxisRight().setEnabled(false);
        elevationChart.setTouchEnabled(true);
        elevationChart.setDragEnabled(true);
        elevationChart.setScaleEnabled(true);
        elevationChart.setPinchZoom(false);
        l = elevationChart.getLegend();

        l.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        l.setHorizontalAlignment(Legend.LegendHorizontalAlignment.LEFT);
        l.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        l.setDrawInside(false);
    }

    private TrackWithPoints trackWithPoints;

    private void setupViewModel() {
        trackViewModel = ViewModelProviders.of(getActivity()).get(TrackViewModel.class);
        trackViewModel.getTrackWithPoints().observe(this, new Observer<TrackWithPoints>() {
            @Override
            public void onChanged(@Nullable TrackWithPoints trackWithPoints) {
                if (trackWithPoints != null) {
                    TrackDetailActivityChartsFragment.this.trackWithPoints = trackWithPoints;
                    setWidgetData(trackWithPoints);
                    setSpeedChartDataByTime(trackWithPoints);
                    setElevationChartDataByTime(trackWithPoints);
                }
            }
        });
    }

    private void setWidgetData(TrackWithPoints trackWithPoints) {
        MyLog.d(LOG_TAG, "setWidgetData");

        maxSpeedPpvi.setValue(StringUtils.getSpeedAsThreeCharactersString(trackWithPoints.getMaxSpeed()));
        avgSpeedPpvi.setValue(StringUtils.getSpeedAsThreeCharactersString(trackWithPoints.getAvgSpeed()));
        maxPacePpvi.setValue(StringUtils.getSpeedAsThreeCharactersString(60 * (1 / trackWithPoints.getMaxSpeed())));
        avgPacePpvi.setValue(StringUtils.getSpeedAsThreeCharactersString(60 * (1 / trackWithPoints.getAvgSpeed())));
        ascentPpvi.setValue(String.format(Locale.getDefault(), "%.0f", trackWithPoints.getAscent()));
        descentPpvi.setValue(String.format(Locale.getDefault(), "%.0f", trackWithPoints.getDescent()));
        minAltitudePpvi.setValue(String.format(Locale.getDefault(), "%.0f", trackWithPoints.getMinAltitude()));
        maxAltitudePpvi.setValue(String.format(Locale.getDefault(), "%.0f", trackWithPoints.getMaxAltitude()));
    }

    private void setElevationChartDataByTime(TrackWithPoints trackWithPoints) {
        List<Entry> values = new ArrayList<>();
        List<TrackpointEntity> trackpointEntityList = trackWithPoints.getTrackPoints();
        long startTime = trackWithPoints.getStartTime();
        long durationInSeconds = (trackpointEntityList.get(trackpointEntityList.size() - 1).getTime()
                - startTime)
                / 1000;

        for (TrackpointEntity trackpointEntity : trackpointEntityList) {
            long elapsedSeconds = (trackpointEntity.getTime() - startTime) / 1000;
            values.add(new Entry((float) elapsedSeconds, (float) trackpointEntity.getAltitude()));
        }
        elevationChart.getXAxis().setValueFormatter(new GraphTimeAxisValueFormatter(durationInSeconds));
        LineDataSet elevationDataSet = new LineDataSet(values, "Elevation [m]");
        setElevationChartData(elevationDataSet);
    }

    private void setElevationChartDataByDistance(TrackWithPoints trackWithPoints) {
        List<Entry> values = new ArrayList<>();
        List<TrackpointEntity> trackpointEntityList = trackWithPoints.getTrackPoints();
        //double totalDistance = trackWithPoints.getDistance();
        double rollingDistance = 0;

        for (TrackpointEntity trackpointEntity : trackpointEntityList) {
            rollingDistance += trackpointEntity.getDistance();
            values.add(new Entry((float) rollingDistance, (float) trackpointEntity.getAltitude()));
        }
        elevationChart.getXAxis().setValueFormatter(new LargeValueFormatter());
        LineDataSet elevationDataSet = new LineDataSet(values, "Elevation [m]");
        setElevationChartData(elevationDataSet);
    }

    private void setSpeedChartDataByTime(TrackWithPoints trackWithPoints) {
        List<Entry> values = new ArrayList<>();
        List<TrackpointEntity> trackpointEntityList = trackWithPoints.getTrackPoints();
        long startTime = trackWithPoints.getStartTime();
        long durationInSeconds = (trackpointEntityList.get(trackpointEntityList.size() - 1).getTime()
                - startTime)
                / 1000;

        for (TrackpointEntity trackpointEntity : trackpointEntityList) {
            long elapsedSeconds = (trackpointEntity.getTime() - startTime) / 1000;
            values.add(new Entry((float) elapsedSeconds, (float) trackpointEntity.getSpeed()));
        }
        speedChart.getXAxis().setValueFormatter(new GraphTimeAxisValueFormatter(durationInSeconds));
        LineDataSet speedDataSet = new LineDataSet(values, "Speed [km/h]");
        setSpeedChartData(speedDataSet);
    }

    private void setSpeedChartDataByDistance(TrackWithPoints trackWithPoints) {
        List<Entry> values = new ArrayList<>();
        List<TrackpointEntity> trackpointEntityList = trackWithPoints.getTrackPoints();
        //double totalDistance = trackWithPoints.getDistance();
        double rollingDistance = 0;

        for (TrackpointEntity trackpointEntity : trackpointEntityList) {
            rollingDistance += trackpointEntity.getDistance();
            values.add(new Entry((float) rollingDistance, (float) trackpointEntity.getSpeed()));
        }
        speedChart.getXAxis().setValueFormatter(new LargeValueFormatter());
        LineDataSet speedDataSet = new LineDataSet(values, "Speed [km/h]");
        setSpeedChartData(speedDataSet);
    }

    private void setPaceChartDataByTime(TrackWithPoints trackWithPoints) {
        List<Entry> values = new ArrayList<>();
        List<TrackpointEntity> trackpointEntityList = trackWithPoints.getTrackPoints();
        long startTime = trackWithPoints.getStartTime();
        long durationInSeconds = (trackpointEntityList.get(trackpointEntityList.size() - 1).getTime()
                - startTime)
                / 1000;
        double highestPaceValue = 0;

        for (TrackpointEntity trackpointEntity : trackpointEntityList) {
            long elapsedSeconds = (trackpointEntity.getTime() - startTime) / 1000;
            if (trackpointEntity.getSpeed() > 1) {
                double pace = 60 * 60 * (1 / trackpointEntity.getSpeed());
                if (pace > highestPaceValue) {
                    highestPaceValue = pace;
                }
                values.add(new Entry((float) elapsedSeconds, (float) pace));
            } else {
                values.add(new Entry((float) elapsedSeconds, (float) 0));
            }
        }
        speedChart.getXAxis().setValueFormatter(new GraphTimeAxisValueFormatter(durationInSeconds));
        speedChart.getAxisLeft().setValueFormatter(new GraphTimeAxisValueFormatter((long)highestPaceValue));
        LineDataSet speedDataSet = new LineDataSet(values, "Pace [min/km]");
        setSpeedChartData(speedDataSet);
    }

    private void setPaceChartDataByDistance(TrackWithPoints trackWithPoints) {
        List<Entry> values = new ArrayList<>();
        List<TrackpointEntity> trackpointEntityList = trackWithPoints.getTrackPoints();
        double rollingDistance = 0;
        double highestPaceValue = 0;

        for (TrackpointEntity trackpointEntity : trackpointEntityList) {
            rollingDistance += trackpointEntity.getDistance();
            if (trackpointEntity.getSpeed() > 1) {
                double pace = 60 * 60 * (1 / trackpointEntity.getSpeed());
                if (pace > highestPaceValue) {
                    highestPaceValue = pace;
                }
                values.add(new Entry((float) rollingDistance, (float) pace));
            } else {
                values.add(new Entry((float) rollingDistance, (float) 0));
            }
        }
        speedChart.getXAxis().setValueFormatter(new LargeValueFormatter());
        speedChart.getAxisLeft().setValueFormatter(new GraphTimeAxisValueFormatter((long)highestPaceValue));
        LineDataSet speedDataSet = new LineDataSet(values, "Speed [min/km]");
        setSpeedChartData(speedDataSet);
    }

    private void setElevationChartData(LineDataSet elevationDataSet) {
        elevationDataSet.setDrawIcons(false);
        elevationDataSet.setDrawValues(false);
        elevationDataSet.setColor(getResources().getColor(R.color.colorPrimary));
        elevationDataSet.setDrawCircles(false);
        elevationDataSet.setLineWidth(3f);
        elevationDataSet.setValueTextSize(9f);
        elevationDataSet.setDrawFilled(true);
        elevationDataSet.setFormLineWidth(1f);
        elevationDataSet.setFormSize(15.f);
        elevationDataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        Drawable drawable = ContextCompat.getDrawable(getContext(), R.drawable.fade_primary_color);
        elevationDataSet.setFillDrawable(drawable);

        ArrayList<ILineDataSet> dataSets = new ArrayList<>();
        dataSets.add(elevationDataSet);
        LineData data = new LineData(dataSets);
        elevationChart.setData(data);
        elevationChart.invalidate();
    }

    private void setSpeedChartData(LineDataSet speedDataSet) {
        speedDataSet.setDrawIcons(false);
        speedDataSet.setDrawValues(false);
        speedDataSet.setColor(getResources().getColor(R.color.colorAccent));
        speedDataSet.setDrawCircles(false);
        speedDataSet.setLineWidth(3f);
        speedDataSet.setValueTextSize(9f);
        speedDataSet.setDrawFilled(true);
        speedDataSet.setFormLineWidth(1f);
        speedDataSet.setFormSize(15.f);
        speedDataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        Drawable drawable = ContextCompat.getDrawable(getContext(), R.drawable.fade_accent_color);
        speedDataSet.setFillDrawable(drawable);

        ArrayList<ILineDataSet> dataSets = new ArrayList<>();
        dataSets.add(speedDataSet);
        LineData data = new LineData(dataSets);
        speedChart.setData(data);
        speedChart.invalidate();
    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        if (xAxis != i) {
            xAxis = i;
            if (i == 0 && trackWithPoints != null) {
                setElevationChartDataByTime(trackWithPoints);
                if (isSpeed) {
                    setSpeedChartDataByTime(trackWithPoints);
                } else {
                    setPaceChartDataByTime(trackWithPoints);
                }
            } else if (i == 1 && trackWithPoints != null) {
                setElevationChartDataByDistance(trackWithPoints);
                if (isSpeed) {
                    setSpeedChartDataByDistance(trackWithPoints);
                } else {
                    setPaceChartDataByDistance(trackWithPoints);
                }
            }
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }
}