package com.example.mesh_base.router;

import java.util.Arrays;
import java.util.UUID;
import java.util.function.Function;

public class Test {

    public static void main(String[] args) {

        byte[] byteArray = new byte[]{
                0x00, 0x00, 0x00, 0x01,  // messageType = 1
                0x00, 0x00, 0x00, 0x04,  // remainingHops = 4
                0x00, 0x00, 0x00, 0x71,  // messageId = 113
                (byte) 0xdd, (byte) 0x91, (byte) 0xa1, (byte) 0xc8, (byte) 0x5f, (byte) 0x6a, (byte) 0x44, (byte) 0x30,  // sender (UUID high bits)
                (byte) 0x81, (byte) 0x5f, (byte) 0xf3, (byte) 0xe1, (byte) 0xc8, (byte) 0x78, (byte) 0x0f, (byte) 0xc8,  // sender (UUID low bits)
                0x00, 0x00, 0x00, 0x24, // bodyLength = 36
                0x00, 0x00, 0x00, 0x04,  // command = 4
                0x00,  // isBroadcast = false
                (byte) 0xa9, (byte) 0xc5, (byte) 0xc7, (byte) 0x10, (byte) 0x07, (byte) 0x53, (byte) 0x43, (byte) 0x43,  // destination (UUID high bits)
                (byte) 0xab, (byte) 0x51, (byte) 0x31, (byte) 0x4e, (byte) 0xc4, (byte) 0x23, (byte) 0xb7, (byte) 0x32,  // destination (UUID low bits)
                0x00, 0x00, 0x00, 0x0B,  // msg length = 11
                (byte) 0x68, (byte) 0x65, (byte) 0x6c, (byte) 0x6c, (byte) 0x6f, (byte) 0x20, (byte) 0x77, (byte) 0x6f,  // "hello"
                (byte) 0x72, (byte) 0x6c, (byte) 0x64   // "world"
        };

        // TO DECODE DATA YOU CAN DO THE FOLLOWING
        Function<byte[], SendMessageBody> bodyDecoder = SendMessageBody::decode;
        MeshProtocol<SendMessageBody> data = MeshProtocol.decode(byteArray, bodyDecoder);
        //

        // Decoding result
        System.out.println("DATA DECODING");
        System.out.println("---HEADER---");
        System.out.println("Message Type: " + data.messageType);
        System.out.println("Remaining Hops: " + data.remainingHops);
        System.out.println("Sender UUID: " + data.sender);
        System.out.println("---BODY---");
        System.out.println("Command: " + data.body.getCommand());
        System.out.println("Is Broadcast: " + data.body.isBroadcast());
        System.out.println("Destination UUID: " + data.body.getDestination());
        System.out.println("Message : " + data.body.getMsg());
        System.out.println();

        // TO ENCODE DATA YOU CAN DO THE FOLLOWING
        SendMessageBody messageBody = new SendMessageBody(4,
                false,
                UUID.fromString("a9c5c710-0753-4343-ab51-314ec423b732"),
                "hello world");

        MeshProtocol<SendMessageBody> protocol = new ConcreteMeshProtocol<>(
                1,
                4,
                113,
                UUID.fromString("dd91a1c8-5f6a-4430-815f-f3e1c8780fc8"),
                messageBody // or do new SendMessageBody(... here
        );

        System.out.println("DATA ENCODING");
        // Just checking if my data is the same as the original byte array data
        System.out.println(Arrays.equals(protocol.encode(), byteArray));
    }
}