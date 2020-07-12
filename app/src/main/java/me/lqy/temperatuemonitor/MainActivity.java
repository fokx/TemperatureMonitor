package me.lqy.temperatuemonitor;

import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import org.json.JSONObject;

import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import me.lqy.temperatuemonitor.database.DatabaseHelper;
import me.lqy.temperatuemonitor.database.Point;
//import me.lqy.temperatuemonitor.workers.UploadWorker;

public class MainActivity extends AppCompatActivity
        implements FragmentManager.OnBackStackChangedListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitleTextColor(getResources().getColor(R.color.colorAppTitle));
        setSupportActionBar(toolbar);
        getSupportFragmentManager().addOnBackStackChangedListener(this);

        uploadFirstRun();

        MainViewModel model = new ViewModelProvider(this).get(MainViewModel.class);
        model.enableSyncDB();
        model.enableHourlySync();

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.fragment, new DevicesFragment(), "devices")
                    .commit();
        } else {
            onBackStackChanged();
        }
    }

    private void uploadFirstRun() {
        Log.w("FIRST RUN", "upload");
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    DatabaseHelper dbHelper = new DatabaseHelper(getApplicationContext());
//                    SQLiteDatabase db = dbHelper.getWritableDatabase();

//                    List<Point> pointsNotSyncedList = new ArrayList<>(dbHelper.getAllPoints());
                    List<Point> pointsNotSyncedList = new ArrayList<>(
                            dbHelper.getAllPointsNotSynced());
                    URL url = new URL(Constants.urlAddress);

                    for (Point point : pointsNotSyncedList) {
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("POST");
                        conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
                        conn.setRequestProperty("Accept", "application/json");
                        conn.setDoOutput(true);
                        conn.setDoInput(true);
                        JSONObject jsonParam = new JSONObject();
                        jsonParam.put("id", point.getId());
                        jsonParam.put("point", point.getPoint());
                        jsonParam.put("synced", point.getSynced());
                        jsonParam.put("timestamp", point.getTimestamp());
                        Log.w("JSON INSIDE attributes", jsonParam.toString());

                        DataOutputStream os = new DataOutputStream(conn.getOutputStream());


                        String to_write = "{\"data\":{\"type\":\"points\", \"attributes\":"
                                + jsonParam.toString()
                                + "}}";
                        Log.w("JSON", to_write);
                        //os.writeBytes(URLEncoder.encode(jsonParam.toString(), "UTF-8"));
                        os.writeBytes(to_write);

                        os.flush();
                        os.close();

                        Log.w("STATUS", String.valueOf(conn.getResponseCode()));
                        Log.w("MSG", conn.getResponseMessage());

                        // update the point in db to be synced
//                        db.execSQL("UPDATE " + Point.TABLE_NAME +
//                                " SET synced = 1 WHERE id = " + String.valueOf(point.getId()));
                        dbHelper.setPointAsSyncedWithId(point.getId());
                        Log.w("updateSyncedTo1", String.valueOf(point.getId()));
                        conn.disconnect();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        thread.start();

    }

    @Override
    public void onBackStackChanged() {
        getSupportActionBar().setDisplayHomeAsUpEnabled(
                getSupportFragmentManager().getBackStackEntryCount() > 0);
    }


    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
