package org.junit.platform.console.options;

import com.teamscale.client.CommitDescriptor;
import okhttp3.HttpUrl;
import org.junit.platform.console.shadow.joptsimple.OptionParser;
import org.junit.platform.console.shadow.joptsimple.OptionSet;
import org.junit.platform.console.shadow.joptsimple.OptionSpec;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

/** Helper class to parse command line options. */
public class AvailableImpactedTestsExecutorCommandLineOptions {

	/** Available options from jUnit */
	private AvailableOptions jUnitOptions;

	/** Holds the command line parser with the standard jUnit options and ours. */
	private final OptionParser parser;

	private final OptionSpec<String> url;
	private final OptionSpec<String> project;
	private final OptionSpec<String> userName;
	private final OptionSpec<String> userAccessToken;
	private final OptionSpec<String> partition;

	private final OptionSpec<String> baseline;
	private final OptionSpec<String> end;

	private final OptionSpec<Void> runAllTests;

	private OptionSpec<String> agentUrl;

	/** Constructor. */
	AvailableImpactedTestsExecutorCommandLineOptions() {
		jUnitOptions = new AvailableOptions();
		parser = jUnitOptions.getParser();
		url = parser.accepts("url",
				"Url of the teamscale server")
				.withRequiredArg();

		project = parser.accepts("project",
				"Project ID of the teamscale project")
				.withRequiredArg();

		userName = parser.accepts("user",
				"The user name in teamscale")
				.withRequiredArg();

		userAccessToken = parser.accepts("access-token",
				"The users access token for Teamscale")
				.withRequiredArg();

		partition = parser.accepts("partition",
				"Partition of the tests")
				.withRequiredArg();

		baseline = parser.accepts("baseline",
				"The baseline commit")
				.withRequiredArg();

		end = parser.accepts("end",
				"The end commit")
				.withRequiredArg();

		runAllTests = parser.acceptsAll(asList("all", "run-all-tests"),
				"Partition of the tests");

		agentUrl = parser.acceptsAll(asList("agent-url", "agent"),
				"Url of the teamscale jacoco agent that generates coverage for the system")
				.withRequiredArg();
	}

	/** Returns an options parser with the available options set. */
	public OptionParser getParser() {
		return parser;
	}

	/** Converts the parsed parameters into a {@link ImpactedTestsExecutorCommandLineOptions} object. */
	public ImpactedTestsExecutorCommandLineOptions toCommandLineOptions(OptionSet detectedOptions) {
		CommandLineOptions jUnitResult = jUnitOptions.toCommandLineOptions(detectedOptions);
		jUnitResult.setIncludedClassNamePatterns(
				jUnitResult.getIncludedClassNamePatterns().stream()
						.map(AvailableImpactedTestsExecutorCommandLineOptions::stripQuotes)
						.collect(toList()));
		jUnitResult.setExcludedClassNamePatterns(
				jUnitResult.getExcludedClassNamePatterns().stream()
						.map(AvailableImpactedTestsExecutorCommandLineOptions::stripQuotes)
						.collect(toList()));

		ImpactedTestsExecutorCommandLineOptions result = new ImpactedTestsExecutorCommandLineOptions(jUnitResult);

		result.getServer().url = detectedOptions.valueOf(this.url);
		result.getServer().project = detectedOptions.valueOf(this.project);
		result.getServer().userName = detectedOptions.valueOf(this.userName);
		result.getServer().userAccessToken = detectedOptions.valueOf(this.userAccessToken);
		result.setPartition(detectedOptions.valueOf(this.partition));

		result.setRunAllTests(detectedOptions.has(this.runAllTests));

		if (detectedOptions.has(this.baseline)) {
			result.setBaseline(Long.parseLong(detectedOptions.valueOf(this.baseline)));
		}
		result.setEndCommit(CommitDescriptor.Companion.parse(detectedOptions.valueOf(this.end)));

		result.setAgentUrls(detectedOptions.valuesOf(this.agentUrl).stream().map(HttpUrl::parse).collect(toList()));

		return result;
	}

	/**
	 * Helper to remove leading and trailing quotes from a string.
	 * Works with single or double quotes.
	 * Copied from {@link com.sun.javafx.util.Utils#stripQuotes(String)} as
	 * javafx is not available in all cases on windows.
	 */
	private static String stripQuotes(String string) {
		if (string == null) return null;
		if (string.length() == 0) return string;

		int beginIndex = 0;
		final char openQuote = string.charAt(beginIndex);
		if (openQuote == '\"' || openQuote == '\'') beginIndex += 1;

		int endIndex = string.length();
		final char closeQuote = string.charAt(endIndex - 1);
		if (closeQuote == '\"' || closeQuote == '\'') endIndex -= 1;

		if ((endIndex - beginIndex) < 0) return string;

		// note that String.substring returns "this" if beginIndex == 0 && endIndex == count
		// or a new string that shares the character buffer with the original string.
		return string.substring(beginIndex, endIndex);
	}
}

