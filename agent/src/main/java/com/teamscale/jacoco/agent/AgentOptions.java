/*-------------------------------------------------------------------------+
|                                                                          |
| Copyright (c) 2009-2018 CQSE GmbH                                        |
|                                                                          |
+-------------------------------------------------------------------------*/
package com.teamscale.jacoco.agent;

import com.teamscale.client.FileSystemUtils;
import com.teamscale.client.TeamscaleServer;
import com.teamscale.jacoco.agent.commandline.Validator;
import com.teamscale.jacoco.agent.store.IXmlStore;
import com.teamscale.jacoco.agent.store.UploadStoreException;
import com.teamscale.jacoco.agent.store.file.TimestampedFileStore;
import com.teamscale.jacoco.agent.store.upload.azure.AzureFileStorageConfig;
import com.teamscale.jacoco.agent.store.upload.azure.AzureFileStorageUploadStore;
import com.teamscale.jacoco.agent.store.upload.http.HttpUploadStore;
import com.teamscale.jacoco.agent.store.upload.teamscale.TeamscaleUploadStore;
import com.teamscale.jacoco.agent.testimpact.TestExecutionWriter;
import com.teamscale.jacoco.agent.testimpact.TestwiseCoverageAgent;
import com.teamscale.report.EDuplicateClassFileBehavior;
import com.teamscale.report.util.ClasspathWildcardIncludeFilter;
import okhttp3.HttpUrl;
import org.conqat.lib.commons.assertion.CCSMAssert;
import org.conqat.lib.commons.collections.PairList;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Parses agent command line options.
 */
public class AgentOptions {

	/**
	 * The original options passed to the agent.
	 */
	/* package */ String originalOptionsString;

	/**
	 * The directories and/or zips that contain all class files being profiled.
	 */
	/* package */ List<File> classDirectoriesOrZips = new ArrayList<>();

	/**
	 * The logging configuration file.
	 */
	/* package */ Path loggingConfig = null;

	/**
	 * The directory to write the XML traces to.
	 */
	/* package */ Path outputDirectory = null;

	/**
	 * The URL to which to upload coverage zips.
	 */
	/* package */ HttpUrl uploadUrl = null;

	/**
	 * Additional meta data files to upload together with the coverage XML.
	 */
	/* package */ List<Path> additionalMetaDataFiles = new ArrayList<>();

	/** Whether the agent should be run in testwise coverage mode or normal mode. */
	/* package */ EMode mode = EMode.NORMAL;

	/**
	 * The interval in minutes for dumping XML data.
	 */
	/* package */ int dumpIntervalInMinutes = 60;


	/** Whether to dump coverage when the JVM shuts down. */
	/* package */ boolean shouldDumpOnExit = true;

	/**
	 * Whether to ignore duplicate, non-identical class files.
	 */
	/* package */ boolean shouldIgnoreDuplicateClassFiles = true;

	/**
	 * Include patterns to pass on to JaCoCo.
	 */
	/* package */ String jacocoIncludes = null;

	/**
	 * Exclude patterns to pass on to JaCoCo.
	 */
	/* package */ String jacocoExcludes = null;

	/**
	 * Additional user-provided options to pass to JaCoCo.
	 */
	/* package */ PairList<String, String> additionalJacocoOptions = new PairList<>();

	/**
	 * The teamscale server to which coverage should be uploaded.
	 */
	/* package */ TeamscaleServer teamscaleServer = new TeamscaleServer();

	/**
	 * The name of the environment variable that holds the test uniform path.
	 */
	/* package */ String testEnvironmentVariable = null;

	/**
	 * The port on which the HTTP server should be listening.
	 */
	/* package */ Integer httpServerPort = null;

	/**
	 * The configuration necessary to upload files to an azure file storage
	 */
	/* package */ AzureFileStorageConfig azureFileStorageConfig = new AzureFileStorageConfig();

	/**
	 * @see #originalOptionsString
	 */
	public String getOriginalOptionsString() {
		return originalOptionsString;
	}

	/**
	 * Validates the options and throws an exception if they're not valid.
	 */
	/* package */ Validator getValidator() {
		Validator validator = new Validator();

		validator.isTrue(!getClassDirectoriesOrZips().isEmpty() || useTestwiseCoverageMode(),
				"You must specify at least one directory or zip that contains class files");
		for (File path : classDirectoriesOrZips) {
			validator.isTrue(path.exists(), "Path '" + path + "' does not exist");
			validator.isTrue(path.canRead(), "Path '" + path + "' is not readable");
		}

		validator.ensure(() -> {
			CCSMAssert.isNotNull(outputDirectory, "You must specify an output directory");
			FileSystemUtils.ensureDirectoryExists(outputDirectory.toFile());
		});

		if (loggingConfig != null) {
			validator.ensure(() -> {
				CCSMAssert.isTrue(Files.exists(loggingConfig),
						"The path provided for the logging configuration does not exist: " + loggingConfig);
				CCSMAssert.isTrue(Files.isRegularFile(loggingConfig),
						"The path provided for the logging configuration is not a file: " + loggingConfig);
				CCSMAssert.isTrue(Files.isReadable(loggingConfig),
						"The file provided for the logging configuration is not readable: " + loggingConfig);
				CCSMAssert.isTrue(FileSystemUtils.getFileExtension(loggingConfig.toFile()).equalsIgnoreCase("xml"),
						"The logging configuration file must have the file extension .xml and be a valid XML file");
			});
		}

		validator.isTrue(!useTestwiseCoverageMode() || uploadUrl == null, "'upload-url' option is " +
				"incompatible with Testwise coverage mode!");

		validator.isFalse(uploadUrl == null && !additionalMetaDataFiles.isEmpty(),
				"You specified additional meta data files to be uploaded but did not configure an upload URL");

		validator.isTrue(teamscaleServer.hasAllRequiredFieldsNull() || teamscaleServer.hasAllRequiredFieldsSet(),
				"You did provide some options prefixed with 'teamscale-', but not all required ones!");

		validator.isTrue((azureFileStorageConfig.hasAllRequiredFieldsSet() || azureFileStorageConfig
						.hasAllRequiredFieldsNull()),
				"If you want to upload data to an azure file storage you need to provide both " +
						"'azure-url' and 'azure-key' ");

		List<Boolean> configuredStores = Stream
				.of(azureFileStorageConfig.hasAllRequiredFieldsSet(), teamscaleServer.hasAllRequiredFieldsSet(),
						uploadUrl != null).filter(x -> x).collect(Collectors.toList());

		validator.isTrue(configuredStores.size() <= 1, "You cannot configure multiple upload stores, " +
				"such as a teamscale instance, upload url or azure file storage");

		return validator;
	}

	/**
	 * Returns the options to pass to the JaCoCo agent.
	 */
	public String createJacocoAgentOptions() {
		StringBuilder builder = new StringBuilder(getModeSpecificOptions());
		if (jacocoIncludes != null) {
			builder.append(",includes=").append(jacocoIncludes);
		}
		if (jacocoExcludes != null) {
			builder.append(",excludes=").append(jacocoExcludes);
		}

		additionalJacocoOptions.forEach((key, value) -> builder.append(",").append(key).append("=").append(value));

		return builder.toString();
	}

	/** Sets output to none for normal mode and destination file in testwise coverage mode */
	private String getModeSpecificOptions() {
		if (useTestwiseCoverageMode()) {
			return "sessionid=,destfile=" + getTempFile("jacoco", "exec").getAbsolutePath();
		} else {
			return "output=none";
		}
	}

	private File getTempFile(final String prefix, final String extension) {
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss.SSS");
		return new File(outputDirectory.toFile(),
				prefix + "-" + dateFormat.format(new Date()) + "." + extension);
	}

	/**
	 * Returns in instance of the agent that was configured. Either an agent with interval based line-coverage dump or
	 * the HTTP server is used.
	 */
	public AgentBase createAgent() throws UploadStoreException {
		if (useTestwiseCoverageMode()) {
			return new TestwiseCoverageAgent(this, new TestExecutionWriter(getTempFile("test-execution", "json")));
		} else {
			return new Agent(this);
		}
	}

	/**
	 * Creates the store to use for the coverage XMLs.
	 */
	public IXmlStore createStore() throws UploadStoreException {
		TimestampedFileStore fileStore = new TimestampedFileStore(outputDirectory);
		if (uploadUrl != null) {
			return new HttpUploadStore(fileStore, uploadUrl, additionalMetaDataFiles);
		}
		if (teamscaleServer.hasAllRequiredFieldsSet()) {
			return new TeamscaleUploadStore(fileStore, teamscaleServer);
		}

		if (azureFileStorageConfig.hasAllRequiredFieldsSet()) {
			return new AzureFileStorageUploadStore(fileStore, azureFileStorageConfig,
					additionalMetaDataFiles);
		}

		return fileStore;
	}

	/**
	 * @see #classDirectoriesOrZips
	 */
	public List<File> getClassDirectoriesOrZips() {
		return classDirectoriesOrZips;
	}

	/** @see #teamscaleServer */
	public TeamscaleServer getTeamscaleServerOptions() {
		return teamscaleServer;
	}

	/**
	 * @see #dumpIntervalInMinutes
	 */
	public int getDumpIntervalInMinutes() {
		return dumpIntervalInMinutes;
	}

	/**
	 * @see #shouldIgnoreDuplicateClassFiles
	 */
	public EDuplicateClassFileBehavior duplicateClassFileBehavior() {
		if (shouldIgnoreDuplicateClassFiles) {
			return EDuplicateClassFileBehavior.WARN;
		} else {
			return EDuplicateClassFileBehavior.FAIL;
		}
	}

	/** Returns whether the config indicates to use Test Impact mode. */
	private boolean useTestwiseCoverageMode() {
		return mode == EMode.TESTWISE;
	}

	/**
	 * Returns the port at which the http server should listen for test execution information or null if disabled.
	 */
	public Integer getHttpServerPort() {
		return httpServerPort;
	}

	/**
	 * Returns the name of the environment variable to read the test uniform path from.
	 */
	public String getTestEnvironmentVariableName() {
		return testEnvironmentVariable;
	}

	/**
	 * @see #loggingConfig
	 */
	public Path getLoggingConfig() {
		return loggingConfig;
	}

	/**
	 * @see #loggingConfig
	 */
	public void setLoggingConfig(Path loggingConfig) {
		this.loggingConfig = loggingConfig;
	}

	/**
	 * @see #jacocoIncludes
	 * @see #jacocoExcludes
	 */
	public Predicate<String> getLocationIncludeFilter() {
		return new ClasspathWildcardIncludeFilter(jacocoIncludes, jacocoExcludes);
	}

	/** Whether coverage should be dumped in regular intervals. */
	public boolean shouldDumpInIntervals() {
		return dumpIntervalInMinutes > 0;
	}

	/** Whether coverage should be dumped on JVM shutdown. */
	public boolean shouldDumpOnExit() {
		return shouldDumpOnExit;
	}

	/** Describes the two possible modes the agent can be started in. */
	public enum EMode {

		/**
		 * The default mode which produces JaCoCo XML coverage files on exit, in a defined interval or when triggered
		 * via an HTTP endpoint. Each dump produces a new file containing the all collected coverage.
		 */
		NORMAL,

		/**
		 * Testwise coverage mode in which the agent only dumps when triggered via an HTTP endpoint. Coverage is written
		 * as exec and appended into a single file.
		 */
		TESTWISE
	}
}
