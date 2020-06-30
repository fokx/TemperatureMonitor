package me.lqy.temperatuemonitor;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Vibrator;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;

import java.util.ArrayList;
import java.util.List;

import me.lqy.temperatuemonitor.database.DatabaseHelper;
import me.lqy.temperatuemonitor.database.Point;
import me.lqy.temperatuemonitor.serial.SerialListener;
import me.lqy.temperatuemonitor.serial.SerialService;
import me.lqy.temperatuemonitor.serial.SerialSocket;

import static me.lqy.temperatuemonitor.Constants.webviewAddress;

//public class TerminalFragment extends Fragment implements ServiceConnection, SerialListener {
public class TerminalFragment extends Fragment implements ServiceConnection, SerialListener {
    private static final float MAX_TEMPERATURE = (float) 50.0;
    private static final float MIN_TEMPERATURE = (float) 10.0;
    // Normally death, or there may be serious brain damage, continuous convulsions and shock.
    private static final float ALERT_POINT_UPBOUND = (float) 37.3; // a line in chart
    private static final float VIBRATE_UPBOUND = ALERT_POINT_UPBOUND; // vibrate

    private enum Connected {False, Pending, True}

    public enum MCUConnectStatus {
        NOT_CONNECTED(0), CONNECTED_AND_RUNNING(1), CONNECTED_AND_HALTED(2);
        private final int code;

        private MCUConnectStatus(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }
    }

    private DatabaseHelper db;
    private List<Point> pointsList = new ArrayList<>();

    private static final String TAG = "ReceivedData";

    private String deviceAddress;
    private String newline = "\r\n";
    private TextView receivedText;
    private View colorfulBackground;
    private TextView currentPointText;
    private TextView currentPointTextLeft;
    private TextView currentPointTextRight;
    private SerialSocket socket;
    //    private SerialService service;
    private SerialService service;
    private boolean initialStart = true;
    private Connected connected = Connected.False;

    private LineChart mChart;
    private TextView sendText;
    private View sendBtn;
    private View toggleLogBtn;
    private View startReportBtn;
    private View haltReportBtn;
    private View sendDiv;
    private View toggleViewPointsBtn;
    private WebView viewPoints;
    private ImageView imageViewSun ;
    private ImageView img_rotate;
    private Animation rotation;
    private boolean darkRotateBackgroundFlag = true;
    private TextView t_in_pic;
    private TextView t_in_pic_pre1; // recording or not
    private TextView t_in_pic_pre2; // recording or not
    private boolean recording_flag = false;

    /*
     * Lifecycle
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new DatabaseHelper(getActivity());
        pointsList.addAll(db.getAllPoints());

        setHasOptionsMenu(true);
        setRetainInstance(true);
        deviceAddress = getArguments().getString("device");


    }

    @Override
    public void onDestroy() {
        if (connected != Connected.False)
            disconnect();
        getActivity().stopService(new Intent(getActivity(), SerialService.class));
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (service != null)
            service.attach(this);
        else
            getActivity().startService(new Intent(getActivity(), SerialService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
    }

    @Override
    public void onStop() {
        if (service != null && !getActivity().isChangingConfigurations())
            service.detach();
        super.onStop();
    }

    @SuppressWarnings("deprecation")
    // onAttach(context) was added with API 23. onAttach(activity) works for all API versions
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        getActivity().bindService(new Intent(getActivity(), SerialService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDetach() {
        try {
            getActivity().unbindService(this);
        } catch (Exception ignored) {
        }
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (initialStart && service != null) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        if (initialStart && isResumed()) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }

    /*
     * UI
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_terminal, container, false);
        mChart = (LineChart) view.findViewById(R.id.chart);
        t_in_pic =    view.findViewById(R.id.t_in_pic);
        t_in_pic_pre1 =    view.findViewById(R.id.t_in_pic_pre1);
        t_in_pic_pre2 =    view.findViewById(R.id.t_in_pic_pre2);

        setupChart();
        setupAxes();
        setupData();
        setLegend();
//        mChart.setVisibility(View.INVISIBLE);
        colorfulBackground = view.findViewById(R.id.colorfulbackground);
        receivedText = view.findViewById(R.id.received_text);                          // TextView performance decreases with number of spans
        receivedText.setTextColor(getResources().getColor(R.color.colorReceiveText)); // set as default color to reduce number of spans
        receivedText.setMovementMethod(ScrollingMovementMethod.getInstance());

        currentPointText = view.findViewById(R.id.current_temperature);
        currentPointTextLeft = view.findViewById(R.id.current_temperature_left);
        currentPointTextRight = view.findViewById(R.id.current_temperature_right);


        imageViewSun = view.findViewById(R.id.imageViewSun);


        sendText = view.findViewById(R.id.send_text);
        sendBtn = view.findViewById(R.id.send_btn);
        startReportBtn = view.findViewById(R.id.start_report_btn);
        haltReportBtn = view.findViewById(R.id.halt_report_btn);
        toggleLogBtn = view.findViewById(R.id.toggle_log_btn);
        sendDiv = view.findViewById(R.id.send_div);
        toggleViewPointsBtn = view.findViewById(R.id.toggle_webview);
        viewPoints = view.findViewById(R.id.view_points);
        WebSettings webSettings = viewPoints.getSettings();
        webSettings.setJavaScriptEnabled(true);


        startReportBtn.setVisibility(View.VISIBLE);
        haltReportBtn.setVisibility(View.INVISIBLE);
        receivedText.setVisibility(View.INVISIBLE);
        sendDiv.setVisibility(View.INVISIBLE);
        viewPoints.setVisibility(View.INVISIBLE);

        mChart.setVisibility(View.INVISIBLE);
//        colorfulBackground.setVisibility(View.INVISIBLE);


        currentPointText.setVisibility(View.INVISIBLE);
        currentPointTextLeft.setVisibility(View.INVISIBLE);
        currentPointTextRight.setVisibility(View.INVISIBLE);

//        sendBtn.setOnClickListener(v -> send(sendText.getText().toString()));
        startReportBtn.setOnClickListener(v -> send1AndsetVisibility());
        haltReportBtn.setOnClickListener(v -> send0AndsetVisibility());
        toggleLogBtn.setOnClickListener(v -> toggleLogPanel());
        toggleViewPointsBtn.setOnClickListener(v -> toggleViewPoints());



        img_rotate = view.findViewById(R.id.imageViewSun);
        rotation = AnimationUtils.loadAnimation(getActivity(), R.anim.rotate);
//        rotation.setFillAfter(true);
        img_rotate.startAnimation(rotation);
        img_rotate.setVisibility(View.INVISIBLE);
        
        Log.w("CONNECTING TO", webviewAddress);
        viewPoints.loadUrl(webviewAddress);
//        imageViewSun.setAlpha((float) 0.3);

        update_picture_related_text();
        return view;
    }

    private void toggleViewPoints() {
        if (viewPoints.getVisibility() == View.INVISIBLE) {
            viewPoints.setVisibility(View.VISIBLE);

            currentPointText.setVisibility(View.VISIBLE);
            currentPointTextLeft.setVisibility(View.VISIBLE);
            currentPointTextRight.setVisibility(View.VISIBLE);
            img_rotate.setVisibility(View.INVISIBLE);
//            colorfulBackground.setVisibility(View.VISIBLE);
            darkRotateBackgroundFlag = false;
            imageViewSun.setVisibility(View.INVISIBLE);
            rotation.cancel();

            img_rotate.setVisibility(View.INVISIBLE);
        } else if (viewPoints.getVisibility() == View.VISIBLE) {
            viewPoints.setVisibility(View.INVISIBLE);

            currentPointText.setVisibility(View.INVISIBLE);
            currentPointTextLeft.setVisibility(View.INVISIBLE);
            currentPointTextRight.setVisibility(View.INVISIBLE);
            img_rotate.setVisibility(View.VISIBLE);
//            colorfulBackground.setVisibility(View.INVISIBLE);
            darkRotateBackgroundFlag = true;
            imageViewSun.setVisibility(View.VISIBLE);
            rotation.start();
            img_rotate.setVisibility(View.VISIBLE);





        }

    }

    private void toggleLogPanel() {
        if (receivedText.getVisibility() == View.INVISIBLE) {
            receivedText.setVisibility(View.VISIBLE);
        } else if (receivedText.getVisibility() == View.VISIBLE) {
            receivedText.setVisibility(View.INVISIBLE);
        }
    }

    private void send1AndsetVisibility() {
        send(String.valueOf(MCUConnectStatus.CONNECTED_AND_RUNNING.getCode()));
        startReportBtn.setVisibility(View.INVISIBLE);
        haltReportBtn.setVisibility(View.VISIBLE);
        mChart.setVisibility(View.VISIBLE);
        recording_flag = true;
        update_picture_related_text();

    }
    private void update_picture_related_text(){
        if (recording_flag) {
            t_in_pic_pre1.setText(R.string.recording_true);
            t_in_pic_pre2.setText(R.string.recording_true_showing_current_t);
        } else {
            t_in_pic_pre1.setText(R.string.recording_false);
            t_in_pic_pre2.setText(R.string.recording_true_showing_last_t);
        }
    }
    private void send0AndsetVisibility() {
        send(String.valueOf(MCUConnectStatus.CONNECTED_AND_HALTED.getCode()));
        haltReportBtn.setVisibility(View.INVISIBLE);
        startReportBtn.setVisibility(View.VISIBLE);
        recording_flag = false;
        update_picture_related_text();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_terminal, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.clear) {
            receivedText.setText("");
            return true;
        } else if (id == R.id.newline) {
            String[] newlineNames = getResources().getStringArray(R.array.newline_names);
            String[] newlineValues = getResources().getStringArray(R.array.newline_values);
            int pos = java.util.Arrays.asList(newlineValues).indexOf(newline);
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("Newline");
            builder.setSingleChoiceItems(newlineNames, pos, (dialog, item1) -> {
                newline = newlineValues[item1];
                dialog.dismiss();
            });
            builder.create().show();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    /*
     * Serial + UI
     */
    private void connect() {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            String deviceName = device.getName() != null ? device.getName() : device.getAddress();
            status("connecting...");
            connected = Connected.Pending;
            socket = new SerialSocket();
            service.connect(this, "Connected to " + deviceName);
            socket.connect(getContext(), service, device);
        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    private void disconnect() {
        connected = Connected.False;
        service.disconnect();
        socket.disconnect();
        socket = null;
    }

    private void send(String str) {
        if (connected != Connected.True) {
            Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            SpannableStringBuilder spn = new SpannableStringBuilder(str + '\n');
            spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            receivedText.append(spn);
            byte[] data = (str + newline).getBytes();
            socket.write(data);
        } catch (Exception e) {
            onSerialIoError(e);
        }
    }

    private static String stripStart(String str, String stripChars) {
        int strLen;
        if (str == null || (strLen = str.length()) == 0) {
            return str;
        }
        int start = 0;
        if (stripChars == null) {
            while ((start != strLen)
                    && Character.isWhitespace(str.charAt(start))) {
                start++;
            }
        } else if (stripChars.length() == 0) {
            return str;
        } else {
            while ((start != strLen)
                    && (stripChars.indexOf(str.charAt(start)) != -1)) {
                start++;
            }
        }

        return str.substring(start);
    }

    private float interpolate(float a, float b, float proportion) {
        return (a + ((b - a) * proportion));
    }

    private int interpolateColor(int a, int b, float proportion) {
        float[] hsva = new float[3];
        float[] hsvb = new float[3];
        Color.colorToHSV(a, hsva);
        Color.colorToHSV(b, hsvb);
        assert (proportion <= 1.0 && proportion >= 0.0);
        for (int i = 0; i < 3; i++) {
            hsvb[i] = interpolate(hsva[i], hsvb[i], proportion);
        }
        return Color.HSVToColor(hsvb);
    }


    private void receive(byte[] data) {
        String received_text_raw = new String(data);
        String received_text = stripStart(received_text_raw, "0");
        receivedText.append(received_text);
        currentPointText.setText(received_text);
        t_in_pic.setText(received_text.replace("\n","") + "°C");

        boolean insert_flag = false;
        float received_point = 0;
        try {
            received_point = Float.parseFloat(received_text);
            insert_flag = true;

            recording_flag = true;
            if (received_point > VIBRATE_UPBOUND) {
                Vibrator v = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
                v.vibrate(400);
            }
            int colorCold = ContextCompat.getColor(getContext(), R.color.colorCold); // -13330213
            int colorWarm = ContextCompat.getColor(getContext(), R.color.colorWarm); // -44234
            int colorRotateBackground = ContextCompat.getColor(getContext(),
                    R.color.colorRotateBackground);
            float LOWEST_VALUE = 27;
            float HIGHEST_VALUE = 35;
            float propotion = (received_point - LOWEST_VALUE) / (HIGHEST_VALUE - LOWEST_VALUE);
            if (received_point > HIGHEST_VALUE) {
                propotion = 1;
            } else if (received_point < LOWEST_VALUE) {
                propotion = 0;
            }
            int colorInterpolated = interpolateColor(colorCold, colorWarm, propotion);
            if (darkRotateBackgroundFlag) {
                colorfulBackground.setBackgroundColor(colorRotateBackground);
                colorfulBackground.setAlpha((float) 1.0);

            } else {
                colorfulBackground.setBackgroundColor(colorInterpolated);
                colorfulBackground.setAlpha((float) 0.7);
            }

        } catch (Exception e) {
            insert_flag = false;
            Log.w("NotValidFloat", received_text);
            e.printStackTrace();
        }
        if (insert_flag) {
            // Timestamp is UTC!
            long id = db.insertPoint(received_point);
            Point latest_point = db.getPoint(id);
            if (latest_point != null) {
                pointsList.add(0, latest_point);
                addEntry(latest_point);
                Log.w("SUCCESS", "inserted point");
            }

        }
        Log.w(TAG, received_text);
    }

    private void status(String str) {
        SpannableStringBuilder spn = new SpannableStringBuilder(str + '\n');
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)),
                0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        receivedText.append(spn);
    }

    /*
     * SerialListener
     */
    @Override
    public void onSerialConnect() {
        status("connected");
        connected = Connected.True;
    }

    @Override
    public void onSerialConnectError(Exception e) {
        status("connection failed: " + e.getMessage());
        disconnect();
    }

    @Override
    public void onSerialRead(byte[] data) {
        receive(data);
    }

    @Override
    public void onSerialIoError(Exception e) {
        status("connection lost: " + e.getMessage());
        disconnect();
    }

    private void setupChart() {
        assert mChart != null;
        // disable description text
//        Description descChartDescription = new Description();
//        descChartDescription.setEnabled(false);
//        mChart.setDescription(descChartDescription);
        mChart.getDescription().setEnabled(false);


        // enable touch gestures
        mChart.setTouchEnabled(true);
        // if disabled, scaling can be done on x- and y-axis separately
        mChart.setPinchZoom(true);
        // enable scaling
        mChart.setScaleEnabled(true);
        mChart.setDrawGridBackground(false);
        // set an alternative background color
        mChart.setBackgroundColor(Color.WHITE);
        mChart.setAlpha((float) 0.85);
    }

    private void setupAxes() {
        XAxis xl = mChart.getXAxis();
        xl.setTextColor(Color.BLACK);
        xl.setDrawGridLines(false);
        xl.setAvoidFirstLastClipping(true);
        xl.setEnabled(true);

        YAxis leftAxis = mChart.getAxisLeft();
        leftAxis.setTextColor(Color.BLACK);
        leftAxis.setAxisMaximum(MAX_TEMPERATURE);
        leftAxis.setAxisMinimum(MIN_TEMPERATURE);
        leftAxis.setDrawGridLines(true);

        YAxis rightAxis = mChart.getAxisRight();
        rightAxis.setEnabled(false);

        // Add a limit line
        LimitLine ll = new LimitLine(ALERT_POINT_UPBOUND,
                String.format("报警温度: %s", ALERT_POINT_UPBOUND));
        ll.setLineWidth(2f);
        ll.setLabelPosition(LimitLine.LimitLabelPosition.RIGHT_TOP);
        ll.setTextSize(10f);
        ll.setTextColor(Color.BLACK);
        // reset all limit lines to avoid overlapping lines
        leftAxis.removeAllLimitLines();
        leftAxis.addLimitLine(ll);
        // limit lines are drawn behind data (and not on top)
        leftAxis.setDrawLimitLinesBehindData(true);
    }

    private void setupData() {
        LineData data = new LineData();
        data.setValueTextColor(Color.WHITE);

        // add empty data
        mChart.setData(data);
    }

    private void setLegend() {
        // get the legend (only possible after setting data)
        Legend l = mChart.getLegend();

        // modify the legend ...
        l.setForm(Legend.LegendForm.CIRCLE);
        l.setTextColor(Color.WHITE);
    }

    private void addEntry(Point point) {
        LineData data = mChart.getData();

        if (data != null) {
            ILineDataSet set = data.getDataSetByIndex(0);

            if (set == null) {
                set = createSet();
                data.addDataSet(set);
            }

            data.addEntry(new Entry(set.getEntryCount(), point.getPoint()), 0);

            // let the chart know it's data has changed
            data.notifyDataChanged();
            mChart.notifyDataSetChanged();

            // limit the number of visible entries
            mChart.setVisibleXRangeMaximum(15);


            // move to the latest entry
            mChart.moveViewToX(data.getEntryCount());
        }
    }

    private LineDataSet createSet() {
        LineDataSet set = new LineDataSet(null, "Memory Data");
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
//        set.setColors(ColorTemplate.MATERIAL_COLORS[0]);
        set.setColors(ColorTemplate.PASTEL_COLORS[0]);
        set.setCircleColor(Color.BLACK);
        set.setLineWidth(2f);
        set.setCircleRadius(4f);
        set.setValueTextColor(Color.BLACK);
        set.setValueTextSize(10f);
        // To show values of each point
        set.setDrawValues(true);

        return set;
    }


}