package com.digitalalu.alu.model;

/** Single chat message in agent conversation */
public class ChatMessage {
    
    public static final int ROLE_USER = 0;
    public static final int ROLE_AGENT = 1;
    
    public int role;
    public String text;
    public long timestamp;
    
    public ChatMessage(int role, String text) {
        this.role = role;
        this.text = text;
        this.timestamp = System.currentTimeMillis();
    }
    
    public boolean isUser() {
        return role == ROLE_USER;
    }
}
