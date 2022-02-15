package com.xpandit.plugins.xrayjenkins.services.enviromentvariables;

import com.xpandit.plugins.xrayjenkins.model.HostingType;
import com.xpandit.plugins.xrayjenkins.services.enviromentvariables.util.XrayEnvironmentVariableSetterUtil;
import com.xpandit.xray.model.UploadResult;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.xpandit.plugins.xrayjenkins.services.enviromentvariables.util.XrayEnvironmentVariableSetterUtil.FALSE_STRING;
import static com.xpandit.plugins.xrayjenkins.services.enviromentvariables.util.XrayEnvironmentVariableSetterUtil.TRUE_STRING;
import static com.xpandit.plugins.xrayjenkins.services.enviromentvariables.util.XrayEnvironmentVariableSetterUtil.getImportedFeatureIssueKeys;
import static com.xpandit.plugins.xrayjenkins.services.enviromentvariables.util.XrayEnvironmentVariableSetterUtil.getModifiedTestExecutionsKeys;
import static com.xpandit.plugins.xrayjenkins.services.enviromentvariables.util.XrayEnvironmentVariableSetterUtil.getModifiedTestKeys;
import static com.xpandit.plugins.xrayjenkins.services.enviromentvariables.util.XrayEnvironmentVariableSetterUtil.getRawResponses;
import static com.xpandit.plugins.xrayjenkins.services.enviromentvariables.util.XrayEnvironmentVariableSetterUtil.isUploadSuccessful;

/**
 * This class will contain and set the new values for the Xray Jenkins environment variables.
 */
public class XrayEnvironmentVariableSetter {

    private enum XrayEnvironmentVariable {
        XRAY_IS_REQUEST_SUCCESSFUL, // "true", if the latest Xray request was successful. "false" otherwise.
        XRAY_ISSUES_MODIFIED, // All issues created or modified
        XRAY_RAW_RESPONSE, // The raw response String of the latest Xray request.
        XRAY_TEST_EXECS, // Test Execution Issues created/modified, separated by a semicolon.
        XRAY_TESTS  // Test Issues created/modified, separated by a semicolon.
    }

    private final Map<XrayEnvironmentVariable, String> newVariables;

    private XrayEnvironmentVariableSetter() {
        // We can't use Java native Synchronized structures since they are blocked by Jenkins in a Pipeline project
        // See: https://jenkins.io/blog/2018/01/13/jep-200/
        newVariables = Collections.synchronizedMap(new HashMap<XrayEnvironmentVariable, String>());

        for (XrayEnvironmentVariable variable : XrayEnvironmentVariable.values()) {
            newVariables.put(variable, StringUtils.EMPTY);
        }
    }

    /**
     * Parses all the Upload Results of a Cucumber Import (features) request.
     *
     * @param results the request results.
     * @param hostingType the hosting type of the Jira instance.
     * @param logger the logger that will be used to log some messages.
     * @return the XrayEnvironmentVariableSetter will all the relevant information.
     */
    public static XrayEnvironmentVariableSetter parseCucumberFeatureImportResponse(final Collection<UploadResult> results,
                                                                                   final HostingType hostingType,
                                                                                   final PrintStream logger) {
        final XrayEnvironmentVariableSetter variableSetter = new XrayEnvironmentVariableSetter();

        variableSetter.newVariables.put(XrayEnvironmentVariable.XRAY_RAW_RESPONSE, getRawResponses(results));
        variableSetter.newVariables.put(XrayEnvironmentVariable.XRAY_IS_REQUEST_SUCCESSFUL, isUploadSuccessful(results));
        variableSetter.newVariables.put(XrayEnvironmentVariable.XRAY_ISSUES_MODIFIED, getImportedFeatureIssueKeys(results, hostingType, logger));

        return variableSetter;
    }

    /**
     * Parses all the Upload Results of a Test result importation.
     *
     * @param results the request results.
     * @param hostingType the hosting type of the Jira instance.
     * @param logger the logger that will be used to log some messages.
     * @return the XrayEnvironmentVariableSetter will all the relevant information.
     */
    public static XrayEnvironmentVariableSetter parseResultImportResponse(final Collection<UploadResult> results,
                                                                          final HostingType hostingType,
                                                                          final PrintStream logger) {
        if (results == null) {
            return failed();
        }

        final XrayEnvironmentVariableSetter variableSetter = new XrayEnvironmentVariableSetter();

        variableSetter.newVariables.put(XrayEnvironmentVariable.XRAY_RAW_RESPONSE, getRawResponses(results));
        variableSetter.newVariables.put(XrayEnvironmentVariable.XRAY_IS_REQUEST_SUCCESSFUL, isUploadSuccessful(results));

        final String testExecKeys = getModifiedTestExecutionsKeys(results, hostingType, logger);
        final String testKeys = getModifiedTestKeys(results, hostingType, logger);
        variableSetter.newVariables.put(XrayEnvironmentVariable.XRAY_TEST_EXECS, testExecKeys);
        variableSetter.newVariables.put(XrayEnvironmentVariable.XRAY_TESTS, testKeys);
        variableSetter.newVariables.put(XrayEnvironmentVariable.XRAY_ISSUES_MODIFIED, getAllKeys(testExecKeys, testKeys));

        return variableSetter;
    }

    /**
     * Quick method that will return a Success XrayEnvironmentVariableSetter with no raw response.
     *
     * @return Success XrayEnvironmentVariableSetter with no raw response.
     */
    public static XrayEnvironmentVariableSetter success() {
        return success(null);
    }

    /**
     * Quick method that will return a Success XrayEnvironmentVariableSetter with a raw response.
     *
     * @return Success XrayEnvironmentVariableSetter with a raw response.
     */
    public static XrayEnvironmentVariableSetter success(@Nullable final String message) {
        final XrayEnvironmentVariableSetter variableSetter = new XrayEnvironmentVariableSetter();

        variableSetter.newVariables.put(XrayEnvironmentVariable.XRAY_IS_REQUEST_SUCCESSFUL, TRUE_STRING);
        if (message != null) {
            variableSetter.newVariables.put(XrayEnvironmentVariable.XRAY_RAW_RESPONSE, message);
        }

        return variableSetter;
    }

    /**
     * Quick method that will return a Failed XrayEnvironmentVariableSetter with no raw response.
     *
     * @return Failed XrayEnvironmentVariableSetter with no raw response.
     */
    public static XrayEnvironmentVariableSetter failed() {
        return failed(null);
    }

    /**
     * Quick method that will return a Failed XrayEnvironmentVariableSetter with a raw response.
     *
     * @return Failed XrayEnvironmentVariableSetter with a raw response.
     */
    public static XrayEnvironmentVariableSetter failed(@Nullable final String message) {
        final XrayEnvironmentVariableSetter variableSetter = new XrayEnvironmentVariableSetter();

        variableSetter.newVariables.put(XrayEnvironmentVariable.XRAY_IS_REQUEST_SUCCESSFUL, FALSE_STRING);
        if (message != null) {
            variableSetter.newVariables.put(XrayEnvironmentVariable.XRAY_RAW_RESPONSE, message);
        }

        return variableSetter;
    }

    public void setAction(Run<?,?> build, TaskListener taskListener) {
        setAction(build, taskListener.getLogger());
    }

    private void setAction(Run<?,?> build, @Nullable PrintStream logger) {
        if (build != null) {
            // Builds the same name, but with the name of each XrayEnvironmentVariable.
            // Key - variable name; Value - Variable value
            final Map<String, String> newVariablesByName = getVariableValuesByName(logger);

            // Adds action to Build
            final XrayEnvironmentInjectAction action = new XrayEnvironmentInjectAction(newVariablesByName);
            build.addOrReplaceAction(action);
        }
    }

    private Map<String, String> getVariableValuesByName(@Nullable PrintStream logger) {
        final Map<String, String> newVariablesByName = new HashMap<>();
        for (Map.Entry<XrayEnvironmentVariable, String> entry : newVariables.entrySet()) {
            final String variableName = entry.getKey().name();
            final String variableValue = entry.getValue();

            newVariablesByName.put(variableName, variableValue);

            if (logger != null) {
                logger.println(variableName + ": " + variableValue);
            }
        }

        return newVariablesByName;
    }

    private static String getAllKeys(String... allKeys) {
        final List<String> keyList = new ArrayList<>(allKeys.length);
        for (String keys : allKeys) {
            if (StringUtils.isNotBlank(keys)) {
                keyList.add(keys);
            }
        }

        return StringUtils.join(keyList, XrayEnvironmentVariableSetterUtil.SEPARATOR);
    }
}
