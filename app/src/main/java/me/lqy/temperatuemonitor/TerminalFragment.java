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
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
    private static final float ALERT_POINT_UPBOUND = (float) 37.3;
    private static final float BIRATE_POINT_UPBOUND = ALERT_POINT_UPBOUND;

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
    private TextView currentPointText;
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
        setupChart();
        setupAxes();
        setupData();
        setLegend();
//        mChart.setVisibility(View.INVISIBLE);

        receivedText = view.findViewById(R.id.received_text);                          // TextView performance decreases with number of spans
        receivedText.setTextColor(getResources().getColor(R.color.colorReceiveText)); // set as default color to reduce number of spans
        receivedText.setMovementMethod(ScrollingMovementMethod.getInstance());
        currentPointText = view.findViewById(R.id.current_temperature);
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


//        sendBtn.setOnClickListener(v -> send(sendText.getText().toString()));
        startReportBtn.setOnClickListener(v -> send1AndsetVisibility());
        haltReportBtn.setOnClickListener(v -> send0AndsetVisibility());
        toggleLogBtn.setOnClickListener(v -> toggleLogPanel());
        toggleViewPointsBtn.setOnClickListener(v -> toggleViewPoints());

        Log.w("CONNECTING TO", webviewAddress);
        viewPoints.loadUrl(webviewAddress);

        return view;
    }

    private void toggleViewPoints() {
        if (viewPoints.getVisibility() == View.INVISIBLE) {
            viewPoints.setVisibility(View.VISIBLE);
        } else if (viewPoints.getVisibility() == View.VISIBLE) {
            viewPoints.setVisibility(View.INVISIBLE);
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
    }

    private void send0AndsetVisibility() {
        send(String.valueOf(MCUConnectStatus.CONNECTED_AND_HALTED.getCode()));
        haltReportBtn.setVisibility(View.INVISIBLE);
        startReportBtn.setVisibility(View.VISIBLE);
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


    private void receive(byte[] data) {
        String received_text_raw = new String(data);
        String received_text = stripStart(received_text_raw, "0");
        receivedText.append(received_text);
        currentPointText.setText(received_text);
        boolean insert_flag = false;
        float received_point = 0;
        try {
            received_point = Float.parseFloat(received_text);
            insert_flag = true;

            if (received_point > ALERT_POINT_UPBOUND) {
                Vibrator v = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
                v.vibrate(400);
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
        set.setColors(ColorTemplate.COLORFUL_COLORS[4]);
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