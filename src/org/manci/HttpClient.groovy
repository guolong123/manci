package org.manci
@Grab('org.codehaus.groovy:groovy-json:3.0.21')
@Grab('org.codehaus.groovy.modules.http-builder:http-builder:0.7.1')
import groovy.json.JsonSlurper
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.ContentType
import groovyx.net.http.Method

class HttpClient {
    private final String baseUrl
    private final String token

    HttpClient(String baseUrl, String token) {
        this.baseUrl = baseUrl
        this.token = token
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

    def request(def method, String path, body = null, Map<String, String> customHeaders = [:]) {
        def http = new HTTPBuilder(baseUrl)

        // 默认的请求头
        def defaultHeaders = [
                "Accept": "application/json",
                "Content-Type": "application/json",
                "Authorization": "token ${token}"
        ]

        // 合并自定义请求头
        defaultHeaders.putAll(customHeaders)

        try {
            http.request(Method.valueOf(method.toUpperCase()), ContentType.JSON) { req ->
                uri.path = path
//                uri.query = ["token": token]

                // 设置请求头
                headers = defaultHeaders

                // 根据HTTP方法处理请求体
                if (body && [Method.POST, Method.PUT].contains(req.method)) {
                    // 对于POST和PUT请求，设置请求体
                    body = body instanceof Map ? JsonOutput.toJson(body) : body
                    requestBody = body
                }
                if (body && [Method.PATCH].contains(req.method)) {
                    // 对于PATCH请求，设置请求体
                    body = body instanceof Map ? JsonOutput.toJson(body) : body
                    requestBody = body
                }

                // 响应处理
                response.success = { resp, json ->
                    // 这里处理成功的响应逻辑
                    println "Response status with Method/Url: ${req.method}/${uri.toString()} ${resp.statusLine}"
                    // 将响应体转换为JSON对象（如果响应内容是JSON格式）
                    def responseBodyJson
                    if (json instanceof ArrayList) {
                        // Assuming each item in the list is already a Map and represents a JSON object
                        responseBodyJson = json
                    } else if (json instanceof Map) {
                        responseBodyJson = json
                    } else if (json != null) {
                        responseBodyJson = new JsonSlurper().parseText(json.toString()) // Convert json to string if it's not already
                    } else {
                        responseBodyJson = null
                    }
                    // 返回响应体数据
                    return responseBodyJson ?: json // 如果是JSON则返回Map对象，否则返回原始文本
                }

                // 错误处理
                response.failure = { resp ->
                    println "Request failed with Method/URL: ${req.method}/${uri.toString()} and status: ${resp.statusLine}"
                    if (body) {
                        println("Request body: ${body}")
                    }
                }



            }
        } catch (Exception e) {
            println "An error occurred while making the request: $e"
        }

    }
}

