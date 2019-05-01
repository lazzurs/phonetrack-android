package net.eneiluj.nextcloud.phonetrack.util;

import net.eneiluj.nextcloud.phonetrack.model.ColoredLocation;

import java.util.Map;

public interface IGetLastPosCallback {
    void onFinish(Map<String, ColoredLocation> locations, String message);
}
