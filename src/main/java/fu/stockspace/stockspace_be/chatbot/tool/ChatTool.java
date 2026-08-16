package fu.stockspace.stockspace_be.chatbot.tool;

import java.util.Map;
import java.util.UUID;











public interface ChatTool {





    String getName();




    String getDescription();





    Map<String, Object> getParameterSchema();








    String execute(Map<String, Object> params, UUID userId);

    /**
     * Executes a tool with server-provided request context. Existing tools can
     * continue to use the authenticated user id; warehouse-aware tools override
     * this method and consume activeWarehouseId without exposing it to the LLM.
     */
    default String executeWithContext(Map<String, Object> params, ChatRequestContext context) {
        return execute(params, context == null ? null : context.userId());
    }
}
