package dev.trashcode07.trashaddons.media.providers;

import dev.trashcode07.trashaddons.media.MediaInfo;

public final class MediaParser {
    public MediaParser() {}

    static MediaInfo parseWindowsPipe(String line) {
        String delimiter = line.contains(":::") ? ":::" : "\\|";
        String[] p = line.split(delimiter, -1);
        if (p.length < 5) return MediaInfo.EMPTY;
        String thumb = p.length >= 7 ? val(p, 6) : null;
        return new MediaInfo(
                val(p, 0), val(p, 1), null, shortSource(val(p, 5)),
                parseLong(val(p, 3)), parseLong(val(p, 2)),
                "true".equalsIgnoreCase(val(p, 4)), thumb, null);
    }

    static String parseMprisMetadataString(String xml, String field) {
        int k = xml.indexOf("string \"" + field + "\"");
        if (k < 0) return null;
        int v = xml.indexOf("variant", k);
        if (v < 0) return null;
        v = xml.indexOf("string \"", v);
        if (v < 0) return null;
        v += 8;
        int e = xml.indexOf('"', v);
        return e < 0 ? null : xml.substring(v, e);
    }

    static long parseMprisMetadataInt64(String xml, String field) {
        int k = xml.indexOf("string \"" + field + "\"");
        if (k < 0) return -1;
        int v = xml.indexOf("variant", k);
        if (v < 0) return -1;
        v = xml.indexOf("int64", v);
        if (v < 0) return -1;
        v += 6;
        int e = xml.indexOf('\n', v);
        try { return Long.parseLong(xml.substring(v, e < 0 ? xml.length() : e).trim()); }
        catch (NumberFormatException ex) { return -1; }
    }

    static long parseMprisVariantInt64(String xml) {
        if (xml == null) return -1;
        int i = xml.indexOf("int64");
        if (i < 0) return -1;
        i += 6;
        int e = xml.indexOf('\n', i);
        try { return Long.parseLong(xml.substring(i, e < 0 ? xml.length() : e).trim()) / 1000; }
        catch (NumberFormatException ex) { return -1; }
    }



    private static long parseLong(String s) {
        if (s == null) return -1;
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return -1; }
    }

    private static String val(String[] arr, int i) {
        String s = i < arr.length ? arr[i].trim() : null;
        return s == null || s.isEmpty() || s.equalsIgnoreCase("null") ? null : s;
    }

    private static String shortSource(String id) {
        if (id == null) return null;
        int dot = id.lastIndexOf('.');
        String n = dot >= 0 ? id.substring(dot + 1) : id;
        return n.replace(".exe", "").replace(".Exe", "").toLowerCase();
    }
}
