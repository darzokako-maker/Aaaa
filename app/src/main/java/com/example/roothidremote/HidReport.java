package com.example.roothidremote;

import java.util.HashMap;
import java.util.Map;

final class HidReport {
    static final byte[] KEYBOARD_DESCRIPTOR = new byte[]{
            (byte)0x05, (byte)0x01, (byte)0x09, (byte)0x06, (byte)0xA1, (byte)0x01, (byte)0x05, (byte)0x07, 
            (byte)0x19, (byte)0xE0, (byte)0x29, (byte)0xE7, (byte)0x15, (byte)0x00, (byte)0x25, (byte)0x01, 
            (byte)0x75, (byte)0x01, (byte)0x95, (byte)0x08, (byte)0x81, (byte)0x02, (byte)0x95, (byte)0x01, 
            (byte)0x75, (byte)0x08, (byte)0x81, (byte)0x01, (byte)0x95, (byte)0x05, (byte)0x75, (byte)0x01, 
            (byte)0x05, (byte)0x08, (byte)0x19, (byte)0x01, (byte)0x29, (byte)0x05, (byte)0x91, (byte)0x02, 
            (byte)0x95, (byte)0x01, (byte)0x75, (byte)0x03, (byte)0x91, (byte)0x01, (byte)0x95, (byte)0x06, 
            (byte)0x75, (byte)0x08, (byte)0x15, (byte)0x00, (byte)0x25, (byte)0x65, (byte)0x05, (byte)0x07, 
            (byte)0x19, (byte)0x00, (byte)0x29, (byte)0x65, (byte)0x81, (byte)0x00, (byte)0xC0
    };

    static final byte[] MOUSE_DESCRIPTOR = new byte[]{
            (byte)0x05, (byte)0x01, (byte)0x09, (byte)0x02, (byte)0xA1, (byte)0x01, (byte)0x09, (byte)0x01, 
            (byte)0xA1, (byte)0x00, (byte)0x05, (byte)0x09, (byte)0x19, (byte)0x01, (byte)0x29, (byte)0x03, 
            (byte)0x15, (byte)0x00, (byte)0x25, (byte)0x01, (byte)0x95, (byte)0x03, (byte)0x75, (byte)0x01, 
            (byte)0x81, (byte)0x02, (byte)0x95, (byte)0x01, (byte)0x75, (byte)0x05, (byte)0x81, (byte)0x01, 
            (byte)0x05, (byte)0x01, (byte)0x09, (byte)0x30, (byte)0x09, (byte)0x31, (byte)0x09, (byte)0x38, 
            (byte)0x15, (byte)0x81, (byte)0x25, (byte)0x7F, (byte)0x75, (byte)0x08, (byte)0x95, (byte)0x03, 
            (byte)0x81, (byte)0x06, (byte)0xC0, (byte)0xC0
    };

    private static final Map<Character, Key> KEYS = new HashMap<>();

    static {
        for (char c = 'a'; c <= 'z'; c++) add(c, 0, 4 + c - 'a');
        for (char c = '1'; c <= '9'; c++) add(c, 0, 30 + c - '1');
        add('0', 0, 39); add('\n', 0, 40); add('\r', 0, 40); add('\t', 0, 43); add(' ', 0, 44);
        add('-', 0, 45); add('_', 2, 45); add('=', 0, 46); add('+', 2, 46);
        add('[', 0, 47); add('{', 2, 47); add(']', 0, 48); add('}', 2, 48);
        add('\\', 0, 49); add('|', 2, 49); add(';', 0, 51); add(':', 2, 51);
        add('\'', 0, 52); add('"', 2, 52); add('`', 0, 53); add('~', 2, 53);
        add(',', 0, 54); add('<', 2, 54); add('.', 0, 55); add('>', 2, 55); add('/', 0, 56); add('?', 2, 56);
        add('!', 2, 30); add('@', 2, 31); add('#', 2, 32); add('$', 2, 33); add('%', 2, 34);
        add('^', 2, 35); add('&', 2, 36); add('*', 2, 37); add('(', 2, 38); add(')', 2, 39);
    }

    private HidReport() {}

    static byte[] keyboard(char c, boolean down) {
        Key key = KEYS.get(Character.toLowerCase(c));
        boolean uppercaseLetter = Character.isUpperCase(c);
        if (key == null) {
            key = KEYS.get(' ');
            uppercaseLetter = false;
        }
        int modifier = uppercaseLetter ? 0x02 : key.modifier;
        return down ? new byte[]{(byte) modifier, (byte) 0, (byte) key.code, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0} : new byte[8];
    }

    static byte[] mouse(int buttons, int dx, int dy, int wheel) {
        return new byte[]{(byte) buttons, clamp(dx), clamp(dy), clamp(wheel)};
    }

    private static void add(char c, int modifier, int code) { KEYS.put(c, new Key(modifier, code)); }
    private static byte clamp(int v) { return (byte) Math.max(-127, Math.min(127, v)); }
    private static final class Key { final int modifier, code; Key(int modifier, int code) { this.modifier = modifier; this.code = code; } }
}

