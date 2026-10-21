package system.common;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.*;

import java.io.IOException;

/** Preserve numeric counters while protecting 64-bit IDs from browser precision loss. */
public class IdSerializer extends JsonSerializer<Long> {
    @Override
    public void serialize(Long value, JsonGenerator generator, SerializerProvider serializers)
            throws IOException {
        String key = generator.getOutputContext().getCurrentName();
        if (key != null
                && (key.equals("id")
                        || key.endsWith("Id")
                        || key.endsWith("_id")
                        || key.equals("requestedBy")
                        || key.equals("createdBy")
                        || key.equals("reviewedBy")
                        || key.equals("verifiedBy")
                        || key.equals("readBy")
                        || key.equals("changedBy"))) generator.writeString(value.toString());
        else generator.writeNumber(value);
    }
}
