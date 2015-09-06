package edu.berkeley.eecs.cfc_tracker;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.github.pires.obd.commands.SpeedCommand;
import com.github.pires.obd.commands.control.VinCommand;
import com.github.pires.obd.commands.engine.RPMCommand;
import com.github.pires.obd.commands.engine.ThrottlePositionCommand;
import com.github.pires.obd.commands.fuel.FindFuelTypeCommand;
import com.github.pires.obd.commands.fuel.FuelLevelCommand;
import com.github.pires.obd.commands.protocol.EchoOffCommand;
import com.github.pires.obd.commands.protocol.LineFeedOffCommand;
import com.github.pires.obd.commands.protocol.SelectProtocolCommand;
import com.github.pires.obd.commands.protocol.TimeoutCommand;
import com.github.pires.obd.enums.ObdProtocols;
import com.github.pires.obd.exceptions.NoDataException;
import com.github.pires.obd.exceptions.UnableToConnectException;
import com.github.pires.obd.exceptions.UnsupportedCommandException;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import edu.berkeley.eecs.cfc_tracker.database.Vehicle;
import edu.berkeley.eecs.cfc_tracker.database.VehicleDataSource;
import edu.berkeley.eecs.cfc_tracker.location.actions.OBDChangeIntentService;
import edu.berkeley.eecs.cfc_tracker.obd.CheckObdCommands;
import edu.berkeley.eecs.cfc_tracker.obd.EcuNameCommand;
import edu.berkeley.eecs.cfc_tracker.obd.FileUtilities;
import edu.berkeley.eecs.cfc_tracker.obd.FuelEconomyObdCommand;

public class CarActivity extends Activity {
    private boolean chosen = false;
    int REQUEST_ENABLE_BT = 1;
    private String fuelType = "Gasoline";
    private String deviceAddress;
    private BluetoothSocket bluetoothSocket;
    private String TAG = "OBD Response";
    private TextView speed;
    private TextView rpm;
    private TextView fuel;
    private TextView flow;
    private TextView ODO;
    private TextView fuelConsumed;
    private Switch mode;
    private Button connect;
    private TextView fuelTypeLabel;
    private FileUtilities fileUtilities;
    private Context currContext;
    private android.os.Handler timeHandler;
    private boolean fineMode = true;
    private VehicleDataSource vehicleDataSource;
    private List<Vehicle> values;
    private Vehicle currentVehicle;
    private boolean connected = false;

    public float getDistanceDriven() {
        return distanceDriven;
    }

    public void setDistanceDriven(float distanceDriven) {
        this.distanceDriven = distanceDriven;
    }

    public float getFuelUsed() {
        return fuelUsed;
    }

    public void setFuelUsed(float fuelUsed) {
        this.fuelUsed = fuelUsed;
    }

    private float distanceDriven;
    private float fuelUsed;

    public void setDeviceAddress(String deviceAddress) {
        this.deviceAddress = deviceAddress;
    }

    public void setBluetoothSocket(BluetoothSocket bluetoothSocket) {
        this.bluetoothSocket = bluetoothSocket;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.car_layout);
        //load database of vehicles
        vehicleDataSource = new VehicleDataSource(this);
        vehicleDataSource.open();
        values = vehicleDataSource.getAllVehicles();
        //set accurent vehicle
        currentVehicle = new Vehicle();
        speed = (TextView) findViewById(R.id.textViewSpeed);
        rpm = (TextView) findViewById(R.id.textViewRpm);
        fuel = (TextView) findViewById(R.id.textViewFuelEco);
        flow = (TextView) findViewById(R.id.textViewFlow);
        ODO = (TextView) findViewById(R.id.textViewODO);
        fuelConsumed = (TextView) findViewById(R.id.textViewConsumed);
        fuelTypeLabel = (TextView) findViewById(R.id.textViewFuelType);
        connect = (Button) findViewById(R.id.button);
        mode = (Switch) findViewById(R.id.switchMode);
        fileUtilities = new FileUtilities(this);
        timeHandler = new android.os.Handler();
        currContext = this;
        if (mode != null)
            mode.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

                @Override
                public void onCheckedChanged(CompoundButton buttonView,
                                             boolean isChecked) {

                    if (isChecked) {
                        fineMode = true;
                    } else {
                        fineMode = false;
                    }

                }
            });

        connect.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                //search for paired bluetooth device when user clicks connect
                if (!connected) {
                    mode.setEnabled(false);
                    selectAvailableDevices();
                } else {
                    connected = false;
                }
            }
        });
    }

    /**
     * Determine which fuel is used gasoline or diesel
     */
    private void whichFuelType() {
        //new instance of the command fuelType
        final FindFuelTypeCommand fuelTypeObdCommand = new FindFuelTypeCommand();

        try {
            fuelTypeObdCommand.run(bluetoothSocket.getInputStream(), bluetoothSocket.getOutputStream());
            String type = fuelTypeObdCommand.getFormattedResult();
            Log.d(TAG, "Fuel type: " + type);

            if (type.equals("Gasoline")) {
                //update view
                fuelType = "Gasoline";
                fuelTypeLabel.setTextColor(Color.GREEN);
                fuelTypeLabel.setText(fuelType);
                currentVehicle.setFuelTye(fuelType);
                vehicleDataSource.createVehicle(currentVehicle.getVehicle(), currentVehicle.getFuelTye(), currentVehicle.getCommands());
                //launching data readings
                new GetUpdates().execute();

            } else if (type.equals("Diesel")) {
                fuelType = "Diesel";
                fuelTypeLabel.setTextColor(Color.BLACK);
                fuelTypeLabel.setText(fuelType);
                currentVehicle.setFuelTye(fuelType);
                vehicleDataSource.createVehicle(currentVehicle.getVehicle(), currentVehicle.getFuelTye(), currentVehicle.getCommands());
                //launching data readings

                new GetUpdates().execute();

            } else {

                /*
                fuelType = "Gasoline";
                fuelTypeLabel.setTextColor(Color.GREEN);
                fuelTypeLabel.setText(fuelType);
                //launching data readings

                new GetUpdates().execute();*/
                Log.d(TAG, "Fuel type: " + "other");
                fuelDialogType(this);
            }
        } catch (IOException e) {
            //case in which fuelTypeCommand is not supported
           /* Log.d(TAG, "Fuel type: " + "unknown IOException");

            fuelType = "Gasoline";
            fuelTypeLabel.setTextColor(Color.GREEN);
            fuelTypeLabel.setText(fuelType);
            //launching data readings

            new GetUpdates().execute();*/
            Log.d(TAG, "Fuel type: " + "unknown, IOException occurred");
            fuelDialogType(this);


        } catch (NoDataException e) {
            Log.d(TAG, "Fuel type: " + "unknown, NoDataException occurred");
            //ask user
            fuelDialogType(this);


        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (IndexOutOfBoundsException e) {
            //message returned from car has not the correct length
            Log.d(TAG, "Fuel type: " + "unknown, IndexOutOfBoundsException occurred");
            //ask user
            fuelDialogType(this);

        } catch (UnsupportedCommandException e) {
            Log.d(TAG, "Fuel type: " + "unknown, UnsupportedCommandException occurred");
            //ask user
            fuelDialogType(this);

        }

    }

    public synchronized void fuelDialogType(final Context c) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final AlertDialog.Builder adb = new AlertDialog.Builder(c);
                CharSequence items[] = new CharSequence[]{"Gasoline", "Diesel"};
                adb.setSingleChoiceItems(items, 0, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface d, int n) {
                        if (n == 0) {
                            fuelType = "Gasoline";
                            fuelTypeLabel.setTextColor(Color.GREEN);
                            fuelTypeLabel.setText(fuelType);
                            currentVehicle.setFuelTye(fuelType);
                        } else {
                            fuelType = "Diesel";
                            fuelTypeLabel.setTextColor(Color.BLACK);
                            fuelTypeLabel.setText(fuelType);
                            currentVehicle.setFuelTye(fuelType);
                        }

                    }

                }).setCancelable(false).setPositiveButton("Ok",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                                int which) {
                                int sel = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
                                if (sel == 0) {
                                    fuelType = "Gasoline";
                                    fuelTypeLabel.setTextColor(Color.GREEN);
                                    fuelTypeLabel.setText(fuelType);
                                    currentVehicle.setFuelTye(fuelType);
                                    vehicleDataSource.createVehicle(currentVehicle.getVehicle(), currentVehicle.getFuelTye(), currentVehicle.getCommands());
                                } else {
                                    fuelType = "Diesel";
                                    fuelTypeLabel.setTextColor(Color.BLACK);
                                    fuelTypeLabel.setText(fuelType);
                                    currentVehicle.setFuelTye(fuelType);
                                    vehicleDataSource.createVehicle(currentVehicle.getVehicle(), currentVehicle.getFuelTye(), currentVehicle.getCommands());
                                }
                                Log.d(TAG, "Which value=" + which);
                                Log.d(TAG, "Selected value=" + fuelType);
                                //launching data readings

                                new GetUpdates().execute();

                            }
                        });


                adb.setTitle(R.string.BeginQuestion);

                adb.show();
            }
        });

    }

    /**
     * initialization of bluetooth communication with adapter
     */
    private void initializeCom() {


        Log.d(TAG, "database: " + values.size());
        int currVal = values.size();
        try {
            //initializing obd adapter with standard commands
            //disable echo
            new EchoOffCommand().run(bluetoothSocket.getInputStream(), bluetoothSocket.getOutputStream());
            new LineFeedOffCommand().run(bluetoothSocket.getInputStream(), bluetoothSocket.getOutputStream());
            new TimeoutCommand(200).run(bluetoothSocket.getInputStream(), bluetoothSocket.getOutputStream());
            new SelectProtocolCommand(ObdProtocols.AUTO).run(bluetoothSocket.getInputStream(), bluetoothSocket.getOutputStream());
            if (currVal == 0) {

                CheckObdCommands check = new CheckObdCommands();
                check.run(bluetoothSocket.getInputStream(), bluetoothSocket.getOutputStream());
                String checked = check.toString();
                currentVehicle.setCommands(checked);
                currentVehicle.setVehicle(check.getVIN());
                Log.d(TAG, "commands supported: " + checked);
                //determine fuel type
                whichFuelType();
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (UnableToConnectException e) {
            Log.d(TAG, "UnableToConnectException");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(currContext, R.string.cannotConnect, Toast.LENGTH_SHORT).show();
                }
            });

        }
        if (currVal > 0) {
            Log.d(TAG, "Database contains some vehicles");
            String VIN = "---";
            try {
                final VinCommand vinCommand = new VinCommand();
                vinCommand.run(bluetoothSocket.getInputStream(), bluetoothSocket.getOutputStream());
                VIN = vinCommand.getFormattedResult();

            } catch (Exception e) {
                final EcuNameCommand nameCommand = new EcuNameCommand();
                String name = "NO NAME";
                try {
                    nameCommand.run(bluetoothSocket.getInputStream(), bluetoothSocket.getOutputStream());
                    name = nameCommand.getFormattedResult();
                    VIN = name;
                } catch (Exception ex) {
                    Log.d("CHECK", "ECU unknown");
                    VIN = "NO NAME";
                    //TODO find a way to uniquely identify the vehicle if both vin (vehicle identification number) or ecu name are not available
                }
            }
            int c = 0;
            for (Vehicle v : values) {
                if (v.getVehicle().equals(VIN) && c == 0) {
                    c++;
                    Log.d(TAG, "Vehicle match" + " " + v.getVehicle()
                    );
                    currentVehicle = v;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            fuelType = currentVehicle.getFuelTye();
                            if (fuelType.equals("Gasoline"))
                                fuelTypeLabel.setTextColor(Color.GREEN);
                            else fuelTypeLabel.setTextColor(Color.BLACK);

                            fuelTypeLabel.setText(fuelType);
                        }
                    });
                    //getupdates
                    new GetUpdates().execute();
                    break;
                } else if (c == 0) {
                    c++;
                    try {
                        CheckObdCommands check = new CheckObdCommands();
                        check.run(bluetoothSocket.getInputStream(), bluetoothSocket.getOutputStream());
                        String checked = check.toString();
                        currentVehicle.setCommands(checked);
                        currentVehicle.setVehicle(check.getVIN());
                        Log.d(TAG, "commands supported: " + checked);
                        //determine fuel type
                        whichFuelType();
                        break;
                    } catch (IOException e) {
                        Log.d(TAG, "IOException check Obd");
                    } catch (InterruptedException e) {
                        Log.d(TAG, "InterruptedException check Obd");
                    }
                }
            }
        }

    }

    /**
     * Dialog showing connecting operation
     */
    public void launchRingDialog() {
        final ProgressDialog ringProgressDialog = ProgressDialog.show(CarActivity.this, "Initializing ...", "Connecting to adapter ...", true);
        ringProgressDialog.setCancelable(false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    connectToAdapter();
                } catch (Exception e) {
                    Log.d(TAG, "exception during launc ring dialog");
                }
                ringProgressDialog.dismiss();
                ////////////////////////

            }
        }).start();

    }

    public void resetUI() {
        //close connection
        try {
            bluetoothSocket.close();
            connected = false;
        } catch (IOException e) {
            Log.d(TAG, "Error closing bluetooth socket");
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                connect.setText("Connect");
                connect.setTextColor(Color.BLACK);
                mode.setEnabled(true);
            }
        });
    }

    /**
     * Initialization of the bluetooth adapter
     */
    private void connectToAdapter() {
        if (chosen) {
            BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = btAdapter.getRemoteDevice(deviceAddress);
            UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
            // creation and connection of a bluetooth socket
            try {
                BluetoothSocket socket = device.createInsecureRfcommSocketToServiceRecord(uuid);
                socket.connect();
                setBluetoothSocket(socket);
                connected = true;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        connect.setText("Disconnect");
                        connect.setTextColor(Color.RED);
                    }
                });
            } catch (IOException e) {
                Log.d("Exception", "Bluetooth IO Exception c");
                connected = false;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(currContext, R.string.cannotConnect, Toast.LENGTH_SHORT).show();
                        connect.setText("Connect");
                        connect.setTextColor(Color.BLACK);
                        mode.setEnabled(true);
                    }
                });
            }
        }
        if (bluetoothSocket.getRemoteDevice().getAddress() != null)
            Log.d(TAG, "Bluetooth connected");
        Log.d(TAG, "Device address: " + bluetoothSocket.getRemoteDevice().getAddress());
        initializeCom();
    }

    /**
     * get the list of available paired bluetooth devices
     */
    private void selectAvailableDevices() {

        ArrayList deviceStrs = new ArrayList();
        final ArrayList<String> devices = new ArrayList();

        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        // checking and enabling bluetooth
        if (!btAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            btAdapter = BluetoothAdapter.getDefaultAdapter();
            Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
            if (pairedDevices.size() > 0) {
                for (BluetoothDevice device : pairedDevices) {
                    deviceStrs.add(device.getName() + "\n" + device.getAddress());
                    devices.add(device.getAddress());
                }
            }

            // show a list of paired devices to connect with
            final AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
            ArrayAdapter adapter = new ArrayAdapter(this, android.R.layout.select_dialog_singlechoice,
                    deviceStrs.toArray(new String[deviceStrs.size()]));
            alertDialog.setSingleChoiceItems(adapter, -1, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    int position = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
                    String deviceAddress = devices.get(position);
                    setDeviceAddress(deviceAddress);
                    chosen = true;
                    //notify user for ongoing connection
                    launchRingDialog();

                }
            });

            alertDialog.setTitle(R.string.blueChose);
            alertDialog.show();
        }
    }

    public void showEnd(final float odo, final float liters, final float finalTankLevel) {
        //final String[] trips={"School", "City", "Groceries", "Highway", "Gas station", "trip A", "trip B"};
        Resources res = getResources();
        final String[] trips = res.getStringArray(R.array.string_array_trips);
        final AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
        ArrayAdapter adapter = new ArrayAdapter(this, android.R.layout.select_dialog_singlechoice,
                trips);
        alertDialog.setSingleChoiceItems(adapter, -1, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                int position = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
                String trip = trips[position];
                String currentDateandTime = new SimpleDateFormat("yyyy MM dd HH mm ss").format(new Date());
                fileUtilities.write("DataEMissionSummary" + currentDateandTime + ".txt", "trip: " + trip + "\t" + "fuel: " + liters + "\t" + "KM: " + odo + "\t" + "fineMode: " + fineMode + "\t" + "TankLevelFinal: " + finalTankLevel + "\n");
            }
        });
        alertDialog.setTitle(R.string.EndQuestion);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                alertDialog.show();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                ArrayList deviceStrs = new ArrayList();
                final ArrayList<String> devices = new ArrayList();
                BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
                Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
                if (pairedDevices.size() > 0) {
                    for (BluetoothDevice device : pairedDevices) {
                        deviceStrs.add(device.getName() + "\n" + device.getAddress());
                        devices.add(device.getAddress());
                    }
                }

                // show a list of paired devices to connect with
                final AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
                ArrayAdapter adapter = new ArrayAdapter(this, android.R.layout.select_dialog_singlechoice,
                        deviceStrs.toArray(new String[deviceStrs.size()]));
                alertDialog.setSingleChoiceItems(adapter, -1, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        int position = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
                        String deviceAddress = devices.get(position);
                        setDeviceAddress(deviceAddress);
                        chosen = true;
                        //notify user for ongoing connection
                        launchRingDialog();

                    }
                });

                alertDialog.setTitle(R.string.blueChose);
                alertDialog.show();

            }
        }
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Async task responsible for getting updates from adapter and refreshing the user interface
     */
    private class GetUpdates extends AsyncTask<Void, String, Void> {
        /**
         * continuos execution of bluetooth commands to get data readings
         */

        private void testCommands() {
            Boolean ready = false;
            String rpmResult = null;
            String speedResult = null;
            String fuelResult = "" + -1 + " L/100km";
            String fuelFlow = "" + -1 + " L/h";
            String odometer = "0 Km";
            String consumption = "0 L";
            float tankLevel = 0.f;
            float finalTankLevel = 0.f;
            boolean throttleSupported = false;
            boolean fuelLevelSupported = false;
            FuelLevelCommand fuelLevelCommand = new FuelLevelCommand();
            RPMCommand engineRpmCommand = new RPMCommand();
            SpeedCommand speedCommand = new SpeedCommand();
            FuelEconomyObdCommand fuelEconomy = new FuelEconomyObdCommand(currentVehicle.getFuelTye(), currentVehicle.getCommands());
            ThrottlePositionCommand throttlePositionObdCommand = new ThrottlePositionCommand();
            try {
                throttlePositionObdCommand.run(bluetoothSocket.getInputStream(), bluetoothSocket.getOutputStream());
                throttleSupported = true;

            } catch (UnsupportedCommandException e) {
                throttleSupported = false;
                Log.d(TAG, "throttleSupported false");
            } catch (IOException e) {
                throttleSupported = false;
                Log.d(TAG, "throttle IO exception");
            } catch (InterruptedException e) {
                throttleSupported = false;
                Log.d(TAG, "throttle Interrupted exception");

            }

            try {
                speedCommand.run(bluetoothSocket.getInputStream(), bluetoothSocket.getOutputStream());
                fuelEconomy.run(bluetoothSocket.getInputStream(), bluetoothSocket.getOutputStream());
                ready = true;
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (UnableToConnectException e) {
                Log.d(TAG, "UnableToConnectException inside");
                //Toast.makeText(currContext,"Unable to connect, try again",Toast.LENGTH_SHORT).show();
                this.cancel(true);
                try {
                    this.finalize();
                } catch (Throwable throwable) {
                    throwable.printStackTrace();
                }
            }
            try {
                fuelLevelCommand.run(bluetoothSocket.getInputStream(), bluetoothSocket.getOutputStream());
                tankLevel = fuelLevelCommand.getFuelLevel();
                Log.d(TAG, "initial tankLevel: " + tankLevel);
                fuelLevelSupported = true;
            } catch (IOException e) {
                tankLevel = -1.f;
                Log.d(TAG, "error initial tankLevel: " + tankLevel);
                fuelLevelSupported = false;
            } catch (InterruptedException e) {
                tankLevel = -1.f;
                fuelLevelSupported = false;
                Log.d(TAG, "error initial tankLevel: " + tankLevel);
            } catch (Exception e) {
                //here nodata exception occurs when the command is not supported, but catching it seems not to work
                tankLevel = -1.f;
                fuelLevelSupported = false;
                Log.d(TAG, "error initial tankLevel: " + tankLevel);
            }

            //getting time in order to determine distance driven
            long previousTime = System.currentTimeMillis();
            long previousSpeed = speedCommand.getMetricSpeed();
            float previousFlow = fuelEconomy.getFlow();
            //distance driven
            float kmODO = 0.f;
            float fuelCons = 0.f;
            while (!Thread.currentThread().isInterrupted() && ready) {
                try {
                    long currentTime = System.currentTimeMillis();
                    long deltaTime = currentTime - previousTime;
                    previousTime = currentTime;
                    engineRpmCommand.run(bluetoothSocket.getInputStream(), bluetoothSocket.getOutputStream());
                    speedCommand.run(bluetoothSocket.getInputStream(), bluetoothSocket.getOutputStream());

                    rpmResult = engineRpmCommand.getFormattedResult();
                    speedResult = speedCommand.getFormattedResult();
                    Log.d(TAG, "deltaTime: " + deltaTime);
                    //calculation of distance driven
                    if (fineMode) {
                        kmODO += ((float) speedCommand.getMetricSpeed()) * ((float) deltaTime) / 1000 / 3600;
                        odometer = "" + String.format("%.3f", kmODO) + " Km";
                        setDistanceDriven(kmODO);
                    } else {
                        long currSpeed = speedCommand.getMetricSpeed();
                        long avgSpeed = (currSpeed + previousSpeed) / 2;
                        previousSpeed = currSpeed;
                        kmODO += ((float) avgSpeed) * ((float) deltaTime) / 1000 / 3600;
                        odometer = "" + String.format("%.3f", kmODO) + " Km";
                        setDistanceDriven(kmODO);
                    }

                    //managing fuel flow rate = 0 when the car is moving but you are not accelerating
                    if (engineRpmCommand.getRPM() >= 1200 && throttleSupported) {
                        throttlePositionObdCommand.run(bluetoothSocket.getInputStream(), bluetoothSocket.getOutputStream());
                        if (((int) throttlePositionObdCommand.getPercentage()) == 0) {
                            fuelFlow = "" + 0 + " L/h";
                            fuelResult = "" + 0 + " L/100km";
                            //cut off
                            fuelEconomy.setFlow(0.f);
                        } else {
                            fuelEconomy.run(bluetoothSocket.getInputStream(), bluetoothSocket.getOutputStream());
                            fuelFlow = "" + String.format("%.3f", fuelEconomy.getFlow()) + " L/h";
                            fuelResult = fuelEconomy.getFormattedResult();
                        }
                    } else {
                        fuelEconomy.run(bluetoothSocket.getInputStream(), bluetoothSocket.getOutputStream());
                        //fuelFlow = "" + fuelEconomy.getFlow() + " L/h";
                        fuelFlow = "" + String.format("%.3f", fuelEconomy.getFlow()) + " L/h";
                        if (speedCommand.getMetricSpeed() > 1) {
                            fuelResult = fuelEconomy.getFormattedResult();
                        } else {
                            fuelResult = "---";
                        }
                    }
                    //calculating fuel consumed
                    if (fineMode) {
                        fuelCons += ((float) deltaTime) / 1000 * fuelEconomy.getFlow() / 3600;
                        consumption = "" + String.format("%.3f", fuelCons) + " L";
                        setFuelUsed(fuelCons);
                    } else {
                        float currFlow = fuelEconomy.getFlow();
                        float avgFlow = (previousFlow + currFlow) / 2;
                        previousFlow = currFlow;
                        fuelCons += ((float) deltaTime) / 1000 * avgFlow / 3600;
                        consumption = "" + String.format("%.3f", fuelCons) + " L";
                        setFuelUsed(fuelCons);
                    }
                    if (fuelLevelSupported) {
                        fuelLevelCommand.run(bluetoothSocket.getInputStream(), bluetoothSocket.getOutputStream());
                        if (fuelLevelCommand.getFuelLevel() > 1.0f && tankLevel != -1.0f) {
                            finalTankLevel = tankLevel - fuelLevelCommand.getFuelLevel();
                            Log.d(TAG, "Actual level: " + fuelLevelCommand.getFuelLevel());
                            Log.d(TAG, "intermediate tankLevel: " + finalTankLevel);
                        } else {
                            Log.d(TAG, "intermediate tankLevel: " + finalTankLevel);
                        }
                    }


                    //Saving data to external memory for analysis
                    String currentDateandTime = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
                    fileUtilities.write("DataEMission" + currentDateandTime + ".txt", engineRpmCommand.getRPM() + "\t" + speedCommand.getMetricSpeed() + "\t" +/*fuelEconomy.getLitersPer100Km()*/fuelEconomy.getFlow() + "\t" + currentTime + "\n");
                    //saving to userCache, consider to use always coarseMode (finemode=false) to limit database updates
                    //you can also add if trip is ended (low rpm)
                    Bundle b = toBundle(fuelEconomy.getFlow(),speedCommand.getMetricSpeed(),getDistanceDriven(),engineRpmCommand.getRPM(),getFuelUsed());
                    Intent i= new Intent(currContext, OBDChangeIntentService.class);
                    i.putExtra("VehicleData",b);
                    startService(i);
                } catch (IOException e) {

                    e.printStackTrace();
                    showEnd(kmODO, fuelCons, finalTankLevel);
                    resetUI();
                    break;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    showEnd(kmODO, fuelCons, finalTankLevel);
                    e.printStackTrace();
                    break;
                } catch (NoDataException e) {
                    Log.d(TAG, "final tankLevel: " + finalTankLevel);

                    showEnd(kmODO, fuelCons, finalTankLevel);
                    resetUI();
                    e.printStackTrace();
                    break;
                } catch (IndexOutOfBoundsException e) {
                    fuelResult = "NO DATA";
                } catch (Exception e) {
                    e.printStackTrace();
                    showEnd(kmODO, fuelCons, finalTankLevel);
                    resetUI();
                    break;
                }

                Log.d(TAG, "RPM: " + rpmResult);
                Log.d(TAG, "Speed: " + speedResult);
                Log.d(TAG, "ODO: " + odometer);
                //publishing results to update the ui
                publishProgress(speedResult, rpmResult, fuelResult, fuelFlow, odometer, consumption);
                if (!connected) {
                    Log.d(TAG, "Disconnect button pressed, terminating...");
                    showEnd(kmODO, fuelCons, finalTankLevel);
                    resetUI();
                    break;
                }
                if (!bluetoothSocket.isConnected()) {
                    Log.d(TAG, "Bluetooth not connected,  final tankLevel: " + finalTankLevel);
                    showEnd(kmODO, fuelCons, finalTankLevel);
                    resetUI();
                    break;
                }
                if (!fineMode) {
                    try {
                        Thread.sleep(30000);
                        //previousTime = System.currentTimeMillis();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                if (engineRpmCommand.getRPM() < 100) {
                    Log.d(TAG, "Low RPM, final tankLevel: " + finalTankLevel);
                    showEnd(kmODO, fuelCons, finalTankLevel);
                    resetUI();
                    break;
                }
            }
        }

        public Bundle toBundle(float flow, float speed, float km, int rpm, float litersSF) {
            Bundle b = new Bundle();
            b.putFloat("FuelFlow", flow);
            b.putFloat("Speed", speed);
            b.putFloat("KM", km);
            b.putInt("RPM", rpm);
            b.putFloat("Liters", litersSF);
            return b;
        }

        @Override
        protected Void doInBackground(Void... params) {
            //execute commands to read vehicle parameters
            if (bluetoothSocket.isConnected()) {
                testCommands();
            }

            return null;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            //updating the ui
            speed.setText(values[0]);
            rpm.setText(values[1]);
            fuel.setText(values[2]);
            flow.setText(values[3]);
            ODO.setText(values[4]);
            fuelConsumed.setText(values[5]);
        }
    }
}
