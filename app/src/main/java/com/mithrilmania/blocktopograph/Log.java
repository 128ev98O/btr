package com.mithrilmania.blocktopograph;

import androidx.annotation.NonNull;

import java.io.PrintWriter;
import java.io.StringWriter;

public class Log {
    private static final String LOG_TAG = "Blocktopograph";

    private static String prependClassName(@NonNull Object caller, @NonNull String msg) {
        Class<?> clazz = caller instanceof Class ? (Class<?>) caller : caller.getClass();
        return clazz.getSimpleName() + ": " + msg;
    }

    public static void d(@NonNull Object caller, @NonNull String msg) {
        android.util.Log.d(LOG_TAG, prependClassName(caller, msg));
    }

    public static void d(@NonNull String tag, @NonNull String msg) {
        android.util.Log.d(tag, msg);
    }

    public static void d(@NonNull Object caller, @NonNull Throwable throwable) {
        StringWriter sw = new StringWriter(4096);
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        android.util.Log.e(LOG_TAG, prependClassName(caller, sw.toString()));
        pw.close();
    }

    public static void e(@NonNull Object caller, @NonNull String msg) {
        d(caller, msg);
    }

    public static void e(@NonNull Object caller, @NonNull Throwable throwable) {
        d(caller, throwable);
    }
}
