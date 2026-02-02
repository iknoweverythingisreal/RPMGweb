package com.rpmedia.backend.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility to map Teamup color indices to Hex codes.
 * Based on standard Teamup classic palette.
 */
public class TeamupColorUtil {

    private static final Map<Integer, String> COLOR_MAP = new HashMap<>();

    static {
        // Standard Teamup Palette (Indices 0-48 approx)
        // Source: Common Teamup color definitions or approximations
        COLOR_MAP.put(0, "#99b3ff"); // Light Blue
        COLOR_MAP.put(1, "#ff9999"); // Light Red
        COLOR_MAP.put(2, "#99ff99"); // Light Green
        COLOR_MAP.put(3, "#ffff99"); // Light Yellow
        COLOR_MAP.put(4, "#ffcc99"); // Light Orange
        COLOR_MAP.put(5, "#cc99ff"); // Light Purple
        COLOR_MAP.put(6, "#99ffff"); // Cyan
        COLOR_MAP.put(7, "#ff99cc"); // Pink
        COLOR_MAP.put(8, "#d9d9d9"); // Grey
        COLOR_MAP.put(9, "#ffcccc"); // Pale Red

        // Deeper / Standard Colors
        COLOR_MAP.put(10, "#3a87ad"); // Classic Blue
        COLOR_MAP.put(11, "#b91d47"); // Dark Red
        COLOR_MAP.put(12, "#00a300"); // Dark Green
        COLOR_MAP.put(13, "#e3a21a"); // Mustard
        COLOR_MAP.put(14, "#da532c"); // Burnt Orange
        COLOR_MAP.put(15, "#603cba"); // Deep Purple
        COLOR_MAP.put(16, "#00aba9"); // Teal
        COLOR_MAP.put(17, "#da532c"); // Orange-Red
        COLOR_MAP.put(18, "#2d89ef"); // Bright Blue
        COLOR_MAP.put(19, "#ffc40d"); // Gold

        // Additional variations to cover up to 48+
        COLOR_MAP.put(20, "#ee1111");
        COLOR_MAP.put(21, "#009900");
        COLOR_MAP.put(22, "#000099");
        COLOR_MAP.put(23, "#990099");
        COLOR_MAP.put(24, "#999900");
        COLOR_MAP.put(25, "#009999");
        COLOR_MAP.put(26, "#990000");
        COLOR_MAP.put(27, "#006600");
        COLOR_MAP.put(28, "#000066");
        COLOR_MAP.put(29, "#660066");

        // ... filling gaps with distinct colors
        COLOR_MAP.put(30, "#7e3878");
        COLOR_MAP.put(31, "#603cba");
        COLOR_MAP.put(32, "#2b5797");
        COLOR_MAP.put(33, "#e3a21a");
        COLOR_MAP.put(34, "#1e7145");
        COLOR_MAP.put(35, "#ff0097");
        COLOR_MAP.put(36, "#9f00a7");
        COLOR_MAP.put(37, "#794fbf");
        COLOR_MAP.put(38, "#00aba9");
        COLOR_MAP.put(39, "#2d89ef");

        // Fallbacks for higher indices
        COLOR_MAP.put(40, "#ffc40d");
        COLOR_MAP.put(41, "#da532c");
        COLOR_MAP.put(42, "#ee1111");
        COLOR_MAP.put(43, "#b91d47");
        COLOR_MAP.put(44, "#99b433");
        COLOR_MAP.put(45, "#00a300");
        COLOR_MAP.put(46, "#1e7145");
        COLOR_MAP.put(47, "#2b5797");
        COLOR_MAP.put(48, "#2d89ef");
    }

    private static final String DEFAULT_COLOR = "#3a87ad"; // Default Blue

    public static String getHexColor(Integer index) {
        if (index == null) {
            return DEFAULT_COLOR;
        }
        // Direct lookup
        if (COLOR_MAP.containsKey(index)) {
            return COLOR_MAP.get(index);
        }
        // Fallback: Modulo to ensure we always return a valid color from our palette
        int safeIndex = Math.abs(index) % COLOR_MAP.size();
        return COLOR_MAP.getOrDefault(safeIndex, DEFAULT_COLOR);
    }
}
