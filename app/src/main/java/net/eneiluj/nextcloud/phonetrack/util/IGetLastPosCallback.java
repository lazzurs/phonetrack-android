package net.eneiluj.nextcloud.phonetrack.util;

import net.eneiluj.nextcloud.phonetrack.model.DBLocation;

import java.util.Map;

public interface IGetLastPosCallback {
    void onFinish();

    void onFinish(Map<String, DBLocation> locations, String message);

    void onScheduled();
}
