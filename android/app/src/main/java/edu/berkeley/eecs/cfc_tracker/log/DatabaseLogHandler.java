package edu.berkeley.eecs.cfc_tracker.log;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Environment;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.logging.StreamHandler;

/**
 * Created by shankari on 7/16/15.
 */
public class DatabaseLogHandler extends SQLiteOpenHelper {
    private String TABLE_LOG = "logTable";
    private String KEY_LINE = "log_line";
    private String KEY_ID = "ID";
    private static final int DATABASE_VERSION = 1;

    private Context cachedContext;

    public DatabaseLogHandler(Context context) {
        super(context, "logDB", null, DATABASE_VERSION);
        cachedContext = context;
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        String CREATE_LOG_TABLE = "CREATE TABLE " + TABLE_LOG +" (" +
                KEY_ID + " INTEGER PRIMARY_KEY, "+ KEY_LINE +" TEXT)";
        System.out.println("CREATE_CLIENT_STATS_TABLE = " + CREATE_LOG_TABLE);
        sqLiteDatabase.execSQL(CREATE_LOG_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {
        sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + TABLE_LOG);
        onCreate(sqLiteDatabase);
    }

    public void log(String line) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(KEY_LINE, line);
        db.insert(TABLE_LOG, null, cv);
        db.close();
    }

    public void export() {
        SQLiteDatabase db = this.getReadableDatabase();
        try {
            String ourFileName = Log.getLogBase(cachedContext) + "/dumped_log_file.txt";
            System.out.println("ourFileName = "+ourFileName);
            PrintStream out = new PrintStream(new FileOutputStream(ourFileName, true));
            String queryString = "SELECT * from " + TABLE_LOG;
            Cursor cur = db.rawQuery(queryString, null);
            int resultCount = cur.getCount();
            if (cur.moveToFirst()) {
                for (int i = 0; i < resultCount; i++) {
                    out.println(cur.getString(0));
                }
            }
        } catch (IOException e) {
            Toast.makeText(cachedContext, e.getMessage(), Toast.LENGTH_LONG).show();
            System.err.println();
        }
        db.close();
    }
}
