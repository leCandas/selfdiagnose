package com.philemonworks.selfdiagnose.report;

import com.philemonworks.selfdiagnose.*;
import com.philemonworks.selfdiagnose.check.CheckProperty;
import com.philemonworks.selfdiagnose.check.CheckResourceProperty;
import com.philemonworks.selfdiagnose.report.util.exception.ErrorMessageException;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.*;

/**
 * This class allows to report properties from a properties file (either from the class-path or from disk),
 * with the option to specify how keys and values are reported. <br/>
 * It is possible to provide an alternative name for a key,
 * as well as a specific format for the value belonging to a key. <br />
 * Another possibility is to filter (limit) which keys should be reported. <br/><br/>
 *
 * A real-world scenario for using this class is when you want to report Git information
 * created by the <a href="maven-git-commit-id-plugin">maven-git-commit-id-plugin</a>. <br/><br/>
 *
 * Example of how to configure this programmatically (showing how to make certain git info clickable):
 * <pre>
 * final String organizationName = "acme";
 * final String projectName = "coolproject";
 * final String tagUrl = String.format("<a href=\"https://bitbucket.org/%s/%s/commits/tag/{value}\">{value}</a>", organizationName, projectName);
 * final String commitUrl = String.format("<a href=\"https://bitbucket.org/%s/%s/commits/{value}\">{value}</a>", organizationName, projectName);
 * ReportPropertiesFile gitInfo = new ReportPropertiesFile();
 * gitInfo.setName("git.properties");
 * gitInfo.setKeysToReport(new HashSet<>(Arrays.asList("git.closest.tag.name", "git.commit.id", "git.branch")));
 * gitInfo.addFormatForKey("git.closest.tag.name", "tag", tagUrl);
 * gitInfo.addFormatForKey("git.commit.id", "commit", commitUrl);
 * gitInfo.addFormatForKey("git.branch", "branch", null);
 * </pre>
 */
public class ReportPropertiesFile extends CheckResourceProperty {

    private Set<String> keysToShow;
    private Map<String, KeyFormat> keyFormatters;

    public ReportPropertiesFile() {
        setSeverity(Severity.NONE);
    }

    public String getDescription() {
        return "Reports on the git.properties generated by maven-git-commit-id-plugin";
    }

    public void setUp(ExecutionContext ctx) throws DiagnoseException {
        if (name == null)
            DiagnoseUtil.verifyNonEmptyString(PARAMETER_URL, url, CheckProperty.class);
    }

    public void run(ExecutionContext ctx, DiagnosticTaskResult result) throws DiagnoseException {
        String message = "?";
        try {
            Properties properties = readProperties(this.getName());
            Set<String> keys = new TreeSet<String>();
            for (Object key : properties.keySet()) {
                keys.add((String) key);
            }
            if (keysToShow != null && !keysToShow.isEmpty()) {
                keys.retainAll(keysToShow);
            }
            message = createReportMessage(properties, keys);
        } catch (Exception e) {
            e.printStackTrace();
            result.setErrorMessage(e.getMessage());
        }
        result.setPassedMessage(message);
    }

    private String createReportMessage(Properties properties, Set<String> keys) {
        StringBuilder sb = new StringBuilder("");
        Iterator<String> keysIter = keys.iterator();
        while (keysIter.hasNext()) {
            final String key = keysIter.next();
            sb.append(
                    createKeyAndValueCombinationToReport(
                        createKeyNameToReport(key),
                        createValueToReport(key, properties)
                    )
            );
            if (keysIter.hasNext()) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    private String createKeyNameToReport(final String key) {
        if (keyFormatters != null && keyFormatters.containsKey(key)) {
            return keyFormatters.get(key).alternativeName;
        } else {
            return key;
        }
    }

    private String createValueToReport(final String key, Properties properties) {
        final String value = properties.getProperty(key);
        if (keyFormatters != null && keyFormatters.containsKey(key)) {
            KeyFormat keyFormat = keyFormatters.get(key);
            if (keyFormat.formatForValue != null) {
                return keyFormat.formatForValue.replaceAll("\\{value}", value);
            }
        }
        return value;
    }

    private String createKeyAndValueCombinationToReport(final String keyNameToReport, final String valueToReport) {
        return String.format("%s: %s", keyNameToReport, valueToReport);
    }

    private Properties readProperties(final String fileName) throws ErrorMessageException {
        try {
            InputStream is = this.getClass().getResourceAsStream(fileName);
            if (is == null) {
                is = this.getClass().getClassLoader().getResourceAsStream(fileName);
            }
            if (is == null) {
                File file = new File(fileName);
                if (file.exists()) {
                    is = new FileInputStream(file);
                }
            }
            if (is == null) {
                throw new ErrorMessageException(
                        String.format("Properties file '%s' not found (tried both class-path and disk)", fileName));
            }
            Properties properties = new Properties();
            properties.load(is);
            return properties;
        } catch (ErrorMessageException ex) {
            throw  ex;
        } catch(Exception ex) {
            ex.printStackTrace();
            throw new ErrorMessageException(
                    String.format("Error while reading properties file '%s': %s", fileName, ex.getMessage()));
        }
    }

    /**
     * Can be used to limit the keys that will be reported
     *
     * @param keysToShow
     */
    public void setKeysToReport(Set<String> keysToShow) {
        this.keysToShow = keysToShow;
    }

    /**
     * Can be used to specify how a key should be reported.
     * Provide an alternative name for the key, and/or a specific format for the value belonging to the key.
     *
     * @param key
     * @param alternativeNameForKey
     * @param formatForValue null allowed, all occurrences of '{value}' will be replaced with the actual value for the key
     */
    public void addFormatForKey(final String key, final String alternativeNameForKey, final String formatForValue) {
        if (keyFormatters == null) {
            keyFormatters = new HashMap<String, KeyFormat>();
        }
        keyFormatters.put(key, new KeyFormat(alternativeNameForKey, formatForValue));
    }

    private class KeyFormat {
        private String alternativeName;
        private String formatForValue;

        private KeyFormat(final String alternativeName, final String formatForValue) {
            this.alternativeName = alternativeName;
            this.formatForValue = formatForValue;
        }
    }
}