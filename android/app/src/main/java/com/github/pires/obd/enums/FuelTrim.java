package com.github.pires.obd.enums;

import java.util.HashMap;
import java.util.Map;

/**
 * Select one of the Fuel Trim percentage banks to access.
 */
public enum FuelTrim {

    SHORT_TERM_BANK_1(0x06, "Short Term Fuel Trim Bank 1"),
    LONG_TERM_BANK_1(0x07, "Long Term Fuel Trim Bank 1"),
    SHORT_TERM_BANK_2(0x08, "Short Term Fuel Trim Bank 2"),
    LONG_TERM_BANK_2(0x09, "Long Term Fuel Trim Bank 2");

    private static Map<Integer, FuelTrim> map = new HashMap<Integer, FuelTrim>();

    static {
        for (FuelTrim error : FuelTrim.values())
            map.put(error.getValue(), error);
    }

    private final int value;
    private final String bank;

    private FuelTrim(final int value, final String bank) {
        this.value = value;
        this.bank = bank;
    }

    /**
     * <p>fromValue.</p>
     *
     * @param value a int.
     * @return a {@link com.github.pires.obd.enums.FuelTrim} object.
     */
    public static FuelTrim fromValue(final int value) {
        return map.get(value);
    }

    /**
     * <p>Getter for the field <code>value</code>.</p>
     *
     * @return a int.
     */
    public int getValue() {
        return value;
    }

    /**
     * <p>Getter for the field <code>bank</code>.</p>
     *
     * @return a {@link java.lang.String} object.
     */
    public String getBank() {
        return bank;
    }

    /**
     * <p>buildObdCommand.</p>
     *
     * @return a {@link java.lang.String} object.
     */
    public final String buildObdCommand() {
        return new String("01 " + value);
    }

}
