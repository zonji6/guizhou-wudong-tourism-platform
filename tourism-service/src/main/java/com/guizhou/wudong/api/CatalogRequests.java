package com.guizhou.wudong.api;

public final class CatalogRequests {
    private CatalogRequests() {}

    public enum Kind {
        MERCHANT("merchants"),
        PRODUCT("products"),
        FOOD("foods"),
        STAY("stays"),
        ROOM_TYPE("room-types"),
        PLACE("places");

        private final String path;

        Kind(String path) {
            this.path = path;
        }

        public static Kind fromPath(String path) {
            for (Kind kind : values()) {
                if (kind.path.equals(path)) {
                    return kind;
                }
            }
            throw new IllegalArgumentException("不支持的目录类型");
        }
    }
}
