/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package retrofit2;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

import okhttp3.ResponseBody;

import static retrofit2.Utils.methodError;

/**
 * Adapts an invocation of an interface method into an HTTP call.
 */
final class HttpServiceMethod<ResponseT, ReturnT> extends ServiceMethod<ReturnT> {
    /**
     * Inspects the annotations on an interface method to construct a reusable service method that
     * speaks HTTP. This requires potentially-expensive reflection so it is best to build each service
     * method only once and reuse it.
     */
    static <ResponseT, ReturnT> HttpServiceMethod<ResponseT, ReturnT> parseAnnotations(
            Retrofit retrofit, Method method, RequestFactory requestFactory) {
        // 1、根据接口方法的注解和返回类型 获取 callAdapter
        CallAdapter<ResponseT, ReturnT> callAdapter = createCallAdapter(retrofit, method);

        // 2、calladapter 的响应类型中的泛型，比如 Call<User> 中的 User
        // 并对响应泛型的类型进行检查
        Type responseType = callAdapter.responseType();
        if (responseType == Response.class || responseType == okhttp3.Response.class) {
            // 如果响应的类型是 Response.class 或 okhttp3.Response.class，则抛出异常
            throw methodError(method, "'"
                    + Utils.getRawType(responseType).getName()
                    + "' is not a valid response body type. Did you mean ResponseBody?");
        }
        // 如果 该请求方法是 HEAD请求，那么 响应类型 必须是 Void，否则就会 抛出异常
        if (requestFactory.httpMethod.equals("HEAD") && !Void.class.equals(responseType)) {
            throw methodError(method, "HEAD method must use Void as response type.");
        }

        // 3、根据之前泛型中的类型以及接口方法的注解创建 ResponseConverter
        Converter<ResponseBody, ResponseT> responseConverter =
                createResponseConverter(retrofit, method, responseType);

        // 4、获取 Retrofit中的 okhttp3.Call.Factory 对象
        okhttp3.Call.Factory callFactory = retrofit.callFactory;

        //5、将 requestFactory、callFactory、callAdapter、responseConverter传入HttpServiceMethod，并创建对象
        return new HttpServiceMethod<>(requestFactory, callFactory, callAdapter, responseConverter);
    }

    /**
     * 根据接口方法的注解和返回类型创建 callAdapter
     */
    private static <ResponseT, ReturnT> CallAdapter<ResponseT, ReturnT> createCallAdapter(
            Retrofit retrofit, Method method) {
        // 方法 的 返回值类型
        Type returnType = method.getGenericReturnType();
        // 方法上 添加的注解
        Annotation[] annotations = method.getAnnotations();
        try {
            //noinspection unchecked
            // 通过 Retrofit对象的callAdapter方法 获取 合适的CallAdapter
            return (CallAdapter<ResponseT, ReturnT>) retrofit.callAdapter(returnType, annotations);
        } catch (RuntimeException e) { // Wide exception range because factories are user code.
            throw methodError(method, e, "Unable to create call adapter for %s", returnType);
        }
    }

    private static <ResponseT> Converter<ResponseBody, ResponseT> createResponseConverter(
            Retrofit retrofit, Method method, Type responseType) {
        Annotation[] annotations = method.getAnnotations();
        try {
            return retrofit.responseBodyConverter(responseType, annotations);
        } catch (RuntimeException e) { // Wide exception range because factories are user code.
            throw methodError(method, e, "Unable to create converter for %s", responseType);
        }
    }

    private final RequestFactory requestFactory;
    private final okhttp3.Call.Factory callFactory;
    private final CallAdapter<ResponseT, ReturnT> callAdapter;
    private final Converter<ResponseBody, ResponseT> responseConverter;

    private HttpServiceMethod(RequestFactory requestFactory, okhttp3.Call.Factory callFactory,
                              CallAdapter<ResponseT, ReturnT> callAdapter,
                              Converter<ResponseBody, ResponseT> responseConverter) {
        this.requestFactory = requestFactory;
        this.callFactory = callFactory;
        this.callAdapter = callAdapter;
        this.responseConverter = responseConverter;
    }

    @Override
    ReturnT invoke(Object[] args) {
        // 调用 请求接口中的方法，实际上 会使用 动态代理调用这个方法，创建Call对象，显然此处是OkHttpCall
        return callAdapter.adapt(
                new OkHttpCall<>(requestFactory, args, callFactory, responseConverter));
    }
}
