package com.example.mesh_base.mesh_manager;

public class Status {
  private final Property ble;
  private final Property wifiDirect;

  private final boolean isOn;

  Status(boolean isOn, Property ble, Property wifiDirect) {
    this.isOn = isOn;
    this.ble = ble;
    this.wifiDirect = wifiDirect;
  }

  public Property getBle() {
    return ble;
  }

  public Property getWifiDirect() {
    return wifiDirect;
  }

  public boolean isOn() {
    return isOn;
  }

  public static class Property {

    private final boolean isSupported;
    private final boolean isOn;
    private final boolean isAllowed;

    public Property(boolean isSupported, boolean isOn, boolean isAllowed) {
      this.isSupported = isSupported;
      this.isOn = isOn;
      this.isAllowed = isAllowed;
    }

    public boolean isSupported() {
      return isSupported;
    }

    public boolean isOn() {
      return isOn;
    }

    public boolean isAllowed() {
      return isAllowed;
    }
  }

}
