package com.duskwalker.bedrock;

/** Binding'den bagimsiz basit gunluk. Binding, Log.sink'i gercek logger'a baglar. */
public final class Log {
    private Log() {}
    public static java.util.function.Consumer<String> sink = s -> System.out.println("[Duskwalker] " + s);
    public static void info(String s) { sink.accept(s); }
    public static void warn(String s) { sink.accept("WARN " + s); }
}
