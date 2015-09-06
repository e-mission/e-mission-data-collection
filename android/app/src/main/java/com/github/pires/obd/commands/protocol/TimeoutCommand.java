package com.github.pires.obd.commands.protocol;

/**
 * This will set the value of time in milliseconds (ms) that the OBD interface
 * will wait for a response from the ECU. If exceeds, the response is "NO DATA".
 */
public class TimeoutCommand extends ObdProtocolCommand {

    /**
     * @param timeout value between 0 and 255 that multiplied by 4 results in the
     *                desired timeout in milliseconds (ms).
     */
    public TimeoutCommand(int timeout) {
        super("AT ST " + Integer.toHexString(0xFF & timeout));
    }

    /**
     * @param other a {@link TimeoutCommand} object.
     */
    public TimeoutCommand(TimeoutCommand other) {
        super(other);
    }

    @Override
    public String getFormattedResult() {
        return getResult();
    }

    @Override
    public String getName() {
        return "Timeout";
    }

}
