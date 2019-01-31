package com.teamscale.jacoco.agent.store

import okhttp3.ResponseBody
import retrofit2.Response

import java.io.IOException

/**
 * Exception thrown from an upload store. Either during the upload or in the validation process.
 */
class UploadStoreException : Exception {

    /** Constructor  */
    constructor(message: String, e: Exception) : super(message, e) {}

    /** Constructor  */
    constructor(message: String) : super(message) {}

    /** Constructor  */
    constructor(message: String, response: Response<ResponseBody>) : super(createResponseMessage(message, response)) {}

    private fun createResponseMessage(message: String, response: Response<ResponseBody>): String {
        try {
            val errorBodyMessage = response.errorBody()!!.string()
            return String.format("%s (%s): \n%s", message, response.code(), errorBodyMessage)
        } catch (e: IOException) {
            return message
        } catch (e: NullPointerException) {
            return message
        }

    }
}
