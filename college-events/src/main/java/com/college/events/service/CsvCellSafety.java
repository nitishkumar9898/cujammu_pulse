package com.college.events.service;

/** Spreadsheet formula defense applied before CSV quote/record encoding. */
public final class CsvCellSafety {
    private CsvCellSafety() { }

    public static String literal(String input) {
        if (input == null) return "";
        String stripped = input.stripLeading();
        boolean formula = input.startsWith("\t") || input.startsWith("\r") || input.startsWith("\n")
                || (!stripped.isEmpty() && "=+-@".indexOf(stripped.charAt(0)) >= 0);
        return formula ? "'" + input : input;
    }
}
