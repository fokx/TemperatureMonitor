package me.lqy.temperatuemonitor.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static me.lqy.temperatuemonitor.Constants.DATABASE_NAME;
import static me.lqy.temperatuemonitor.Constants.DATABASE_VERSION;
import static me.lqy.temperatuemonitor.Constants.FILE_DIR;

public class DatabaseHelper extends SQLiteOpenHelper {

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    // for debug purpose
//    public DatabaseHelper(Context context) {
//        super(context, Environment.getExternalStorageDirectory().getPath()
//                + File.separator + FILE_DIR
//                + File.separator + DATABASE_NAME, null, DATABASE_VERSION);
//    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(Point.CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + Point.TABLE_NAME);
        onCreate(db);
    }

    public long insertPoint(float point) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        // `id` and `timestamp` will be inserted automatically.
        values.put(Point.COLUMN_POINT, point);
        values.put(Point.COLUMN_SYNCED, 0); // not synchronised
        long id = db.insert(Point.TABLE_NAME, null, values);
        db.close();
        // return newly inserted row id
        return id;
    }

    public void setPointAsSyncedWithId(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("UPDATE " + Point.TABLE_NAME +
                " SET synced = 1 WHERE id = " + String.valueOf(id));
        db.close();

    }
//    @Override
//    public SQLiteDatabase getWritableDatabase() {
//        return super.getWritableDatabase();
//    }

    public Point getPoint(long id) {
        // get readable database as we are not inserting anything
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(Point.TABLE_NAME,
                new String[]{Point.COLUMN_ID, Point.COLUMN_POINT, Point.COLUMN_SYNCED, Point.COLUMN_TIMESTAMP},
                Point.COLUMN_ID + "=?",
                new String[]{String.valueOf(id)}, null, null, null, null);

        if (cursor != null) {
            cursor.moveToFirst();
        }

        // prepare point object
        assert cursor != null;
        Point point = new Point(
                cursor.getInt(cursor.getColumnIndex(Point.COLUMN_ID)),
                cursor.getFloat(cursor.getColumnIndex(Point.COLUMN_POINT)),
                cursor.getInt(cursor.getColumnIndex(Point.COLUMN_SYNCED)),
                cursor.getString(cursor.getColumnIndex(Point.COLUMN_TIMESTAMP)));

        // close the db connection
        cursor.close();
        db.close();
        return point;
    }

    public List<Point> getAllPoints() {
        List<Point> points = new ArrayList<>();

        // Select All Query
        String selectQuery = "SELECT  * FROM " + Point.TABLE_NAME + " ORDER BY " +
                Point.COLUMN_TIMESTAMP + " DESC";

        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);

        // looping through all rows and adding to list
        if (cursor.moveToFirst()) {
            do {
                Point point = new Point();
                point.setId(cursor.getInt(cursor.getColumnIndex(Point.COLUMN_ID)));
                point.setPoint(cursor.getFloat(cursor.getColumnIndex(Point.COLUMN_POINT)));
                point.setSynced(cursor.getInt(cursor.getColumnIndex(Point.COLUMN_SYNCED)));
                point.setTimestamp(cursor.getString(cursor.getColumnIndex(Point.COLUMN_TIMESTAMP)));

                points.add(point);
            } while (cursor.moveToNext());
        }

        // really needed ?
        cursor.close();
        // close db connection
        db.close();

        // return points list
        return points;
    }

    public List<Point> getAllPointsNotSynced() {
        List<Point> points = new ArrayList<>();

        // Select All Query
        String selectQuery = "SELECT  * FROM " + Point.TABLE_NAME +
                " WHERE synced == 0 " + " ORDER BY "
                + Point.COLUMN_TIMESTAMP + " DESC ";

        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);

        // looping through all rows and adding to list
        if (cursor.moveToFirst()) {
            do {
                Point point = new Point();
                int point_id = cursor.getInt(cursor.getColumnIndex(Point.COLUMN_ID));
                point.setId(point_id);
                point.setPoint(cursor.getFloat(cursor.getColumnIndex(Point.COLUMN_POINT)));
                point.setSynced(cursor.getInt(cursor.getColumnIndex(Point.COLUMN_SYNCED)));
                point.setTimestamp(cursor.getString(cursor.getColumnIndex(Point.COLUMN_TIMESTAMP)));

                points.add(point);
            } while (cursor.moveToNext());
        }

        // really needed ?
        cursor.close();
        // close db connection
        db.close();

        // return points list
        return points;
    }

    public int getPointsCount() {
        String countQuery = "SELECT  * FROM " + Point.TABLE_NAME;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(countQuery, null);

        int count = cursor.getCount();
        cursor.close();
        db.close();


        // return count
        return count;
    }

    public int updatePoint(Point point) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(Point.COLUMN_POINT, point.getPoint());

        // updating row
        int update_result = db.update(
                Point.TABLE_NAME,
                values,
                Point.COLUMN_ID + " = ?",
                new String[]{
                        String.valueOf(point.getId())
                }
        );
        db.close();
        return update_result;
    }

    public void deletePoint(Point point) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(Point.TABLE_NAME, Point.COLUMN_ID + " = ?",
                new String[]{String.valueOf(point.getId())});
        db.close();
    }

}
