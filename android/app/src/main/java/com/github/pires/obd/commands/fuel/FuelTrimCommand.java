package com.github.pires.obd.commands.fuel;

import com.github.pires.obd.commands.PercentageObdCommand;
import com.github.pires.obd.enums.FuelTrim;

/**
 * Fuel Trim.
 */
public class FuelTrimCommand extends PercentageObdCommand {

    private final FuelTrim bank;

    /**
     * Default ctor.
     * <p>
     * Will read the bank from parameters and construct the command accordingly.
     * Please, see FuelTrim enum for more details.
     *
     * @param bank a {@link com.github.pires.obd.enums.FuelTrim} object.
     */
    public FuelTrimCommand(final FuelTrim bank) {
        super(bank.buildObdCommand());
        this.bank = bank;
    }

    public FuelTrimCommand() {
        this(FuelTrim.SHORT_TERM_BANK_1);
    }

    /**
     * @param value
     * @return
     */
    private float prepareTempValue(final int value) {
        return Double.valueOf((value - 128) * (100.0 / 128)).floatValue();
    }

    protected void performCalculations() {
        // ignore first two bytes [hh hh] of the response
        percentage = prepareTempValue(buffer.get(2));
    }

    /**
     * @return the readed Fuel Trim percentage value.
     * @deprecated use #getCalculatedResult()
     */
    public final float getValue() {
        return percentage;
    }

    /**
     * @return the name of the bank in string representation.
     */
    public final String getBank() {
        return bank.getBank();
    }

    @Override
    public String getName() {
        return bank.getBank();
    }

}
