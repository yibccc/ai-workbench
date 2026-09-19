package com.aiworkbench.status;

public record ProbeStatus(String status, String detail) {

    public static ProbeStatus up(String detail) {
        return new ProbeStatus("UP", detail);
    }

    public static ProbeStatus down(String detail) {
        return new ProbeStatus("DOWN", detail);
    }

    public static ProbeStatus notConfigured(String detail) {
        return new ProbeStatus("NOT_CONFIGURED", detail);
    }

    public static ProbeStatus notChecked(String detail) {
        return new ProbeStatus("NOT_CHECKED", detail);
    }
}
