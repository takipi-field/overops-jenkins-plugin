package com.takipi.common.udf.volume;

public class FixedThresholdFunction extends ThresholdFunction {
	private static String adjustInput(String rawInput) {
		return rawInput + "\nrelative_to=Absolute";
	}

	public static String validateInput(String rawInput) {
		return getThresholdInput(adjustInput(rawInput)).toString();
	}
	
	public static void execute(String rawContextArgs, String rawInput) {
		execute(rawContextArgs, getThresholdInput(adjustInput(rawInput)));
	}
}
