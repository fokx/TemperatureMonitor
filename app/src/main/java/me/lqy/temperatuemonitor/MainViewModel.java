package me.lqy.temperatuemonitor;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

import me.lqy.temperatuemonitor.workers.UploadWorker;
import me.lqy.temperatuemonitor.workers.notifyWorker;

public class MainViewModel extends AndroidViewModel {
    private WorkManager workManager;

    //    private Context context;
    public MainViewModel(@NonNull Application application) {
        super(application);
//        context  = getApplication().getApplicationContext()
        workManager = WorkManager.getInstance(application);
    }

    void enableSyncDB() {
        Constraints myConstraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest uploadDBRequest =
                new PeriodicWorkRequest.Builder(
                        UploadWorker.class, 15, TimeUnit.MINUTES)
                        .setConstraints(myConstraints)
                        .addTag("uploadDB")
                        .build();
//        WorkManager.getInstance(context).enqueue(uploadDBRequest);
        workManager.enqueue(uploadDBRequest);
    }

    public void enableHourlySync() {
        Constraints myConstraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest hourlyNotifyRequest =
                new PeriodicWorkRequest.Builder(
                        notifyWorker.class, 60, TimeUnit.MINUTES)
                        .setConstraints(myConstraints)
                        .addTag("hourlyNotify")
                        .build();
        workManager.enqueue(hourlyNotifyRequest);
    }
}
