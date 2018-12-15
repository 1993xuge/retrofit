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

import java.lang.reflect.Method;
import java.lang.reflect.Type;

import static retrofit2.Utils.methodError;

/**
 * 把对接口方法的调用转为一次 HTTP 调用。
 * <p>
 * 一个ServiceMethod对象 对应于 一个 API interface的一个方法
 */
abstract class ServiceMethod<T> {

    /**
     * 解析 Method上的注解，并返回一个ServiceMethod对象，实际上是ServiceMethod的子类HttpServiceMethod
     */
    static <T> ServiceMethod<T> parseAnnotations(Retrofit retrofit, Method method) {

        // 1、创建 一个 RequestFactory 对象
        RequestFactory requestFactory = RequestFactory.parseAnnotations(retrofit, method);

        // 2、获取 方法 的 返回值类型，并对返回值类型进行检查
        Type returnType = method.getGenericReturnType();
        if (Utils.hasUnresolvableType(returnType)) {
            // 如果有 不合法的类型，那么 就抛出 方法异常
            throw methodError(method,
                    "Method return type must not include a type variable or wildcard: %s", returnType);
        }
        // 返回值的类型 不能是 void
        if (returnType == void.class) {
            throw methodError(method, "Service methods cannot return void.");
        }

        // 3、通过 HttpServiceMethod 继续解析
        return HttpServiceMethod.parseAnnotations(retrofit, method, requestFactory);
    }

    abstract T invoke(Object[] args);
}
