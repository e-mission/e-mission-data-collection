package edu.berkeley.eecs.cfc_tracker.obd;

import android.util.Log;

import com.github.pires.obd.commands.ObdCommand;
import com.github.pires.obd.commands.control.VinCommand;
import com.github.pires.obd.commands.engine.MassAirFlowCommand;
import com.github.pires.obd.commands.fuel.ConsumptionRateCommand;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


/**
 * Created by Patrick on 24/08/2015.
 */
public class CheckObdCommands extends ObdCommand {

    private Boolean supportMAF= false;
    private Boolean supportFuelRate=false;
    private float MAF;
    private float fuelRate;
    private String VIN;

    public Boolean getSupportMAF() {
        return supportMAF;
    }

    public Boolean getSupportFuelRate() {
        return supportFuelRate;
    }

    public String getVIN() {
        return VIN;
    }

    public CheckObdCommands() {
        super("");
    }

    @Override
    protected void performCalculations() {
    }

    @Override
    public void run(InputStream in, OutputStream out) throws IOException, InterruptedException {
        //check MAF
        try {
            final MassAirFlowCommand mafCommand= new MassAirFlowCommand();
            mafCommand.run(in, out);
            MAF= (float) mafCommand.getMAF();
            if (MAF!=-1.0f){
                supportMAF=true;
                Log.d("CHECK", "MAF supported");
            }else{
                supportMAF=false;
                Log.d("CHECK", "MAF not supported");
            }

        }catch (Exception e){
            supportMAF=false;
            Log.d("CHECK", "MAF not supported");
        }

        try {
            final ConsumptionRateCommand fuelrate= new ConsumptionRateCommand();
            fuelrate.run(in, out);
            fuelRate= (float) fuelrate.getLitersPerHour();
            if (fuelRate!=-1.0f){
                supportFuelRate=true;
                Log.d("CHECK", "Fuel rate supported");
            }else{
                supportFuelRate=false;
                Log.d("CHECK", "Fuel rate not supported");
            }

        }catch (Exception e){
            supportFuelRate=false;
            Log.d("CHECK", "Fuel rate  not supported");
        }


        try {
            final VinCommand vinCommand= new VinCommand();
            vinCommand.run(in, out);
            VIN=  vinCommand.getFormattedResult();
            if (!VIN.equals("")){
                Log.d("CHECK", "VIN : "+VIN);
            }else{
                VIN=getEcuName(in, out);
                Log.d("CHECK", "VIN unknown");
            }

        }catch (Exception e){
            VIN=getEcuName(in, out);
            Log.d("CHECK", "VIN unknown");
        }
    }
    public String getEcuName(InputStream in, OutputStream out){
        final EcuNameCommand nameCommand= new EcuNameCommand();
        String name="NO NAME";
        try{
            nameCommand.run(in, out);
            name=nameCommand.getFormattedResult();
        }catch(Exception e){
            Log.d("CHECK", "ECU unknown");
            return "NO NAME";
        }
        return name;
    }

    @Override
    public String toString() {
        return ""+supportMAF+"\n"+supportFuelRate;
    }

    @Override
    public String getCalculatedResult() {
        return String.valueOf(toString());
    }

    @Override
    public String getFormattedResult() {
        return String.valueOf(toString());
    }

    @Override
    public String getName() {
        return null;
    }
}
