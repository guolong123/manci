package org.manci

@Grab('org.codehaus.groovy:groovy-json:3.0.21')
@Grab('org.codehaus.groovy.modules.http-builder:http-builder:0.7.1')
import groovy.json.JsonOutput
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.ContentType
import groovyx.net.http.Method

class HttpClient {
    private final String baseUrl
    private final String token
    def script
    Logger logger
    Utils utils
    def headers = ["Content-Type": "application/json;charset=UTF-8"]

    HttpClient(script, String baseUrl, Map<String ,String> headers = null) {
        this.baseUrl = baseUrl
        this.token = token
        this.headers.putAll(headers)
        this.script = script
        logger = new Logger(script)
        utils = new Utils(script)
    }

    def post(String path, body = null, Map<String, String> customHeaders = [:]) {
        return request("post", path, body, customHeaders)
    }

    def get(String path, Map<String, String> customHeaders = [:]) {
        return request("get", path, null, customHeaders)
    }

    def delete(String path, Map<String, String> customHeaders = [:]) {
        return request("delete", path, null, customHeaders)
    }

    def put(String path, body = null, Map<String, String> customHeaders = [:]) {
        return request("put", path, body, customHeaders)
    }

    def patch(String path, body = null, Map<String, String> customHeaders = [:]) {
        return request("patch", path, body, customHeaders)
    }

    @NonCPS
    def request(def method, String path, bodyData = null, Map<String, String> customHeaders = [:]) {
        HTTPBuilder http = new HTTPBuilder(baseUrl)
        headers.putAll(customHeaders)
        try {
            http.request(Method.valueOf(method.toUpperCase()), ContentType.JSON) { req ->
                uri.path = path
                headers = this.headers

                if (bodyData && ["POST", "PUT", "PATCH"].contains(method.toUpperCase())) {
                    body = bodyData instanceof Map ? JsonOutput.toJson(bodyData) : bodyData
                }

                // 响应处理
                response.success = { resp, json ->
                    logger.info "Response status with Method/Url: ${req.method}/${uri.toString()} ${resp.statusLine}"
                    // 将响应体转换为JSON对象（如果响应内容是JSON格式）
                    def responseBodyJson
                    if (json instanceof ArrayList) {
                        // Assuming each item in the list is already a Map and represents a JSON object
                        responseBodyJson = json
                    } else if (json instanceof Map) {
                        responseBodyJson = json
                    } else if (json != null) {
                        responseBodyJson = utils.jsonParse(json.toString())
                    } else {
                        responseBodyJson = null
                    }
                    return responseBodyJson ?: json // 如果是JSON则返回Map对象，否则返回原始文本
                }

                // 错误处理
                response.failure = { resp, json ->
                    logger.info "Request failed with Method/URL: ${req.method}/${uri.toString()} and status: ${resp.statusLine}"
                    logger.info("Request with headers: ${headers}")
                    logger.info("Response body: ${json}")
                }
            }
        } catch (Exception e) {
            println "An error occurred while making the request: $e"
        }

    }
}

