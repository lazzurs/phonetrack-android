package net.eneiluj.nextcloud.phonetrack.util;

import net.eneiluj.nextcloud.phonetrack.model.DBColoredLocation;
import net.eneiluj.nextcloud.phonetrack.model.DBLocation;

import java.util.Map;

public interface IGetLastPosCallback {
    void onFinish(Map<String, DBColoredLocation> locations, String message);
}
