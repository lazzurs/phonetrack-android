package net.eneiluj.nextcloud.phonetrack;

import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Which strings the translations still lack (see TRANSLATING.md).
 *
 * Translations are contributed by pull request, so every language lags behind
 * values/strings.xml, and a pull request that adds an English string must not fail because
 * of that. This test therefore prints a report (in the test output and the test report)
 * instead of failing on missing translations. It fails only when
 * src/test/resources/translations-known-missing.txt names a string that is no longer
 * translatable in values/strings.xml, so that the list never sends a translator to a string
 * that is gone. Broken placeholders are caught by {@link ResourceFormatStringsTest}.
 */
public class TranslationCoverageTest {

    private static final File RES = new File("src/main/res");
    private static final File KNOWN_MISSING = new File("src/test/resources/translations-known-missing.txt");

    /** translatable default strings and plurals: name -> text (plurals: the "other" form) */
    private static Map<String, String> translatable;
    /** strings marked translatable="false": name -> text */
    private static Map<String, String> untranslatable;
    /** values-xx folder -> (name -> text) */
    private static Map<String, Map<String, String>> locales;
    private static Set<String> knownMissing;

    @BeforeClass
    public static void loadResources() throws Exception {
        translatable = new LinkedHashMap<>();
        untranslatable = new LinkedHashMap<>();
        Document defaults = parse(new File(RES, "values/strings.xml"));
        for (Element e : resources(defaults)) {
            String name = e.getAttribute("name");
            if ("false".equals(e.getAttribute("translatable"))) {
                untranslatable.put(name, text(e));
            } else {
                translatable.put(name, text(e));
            }
        }
        locales = new TreeMap<>();
        File[] dirs = RES.listFiles((dir, name) -> name.startsWith("values-"));
        for (File dir : dirs) {
            File strings = new File(dir, "strings.xml");
            if (strings.exists()) {
                Map<String, String> texts = new LinkedHashMap<>();
                for (Element e : resources(parse(strings))) {
                    texts.put(e.getAttribute("name"), text(e));
                }
                locales.put(dir.getName(), texts);
            }
        }
        assertTrue("no translations found under " + RES.getAbsolutePath(), locales.size() > 10);
        knownMissing = new LinkedHashSet<>();
        for (String line : Files.readAllLines(KNOWN_MISSING.toPath(), StandardCharsets.UTF_8)) {
            String key = line.trim();
            if (!key.isEmpty() && !key.startsWith("#")) {
                knownMissing.add(key);
            }
        }
    }

    @Test
    public void knownMissingListOnlyNamesTranslatableStrings() {
        List<String> stale = new ArrayList<>();
        for (String key : knownMissing) {
            if (!translatable.containsKey(key)) {
                stale.add(key);
            }
        }
        assertTrue(KNOWN_MISSING + " names strings that values/strings.xml no longer has as translatable"
                + " (removed, renamed or marked translatable=\"false\"): remove these lines: " + stale, stale.isEmpty());
    }

    /** Never fails: prints what each language lacks, for maintainers and translators. */
    @Test
    public void reportMissingTranslations() {
        StringBuilder report = new StringBuilder();
        report.append("Translation coverage: ").append(translatable.size())
                .append(" translatable strings and plurals in values/strings.xml\n");
        report.append(String.format("%-16s %8s %16s%n", "folder", "missing", "same as English"));
        Set<String> missingEverywhere = new LinkedHashSet<>(translatable.keySet());
        for (Map.Entry<String, Map<String, String>> locale : locales.entrySet()) {
            // British and US English fall back to values/, which is English too
            if (locale.getKey().startsWith("values-en")) {
                continue;
            }
            Map<String, String> texts = locale.getValue();
            int missing = 0;
            int sameAsEnglish = 0;
            for (Map.Entry<String, String> e : translatable.entrySet()) {
                String text = texts.get(e.getKey());
                if (text == null) {
                    missing++;
                } else {
                    missingEverywhere.remove(e.getKey());
                    // a hint, not an error: the old Crowdin export copied untranslated English in,
                    // but some words ("GPS", "SMS") are the same in many languages
                    if (text.equals(e.getValue())) {
                        sameAsEnglish++;
                    }
                }
            }
            report.append(String.format("%-16s %8d %16d%n", locale.getKey(), missing, sameAsEnglish));
        }

        List<String> notListed = new ArrayList<>();
        for (String key : missingEverywhere) {
            if (!knownMissing.contains(key)) {
                notListed.add(key);
            }
        }
        List<String> nowTranslated = new ArrayList<>();
        for (String key : knownMissing) {
            if (translatable.containsKey(key) && !missingEverywhere.contains(key)) {
                nowTranslated.add(key);
            }
        }
        report.append(missingEverywhere.size()).append(" strings are not translated in any language.\n");
        appendList(report, "Not translated in any language and not yet in " + KNOWN_MISSING.getName()
                + " (add them to it):", notListed);
        appendList(report, "In " + KNOWN_MISSING.getName()
                + " but now translated in at least one language (they can be removed from it):", nowTranslated);

        // the locale's text wins at runtime, whatever translatable="false" says
        List<String> overridden = new ArrayList<>();
        for (Map.Entry<String, String> e : untranslatable.entrySet()) {
            List<String> where = new ArrayList<>();
            for (Map.Entry<String, Map<String, String>> locale : locales.entrySet()) {
                String text = locale.getValue().get(e.getKey());
                if (text != null && !text.equals(e.getValue())) {
                    where.add(locale.getKey());
                }
            }
            if (!where.isEmpty()) {
                overridden.add(e.getKey() + " (" + where.size() + " folders, e.g. " + where.get(0) + ")");
            }
        }
        appendList(report, "Strings marked translatable=\"false\" that language folders replace with another text"
                + " (Android shows the folder's text):", overridden);
        System.out.print(report);
    }

    private static void appendList(StringBuilder report, String title, List<String> items) {
        if (items.isEmpty()) {
            return;
        }
        report.append(title).append('\n');
        for (String item : items) {
            report.append("  ").append(item).append('\n');
        }
    }

    private static Document parse(File file) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file);
    }

    /** the top-level string and plurals elements */
    private static List<Element> resources(Document doc) {
        List<Element> list = new ArrayList<>();
        NodeList children = doc.getDocumentElement().getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element
                    && ("string".equals(node.getNodeName()) || "plurals".equals(node.getNodeName()))) {
                list.add((Element) node);
            }
        }
        return list;
    }

    /** a string's text, or a plural's "other" form; escaped quotes compared as plain ones */
    private static String text(Element e) {
        String text = e.getTextContent();
        if ("plurals".equals(e.getNodeName())) {
            NodeList items = e.getElementsByTagName("item");
            for (int i = 0; i < items.getLength(); i++) {
                Element item = (Element) items.item(i);
                if ("other".equals(item.getAttribute("quantity"))) {
                    text = item.getTextContent();
                }
            }
        }
        return text.trim().replace("\\\"", "\"").replace("\\'", "'");
    }
}
