package com.huq.idea.flow.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class FlowGraphDataTest {

    @Test
    public void testFromJson_ValidJson() {
        String json = """
                {
                  "nodes": [
                    {
                      "id": "node1",
                      "label": "Start",
                      "type": "start"
                    }
                  ],
                  "edges": [
                    {
                      "source": "node1",
                      "target": "node2",
                      "label": "to"
                    }
                  ]
                }
                """;

        FlowGraphData data = FlowGraphData.fromJson(json);

        assertNotNull(data);
        assertEquals(1, data.getNodes().size());
        assertEquals("node1", data.getNodes().get(0).getId());
        assertEquals("Start", data.getNodes().get(0).getLabel());
        assertEquals("start", data.getNodes().get(0).getType());

        assertEquals(1, data.getEdges().size());
        assertEquals("node1", data.getEdges().get(0).getSource());
        assertEquals("node2", data.getEdges().get(0).getTarget());
        assertEquals("to", data.getEdges().get(0).getLabel());
    }

    @Test
    public void testFromJson_InvalidJson() {
        String invalidJson = "{ invalid json }";

        FlowGraphData data = FlowGraphData.fromJson(invalidJson);

        assertNotNull(data);
        assertTrue(data.getNodes().isEmpty());
        assertTrue(data.getEdges().isEmpty());
    }

    @Test
    public void testFromJson_EmptyJson() {
        String emptyJson = "";

        FlowGraphData data = FlowGraphData.fromJson(emptyJson);

        assertNotNull(data);
        assertTrue(data.getNodes().isEmpty());
        assertTrue(data.getEdges().isEmpty());
    }

    @Test
    public void testFromJson_BlankJson() {
        String blankJson = "   ";

        FlowGraphData data = FlowGraphData.fromJson(blankJson);

        assertNotNull(data);
        assertTrue(data.getNodes().isEmpty());
        assertTrue(data.getEdges().isEmpty());
    }

    @Test
    public void testFromJson_NullJson() {
        FlowGraphData data = FlowGraphData.fromJson(null);

        assertNotNull(data);
        assertTrue(data.getNodes().isEmpty());
        assertTrue(data.getEdges().isEmpty());
    }
}
