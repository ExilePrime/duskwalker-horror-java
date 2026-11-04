package com.duskwalker.bedrock;

/** Bedrock API'sinin firlattigi hatalarin karsiligi (gecersiz varlik, yuklu olmayan chunk ...). */
public class BedrockException extends RuntimeException {
    public BedrockException(String msg) { super(msg); }
    public BedrockException(String msg, Throwable cause) { super(msg, cause); }
}
