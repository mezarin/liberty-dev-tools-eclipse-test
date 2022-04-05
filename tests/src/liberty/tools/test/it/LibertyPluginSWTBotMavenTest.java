package liberty.tools.test.it;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.embedder.MavenModelManager;
import org.eclipse.m2e.core.project.IProjectConfigurationManager;
import org.eclipse.m2e.core.project.LocalProjectScanner;
import org.eclipse.m2e.core.project.MavenProjectInfo;
import org.eclipse.m2e.core.project.ProjectImportConfiguration;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotView;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.browser.IWorkbenchBrowserSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import liberty.tools.DevModeOperations;
import liberty.tools.test.it.utils.SWTPluginOperations;
import liberty.tools.ui.DashboardView;

/**
 * Tests Open Liberty Eclipse plugin functions.
 */
public class LibertyPluginSWTBotMavenTest {

    /**
     * Wokbench bot instance.
     */
    static SWTWorkbenchBot bot;

    /**
     * Dashboard instance.
     */
    static SWTBotView dashboard;

    /**
     * Application name.
     */
    static final String MVN_APP_NAME = "liberty.maven.test.app";

    /**
     * Expected menu items.
     */
    static String[] mvnMenuItems = new String[] { DashboardView.APP_MENU_ACTION_START, DashboardView.APP_MENU_ACTION_START_PARMS,
            DashboardView.APP_MENU_ACTION_START_IN_CONTAINER, DashboardView.APP_MENU_ACTION_STOP, DashboardView.APP_MENU_ACTION_RUN_TESTS,
            DashboardView.APP_MENU_ACTION_VIEW_MVN_IT_REPORT, DashboardView.APP_MENU_ACTION_VIEW_MVN_UT_REPORT };

    /**
     * Setup.
     */
    @BeforeAll
    public static void setup() {
        bot = new SWTWorkbenchBot();
        SWTPluginOperations.closeWelcomePage(bot);
        importMavenApplications();
        initialize();
    }

    /**
     * Cleanup.
     */
    @AfterAll
    public static void cleanup() {
        bot.closeAllEditors();
        bot.closeAllShells();
        bot.resetWorkbench();
    }

    /**
     * Makes sure that some basics elements can be accessed or are present before running the tests. The following are
     * checked:
     * 1. The dashboard can be opened and its content retrieved.
     * 2. The dashboard contains the expected applications.
     * 3. The menu for the expected application contains the required actions.
     */
    public static final void initialize() {

        dashboard = SWTPluginOperations.openDashboardUsingMenu(bot);

        // Check that the dashboard can be opened and its content retrieved.
        String[] dashboardContent = SWTPluginOperations.getDashboardContent(bot, dashboard);

        // Check that dashboard contains the expected applications.
        boolean foundApp = false;
        for (int i = 0; i < dashboardContent.length; i++) {
            if (dashboardContent[i].equals(MVN_APP_NAME)) {
                foundApp = true;
                break;
            }
        }
        Assertions.assertTrue(foundApp, () -> "The dashboard does not contain expected application: " + MVN_APP_NAME);

        // Check that the menu for the expected application contains the required actions.
        List<String> menuItems = SWTPluginOperations.getDashboardItemMenuActions(bot, dashboard, MVN_APP_NAME);
        Assertions.assertTrue(menuItems.size() == mvnMenuItems.length,
                () -> "Maven application " + MVN_APP_NAME + " does not contain the expected number of menu items: " + mvnMenuItems.length);
        Assertions.assertTrue(menuItems.containsAll(Arrays.asList(mvnMenuItems)),
                () -> "Maven application " + MVN_APP_NAME + " does not contain the expected menu items: " + mvnMenuItems);
    }

    /**
     * Tests opening the dashboard using the main toolbar icon.
     */
    // @Test
    public void testOpenDashboardWithToolbarIcon() {
        SWTPluginOperations.openDashboardUsingToolbar(bot);
    }

    /**
     * Tests opening the dashboard using the Liberty menu.
     */
    // @Test
    public void testOpenDashboardUsingMenu() {
        SWTPluginOperations.openDashboardUsingMenu(bot);
    }

    /**
     * Tests the start menu action on a dashboard listed application.
     */
    // @Test
    public void testStart() {
        // Start dev mode.
        SWTPluginOperations.launchAppMenuStartAction(bot, dashboard, MVN_APP_NAME);
        SWTBotView terminal = bot.viewByTitle("Terminal");
        terminal.show();

        try {
            Thread.sleep(45000);
        } catch (Exception e) {
            System.out.println("@ed ERROR while sleeping?: " + e.getMessage());
        }

        // Validate application is up and running.
        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
            System.out.println("@ed: ENV JAVA: key: " + entry.getKey() + ". Value: " + entry.getValue());
        }

        String appUrl = "";
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            appUrl = "http://" + hostname + ":9080/liberty.maven.test.app/servlet";
            URL url = new URL(appUrl);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");

            // Possible error: java.net.ConnectException: Connection refused
            con.connect();
            int status = con.getResponseCode();

            System.out.println("@ed ok we tried making a connection ... here is the status: " + status);

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("@ed Somethig went wrong using this URL: " + appUrl + ". Error: " + e.getMessage());
        }

        validateApplicationOutcome(true);

        // Stop dev mode.
        SWTPluginOperations.launchAppMenuStopAction(bot, dashboard, MVN_APP_NAME);
        terminal.show();

        // Validate application stopped.
        validateApplicationOutcome(false);
    }

    /**
     * Tests the start with parameters menu action on a dashboard listed application.
     */
    // @Test
    public void testStartWithParms() {
        Path projectPath = Paths.get("applications", "maven", "liberty-maven-test-app");
        Path pathToITReport = DevModeOperations.getMavenIntegrationTestReportPath(projectPath.toString());
        boolean testReportDeleted = deleteFile(pathToITReport);
        Assertions.assertTrue(testReportDeleted, () -> "File: " + pathToITReport + " was not be deleted.");

        // Start dev mode with parms.
        SWTPluginOperations.launchAppMenuStartWithParmsAction(bot, dashboard, MVN_APP_NAME, "-DhotTests=true");
        SWTBotView terminal = bot.viewByTitle("Terminal");
        terminal.show();

        // Validate application is up and running.
        validateApplicationOutcome(true);

        // Validate that the test reports were generated.
        validateTestReportExists(pathToITReport);

        // Stop dev mode.
        SWTPluginOperations.launchAppMenuStopAction(bot, dashboard, MVN_APP_NAME);
        terminal.show();

        // Validate application stopped.
        validateApplicationOutcome(false);
    }

    /**
     * Tests the "Run Tests" menu action and test report view actions if internal browser support is available.
     */
    @Test
    public void testRunTests() {
        // Delete the test report files before we start this test.
        Path projectPath = Paths.get("applications", "maven", "liberty-maven-test-app");
        Path pathToITReport = DevModeOperations.getMavenIntegrationTestReportPath(projectPath.toString());
        boolean itReportDeleted = deleteFile(pathToITReport);
        Assertions.assertTrue(itReportDeleted, () -> "Test report file: " + pathToITReport + " was not be deleted.");

        Path pathToUTReport = DevModeOperations.getMavenUnitTestReportPath(projectPath.toString());
        boolean utReportDeleted = deleteFile(pathToITReport);
        Assertions.assertTrue(utReportDeleted, () -> "Test report file: " + pathToITReport + " was not be deleted.");

        // Start dev mode.
        SWTPluginOperations.launchAppMenuStartAction(bot, dashboard, MVN_APP_NAME);
        SWTBotView terminal = bot.viewByTitle("Terminal");
        terminal.show();

        // Validate application is up and running.
        validateApplicationOutcome(true);

        // Run Tests.
        SWTPluginOperations.launchAppMenuRunTestsAction(bot, dashboard, MVN_APP_NAME);

        // Validate that the reports were generated and the the browser editor was launched.
        validateTestReportExists(pathToITReport);
        if (isInternalBrowserSupportAvailable()) {
            System.out.println("@ed: Internal browser available! really?.");
            SWTPluginOperations.launchAppMenuViewMavenITReportAction(bot, dashboard, MVN_APP_NAME);
        } else {
            System.out.println("@ed: Internal browser NOT !!!! available");
        }

        validateTestReportExists(pathToUTReport);
        if (isInternalBrowserSupportAvailable()) {
            SWTPluginOperations.launchAppMenuViewMavenUTReportAction(bot, dashboard, MVN_APP_NAME);
        }

        // Stop dev mode.
        SWTPluginOperations.launchAppMenuStopAction(bot, dashboard, MVN_APP_NAME);
        terminal.show();

        // Validate application stopped.
        validateApplicationOutcome(false);
    }

    /**
     * Tests the refresh action on the dashboard's toolbar.
     */
    // @Test
    public void testRefresh() {
        final String fileName = "pom.xml";
        // Get the list of entries on the dashboard and verify the expected number found.
        String[] dashboardContent = SWTPluginOperations.getDashboardContent(bot, dashboard);
        Assertions.assertTrue(dashboardContent.length == 1, () -> "The dashboard did not display the expected number of applications: 1");

        String originalContent = SWTPluginOperations.getAppFileContent(bot, "Project Explorer", MVN_APP_NAME, fileName);
        Path noOLPluginPom = Paths.get("resources", "maven", "liberty-maven-test-app", "pom.xml");

        try {
            // Modify the application metadata to make it not capable of using Liberty's dev mode.
            StringBuilder contentBuilder = new StringBuilder();
            try {

                BufferedReader br = new BufferedReader(new FileReader(noOLPluginPom.toString()));
                String sCurrentLine;
                while ((sCurrentLine = br.readLine()) != null) {
                    contentBuilder.append(sCurrentLine).append(System.lineSeparator());
                }
            } catch (IOException e) {
                Assertions.fail("Error while reading file: " + noOLPluginPom.toString() + "Error: " + e.getMessage());
            }

            String pomEditorTitle = Paths.get(MVN_APP_NAME, fileName).toString();
            SWTPluginOperations.setEditorText(bot, pomEditorTitle, contentBuilder.toString());

            // Refresh
            SWTPluginOperations.refreshDashboard(bot);

            // Get the list of entries on the dashboard and verify the expected number is found.
            dashboardContent = SWTPluginOperations.getDashboardContent(bot, dashboard);
            Assertions.assertTrue(dashboardContent.length == 0,
                    () -> "The dashboard did not display the expected number of applications: 0");

        } finally {
            // Update the application metadata to make it capable of using Liberty's dev mode.
            SWTPluginOperations.setEditorText(bot, fileName, originalContent);

            // Refresh
            SWTPluginOperations.refreshDashboard(bot);

            // Get the list of entries on the dashboard and verify the expected number is found.
            dashboardContent = SWTPluginOperations.getDashboardContent(bot, dashboard);
            Assertions.assertTrue(dashboardContent.length == 1,
                    () -> "The dashboard did not display the expected number of applications: 1");
        }
    }

    /**
     * Imports existing Maven application projects into the workspace.
     */
    public static void importMavenApplications() {
        Display.getDefault().syncExec(new Runnable() {

            @Override
            public void run() {
                File workspaceRoot = ResourcesPlugin.getWorkspace().getRoot().getLocation().toFile();
                ArrayList<String> projectPaths = new ArrayList<String>();
                Path projPath = Paths.get("applications", "maven", "liberty-maven-test-app");
                projectPaths.add(projPath.toString());

                try {
                    importProjects(workspaceRoot, projectPaths);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * Imports the specified list of projects.
     *
     * @param workspaceRoot The workspace root location.
     * @param folders The list of folders containing the projects to install.
     *
     * @throws InterruptedException
     * @throws CoreException
     */
    public static void importProjects(File workspaceRoot, List<String> folders) throws InterruptedException, CoreException {
        // Get the list of projects to install.
        MavenModelManager modelManager = MavenPlugin.getMavenModelManager();
        LocalProjectScanner lps = new LocalProjectScanner(workspaceRoot, folders, false, modelManager);
        lps.run(new NullProgressMonitor());
        List<MavenProjectInfo> projects = lps.getProjects();

        // Import the projects.
        ProjectImportConfiguration projectImportConfig = new ProjectImportConfiguration();
        IProjectConfigurationManager projectConfigurationManager = MavenPlugin.getProjectConfigurationManager();
        projectConfigurationManager.importProjects(projects, projectImportConfig, new NullProgressMonitor());

    }

    /**
     * Validates that the deployed application is active.
     *
     * @param expectSuccess True if the validation is expected to be successful. False, otherwise.
     */
    private void validateApplicationOutcome(boolean expectSuccess) {
        String expectedMvnAppResp = "Hello! How are you today?";
        String appUrl = "http://localhost:9080/liberty.maven.test.app/servlet";
        int retryCountLimit = 20;
        int reryIntervalSecs = 3;
        int retryCount = 0;

        while (retryCount < retryCountLimit) {
            retryCount++;
            int status = 0;
            try {
                URL url = new URL(appUrl);
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("GET");

                // Possible error: java.net.ConnectException: Connection refused
                con.connect();
                status = con.getResponseCode();

                if (expectSuccess) {
                    if (status != HttpURLConnection.HTTP_OK) {
                        Thread.sleep(reryIntervalSecs * 1000);
                        con.disconnect();
                        continue;
                    }

                    BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()));
                    String responseLine = "";
                    StringBuffer content = new StringBuffer();
                    while ((responseLine = br.readLine()) != null) {
                        content.append(responseLine).append(System.lineSeparator());
                    }

                    if (!(content.toString().contains(expectedMvnAppResp))) {
                        Thread.sleep(reryIntervalSecs * 1000);
                        con.disconnect();
                        continue;
                    }

                    return;
                } else {
                    if (status == HttpURLConnection.HTTP_OK) {
                        Thread.sleep(reryIntervalSecs * 1000);
                        con.disconnect();
                        continue;
                    }

                    return;
                }
            } catch (Exception e) {
                if (expectSuccess) {
                    System.out.println(
                            "INFO: Retrying application connection: Responce code: " + status + ". Error message: " + e.getMessage());
                    e.printStackTrace();
                    try {
                        Thread.sleep(reryIntervalSecs * 1000);
                    } catch (Exception ee) {
                        ee.printStackTrace(System.out);
                    }
                    continue;
                }

                return;
            }
        }

        // If we are here, the expected outcome was not found.
        Assertions.fail("Timed out while waiting for application under URL: " + appUrl + " to become available.");
    }

    /**
     * Validates that the test report represented by the input path exists.
     *
     * @param pathToTestReport The path to the report.
     */
    public void validateTestReportExists(Path pathToTestReport) {
        int retryCountLimit = 10;
        int reryIntervalSecs = 1;
        int retryCount = 0;

        while (retryCount < retryCountLimit) {
            retryCount++;

            boolean fileExists = fileExists(pathToTestReport.toAbsolutePath());
            if (!fileExists) {
                try {
                    Thread.sleep(reryIntervalSecs * 1000);
                } catch (Exception e) {
                    e.printStackTrace(System.out);
                    continue;
                }
                continue;
            }

            return;
        }

        // If we are here, the expected outcome was not found.
        Assertions.fail("Timed out while waiting for test report: " + pathToTestReport + " to become available.");
    }

    /**
     * Returns true if the Eclipse instance supports internal browsers. False, otherwise.
     *
     * @return True if the Eclipse instance supports internal browsers. False, otherwise.
     */
    public boolean isInternalBrowserSupportAvailable() {
        final String availableKey = "available";
        final Map<String, Boolean> results = new HashMap<String, Boolean>();

        Display.getDefault().syncExec(new Runnable() {
            @Override
            public void run() {
                IWorkbenchBrowserSupport bSupport = PlatformUI.getWorkbench().getBrowserSupport();
                if (bSupport.isInternalWebBrowserAvailable()) {
                    results.put(availableKey, Boolean.TRUE);
                } else {
                    results.put(availableKey, Boolean.FALSE);
                }
            }
        });

        return results.get(availableKey);
    }

    /**
     * Returns true if the file identified by the input path exists. False, otherwise.
     *
     * @param path The file's path.
     *
     * @return True if the file identified by the input path exists. False, otherwise.
     */
    public boolean fileExists(Path filePath) {
        File f = new File(filePath.toString());
        boolean exists = f.exists();

        return exists;
    }

    /**
     * Deletes file identified by the input path.
     *
     * @param path The file's path.
     *
     * @return Returns true if the file identified by the input path was deleted. False, otherwise.
     */
    public boolean deleteFile(Path filePath) {
        boolean deleted = true;
        File f = new File(filePath.toString());

        if (f.exists()) {
            deleted = f.delete();
        }

        return deleted;
    }
}
