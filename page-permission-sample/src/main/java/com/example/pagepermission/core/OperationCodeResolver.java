package com.example.pagepermission.core;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.BeanWrapperImpl;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;
import java.util.Optional;

public final class OperationCodeResolver {
    public String resolve(ProceedingJoinPoint joinPoint, String codeField) {
        return resolveOptional(joinPoint, codeField)
                .orElseThrow(() -> new PagePermissionException(
                        PagePermissionError.OPERATION_CODE_REQUIRED,
                        "Request field not found: " + codeField));
    }

    public Optional<String> resolveOptional(ProceedingJoinPoint joinPoint, String codeField) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Object[] args = joinPoint.getArgs();
        Parameter[] parameters = method.getParameters();

        for (int i = 0; i < args.length; i++) {
            Object value = args[i];
            if (value == null) {
                continue;
            }
            // 支持Controller直接把code声明为具名参数。
            // Controllerでcodeを名前付き引数として直接宣言する形式に対応します。
            if (parameters[i].isNamePresent() && parameters[i].getName().equals(codeField)) {
                return Optional.of(String.valueOf(value));
            }
            // 支持Map形式请求。
            // Map形式のリクエストに対応します。
            if (value instanceof Map<?, ?> map && map.containsKey(codeField)) {
                Object property = map.get(codeField);
                return property == null ? Optional.empty() : Optional.of(String.valueOf(property));
            }
            // 支持普通DTO或record请求对象，例如BusinessRequest.code。
            // BusinessRequest.codeなど、通常のDTOまたはrecord形式のリクエストに対応します。
            BeanWrapperImpl wrapper = new BeanWrapperImpl(value);
            if (wrapper.isReadableProperty(codeField)) {
                Object property = wrapper.getPropertyValue(codeField);
                if (property != null) {
                    return Optional.of(String.valueOf(property));
                }
            }
        }
        return Optional.empty();
    }
}
