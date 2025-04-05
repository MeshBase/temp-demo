package com.example.mesh_base.router;

import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.function.Function;

public abstract class MeshProtocol<T extends MeshSerializer<T>> implements MeshSerializer<MeshProtocol<T>> {
  //TODO: revert to protected when the BLETestScreen.kt doesn't need to decode bytes no more
  public UUID sender;
  //TODO: revert to protected when the BLETestScreen.kt doesn't need to decode bytes no more
  public T body;
  protected int messageType;
  protected int remainingHops;
  protected int messageId;

  public MeshProtocol(int messageType, int remainingHops, int messageId, UUID sender, T body) {
    this.messageType = messageType;
    this.remainingHops = remainingHops;
    this.messageId = messageId;
    this.sender = sender;
    this.body = body;
  }

  public static <T extends MeshSerializer<T>> MeshProtocol<T> decode(byte[] data,
                                                                     Function<byte[], T> bodyDecoder) {
    ByteBuffer buffer = ByteBuffer.wrap(data);
    int messageType = buffer.getInt();
    int remainingHops = buffer.getInt();
    int messageId = buffer.getInt();
    UUID sender = new UUID(buffer.getLong(), buffer.getLong());
    int bodyLength = buffer.getInt();

    byte[] bodyBytes = new byte[bodyLength];
    buffer.get(bodyBytes);
    T body = bodyDecoder.apply(bodyBytes);

    return new ConcreteMeshProtocol<>(messageType, remainingHops, messageId, sender, body);
  }

  public static ProtocolType getByteType(byte[] data) {
    ByteBuffer buffer = ByteBuffer.wrap(data);
    int messageType = buffer.getInt();

    switch(messageType) {
      case 1:
        return ProtocolType.sendMessage;
      case 2:
        return ProtocolType.receiveMessage;
        //add more protocol cases here
      default:
        return ProtocolType.unknownMessageType;
    }
  }
  @Override
  public byte[] encode() {
    byte[] bodyBytes = body != null ? body.encode() : new byte[0];
    int bodyLength = bodyBytes.length;

    ByteBuffer buffer = ByteBuffer.allocate(4 + 4 + 4 + 16 + 4 + bodyLength);
    buffer.putInt(messageType);
    buffer.putInt(remainingHops);
    buffer.putInt(messageId);
    buffer.putLong(sender.getMostSignificantBits());
    buffer.putLong(sender.getLeastSignificantBits());
    buffer.putInt(bodyLength);
    buffer.put(bodyBytes);
    return buffer.array();

  }

}
