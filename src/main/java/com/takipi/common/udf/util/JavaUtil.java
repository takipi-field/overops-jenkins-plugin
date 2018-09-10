package com.takipi.common.udf.util;

public class JavaUtil {
    public static final char DELIM_QUALIFIED = '.';
    public static final char DELIM_INTERNAL = '/';

    public static String toInternalName(String name) {
        if (name == null) {
            throw new IllegalArgumentException();
        }

        return name.replace(DELIM_QUALIFIED, DELIM_INTERNAL);
    }

    public static String toQualifiedName(String name) {
        if (name == null) {
            throw new IllegalArgumentException();
        }

        return name.replace(DELIM_INTERNAL, DELIM_QUALIFIED);
    }
}
