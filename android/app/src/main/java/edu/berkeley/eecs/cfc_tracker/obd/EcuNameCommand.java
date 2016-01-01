package edu.berkeley.eecs.cfc_tracker.obd;

import com.github.pires.obd.commands.PersistentCommand;
import com.github.pires.obd.enums.AvailableCommandNames;

public class EcuNameCommand extends PersistentCommand {

    String name = "";
    int[] bufferUse = new int[]{
            2, 3, 4, 5, 6,
            9, 10, 11, 12, 13,
            16, 17, 18, 19, 20,
            23, 24, 25, 26, 27,
            30, 31, 32, 33, 34
    };

    /**
     * Default ctor.
     */
    public EcuNameCommand() {
        super("09 0A");
    }

    /**
     * Copy ctor.
     *
     * @param other a  object.
     */
    public EcuNameCommand(EcuNameCommand other) {
        super(other);
    }

    @Override
    protected void performCalculations() {
        // ignore first two bytes [01 31] of the response
        StringBuilder b = new StringBuilder();
        for (int i : bufferUse) {
            b.append(new Character((char) buffer.get(i).intValue()).toString());
        }
        name = b.toString().replaceAll("[\u0000-\u001f]", "");
    }

    @Override
    public String getFormattedResult() {
        return String.valueOf(name);
    }

    @Override
    public String getName() {
        return AvailableCommandNames.VIN.getValue();
    }

    @Override
    public String getCalculatedResult() {
        return String.valueOf(name);
    }

}


