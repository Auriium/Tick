package xyz.auriium.tick.docker.source.impl;

import org.apache.commons.lang.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;
import xyz.auriium.tick.docker.source.*;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

public class MachineSourceProvider extends SimpleSourceProvider {

    private static final Logger logger = LoggerFactory.getLogger(MachineSourceProvider.class);
    private static final String DEFAULT = "default";

    @Override
    public String name() {
        return "MachineSourceProvider";
    }

    @Override
    public Integer priority() {
        return 100;
    }

    @Override
    public ApplicableResult isApplicable() {
        String executableName = "docker-machine";
        if (SystemUtils.IS_OS_WINDOWS) {
            executableName="docker-machine.exe";
        }

        if (!executableExists(executableName)) return ApplicableResult.fail("DockerMachine is not present on this system!");

        try {
            ProcessResult result = new ProcessExecutor()
                    .command(executableName,"ls","-q", "--filter", "state=Running")
                    .readOutput(true)
                    .exitValueNormal()
                    .execute();

            Optional<String> toUse = getToUse("ignored", List.of(result.outputUTF8().split("\n")));

            if (toUse.isEmpty()) return ApplicableResult.fail("No usable DockerMachine system is active. Please create one and try again.");

            String url = new ProcessExecutor()
                    .command(executableName,"url", toUse.get())
                    .readOutput(true)
                    .exitValueNormal()
                    .execute().outputString().replaceAll("\n","");

            String ip = new ProcessExecutor()
                    .command(executableName,"ip", toUse.get())
                    .readOutput(true)
                    .exitValueNormal()
                    .execute().outputString().replaceAll("\n","");

            return ApplicableResult.success();


        } catch (IOException | InterruptedException | TimeoutException e) {
            return ApplicableResult.fail(e.getMessage());
        }
    }

    @Override
    public URI makeURI(ClientOptions options) throws SourceProvideException {

        String preferredProvider = options.getPreferredDockerMachineName();

        logger.info("Attempting to produce DockerSource using docker machine commandline!");

        String executableName = "docker-machine";
        if (SystemUtils.IS_OS_WINDOWS) {
            executableName="docker-machine.exe";
        }

        if (!executableExists(executableName)) throw new SourceProvideException("DockerMachine executable not found on this system!");

        try {
            ProcessResult result = new ProcessExecutor()
                    .command(executableName,"ls","-q", "--filter", "state=Running")
                    .readOutput(true)
                    .exitValueNormal()
                    .execute();

            String toUse = getToUse(preferredProvider, List.of(result.outputUTF8().split("\n"))).orElseThrow(() -> new SourceProvideException("No default machine present on this system!"));

            logger.info(String.format("Using docker-machine with system %s (selected machine %s). If these do not match machine %s was likely ineligible for use.", toUse, preferredProvider, preferredProvider));

            /*URI url = new ProcessExecutor()
                    .command(executableName,"url", toUse)
                    .readOutput(true)
                    .exitValueNormal()
                    .execute().outputString().replaceAll("\n","");*/

            return null;


        } catch (IOException | InterruptedException | TimeoutException e) {
            throw new ShellExecutionException(e);
        }

    }

    private Optional<String> getToUse(String preferredProvider, Collection<String> strings) {
        if (strings.contains(preferredProvider)) {
            return Optional.of(preferredProvider);
        } else if (strings.contains(DEFAULT)) {
            return Optional.of(DEFAULT);
        } else {
            return Optional.empty();
        }
    }

    private boolean executableExists(String executable) {

        Path directFile = Path.of(executable);
        if (Files.isExecutable(directFile)) {
            return true;
        }

        for (String pathString : System.getenv("PATH").split(Pattern.quote(File.pathSeparator))) {
            Path path = Paths.get(pathString);
            if (Files.isExecutable(path.resolve(executable))) {
                return true;
            }
        }

        return false;
    }
}
