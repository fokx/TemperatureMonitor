package me.lqy.temperatuemonitor.workers;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONObject;

import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import me.lqy.temperatuemonitor.Constants;
import me.lqy.temperatuemonitor.database.DatabaseHelper;
import me.lqy.temperatuemonitor.database.Point;

import static me.lqy.temperatuemonitor.workers.WorkerUtils.makeStatusNotification;

public class UploadWorker extends Worker {
    private Context context;

    public UploadWorker(
            @NonNull Context context,
            @NonNull WorkerParameters params) {
        super(context, params);
        this.context = context;
    }


    private static final String TAG = UploadWorker.class.getSimpleName();

    @NonNull
    @Override
    public Result doWork() {
        try {
            uploadDB();
            Log.w(TAG, "upload db every 15 mins");

            return Result.success();
        } catch (Throwable throwable) {

            // Technically WorkManager will return Result.failure()
            // but it's best to be explicit about it.
            // Thus if there were errors, we're return FAILURE
            Log.e(TAG, "Error upload db", throwable);
            return Result.failure();
        }
    }

    private void uploadDB() {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    DatabaseHelper dbHelper = new DatabaseHelper(context);
//                    SQLiteDatabase db = dbHelper.getWritableDatabase();

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
}
