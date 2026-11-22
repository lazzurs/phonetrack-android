package net.eneiluj.nextcloud.phonetrack.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.eneiluj.nextcloud.phonetrack.model.DBLogjobLocation;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

/** The GPX 1.1 file written for a log job export (issue #127). */
public class GpxWriterTest {

    private static final String GPX_NS = "http://www.topografix.com/GPX/1/1";

    private static DBLogjobLocation position(double lat, double lon, long time, Double altitude, Long satellites) {
        return new DBLogjobLocation(1, 1, lat, lon, time, 90.0, altitude, 3.5, 8.0, satellites, 75.0,
                "PhoneTrack", true, true);
    }

    private static String write(String title, List<DBLogjobLocation> positions, int expectedPoints) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertEquals(expectedPoints, GpxWriter.write(out, title, "PhoneTrack/1.0", positions));
        return out.toString(StandardCharsets.UTF_8);
    }

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static NodeList elements(Document doc, String name) {
        return doc.getElementsByTagNameNS(GPX_NS, name);
    }

    @Test
    public void writesAWellFormedGpx11Track() throws Exception {
        String xml = write("Hike", Arrays.asList(
                position(53.3498, -6.2603, 1763803800L, 12.5, 9L),
                position(53.35, -6.26, 1763803860L, 13.0, 10L)), 2);
        Document doc = parse(xml);

        Element root = doc.getDocumentElement();
        assertEquals("gpx", root.getLocalName());
        assertEquals(GPX_NS, root.getNamespaceURI());
        assertEquals("1.1", root.getAttribute("version"));
        assertEquals("PhoneTrack/1.0", root.getAttribute("creator"));
        assertEquals(1, elements(doc, "trk").getLength());
        assertEquals(1, elements(doc, "trkseg").getLength());
        assertEquals("Hike", ((Element) elements(doc, "trk").item(0)).getElementsByTagNameNS(GPX_NS, "name").item(0).getTextContent());

        NodeList points = elements(doc, "trkpt");
        assertEquals(2, points.getLength());
        Element first = (Element) points.item(0);
        assertEquals("53.3498", first.getAttribute("lat"));
        assertEquals("-6.2603", first.getAttribute("lon"));
        assertEquals("12.5", first.getElementsByTagNameNS(GPX_NS, "ele").item(0).getTextContent());
        // UTC with Z, from the position's own time
        assertEquals("2025-11-22T09:30:00Z", first.getElementsByTagNameNS(GPX_NS, "time").item(0).getTextContent());
        assertEquals("9", first.getElementsByTagNameNS(GPX_NS, "sat").item(0).getTextContent());
        Element second = (Element) points.item(1);
        assertEquals("53.35", second.getAttribute("lat"));
        assertEquals("-6.26", second.getAttribute("lon"));
        assertEquals("13", second.getElementsByTagNameNS(GPX_NS, "ele").item(0).getTextContent());
        assertEquals("2025-11-22T09:31:00Z", second.getElementsByTagNameNS(GPX_NS, "time").item(0).getTextContent());
    }

    @Test
    public void childrenFollowTheSchemaOrder() throws Exception {
        Document doc = parse(write("t", Collections.singletonList(position(1, 2, 1000, 3.0, 4L)), 1));
        NodeList children = elements(doc, "trkpt").item(0).getChildNodes();
        assertEquals(3, children.getLength());
        assertEquals("ele", children.item(0).getLocalName());
        assertEquals("time", children.item(1).getLocalName());
        assertEquals("sat", children.item(2).getLocalName());
    }

    @Test
    public void unknownFieldsAreLeftOutRatherThanWrittenAsZero() throws Exception {
        String xml = write("t", Collections.singletonList(position(48.85, 2.35, 0, null, null)), 1);
        Document doc = parse(xml);

        Element point = (Element) elements(doc, "trkpt").item(0);
        assertEquals(0, point.getChildNodes().getLength());
        assertFalse(xml, xml.contains("<ele>"));
        assertFalse(xml, xml.contains("<time>"));
        assertFalse(xml, xml.contains("<sat>"));
        // speed, bearing, accuracy and battery have no standard GPX 1.1 element
        assertFalse(xml, xml.contains("<extensions>"));
        assertFalse(xml, xml.contains("speed"));
    }

    @Test
    public void coordinatesAreNeverInScientificNotation() throws Exception {
        Document doc = parse(write("t", Collections.singletonList(position(0.00001, -0.0000005, 1, 0.0, 0L)), 1));
        Element point = (Element) elements(doc, "trkpt").item(0);
        assertEquals("0.00001", point.getAttribute("lat"));
        assertEquals("-0.0000005", point.getAttribute("lon"));
        // zero is a known value, unlike null
        assertEquals("0", point.getElementsByTagNameNS(GPX_NS, "ele").item(0).getTextContent());
        assertEquals("0", point.getElementsByTagNameNS(GPX_NS, "sat").item(0).getTextContent());
    }

    @Test
    public void positionsWithoutValidCoordinatesAreSkipped() throws Exception {
        Document doc = parse(write("t", Arrays.asList(
                position(Double.NaN, 1, 1, null, null),
                position(91, 1, 1, null, null),
                position(1, 181, 1, null, null),
                position(1, 1, 1, null, null)), 1));
        assertEquals(1, elements(doc, "trkpt").getLength());
    }

    @Test
    public void titleIsEscaped() throws Exception {
        String title = "Tom & Jerry's <\"walk\"> \u0001done 🚶";
        String xml = write(title, Collections.emptyList(), 0);
        Document doc = parse(xml);

        // the control character is not allowed in XML 1.0, the rest is kept
        assertEquals("Tom & Jerry's <\"walk\"> done 🚶",
                elements(doc, "name").item(0).getTextContent());
        assertTrue(xml, xml.contains("Tom &amp; Jerry&apos;s &lt;&quot;walk&quot;&gt;"));
    }

    @Test
    public void creatorIsEscaped() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GpxWriter.write(out, "t", "A \"B\" & <C>", Collections.emptyList());
        Document doc = parse(out.toString(StandardCharsets.UTF_8));
        assertEquals("A \"B\" & <C>", doc.getDocumentElement().getAttribute("creator"));
    }

    @Test
    public void emptyLogjobGivesAValidFileWithNoPoints() throws Exception {
        Document doc = parse(write("Nothing yet", Collections.emptyList(), 0));
        assertEquals("gpx", doc.getDocumentElement().getLocalName());
        assertEquals(1, elements(doc, "trkseg").getLength());
        assertEquals(0, elements(doc, "trkpt").getLength());
    }

    @Test
    public void suggestedFileNameHasTheTitleAndTheDate() {
        LocalDate date = LocalDate.of(2026, 11, 22);
        assertEquals("Hike 2026-11-22.gpx", GpxWriter.suggestedFileName("Hike", date));
        assertEquals("a_b_c_d_e_f_g_h_i_ 2026-11-22.gpx",
                GpxWriter.suggestedFileName("a/b\\c:d*e?f\"g<h>i|", date));
        assertEquals("my run 2026-11-22.gpx", GpxWriter.suggestedFileName("  my\trun  ", date));
        assertEquals("hidden 2026-11-22.gpx", GpxWriter.suggestedFileName("..hidden.", date));
        assertEquals("track 2026-11-22.gpx", GpxWriter.suggestedFileName("", date));
        assertEquals("track 2026-11-22.gpx", GpxWriter.suggestedFileName(null, date));
        assertEquals("track 2026-11-22.gpx", GpxWriter.suggestedFileName("...", date));

        StringBuilder longTitle = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            longTitle.append('x');
        }
        String name = GpxWriter.suggestedFileName(longTitle.toString(), date);
        assertTrue(name, name.length() <= 100);
        assertTrue(name, name.endsWith(" 2026-11-22.gpx"));
    }
}
