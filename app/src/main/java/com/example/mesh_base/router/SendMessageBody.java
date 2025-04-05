package com.example.mesh_base.router;

import java.nio.ByteBuffer;
import java.util.UUID;

public class SendMessageBody implements MeshSerializer<SendMessageBody> {
  private final int command;
  private final boolean isBroadcast;
  private final UUID destination;
  private final String msg;

  public SendMessageBody(int command, boolean isBroadcast, UUID destination, String msg) {
    this.command = command;
    this.isBroadcast = isBroadcast;
    this.destination = destination;
    this.msg = msg;
  }

  public static SendMessageBody decode(byte[] data) {
    ByteBuffer buffer = ByteBuffer.wrap(data);
    int command = buffer.getInt();
    boolean isBroadcast = buffer.get() == 1;

    long mostSignificantBits = buffer.getLong();
    long leastSignificantBits = buffer.getLong();

    UUID sender = null;

    if (mostSignificantBits != 0L || leastSignificantBits != 0L) {
      sender = new UUID(mostSignificantBits, leastSignificantBits);
    }

    int msgLength = buffer.getInt();
    byte[] msgBytes = new byte[msgLength];
    String msg = "";

    if (msgLength > 0) {
      buffer.get(msgBytes);
      msg = new String(msgBytes);
    }

    return new SendMessageBody(command, isBroadcast, sender, msg);
  }

  @Override
  public byte[] encode() {
    int messageLength = msg.length();
    ByteBuffer buffer = ByteBuffer.allocate(4 + 1 + 16 + 4 + messageLength);

    buffer.putInt(command);
    buffer.put((byte) (isBroadcast ? 1 : 0));

    if (destination == null) {
      buffer.putLong(0L);
      buffer.putLong(0L);
    } else {
      buffer.putLong(destination.getMostSignificantBits());
      buffer.putLong(destination.getLeastSignificantBits());
    }
    buffer.putInt(messageLength);
    buffer.put(msg.getBytes());
    return buffer.array();
  }

  public int getCommand() {
    return command;
  }

  public boolean isBroadcast() {
    return isBroadcast;
  }

  public UUID getDestination() {
    return destination;
  }

  public String getMsg() {
    return msg;
  }
}
