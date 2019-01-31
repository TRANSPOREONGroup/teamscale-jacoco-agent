/*-------------------------------------------------------------------------+
|                                                                          |
| Copyright (c) 2009-2018 CQSE GmbH                                        |
|                                                                          |
+-------------------------------------------------------------------------*/
package com.teamscale.jacoco.agent.store.upload.http

import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

import java.io.IOException

/** [Retrofit] API specification for the [HttpUploadStore].  */
interface IHttpUploadApi {

    /** The upload API call.  */
    @Multipart
    @POST("/")
    fun upload(@Part uploadedFile: MultipartBody.Part): Call<ResponseBody>

    /**
     * Convenience method to perform an [.upload]
     * call for a coverage zip.
     */
    @Throws(IOException::class)
    fun uploadCoverageZip(data: ByteArray): Response<ResponseBody> {
        val body = RequestBody.create(MediaType.parse("application/zip"), data)
        val part = MultipartBody.Part.createFormData("file", "coverage.zip", body)
        return upload(part).execute()
    }

}
