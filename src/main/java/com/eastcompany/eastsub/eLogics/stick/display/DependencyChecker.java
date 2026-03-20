package com.eastcompany.eastsub.eLogics.stick.display;

public class DependencyChecker {
    /**
     * 指定されたクラスがクラスパス上に存在するか確認する
     */
    public static boolean isClassPresent(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * PacketEventsが利用可能かチェック
     */
    public static boolean isPacketEventsAvailable() {
        return isClassPresent("com.github.retrooper.packetevents.PacketEvents");
    }
}