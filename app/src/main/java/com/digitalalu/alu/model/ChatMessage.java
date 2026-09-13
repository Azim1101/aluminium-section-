package com.digitalalu.alu.model;

import java.util.ArrayList;
import java.util.List;

/** Single chat message in agent conversation with interactive actions */
public class ChatMessage {
    
    public static final int ROLE_USER = 0;
    public static final int ROLE_AGENT = 1;
    
    public int role;
    public String text;
    public long timestamp;
    public List<Action> actions = new ArrayList<>();
    
    public static class Action {
        public String title;
        public String type;    // e.g. "ESTIMATE", "CUTTING", "WINDOWS", "PRICE", "CUSTOMERS", "ADD_WINDOW", "EXECUTE_TEXT"
        public String payload; // optional data

        public Action(String title, String type, String payload) {
            this.title = title;
            this.type = type;
            this.payload = payload;
        }

        public Action(String title, String type) {
            this(title, type, "");
        }
    }
    
    public ChatMessage(int role, String text) {
        this.role = role;
        this.text = text;
        this.timestamp = System.currentTimeMillis();
    }

    public ChatMessage(int role, String text, List<Action> actions) {
        this(role, text);
        if (actions != null) {
            this.actions.addAll(actions);
        }
    }
    
    public boolean isUser() {
        return role == ROLE_USER;
    }
}

