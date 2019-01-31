package com.teamscale.jacoco.agent.store.upload.azure;

/** Constants for the names of HTTP header used in a request to an Azure file storage. */
/* package */ class AzureHttpHeader {
	/** Same as {@link #CONTENT_LENGTH} */
	/* package */ static final String X_MS_CONTENT_LENGTH = "x-ms-content-length";

	/** Same as {@link #DATE} */
	/* package */ static final String X_MS_DATE = "x-ms-date";

	/** Same as {@link #RANGE} */
	/* package */ static final String X_MS_RANGE = "x-ms-range";

	/** Type of filesystem object which the request is referring to. Can be 'file' or 'directory'. */
	/* package */ static final String X_MS_TYPE = "x-ms-type";

	/** Version of the Azure file storage API */
	/* package */ static final String X_MS_VERSION = "x-ms-version";

	/**
	 * Defines the type of write operation on a file. Can either be 'Update' or 'Clear'.
	 * For 'Update' the 'Range' and 'Content-Length' headers must match, for 'Clear', 'Content-Length' must be set
	 * to 0.
	 */
	/* package */ static final String X_MS_WRITE = "x-ms-write";

	/**
	 * Defines the authorization and must contain the account name and signature.
	 * Must be given in the following format: Authorization="[SharedKey|SharedKeyLite] <AccountName>:<Signature>"
	 */
	/* package */ static final String AUTHORIZATION = "Authorization";

	/** Content-Encoding */
	/* package */ static final String CONTENT_ENCODING = "Content-Encoding";

	/** Content-Language */
	/* package */ static final String CONTENT_LANGUAGE = "Content-Language";

	/** Content-Length */
	/* package */ static final String CONTENT_LENGTH = "Content-Length";

	/** The md5 hash of the sent content. */
	/* package */ static final String CONTENT_MD_5 = "Content-MD5";

	/** Content-Type */
	/* package */ static final String CONTENT_TYPE = "Content-Type";

	/** The date time of the request */
	/* package */ static final String DATE = "Date";

	/** Only send the response if the entity has not been modified since a specific time. */
	/* package */ static final String IF_UNMODIFIED_SINCE = "If-Unmodified-Since";

	/** Allows a 304 Not Modified to be returned if content is unchanged. */
	/* package */ static final String IF_MODIFIED_SINCE = "If-Modified-Since";

	/**
	 * Only perform the action if the client supplied entity matches the same entity on the server.
	 * This is mainly for methods like PUT to only update a resource if it has not been modified
	 * since the user last updated it.
	 */
	/* package */ static final String IF_MATCH = "If-Match";

	/** Allows a 304 Not Modified to be returned if content is unchanged */
	/* package */ static final String IF_NONE_MATCH = "If-None-Match";

	/**
	 * Specifies the range of bytes to be written. Both the start and end of the range must be specified.
	 * Must be given in the following format: "bytes=startByte-endByte"
	 */
	/* package */ static final String RANGE = "Range";
}
