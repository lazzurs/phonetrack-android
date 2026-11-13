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

    // Constants.SSO_SHARED_PREFERENCE of Android-SingleSignOn 1.3.4; it holds the account token.
    private static final String SSO_FILE = "single-sign-on.xml";

    private static int excludes(Element parent, String domain, String path) {
        int found = 0;
        NodeList excludes = parent.getElementsByTagName("exclude");
        for (int i = 0; i < excludes.getLength(); i++) {
            Element e = (Element) excludes.item(i);
            if (domain.equals(e.getAttribute("domain")) && path.equals(e.getAttribute("path"))) {
                found++;
            }
        }
        return found;
    }

    private static int credentialExcludes(Element parent) {
        return excludes(parent, "sharedpref", CREDENTIALS_FILE);
    }

    private static Element section(String name) throws Exception {
        return (Element) parse("data_extraction_rules.xml").getElementsByTagName(name).item(0);
    }

    @Test
    public void cloudBackupExcludesDatabaseAndSsoToken() throws Exception {
        Element cloud = section("cloud-backup");
        assertEquals(1, excludes(cloud, "database", "."));
        assertEquals(1, excludes(cloud, "sharedpref", SSO_FILE));
    }

    @Test
    public void cloudBackupRequiresEncryption() throws Exception {
        assertEquals("true", section("cloud-backup").getAttribute("disableIfNoEncryptionCapabilities"));
    }

    @Test
    public void deviceTransferKeepsDatabaseButNotSecrets() throws Exception {
        Element transfer = section("device-transfer");
        assertEquals(0, excludes(transfer, "database", "."));
        assertEquals(1, credentialExcludes(transfer));
        assertEquals(1, excludes(transfer, "sharedpref", SSO_FILE));
    }

    @Test
    public void legacyBackupExcludesDatabaseAndSsoToken() throws Exception {
        Element legacy = parse("backup_rules.xml");
        assertEquals(1, excludes(legacy, "database", "."));
        assertEquals(1, excludes(legacy, "sharedpref", SSO_FILE));
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
