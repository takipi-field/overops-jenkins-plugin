package com.takipi.common.udf.volume;

public class RelativeThresholdFunction extends ThresholdFunction {
	public static String validateInput(String rawInput) {
		return getThresholdInput(rawInput).toString();
	}
	
	public static void execute(String rawContextArgs, String rawInput) {
		execute(rawContextArgs, getThresholdInput(rawInput));
	}
}
