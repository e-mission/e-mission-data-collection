package edu.berkeley.eecs.cfc_tracker.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Created by Patrick on 23/08/2015.
 */
public class OBDSqLiteHelper extends SQLiteOpenHelper {
    public static final String TABLE_VEHICLE = "vehicles";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_VEHICLE = "vehicle";
    public static final String COLUMN_FUEL_TYPE = "type";
    public static final String COLUMN_COMMANDS = "command";
    private static final String DATABASE_NAME = "vehicle.db";
    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_CREATE = "create table "
            + TABLE_VEHICLE + "(" + COLUMN_ID
            + " integer primary key autoincrement, " + COLUMN_VEHICLE
            + " text not null," + COLUMN_FUEL_TYPE
            + " text not null,"  +COLUMN_COMMANDS
            + " text not null"+");";

    public OBDSqLiteHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(DATABASE_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }
}
