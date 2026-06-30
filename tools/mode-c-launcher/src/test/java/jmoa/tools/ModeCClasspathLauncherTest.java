package jmoa.tools;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModeCClasspathLauncherTest {

    private static final String FIXTURE_PROPERTY = "jmoa.modec.fixture.arg";

    @AfterEach
    void resetFixture() {
        System.clearProperty(FIXTURE_PROPERTY);
    }

    @Test
    void launchesConfiguredMainClassFromClasspathFile() throws Exception {
        Path classpathFile = Files.createTempFile("jmoa-modec", ".txt");
        Path location = Path.of(FixtureMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Files.writeString(classpathFile, location.toString() + System.lineSeparator());

        ModeCClasspathLauncher.LaunchOutcome outcome = ModeCClasspathLauncher.launch(
            ModeCClasspathLauncher.LaunchRequest.parse(new String[]{
                "--classpath-file", classpathFile.toString(),
                "--main-class", FixtureMain.class.getName(),
                "--",
                "patient-smoke"
            })
        );

        assertEquals(ModeCClasspathLauncher.FailureClassification.NONE, outcome.classification());
        assertEquals("patient-smoke", System.getProperty(FIXTURE_PROPERTY));
    }

    @Test
    void classifiesEnvironmentWallFailuresSeparately() {
        ModeCClasspathLauncher.FailureClassification classification =
            ModeCClasspathLauncher.classify(new RuntimeException(new ConnectException("Connection refused")));

        assertEquals(ModeCClasspathLauncher.FailureClassification.ENVIRONMENT_WALL, classification);
    }

    @Test
    void classifiesLinkageFailuresAsJmoaFailures() {
        ModeCClasspathLauncher.FailureClassification classification =
            ModeCClasspathLauncher.classify(new BootstrapMethodError("lambda linkage"));

        assertEquals(ModeCClasspathLauncher.FailureClassification.JMOA_LINKAGE_FAILURE, classification);
    }

    @Test
    void preservesApplicationFailureWhenNoKnownClassificationMatches() {
        ModeCClasspathLauncher.LaunchOutcome outcome = ModeCClasspathLauncher.LaunchOutcome.failure(
            ModeCClasspathLauncher.classify(new IllegalStateException("boom")),
            "app failed",
            new IllegalStateException("boom")
        );

        assertEquals(ModeCClasspathLauncher.FailureClassification.APPLICATION_FAILURE, outcome.classification());
        assertTrue(outcome.message().contains("app failed"));
    }

    @Test
    void parsesForceGcArguments() {
        ModeCClasspathLauncher.LaunchRequest request = ModeCClasspathLauncher.LaunchRequest.parse(new String[]{
            "--classpath-file", "cp.txt",
            "--main-class", FixtureMain.class.getName(),
            "--force-gc-before-exit",
            "--sleep-before-exit-ms=2000",
            "--resource-log-file=resources.json"
        });

        assertTrue(request.forceGcBeforeExit());
        assertEquals(2000L, request.sleepBeforeExitMs());
        assertEquals(Path.of("resources.json"), request.resourceLogFile());
    }

    @Test
    void preservesEnvironmentWallClassificationWhenForceGcIsEnabled() throws Exception {
        Path classpathFile = Files.createTempFile("jmoa-modec", ".txt");
        Path location = Path.of(EnvironmentWallFixtureMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Files.writeString(classpathFile, location.toString() + System.lineSeparator());

        ModeCClasspathLauncher.LaunchOutcome outcome = ModeCClasspathLauncher.launch(
            ModeCClasspathLauncher.LaunchRequest.parse(new String[]{
                "--classpath-file", classpathFile.toString(),
                "--main-class", EnvironmentWallFixtureMain.class.getName(),
                "--force-gc-before-exit",
                "--sleep-before-exit-ms=1"
            })
        );

        assertEquals(ModeCClasspathLauncher.FailureClassification.ENVIRONMENT_WALL, outcome.classification());
    }

    @Test
    void writesResourceAccessLogWhenEnabled() throws Exception {
        Path classpathFile = Files.createTempFile("jmoa-modec", ".txt");
        Path resourceLogFile = Files.createTempFile("jmoa-resources", ".json");
        Path location = Path.of(ResourceFixtureMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Files.writeString(classpathFile, location.toString() + System.lineSeparator());

        ModeCClasspathLauncher.LaunchOutcome outcome = ModeCClasspathLauncher.launch(
            ModeCClasspathLauncher.LaunchRequest.parse(new String[]{
                "--classpath-file", classpathFile.toString(),
                "--main-class", ResourceFixtureMain.class.getName(),
                "--resource-log-file", resourceLogFile.toString()
            })
        );

        assertEquals(ModeCClasspathLauncher.FailureClassification.NONE, outcome.classification());
        String report = Files.readString(resourceLogFile);
        assertTrue(report.contains("jmoa/tools/ModeCClasspathLauncherTest.class"));
        assertTrue(report.contains("\"foundCalls\": 1"));
    }

    public static final class FixtureMain {
        public static void main(String[] args) {
            System.setProperty(FIXTURE_PROPERTY, args.length == 0 ? "" : args[0]);
        }
    }

    public static final class EnvironmentWallFixtureMain {
        public static void main(String[] args) throws Exception {
            throw new SQLException("Connection refused");
        }
    }

    public static final class ResourceFixtureMain {
        public static void main(String[] args) {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            loader.getResource("jmoa/tools/ModeCClasspathLauncherTest.class");
        }
    }
}
