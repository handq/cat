package com.handq.track.utils;

/**
 * <p>com.handq.track.utils<br/>
 * 创建时间：2016/12/8 12:23<br/>
 * </p>
 *
 * @author handaquan
 * @version V1.0
 */
public class ServletPathMatcher implements PatternMatcher {
    private static final ServletPathMatcher INSTANCE = new ServletPathMatcher();

    public ServletPathMatcher() {
    }

    public static ServletPathMatcher getInstance() {
        return INSTANCE;
    }

    public boolean matches(String pattern, String source) {
        if(pattern != null && source != null) {
            pattern = pattern.trim();
            source = source.trim();
            int start;
            if(pattern.endsWith("*")) {
                start = pattern.length() - 1;
                if(source.length() >= start && pattern.substring(0, start).equals(source.substring(0, start))) {
                    return true;
                }
            } else if(pattern.startsWith("*")) {
                start = pattern.length() - 1;
                if(source.length() >= start && source.endsWith(pattern.substring(1))) {
                    return true;
                }
            } else if(pattern.contains("*")) {
                start = pattern.indexOf("*");
                int end = pattern.lastIndexOf("*");
                if(source.startsWith(pattern.substring(0, start)) && source.endsWith(pattern.substring(end + 1))) {
                    return true;
                }
            } else if(pattern.equals(source)) {
                return true;
            }

            return false;
        } else {
            return false;
        }
    }
}

