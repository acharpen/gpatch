package com.yac.gpatch;

import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import picocli.CommandLine.Command;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "gpatch", description = "Perform collateral evolutions", versionProvider = AppConfig.PropertiesVersionProvider.class)
public class AppConfig {

    @Option(names = { "-e", "--ext-files" }, description = "the extension of files to process", required = true)
    private String extension;

    @Option(names = { "--p-file" }, description = "the patch file", paramLabel = "<patch>", required = true)
    private Path patchPath;

    @Parameters(arity = "1..*", description = "process all files in directory recursively", paramLabel = "FILES")
    private List<Path> paths;

    @Option(names = { "-h", "--help" }, description = "display this help message", usageHelp = true)
    private boolean usageHelpRequested;

    @Option(names = { "--version" }, description = "display version info", versionHelp = true)
    private boolean versionInfoRequested;

    public String getExtension() {
        return extension;
    }

    public Path getPatchPath() {
        return patchPath;
    }

    public List<Path> getPaths() {
        return paths;
    }

    static class PropertiesVersionProvider implements IVersionProvider {

        public String[] getVersion() throws Exception {
            URL url = getClass().getResource("/version.txt");
            if (url == null) {
                return new String[] { "No version.txt file found in the classpath." };
            }

            Properties properties = new Properties();
            properties.load(url.openStream());

            return new String[] { properties.getProperty("Version") };
        }

    }
}
