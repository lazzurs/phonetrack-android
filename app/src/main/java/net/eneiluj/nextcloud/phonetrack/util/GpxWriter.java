package net.eneiluj.nextcloud.phonetrack.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.eneiluj.nextcloud.phonetrack.model.BasicLocation;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Writes positions as a GPX 1.1 file: one track with one segment, one point per position.
 * A point has its elevation, time (UTC) and number of satellites when they are known; the other
 * fields of a position (speed, bearing, accuracy, battery) have no standard GPX 1.1 element.
 */
public final class GpxWriter {

    public static final String MIME_TYPE = "application/gpx+xml";
    public static final String FILE_EXTENSION = ".gpx";

    /** Longest file name suggested, extension included (most file systems allow 255 bytes). */
    private static final int MAX_FILE_NAME_LENGTH = 100;
    private static final String DEFAULT_FILE_NAME = "track";

    private GpxWriter() {
    }

    /**
     * Writes the GPX document to the stream (UTF-8) and flushes it; the caller closes the stream.
     *
     * @param trackName name of the track (the log job title)
     * @param creator   the program that wrote the file, as GPX asks for
     * @param positions in the order the track goes through them
     * @return the number of points written (positions with an invalid latitude or longitude are left out)
     */
    public static int write(@NonNull OutputStream out, @Nullable String trackName, @NonNull String creator,
                            @NonNull List<? extends BasicLocation> positions) throws IOException {
        Writer w = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        w.write("<gpx version=\"1.1\" creator=\"");
        w.write(escape(creator));
        w.write("\" xmlns=\"http://www.topografix.com/GPX/1/1\""
                + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                + " xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\">\n");
        w.write("  <trk>\n");
        if (trackName != null && !trackName.isEmpty()) {
            w.write("    <name>");
            w.write(escape(trackName));
            w.write("</name>\n");
        }
        w.write("    <trkseg>\n");
        int written = 0;
        for (BasicLocation position : positions) {
            if (writePoint(w, position)) {
                written++;
            }
        }
        w.write("    </trkseg>\n");
        w.write("  </trk>\n");
        w.write("</gpx>\n");
        w.flush();
        return written;
    }

    private static boolean writePoint(Writer w, BasicLocation position) throws IOException {
        double lat = position.getLat();
        double lon = position.getLon();
        // required by the GPX schema: a point without them can't be written
        if (!(lat >= -90 && lat <= 90) || !(lon >= -180 && lon <= 180)) {
            return false;
        }
        w.write("      <trkpt lat=\"");
        w.write(decimal(lat));
        w.write("\" lon=\"");
        w.write(decimal(lon));
        w.write("\">");
        // in the order of the GPX 1.1 schema (wptType)
        Double altitude = position.getAltitude();
        if (altitude != null && Double.isFinite(altitude)) {
            w.write("<ele>");
            w.write(decimal(altitude));
            w.write("</ele>");
        }
        if (position.getTimestamp() > 0) {
            w.write("<time>");
            // e.g. 2026-11-22T09:30:00Z
            w.write(Instant.ofEpochSecond(position.getTimestamp()).toString());
            w.write("</time>");
        }
        Long satellites = position.getSatellites();
        if (satellites != null && satellites >= 0) {
            w.write("<sat>");
            w.write(Long.toString(satellites));
            w.write("</sat>");
        }
        w.write("</trkpt>\n");
        return true;
    }

    /** Plain decimal notation, never 1.0E-5, as xsd:decimal asks for. */
    static String decimal(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    /** Escapes text for an XML element or attribute, dropping characters XML 1.0 does not allow. */
    static String escape(@NonNull String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                case '\'': sb.append("&apos;"); break;
                default:
                    if (c >= 0x20 || c == '\t' || c == '\n' || c == '\r') {
                        if (c == 0xFFFE || c == 0xFFFF) {
                            break;
                        }
                        if (Character.isSurrogate(c)) {
                            // only a complete pair is a character
                            if (Character.isHighSurrogate(c) && i + 1 < text.length()
                                    && Character.isLowSurrogate(text.charAt(i + 1))) {
                                sb.append(c).append(text.charAt(i + 1));
                                i++;
                            }
                            break;
                        }
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    /**
     * A file name for the export of a log job, e.g. "Hike 2026-11-22.gpx": characters that file
     * systems or the document provider may refuse are replaced.
     */
    @NonNull
    public static String suggestedFileName(@Nullable String title, @NonNull LocalDate date) {
        StringBuilder sb = new StringBuilder();
        String source = title == null ? "" : title;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (Character.isWhitespace(c)) {
                sb.append(' ');
            } else if (c < 0x20 || c == 0x7F || "/\\:*?\"<>|".indexOf(c) >= 0) {
                sb.append('_');
            } else {
                sb.append(c);
            }
        }
        // no hidden file, no trailing dot or space (Windows drops them)
        String name = sb.toString().replaceAll(" +", " ").trim().replaceAll("^\\.+", "").replaceAll("[. ]+$", "");
        if (name.isEmpty()) {
            name = DEFAULT_FILE_NAME;
        }
        String suffix = " " + date + FILE_EXTENSION;
        int maxName = MAX_FILE_NAME_LENGTH - suffix.length();
        if (name.length() > maxName) {
            int end = maxName;
            // don't cut a surrogate pair in two
            if (Character.isHighSurrogate(name.charAt(end - 1))) {
                end--;
            }
            name = name.substring(0, end).trim();
        }
        return name + suffix;
    }
}
