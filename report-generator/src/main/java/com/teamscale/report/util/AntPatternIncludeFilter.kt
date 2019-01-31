/*-------------------------------------------------------------------------+
|                                                                          |
| Copyright (c) 2009-2018 CQSE GmbH                                        |
|                                                                          |
+-------------------------------------------------------------------------*/
package com.teamscale.report.util;

import org.conqat.lib.commons.collections.CollectionUtils;
import org.conqat.lib.commons.filesystem.AntPatternUtils;
import org.conqat.lib.commons.filesystem.FileSystemUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Applies ANT include and exclude patterns to paths.
 */
public class AntPatternIncludeFilter implements Predicate<String> {

	/** The include filters. Empty means include everything. */
	private final List<Pattern> locationIncludeFilters;

	/** The exclude filters. Empty means exclude nothing. */
	private final List<Pattern> locationExcludeFilters;

	/** Constructor. */
	public AntPatternIncludeFilter(List<String> locationIncludeFilters, List<String> locationExcludeFilters) {
		this.locationIncludeFilters = CollectionUtils.map(locationIncludeFilters,
				filter -> AntPatternUtils.convertPattern(filter, false));
		this.locationExcludeFilters = CollectionUtils.map(locationExcludeFilters,
				filter -> AntPatternUtils.convertPattern(filter, false));
	}

	/** {@inheritDoc} */
	@Override
	public boolean test(String path) {
		return !isFiltered(FileSystemUtils.normalizeSeparators(path));
	}

	/**
	 * Returns <code>true</code> if the given class file location (normalized to
	 * forward slashes as path separators) should not be analyzed.
	 * <p>
	 * Exclude filters overrule include filters.
	 */
	private boolean isFiltered(String location) {
		// first check includes
		if (!locationIncludeFilters.isEmpty()
				&& locationIncludeFilters.stream().noneMatch(filter -> filter.matcher(location).matches())) {
			return true;
		}
		// only if they match, check excludes
		return locationExcludeFilters.stream().anyMatch(filter -> filter.matcher(location).matches());
	}

}
