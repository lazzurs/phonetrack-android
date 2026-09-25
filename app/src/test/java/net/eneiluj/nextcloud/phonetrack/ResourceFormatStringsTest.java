package net.eneiluj.nextcloud.phonetrack;

import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.IllegalFormatException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Checks the format arguments of every translated string against the default
 * (values/strings.xml) one. getString(id, args...) throws
 * IllegalFormatConversionException at runtime when a translation reorders
 * non-positional arguments of different types, so this is a crash, not a cosmetic issue.
 */
public class ResourceFormatStringsTest {

    // same syntax as java.util.Formatter; group 1 = explicit index, group 2 = conversion
    private static final Pattern SPEC = Pattern.compile(
            "%(?:(\\d+)\\$)?[-#+ 0,(<]*\\d*(?:\\.\\d+)?([a-zA-Z%])");

    private static final File RES = new File("src/main/res");

    private static Map<String, String> defaults;
    private static Map<String, Map<String, String>> translations;

    @BeforeClass
    public static void loadResources() throws Exception {
        defaults = load(new File(RES, "values/strings.xml"));
        translations = new TreeMap<>();
        File[] dirs = RES.listFiles((dir, name) -> name.startsWith("values-"));
        for (File dir : dirs) {
            File strings = new File(dir, "strings.xml");
            if (strings.exists()) {
                translations.put(dir.getName(), load(strings));
            }
        }
        assertTrue("no translations found under " + RES.getAbsolutePath(), translations.size() > 10);
    }

    @Test
    public void stringsWithSeveralArgumentsUsePositionalArguments() {
        List<String> problems = new ArrayList<>();
        check(defaults, "values", problems);
        for (Map.Entry<String, Map<String, String>> locale : translations.entrySet()) {
            check(locale.getValue(), locale.getKey(), problems);
        }
        assertTrue(String.join("\n", problems), problems.isEmpty());
    }

    private static void check(Map<String, String> strings, String where, List<String> problems) {
        for (Map.Entry<String, String> e : strings.entrySet()) {
            List<MatchResult> specs = specs(e.getValue());
            boolean anyImplicit = specs.stream().anyMatch(m -> m.group(1) == null);
            if (specs.size() > 1 && anyImplicit) {
                problems.add(where + "/" + e.getKey() + ": several arguments must be positional (%1$s, %2$d): " + e.getValue());
            }
        }
    }

    @Test
    public void translationsUseTheSameArgumentsAsTheDefault() {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> locale : translations.entrySet()) {
            for (Map.Entry<String, String> e : locale.getValue().entrySet()) {
                String base = defaultFor(e.getKey());
                if (base == null) {
                    continue;
                }
                Map<Integer, Character> expected = arguments(base);
                Map<Integer, Character> actual = arguments(e.getValue());
                boolean plural = e.getKey().contains("#");
                // a plural form may leave the count out ("one day"), a plain string may not
                boolean ok = plural ? expected.entrySet().containsAll(actual.entrySet()) : expected.equals(actual);
                if (!ok) {
                    problems.add(locale.getKey() + "/" + e.getKey() + ": arguments " + actual
                            + " but default has " + expected + ": " + e.getValue());
                }
            }
        }
        assertTrue(String.join("\n", problems), problems.isEmpty());
    }

    @Test
    public void everyTranslationFormatsWithTheDefaultArgumentTypes() {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> locale : translations.entrySet()) {
            for (Map.Entry<String, String> e : locale.getValue().entrySet()) {
                String base = defaultFor(e.getKey());
                if (base == null || specs(base).isEmpty()) {
                    continue;
                }
                try {
                    String.format(Locale.ROOT, e.getValue(), sampleArguments(arguments(base)));
                } catch (IllegalFormatException ex) {
                    problems.add(locale.getKey() + "/" + e.getKey() + ": " + ex + ": " + e.getValue());
                }
            }
        }
        assertTrue(String.join("\n", problems), problems.isEmpty());
    }

    /** Plural quantities differ between languages: compare any form with the default "other". */
    private static String defaultFor(String key) {
        String base = defaults.get(key);
        if (base == null && key.contains("#")) {
            base = defaults.get(key.substring(0, key.indexOf('#')) + "#other");
        }
        return base;
    }

    /** argument index (1-based) -> conversion character */
    private static Map<Integer, Character> arguments(String text) {
        Map<Integer, Character> args = new TreeMap<>();
        int next = 1;
        for (MatchResult m : specs(text)) {
            int index = m.group(1) != null ? Integer.parseInt(m.group(1)) : next++;
            args.put(index, Character.toLowerCase(m.group(2).charAt(0)));
        }
        return args;
    }

    private static Object[] sampleArguments(Map<Integer, Character> args) {
        int count = args.isEmpty() ? 0 : ((TreeMap<Integer, Character>) args).lastKey();
        Object[] values = new Object[count];
        for (Map.Entry<Integer, Character> a : args.entrySet()) {
            switch (a.getValue()) {
                case 'd': case 'x': case 'o':
                    values[a.getKey() - 1] = 42;
                    break;
                case 'f': case 'e': case 'g':
                    values[a.getKey() - 1] = 4.2;
                    break;
                default:
                    values[a.getKey() - 1] = "text";
            }
        }
        return values;
    }

    private static List<MatchResult> specs(String text) {
        List<MatchResult> specs = new ArrayList<>();
        Matcher m = SPEC.matcher(text);
        while (m.find()) {
            if (!"%".equals(m.group(2))) {
                specs.add(m.toMatchResult());
            }
        }
        return specs;
    }

    /** name -> text for formatted strings; plural items are keyed "name#quantity" */
    private static Map<String, String> load(File file) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file);
        Map<String, String> strings = new LinkedHashMap<>();
        NodeList list = doc.getElementsByTagName("string");
        for (int i = 0; i < list.getLength(); i++) {
            Element s = (Element) list.item(i);
            if (!"false".equals(s.getAttribute("formatted"))) {
                strings.put(s.getAttribute("name"), s.getTextContent());
            }
        }
        NodeList plurals = doc.getElementsByTagName("plurals");
        for (int i = 0; i < plurals.getLength(); i++) {
            Element p = (Element) plurals.item(i);
            NodeList items = p.getElementsByTagName("item");
            for (int j = 0; j < items.getLength(); j++) {
                Element item = (Element) items.item(j);
                strings.put(p.getAttribute("name") + "#" + item.getAttribute("quantity"), item.getTextContent());
            }
        }
        return strings;
    }
}
