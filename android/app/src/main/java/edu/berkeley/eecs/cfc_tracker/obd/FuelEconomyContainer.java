package edu.berkeley.eecs.cfc_tracker.obd;

/**
 * Created by Patrick on 18/06/2015.
 */
public class FuelEconomyContainer {
    private float fuelFlow; // L/h
    private float km; // distance travelled
    private float speed;
    private int rpm;
    private float literSoFar;


    public float getSpeed() {
        return speed;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public int getRpm() {
        return rpm;
    }

    public void setRpm(int rpm) {
        this.rpm = rpm;
    }

    public float getLiterSoFar() {
        return literSoFar;
    }

    public void setLiterSoFar(float literSoFar) {
        this.literSoFar = literSoFar;
    }

    public float getKm() {
        return km;
    }

    public void setKm(float km) {
        this.km = km;
    }

    public float getFuelFlow() {
        return fuelFlow;
    }

    public void setFuelFlow(float fuelFlow) {
        this.fuelFlow = fuelFlow;
    }
    public FuelEconomyContainer(float flow, float speed, float km, int rpm, float litersSF){
        this.fuelFlow =flow;
        this.speed=speed;
        this.km=km;
        this.rpm=rpm;
        this.literSoFar=litersSF;
    }

    @Override
    public String toString() {
        return "Speed"+getSpeed()+ " Flow"+getFuelFlow()+" Km"+getKm()+" RPM"+getRpm()+" Liters"+getLiterSoFar();
    }
}
