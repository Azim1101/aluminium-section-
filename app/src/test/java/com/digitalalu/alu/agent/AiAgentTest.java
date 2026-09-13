package com.digitalalu.alu.agent;

import com.digitalalu.alu.model.WindowItem;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AiAgentTest {

    @Test
    public void testParseAddWindowCommand() {
        String input = "48x60 ki 2 window add karo zed me";
        AluNeuralNLP.ParsedEntity entity = AluNeuralNLP.parse(input);

        assertEquals(AluNeuralNLP.Intent.ADD_WINDOW, entity.intent);
        assertTrue(entity.hasDimensions);
        assertEquals(48.0, entity.height, 0.01);
        assertEquals(60.0, entity.width, 0.01);
        assertEquals(2, entity.nos);
        assertEquals(WindowItem.ZED, entity.system);
    }

    @Test
    public void testParseBrokenHindiFeetDimensions() {
        String input = "4 foot 5 foot do sutter 3 peace bana do domal me";
        AluNeuralNLP.ParsedEntity entity = AluNeuralNLP.parse(input);

        assertEquals(AluNeuralNLP.Intent.ADD_WINDOW, entity.intent);
        assertTrue(entity.hasDimensions);
        // 4 feet = 48 inches, 5 feet = 60 inches
        assertEquals(48.0, entity.height, 0.01);
        assertEquals(60.0, entity.width, 0.01);
        assertEquals(2, entity.sutter);
        assertEquals(3, entity.nos);
        assertEquals(WindowItem.DOMAL, entity.system);
    }

    @Test
    public void testParseCostingAndPrice() {
        String input1 = "total kharcha kitna hua project ka?";
        AluNeuralNLP.ParsedEntity entity1 = AluNeuralNLP.parse(input1);
        assertEquals(AluNeuralNLP.Intent.VIEW_COSTING, entity1.intent);

        String input2 = "alu rate 470 kardo";
        AluNeuralNLP.ParsedEntity entity2 = AluNeuralNLP.parse(input2);
        assertEquals(AluNeuralNLP.Intent.UPDATE_PRICE, entity2.intent);
        assertEquals(470.0, entity2.targetRate, 0.01);
    }

    @Test
    public void testParseCuttingPlanIntent() {
        String input = "pipe cutting plan aur waste dikhao";
        AluNeuralNLP.ParsedEntity entity = AluNeuralNLP.parse(input);
        assertEquals(AluNeuralNLP.Intent.VIEW_CUTTING, entity.intent);
    }

    @Test
    public void testParseCustomerInfo() {
        String input = "customer Ramesh Kumar mobile 9825012345 save kardo";
        AluNeuralNLP.ParsedEntity entity = AluNeuralNLP.parse(input);
        assertEquals(AluNeuralNLP.Intent.SAVE_CUSTOMER, entity.intent);
        assertEquals("9825012345", entity.mobileNumber);
    }

    @Test
    public void testParseClearAndSheet() {
        String input = "sab window clear kardo";
        AluNeuralNLP.ParsedEntity entity = AluNeuralNLP.parse(input);
        assertEquals(AluNeuralNLP.Intent.CLEAR_SHEET, entity.intent);
    }
}
