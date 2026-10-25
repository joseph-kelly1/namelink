package io.github.josephkelly.namelink;

import com.google.gson.*;
import com.google.gson.stream.*;
import java.io.*;
import java.util.HashSet;

/** Gson's tree parser accepts duplicate object keys; identity files must not silently choose one. */
final class ValidatedJson {
    private ValidatedJson() {}
    static JsonObject object(String text) throws IOException {
        try(JsonReader reader=new JsonReader(new StringReader(text))) {
            reader.setStrictness(Strictness.STRICT);
            scan(reader,0);
            if(reader.peek()!=JsonToken.END_DOCUMENT) throw new IOException("Trailing JSON data");
        }
        return JsonParser.parseString(text).getAsJsonObject();
    }
    private static void scan(JsonReader reader,int depth) throws IOException {
        if(depth>64) throw new IOException("JSON nesting limit exceeded");
        switch(reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject();var keys=new HashSet<String>();
                while(reader.hasNext()) {
                    String key=reader.nextName();
                    if(!keys.add(key)) throw new IOException("Duplicate JSON key: "+key);
                    scan(reader,depth+1);
                }
                reader.endObject();
            }
            case BEGIN_ARRAY -> { reader.beginArray();while(reader.hasNext()) scan(reader,depth+1);reader.endArray(); }
            case STRING, NUMBER -> reader.nextString();
            case BOOLEAN -> reader.nextBoolean();
            case NULL -> reader.nextNull();
            default -> throw new IOException("Unexpected JSON token: "+reader.peek());
        }
    }
    static String string(JsonElement value) {
        if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Expected JSON string");
        return value.getAsString();
    }
}
