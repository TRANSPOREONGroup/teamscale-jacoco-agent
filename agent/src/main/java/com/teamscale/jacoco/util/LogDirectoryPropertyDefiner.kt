package com.teamscale.jacoco.util

import ch.qos.logback.core.PropertyDefinerBase
import com.teamscale.jacoco.agent.PreMain

import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Paths

/** Defines a property that contains the default path to which log files should be written.  */
class LogDirectoryPropertyDefiner : PropertyDefinerBase() {
    override fun getPropertyValue(): String {
        try {
            val jarFileUri = PreMain::class.java.protectionDomain.codeSource.location.toURI()
            // we assume that the dist zip is extracted and the agent jar not moved
            // Then the log dir should be next to the bin/ dir
            return Paths.get(jarFileUri).parent.parent.resolve("logs").toAbsolutePath().toString()
        } catch (e: URISyntaxException) {
            // we can't log the exception yet since logging is not yet initialized
            // fall back to the working directory
            return Paths.get(".").toAbsolutePath().toString()
        }

    }
}
