package me.geek.tom.modrinthcli.util;

import me.tongfei.progressbar.ConsoleProgressBarConsumer;
import org.jetbrains.annotations.NotNull;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class Logger {
    public static final String CHECK = "✔";
    public static final String CROSS = "❌";
    public static final String DOT = "•";
    private static final String[] SPINNER = {
            "-",
            "\\",
            "|",
            "/"
    };

    public static void info(String message) {
        emoji(DOT, message);
    }

    public static void success(String message) {
        emoji(CHECK, message);
    }

    public static void error(String message) {
        emoji(CROSS, message);
    }

    public static <T> T requesting(Call<T> call, String message) throws Throwable {
        AtomicBoolean loading = new AtomicBoolean(true);
        AtomicReference<T> ret = new AtomicReference<>(null);
        AtomicReference<Throwable> err = new AtomicReference<>(null);
        call.enqueue(new Callback<T>() {
            @Override
            public void onResponse(@NotNull Call<T> call, @NotNull Response<T> response) {
                ret.set(response.body());
                loading.set(false);
            }

            @Override
            public void onFailure(@NotNull Call<T> call, @NotNull Throwable throwable) {
                err.set(throwable);
                loading.set(false);
            }
        });

        ConsoleProgressBarConsumer consoleConsumer = createConsoleConsumer();
        assert consoleConsumer != null;
        int spinnerIndex = 0;
        try {
            while (loading.get()) {
//                System.out.print("\r" + SPINNER[spinnerIndex] + " " + message);
                consoleConsumer.accept("\r" + SPINNER[spinnerIndex] + " " + message);
                spinnerIndex++;
                spinnerIndex %= SPINNER.length;
                Thread.sleep(120L);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (err.get() != null) {
            System.out.println("\r" + CROSS + " Failed: " + message);
            throw err.get();
        } else {
            System.out.println("\r" + CHECK + " Done: " + message);
        }
        return ret.get();
    }
    // :concern:

    static ConsoleProgressBarConsumer createConsoleConsumer() {
        try {
            Method createConsoleConsumer = Class.forName("me.tongfei.progressbar.Util").getDeclaredMethod("createConsoleConsumer", PrintStream.class);
            createConsoleConsumer.setAccessible(true);
            return (ConsoleProgressBarConsumer) createConsoleConsumer.invoke(null, System.out);
        } catch (NoSuchMethodException | ClassNotFoundException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void emoji(String emoji, String message) {
        System.out.println(emoji + " " + message);
    }
}
