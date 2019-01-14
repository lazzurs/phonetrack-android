package net.eneiluj.nextcloud.phonetrack.util;

/**
 * Callback
 */
public interface ICallback {
    void onFinish();

    void onFinish(String result, String message);

    void onScheduled();
}
