package com.example.mesh_base.mesh_manager;

public class Status {
  Property ble;
  Property wifi;

  Status(Property ble, Property wifi) {
    this.ble = ble;
    this.wifi = wifi;
  }

  public static class Property {

    boolean isSupported;
    boolean isOn;
    boolean isAllowed;
    public Property(boolean isSupported, boolean isOn, boolean isAllowed) {
      this.isSupported = isSupported;
      this.isOn = isOn;
      this.isAllowed = isAllowed;
    }
  }

}
