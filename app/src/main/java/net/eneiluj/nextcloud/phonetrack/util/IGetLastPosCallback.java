package net.eneiluj.nextcloud.phonetrack.util;

import net.eneiluj.nextcloud.phonetrack.model.BasicLocation;
import net.eneiluj.nextcloud.phonetrack.model.ColoredLocation;

import java.util.List;
import java.util.Map;

public interface IGetLastPosCallback {
    void onFinish(Map<String, List<BasicLocation>> locations, Map<String, String> colors, String message);
}
