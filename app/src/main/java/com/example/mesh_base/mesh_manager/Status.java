package com.example.mesh_base.mesh_manager;

public class Status {
  public Property ble;
  public Property wifi;

  public boolean isOn;

  Status(boolean isOn, Property ble, Property wifi) {
    this.isOn = isOn;
    this.ble = ble;
    this.wifi = wifi;
  }

  public static class Property {

    public boolean isSupported;
    public boolean isOn;
    public boolean isAllowed;

    public Property(boolean isSupported, boolean isOn, boolean isAllowed) {
      this.isSupported = isSupported;
      this.isOn = isOn;
      this.isAllowed = isAllowed;
    }
  }

}
