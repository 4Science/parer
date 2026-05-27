package it.ivscience.parer.utils;

import org.apache.commons.lang3.StringUtils;

public class ParerUtils {

    private ParerUtils() {}

    public static String getKeyNumber(String key) {
        if (StringUtils.contains(key, "^")) {
            return key.substring(key.lastIndexOf('^') + 1);
        }
        return key;
    }
}
