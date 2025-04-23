package com.example.mesh_base.global_interfaces;

public class InternalRouterError extends Exception {
    String message;

    public InternalRouterError(String message) {
        this.message = message;
    }
}
