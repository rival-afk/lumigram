package com.lumigram.messenger.plugins.proxy;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.FileLog;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Static proxy registry for class/method interception points.
 * Current version provides registration and manual dispatch API.
 * Bytecode-level weaving can be added later without breaking this contract.
 */
public final class StaticProxyRegistry {

    public interface MethodInterceptor {
        @Nullable
        Object intercept(@NonNull InvocationContext context) throws Throwable;
    }

    public interface Proceed {
        @Nullable
        Object call() throws Throwable;
    }

    public static final class InvocationContext {
        @NonNull
        public final String className;
        @NonNull
        public final String methodName;
        @Nullable
        public final Object target;
        @NonNull
        public final Object[] args;
        @NonNull
        private final Proceed proceed;

        InvocationContext(
                @NonNull String className,
                @NonNull String methodName,
                @Nullable Object target,
                @NonNull Object[] args,
                @NonNull Proceed proceed
        ) {
            this.className = className;
            this.methodName = methodName;
            this.target = target;
            this.args = args;
            this.proceed = proceed;
        }

        @Nullable
        public Object proceed() throws Throwable {
            return proceed.call();
        }
    }

    private static final Map<String, CopyOnWriteArrayList<MethodInterceptor>> interceptors = new ConcurrentHashMap<>();

    private StaticProxyRegistry() {
    }

    public static void registerClassInterceptor(@NonNull String className, @NonNull MethodInterceptor interceptor) {
        interceptors.computeIfAbsent(className, key -> new CopyOnWriteArrayList<>()).add(interceptor);
    }

    @Nullable
    public static Object dispatch(
            @NonNull String className,
            @NonNull String methodName,
            @Nullable Object target,
            @NonNull Object[] args,
            @NonNull Proceed original
    ) throws Throwable {
        List<MethodInterceptor> chain = interceptors.get(className);
        if (chain == null || chain.isEmpty()) {
            return original.call();
        }
        InvocationContext context = new InvocationContext(className, methodName, target, args, original);
        Object result = null;
        boolean handled = false;
        for (MethodInterceptor interceptor : chain) {
            try {
                result = interceptor.intercept(context);
                handled = true;
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }
        return handled ? result : original.call();
    }
}
