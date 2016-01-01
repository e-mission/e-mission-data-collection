package edu.berkeley.eecs.cfc_tracker.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Patrick on 23/08/2015.
 */
public class VehicleDataSource {
    private SQLiteDatabase database;
    private OBDSqLiteHelper dbHelper;
    private String[] allColumns = { OBDSqLiteHelper.COLUMN_ID,
            OBDSqLiteHelper.COLUMN_VEHICLE, OBDSqLiteHelper.COLUMN_FUEL_TYPE, OBDSqLiteHelper.COLUMN_COMMANDS };

    public VehicleDataSource(Context context){
        dbHelper=new OBDSqLiteHelper(context);
    }
    public void open() throws SQLException {
        database = dbHelper.getWritableDatabase();
    }
    public void close() {
        dbHelper.close();
    }
    public Vehicle createVehicle(String vehicle, String fuelType, String commands){
        ContentValues values = new ContentValues();
        values.put(OBDSqLiteHelper.COLUMN_VEHICLE,vehicle);
        values.put(OBDSqLiteHelper.COLUMN_FUEL_TYPE,fuelType);
        values.put(OBDSqLiteHelper.COLUMN_COMMANDS,commands);
        long insertId=database.insert(OBDSqLiteHelper.TABLE_VEHICLE, null, values);
        Cursor cursor = database.query(OBDSqLiteHelper.TABLE_VEHICLE,
                allColumns, OBDSqLiteHelper.COLUMN_ID + " = " + insertId, null,
                null, null, null);
        cursor.moveToFirst();
        Vehicle newVehicle= cursorToVehicle(cursor);
        cursor.close();
        return newVehicle;

    }
    public void deleteVehicle(Vehicle vehicle){
        long id =vehicle.getId();
        database.delete(OBDSqLiteHelper.TABLE_VEHICLE, OBDSqLiteHelper.COLUMN_ID
                + " = " + id, null);

    }
    public List<Vehicle> getAllVehicles(){
        List<Vehicle> vehicles= new ArrayList<Vehicle>();
        Cursor cursor = database.query(OBDSqLiteHelper.TABLE_VEHICLE,
                allColumns, null, null, null, null, null);
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            Vehicle vehicle = cursorToVehicle(cursor);
            vehicles.add(vehicle);
            cursor.moveToNext();
        }
        cursor.close();
        return vehicles;
    }
    private Vehicle cursorToVehicle(Cursor cursor) {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(cursor.getLong(0));
        vehicle.setVehicle(cursor.getString(1));
        vehicle.setFuelTye(cursor.getString(2));
        vehicle.setCommands(cursor.getString(3));
        return vehicle;
    }
}
