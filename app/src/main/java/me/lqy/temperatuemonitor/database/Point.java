package me.lqy.temperatuemonitor;

import java.util.Date;

public class Point {
    public static final String TABLE_NAME = "points";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_POINT = "point";
    public static final String COLUMN_SYNCED = "synced";
    public static final String COLUMN_TIMESTAMP = "timestamp";

    private int id;
    private float point;
    private int synced;
    private String timestamp;

    public static final String CREATE_TABLE =
            "CREATE TABLE " + TABLE_NAME + "("
                    + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + COLUMN_POINT + " REAL,"
                    + COLUMN_SYNCED + " INTEGER,"
                    + COLUMN_TIMESTAMP + " DATETIME DEFAULT CURRENT_TIMESTAMP"
                    + ")";

    public Point() {

    }

    public Point(int id, float point, int synced, String timestamp) {
        this.id = id;
        this.point = point;
        this.synced = synced;
        this.timestamp = timestamp;
    }

    public int getId() {
        return this.id;
    }

    public float getPoint() {
        return this.point;
    }

    public int getSynced() {
        return this.synced;
    }

    public String getTimestamp() {
        return this.timestamp;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setPoint(float point) {
        this.point = point;
    }

    public void setSynced(int synced) {
        this.synced = synced;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
