package me.lqy.temperatuemonitor;

public class Constants {
    public static final CharSequence VERBOSE_NOTIFICATION_CHANNEL_NAME =
            "Verbose WorkManager Notifications";
    public static String VERBOSE_NOTIFICATION_CHANNEL_DESCRIPTION =
            "Shows notifications whenever work starts";
    public static final int NOTIFICATION_ID = 1;
    public static final CharSequence NOTIFICATION_TITLE = "WorkRequest Starting";
    public static final String CHANNEL_ID = "VERBOSE_NOTIFICATION";
    // not needed if not debug
    public static final long DELAY_TIME_MILLIS = 3000;

    public static final String SYNC_DATA_WORK_NAME = "sync_data_work_name";
    public static final String TAG_SYNC_DATA = "TAG_SYNC_DATA";

    public static final String INTENT_ACTION_DISCONNECT = BuildConfig.APPLICATION_ID + ".Disconnect";
    public static final String NOTIFICATION_CHANNEL = BuildConfig.APPLICATION_ID + ".Channel";
    public static final String INTENT_CLASS_MAIN_ACTIVITY = BuildConfig.APPLICATION_ID + ".MainActivity";

    public static final int NOTIFY_MANAGER_START_FOREGROUND_SERVICE = 1001;

    public static final int DATABASE_VERSION = 1;
    public static final String FILE_DIR = "_mydb";
    public static final String DATABASE_NAME = "realtime_points";

    public static final String urlAddress = "https://temp-sync-1.lqy.me/points";

    public static final String webviewAddress = "https://mytemperature-int.lqy.me";

    private Constants() {
    }
}