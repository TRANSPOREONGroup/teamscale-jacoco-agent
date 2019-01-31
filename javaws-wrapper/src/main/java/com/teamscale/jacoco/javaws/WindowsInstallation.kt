package com.teamscale.jacoco.javaws;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.conqat.lib.commons.filesystem.FileSystemUtils;
import org.conqat.lib.commons.string.StringUtils;

/**
 * Installs/uninstalls the wrapper under Windows.
 * 
 * We must set the file type association for JNLP files so it points to our
 * wrapper. We must also overwrite the system security policy since with some
 * JREs the additional system property the wrapper sets to make the JVM use our
 * custom security policy is ignored.
 */
public class WindowsInstallation {

	private static final String JNLP_FTYPE = "JNLPFile";
	private static final String SECURITY_POLICY_FILE = "javaws.policy";
	private static final String FTYPE_MAPPING_BACKUP_FILE = "ftype.bak";

	private final Path systemSecurityPolicy;
	private final Path systemJavaws;
	private final WrapperPaths wrapperPaths;
	private final BackupPaths backupPaths;

	public WindowsInstallation(Path workingDirectory) throws InstallationException {
		this.systemSecurityPolicy = getJvmInstallPath().resolve("lib/security").resolve(SECURITY_POLICY_FILE);
		this.systemJavaws = getJvmInstallPath().resolve("bin/javaws");
		this.wrapperPaths = new WrapperPaths(workingDirectory);
		this.backupPaths = new BackupPaths(workingDirectory.resolve("backup"));
		validate();
	}

	private void validate() throws InstallationException {
		try {
			FileSystemUtils.mkdirs(backupPaths.backupDirectory.toFile());
		} catch (IOException e) {
			throw new InstallationException("Cannot create backup directory at " + backupPaths.backupDirectory, e);
		}

		if (!Files.exists(systemSecurityPolicy)) {
			throw new InstallationException("Could not locate the javaws security policy file at "
					+ systemSecurityPolicy + ". Please make sure the JAVA_HOME environment variable is properly set");
		}

		if (!Files.exists(wrapperPaths.securityPolicy) || !Files.exists(wrapperPaths.wrapperExecutable)) {
			throw new InstallationException("Could not locate all necessary data files that came with this program."
					+ " Please make sure you run this installation routine from within its installation directory.");
		}
	}

	/** Checks whether the wrapper is currently installed. */
	public boolean isInstalled() {
		return Files.exists(backupPaths.ftypeMapping);
	}

	/**
	 * Installs the wrapper or throws an exception if the installation fails. In
	 * case of an exception, the installation may be partly done.
	 */
	public void install() throws InstallationException {
		if (isInstalled()) {
			throw new InstallationException("Wrapper is already installed");
		}

		try {
			FileSystemUtils.mkdirs(wrapperPaths.defaultOutputDirectory.toFile());
		} catch (IOException e) {
			System.err.println("Unable to create default output directory " + wrapperPaths.defaultOutputDirectory
					+ ". Please create it yourself");
			e.printStackTrace(System.err);
		}

		try {
			Files.copy(systemSecurityPolicy, backupPaths.securityPolicy, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new InstallationException("Failed to backup current javaws security policy from "
					+ systemSecurityPolicy + " to " + backupPaths.securityPolicy, e);
		}
		try {
			FileSystemUtils.writeFileUTF8(backupPaths.ftypeMapping.toFile(), readCurrentJnlpFtype());
		} catch (IOException | InterruptedException e) {
			throw new InstallationException(
					"Failed to backup current file type mapping for JNLP files to " + backupPaths.ftypeMapping, e);
		}

		try {
			Files.copy(wrapperPaths.securityPolicy, systemSecurityPolicy, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new InstallationException("Failed to overwrite the system security policy at " + systemSecurityPolicy
					+ ". You must run this installer as an administrator", e);
		}
		try {
			setFtype(JNLP_FTYPE + "=" + wrapperPaths.wrapperExecutable.toAbsolutePath() + " \"%1\"");
		} catch (IOException | InterruptedException e) {
			throw new InstallationException(
					"Failed to change the file type mapping for JNLP files. You must run this installer as an administrator",
					e);
		}

		Properties properties = new Properties();
		properties.setProperty("javaws", systemJavaws.toAbsolutePath().toString());
		properties.setProperty("agentArguments",
				"out=" + wrapperPaths.defaultOutputDirectory.toAbsolutePath() + ",includes=*com.yourcompany.*");

		try (FileOutputStream outputStream = new FileOutputStream(wrapperPaths.configProperties.toFile())) {
			properties.store(outputStream, StringUtils.EMPTY_STRING);
		} catch (IOException e) {
			System.err.print("WARN: Failed to write the wrapper config file to " + wrapperPaths.configProperties
					+ ". The installation itself was successful but you'll have to configure the wrapper manually (see the userguide for instructions)");
			e.printStackTrace(System.err);
		}

		System.out.println("The installation was successful. Please fill in the agent arguments in the config file "
				+ wrapperPaths.configProperties);
	}

	/**
	 * Uninstalls the wrapper or throws an exception if the uninstallation fails. In
	 * case of an exception, the uninstallation may be partly done.
	 */
	public void uninstall() throws InstallationException {
		if (!isInstalled()) {
			throw new InstallationException("Wrapper does not seem to be installed");
		}

		String oldFtypeMapping;
		try {
			oldFtypeMapping = FileSystemUtils.readFileUTF8(backupPaths.ftypeMapping.toFile());
		} catch (IOException e) {
			throw new InstallationException("Failed to read the backup of the file type mapping for JNLP files from "
					+ backupPaths.ftypeMapping, e);
		}

		try {
			setFtype(oldFtypeMapping);
		} catch (IOException | InterruptedException e) {
			throw new InstallationException(
					"Failed to change the file type mapping for JNLP files. You must run this installer as an administrator",
					e);
		}
		try {
			Files.copy(backupPaths.securityPolicy, systemSecurityPolicy, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new InstallationException("Failed to overwrite the system security policy at " + systemSecurityPolicy
					+ " with the backup from " + backupPaths.securityPolicy
					+ ". You must run this installer as an administrator", e);
		}

		try {
			Files.deleteIfExists(backupPaths.ftypeMapping);
			Files.deleteIfExists(backupPaths.securityPolicy);
		} catch (IOException e) {
			System.err.println(
					"WARN: Failed to delete the backup files. The uninstallation was successful but the backup files remain at "
							+ backupPaths.backupDirectory);
			e.printStackTrace(System.err);
		}
	}

	private String readCurrentJnlpFtype() throws IOException, InterruptedException {
		return runFtype(JNLP_FTYPE);
	}

	/** Sets the given mapping of the form <code>MAPPING=COMMAND</code>. */
	private void setFtype(String desiredMapping) throws InstallationException, IOException, InterruptedException {
		String ftypeOutput = runFtype(desiredMapping);
		if (!readCurrentJnlpFtype().equalsIgnoreCase(desiredMapping)) {
			throw new InstallationException(
					"Failed to set file mapping " + desiredMapping + ". Output of ftype: " + ftypeOutput);
		}
	}

	/** Runs the ftype shell builtin to change file associations. */
	private static String runFtype(String argument) throws IOException, InterruptedException {
		CommandLine commandLine = new CommandLine("cmd.exe");
		commandLine.addArgument("/s");
		commandLine.addArgument("/c");
		// cmd.exe rejects the command unless we disable quoting and wrap the entire
		// thing in quotes for the /s parameter's quote handling
		commandLine.addArgument("\"ftype " + argument + "\"", false);

		DefaultExecutor executor = new DefaultExecutor();
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
		executor.setStreamHandler(streamHandler);
		// ftype has weird exit values. We just ignore them and only check the output
		executor.setExitValues(null);

		executor.execute(commandLine);
		return outputStream.toString().trim();
	}

	/** The path where the currently running JVM is installed. */
	private static final Path getJvmInstallPath() {
		return Paths.get(System.getProperty("java.home"));
	}

	private class BackupPaths {

		private final Path backupDirectory;
		private final Path securityPolicy;
		private final Path ftypeMapping;

		public BackupPaths(Path backupDirectory) {
			this.backupDirectory = backupDirectory;
			this.securityPolicy = backupDirectory.resolve(SECURITY_POLICY_FILE);
			this.ftypeMapping = backupDirectory.resolve(FTYPE_MAPPING_BACKUP_FILE);
		}

	}

	private class WrapperPaths {

		private final Path securityPolicy;
		private final Path wrapperExecutable;
		private final Path configProperties;
		private final Path defaultOutputDirectory;

		public WrapperPaths(Path wrapperDirectory) {
			securityPolicy = wrapperDirectory.resolve("agent.policy");
			wrapperExecutable = wrapperDirectory.resolve("bin/javaws");
			configProperties = wrapperDirectory.resolve("javaws.properties");
			defaultOutputDirectory = wrapperDirectory.resolve("coverage");
		}

	}

	/** Thrown if the installation/uninstallation fails. */
	public static class InstallationException extends Exception {

		private static final long serialVersionUID = 1L;

		public InstallationException(String message, Throwable cause) {
			super(message, cause);
		}

		public InstallationException(String message) {
			super(message);
		}

	}

}
