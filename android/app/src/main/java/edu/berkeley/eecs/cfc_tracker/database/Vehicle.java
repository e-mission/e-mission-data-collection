package edu.berkeley.eecs.cfc_tracker.database;

/**
 * Created by Patrick on 23/08/2015.
 */
public class Vehicle {
    private long id;
    private String vehicle;
    private String fuelTye;
    private String commands;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getVehicle() {
        return vehicle;
    }

    public void setVehicle(String vehicle) {
        this.vehicle = vehicle;
    }

    public String getFuelTye() {
        return fuelTye;
    }

    public void setFuelTye(String fuelTye) {
        this.fuelTye = fuelTye;
    }

    public String getCommands() {
        return commands;
    }

    public void setCommands(String commands) {
        this.commands = commands;
    }

}
