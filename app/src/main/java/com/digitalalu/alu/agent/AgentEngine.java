package com.digitalalu.alu.agent;

import android.content.Context;
import com.digitalalu.alu.model.ChatMessage;

/**
 * AI Agent Engine powered by on-device text generator model and app controller.
 * Supports full app control: Windows sheet, Cutting optimization, Costing, Customers.
 * Works 100% offline on-device with zero internet needed.
 */
public class AgentEngine {

    private final Context context;
    private final AgentAppController controller;
    private final OnDeviceTextGenerator textGenerator;

    public AgentEngine(Context context) {
        this.context = context.getApplicationContext();
        this.controller = new AgentAppController(context);
        this.textGenerator = new OnDeviceTextGenerator(controller);
    }

    public AgentAppController getController() {
        return controller;
    }

    /** Process user input and return rich ModelResponse with interactive actions */
    public OnDeviceTextGenerator.ModelResponse process(String userInput) {
        return textGenerator.generateResponse(userInput);
    }

    /** Process user input and return plain text response */
    public String respond(String userInput) {
        OnDeviceTextGenerator.ModelResponse res = textGenerator.generateResponse(userInput);
        return res.text;
    }

    /** Get initial greeting message */
    public String getGreeting() {
        return textGenerator.generateResponse("").text;
    }

    /** Get initial greeting ModelResponse with quick actions */
    public OnDeviceTextGenerator.ModelResponse getGreetingResponse() {
        return textGenerator.generateResponse("");
    }
}
