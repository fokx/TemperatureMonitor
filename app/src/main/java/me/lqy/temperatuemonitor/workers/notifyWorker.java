package me.lqy.temperatuemonitor.workers;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import static me.lqy.temperatuemonitor.workers.WorkerUtils.makeStatusNotification;


public class notifyWorker extends Worker {
    private Context context;
    private static final String TAG = UploadWorker.class.getSimpleName();

    public notifyWorker(
            @NonNull Context context,
            @NonNull WorkerParameters params) {
        super(context, params);
        this.context = context;
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            makeStatusNotification("请每隔半个小时测温一次", getApplicationContext());

            Log.w(TAG, "notify periodically");

            return Result.success();
        } catch (Throwable throwable) {

            // Technically WorkManager will return Result.failure()
            // but it's best to be explicit about it.
            // Thus if there were errors, we're return FAILURE
            Log.e(TAG, "Error notify user", throwable);
            return Result.failure();
        }
    }

}
