package me.lqy.temperatuemonitor;
import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHandler extends SQLiteOpenHelper {
    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "Trealtime";
    private static final String TABLE_REALTIME = "contacts";

    private static final String KEY_ID = "id";
    private static final String KEY_T = "T";
    private static final String KEY_TIME = "time";

    public DatabaseHandler(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);

    }
     @Override
     public void onCreate(SQLiteDatabase db) {
         String CREATE_T_REALTIME_TABLE = "CREATE TABLE " + TABLE_REALTIME + "("
                 + KEY_ID + " INTEGER PRIMARY KEY," + KEY_T + " REAL,"
                 + KEY_TIME + " TEXT" + ")";
         db.execSQL(CREATE_T_REALTIME_TABLE);
     }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_REALTIME);

        onCreate(db);
    }

    void addTData(Point Tdata) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(KEY_T, Tdata.getPoint());
        values.put(KEY_TIME, Tdata.getTimestamp());

    }
}
