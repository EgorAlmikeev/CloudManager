package lostnfound.university.herzen.lostnfound.managers

import android.content.Context
import android.os.AsyncTask
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
import java.io.IOException

class CloudManager(val context: Context) {

    data class Response(
        val code: Int,
        val data: JSONObject?,
        val error: String?,
        val success: Boolean
    )

    enum class RequestExecutionResult {
        OK,
        AUTH_ERROR,
        ACCESS_ERROR,
        NETWORK_ERROR,
        OTHER_CLIENT_ERROR,
        OTHER_SERVER_ERROR
    }

    companion object Route {
        private const val SERVER_ADDRESS = "http://0.0.0.0:8080/"

        enum class Upload(val url: String) {
            ADD_FOUND(SERVER_ADDRESS + "add_found"),
            UPDATE_FOUND(SERVER_ADDRESS + "update_found"),
            DELETE_FOUND(SERVER_ADDRESS + "delete_found"),
            ADD_STOCK(SERVER_ADDRESS + "add_stock"),
            UPDATE_STOCK(SERVER_ADDRESS + "update_stock"),
            DELETE_STOCK(SERVER_ADDRESS + "delete_stock")
        }

        enum class Download(val url: String) {
            GET_FOUNDS(SERVER_ADDRESS + "get_founds"),
            GET_STOCKS(SERVER_ADDRESS + "get_stocks"),
            GET_USERS(SERVER_ADDRESS + "get_users")
        }

        enum class Authentication(val url: String) {
            APPLICATION_FOR_REGISTRATION(SERVER_ADDRESS + "application_for_registration"),
            REGISTER_USER(SERVER_ADDRESS + "register_user"),
            AUTHENTICATE_WITH_TOKEN(SERVER_ADDRESS + "authenticate_with_token"),
            AUTHENTICATE_WITH_LOGIN_PASSWORD(SERVER_ADDRESS + "authenticate_with_login_password")
        }
    }

    fun upload(
        route: Upload,
        requestBody: JSONObject,
        preExecuteCallback: (() -> Unit)?,
        postExecuteCallback: (result: RequestExecutionResult, response: Response?) -> Unit
    ) {
        val request = getRequest(route.url, requestBody)
        execute(request, preExecuteCallback, postExecuteCallback)
    }

    fun download(
        route: Download,
        requestBody: JSONObject,
        preExecuteCallback: (() -> Unit)?,
        postExecuteCallback: (result: RequestExecutionResult, response: Response?) -> Unit
    ) {
        val request = getRequest(route.url, requestBody)
        execute(request, preExecuteCallback, postExecuteCallback)
    }

    fun auth(
        route: Authentication,
        requestBody: JSONObject,
        preExecuteCallback: (() -> Unit)?,
        postExecuteCallback: (result: RequestExecutionResult, response: Response?) -> Unit
    ) {
        val request = getRequest(route.url, requestBody)
        execute(request, preExecuteCallback, postExecuteCallback)
    }

    private fun getRequest(url: String, requestBody: JSONObject): Request {
        requestBody.put("auth_data", AuthManager.Database.getAuthData(context))
        return Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(
                RequestBody.create(
                    MediaType.parse("application/json; charset=utf-8"),
                    requestBody.toString(4)
                )
            )
            .build()
    }

    private fun execute(
        request: Request,
        preExecuteCallback: (() -> Unit)?,
        postExecuteCallback: (result: RequestExecutionResult, response: Response?) -> Unit
    ) {
        RequestAsyncExecutor(preExecuteCallback, postExecuteCallback).execute(request)
    }

    class RequestAsyncExecutor(
        private val preExecuteCallback: (() -> Unit)?,
        private val postExecuteCallback: (result: RequestExecutionResult, response: Response?) -> Unit
    ) : AsyncTask<Request, Unit, Pair<RequestExecutionResult, Response?>>() {

        override fun onPreExecute() {
            super.onPreExecute()
            preExecuteCallback?.invoke()
        }

        override fun doInBackground(vararg params: Request): Pair<RequestExecutionResult, Response?> {
            Thread.sleep(1000)
            val request = params[0]
            val client = OkHttpClient()

            return try {
                val response = JSONObject(client.newCall(request).execute().body()?.string())
                Pair(getResultCodeFromResponse(response), parseResponse(response))
            } catch (ex: IOException) {
                Pair(RequestExecutionResult.NETWORK_ERROR, null)
            } catch (ex: Exception) {
                Pair(RequestExecutionResult.OTHER_CLIENT_ERROR, null)
            }
        }

        private fun getResultCodeFromResponse(jsonObject: JSONObject): RequestExecutionResult =
            when (jsonObject.getInt("code")) {
                0 -> RequestExecutionResult.OK
                5 -> RequestExecutionResult.AUTH_ERROR
                6 -> RequestExecutionResult.ACCESS_ERROR
                else -> RequestExecutionResult.OTHER_SERVER_ERROR
            }

        override fun onPostExecute(result: Pair<RequestExecutionResult, Response?>) {
            super.onPostExecute(result)
            postExecuteCallback(result.first, result.second)
        }

        private fun parseResponse(jsonObject: JSONObject): Response? {
            return try {
                Response(
                    jsonObject.getInt("code"),
                    jsonObject.get("data") as JSONObject?,
                    jsonObject.get("error") as String?,
                    jsonObject.getBoolean("success")
                )
            } catch (e: java.lang.Exception) {
                null
            }
        }
    }
}
