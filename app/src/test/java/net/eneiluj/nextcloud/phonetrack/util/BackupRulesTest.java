package net.eneiluj.nextcloud.phonetrack.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * The backup rules must exclude the file CredentialStore writes, or passwords
 * end up in the cloud backup again.
 */
public class BackupRulesTest {

    private static final String CREDENTIALS_FILE = CredentialStore.FILE_NAME + ".xml";

    private static Element parse(String name) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new File("src/main/res/xml/" + name));
        return doc.getDocumentElement();
    }

    private static int credentialExcludes(Element parent) {
        int found = 0;
        NodeList excludes = parent.getElementsByTagName("exclude");
        for (int i = 0; i < excludes.getLength(); i++) {
            Element e = (Element) excludes.item(i);
            if ("sharedpref".equals(e.getAttribute("domain")) && CREDENTIALS_FILE.equals(e.getAttribute("path"))) {
                found++;
            }
        }
        return found;
    }

    @Test
    public void cloudBackupExcludesCredentials() throws Exception {
        Element rules = parse("data_extraction_rules.xml");
        Element cloud = (Element) rules.getElementsByTagName("cloud-backup").item(0);
        assertEquals(1, credentialExcludes(cloud));
    }

    @Test
    public void legacyBackupExcludesCredentials() throws Exception {
        assertEquals(1, credentialExcludes(parse("backup_rules.xml")));
    }

    @Test
    public void manifestUsesTheRules() throws Exception {
        Element app = (Element) DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new File("src/main/AndroidManifest.xml"))
                .getElementsByTagName("application").item(0);
        assertEquals("@xml/data_extraction_rules", app.getAttribute("android:dataExtractionRules"));
        assertEquals("@xml/backup_rules", app.getAttribute("android:fullBackupContent"));
    }
}
