package com.lumigram.messenger.plugins.proxy;

import androidx.annotation.NonNull;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

public final class JavaDynamicProxyFactory {

    private JavaDynamicProxyFactory() {
    }

    @SuppressWarnings("unchecked")
    @NonNull
    public static <T> T create(@NonNull Class<T> iface, @NonNull InvocationHandler handler) {
        if (!iface.isInterface()) {
            throw new IllegalArgumentException("Dynamic proxy target must be an interface: " + iface.getName());
        }
        return (T) Proxy.newProxyInstance(
                iface.getClassLoader(),
                new Class<?>[]{iface},
                handler
        );
    }
}
