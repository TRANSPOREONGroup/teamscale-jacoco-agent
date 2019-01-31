package com.teamscale.report.jacoco;

import com.teamscale.report.jacoco.dump.Dump;
import com.teamscale.report.util.ILogger;
import org.conqat.lib.commons.filesystem.FileSystemUtils;
import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IBundleCoverage;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.SessionInfo;
import org.jacoco.report.IReportVisitor;
import org.jacoco.report.xml.XMLFormatter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/** Creates an XML report from binary execution data. */
public class JaCoCoXmlReportGenerator {

	/** The logger. */
	private final ILogger logger;

	/** Directories and zip files that contain class files. */
	private final List<File> codeDirectoriesOrArchives;

	/**
	 * Include filter to apply to all locations during class file traversal.
	 */
	private final Predicate<String> locationIncludeFilter;

	/** Whether to ignore non-identical duplicates of class files. */
	private final boolean ignoreNonidenticalDuplicateClassFiles;

	/** Constructor. */
	public JaCoCoXmlReportGenerator(List<File> codeDirectoriesOrArchives, Predicate<String> locationIncludeFilter,
									boolean ignoreDuplicates, ILogger logger) {
		this.codeDirectoriesOrArchives = codeDirectoriesOrArchives;
		this.ignoreNonidenticalDuplicateClassFiles = ignoreDuplicates;
		this.locationIncludeFilter = locationIncludeFilter;
		this.logger = logger;
	}

	/** Creates the report. */
	public String convert(Dump dump) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		convertToReport(output, dump);
		return output.toString(FileSystemUtils.UTF8_ENCODING);
	}

	/** Creates the report. */
	public void convertToReport(OutputStream output, Dump dump) throws IOException {
		ExecutionDataStore mergedStore = dump.store;
		IBundleCoverage bundleCoverage = analyzeStructureAndAnnotateCoverage(mergedStore);
		createReport(output, bundleCoverage, dump.info, mergedStore);
	}

	/** Creates an XML report based on the given session and coverage data. */
	private static void createReport(OutputStream output, IBundleCoverage bundleCoverage, SessionInfo sessionInfo, ExecutionDataStore store) throws IOException {
		XMLFormatter xmlFormatter = new XMLFormatter();
		IReportVisitor visitor = xmlFormatter.createVisitor(output);

		visitor.visitInfo(Collections.singletonList(sessionInfo), store.getContents());
		visitor.visitBundle(bundleCoverage, null);
		visitor.visitEnd();
	}

	/**
	 * Analyzes the structure of the class files in
	 * {@link #codeDirectoriesOrArchives} and builds an in-memory coverage report
	 * with the coverage in the given store.
	 */
	private IBundleCoverage analyzeStructureAndAnnotateCoverage(ExecutionDataStore store) throws IOException {
		CoverageBuilder coverageBuilder = new CoverageBuilder();
		if (ignoreNonidenticalDuplicateClassFiles) {
			coverageBuilder = new DuplicateIgnoringCoverageBuilder(this.logger);
		}

		Analyzer analyzer = new FilteringAnalyzer(store, coverageBuilder, locationIncludeFilter, logger);

		for (File file : codeDirectoriesOrArchives) {
			analyzer.analyzeAll(file);
		}

		return coverageBuilder.getBundle("dummybundle");
	}

}
