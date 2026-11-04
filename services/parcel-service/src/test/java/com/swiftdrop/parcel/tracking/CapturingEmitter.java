package com.swiftdrop.parcel.tracking;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** SseEmitter for unit tests: records each frame as text instead of writing to a response. */
class CapturingEmitter extends SseEmitter {

    final List<String> frames = new ArrayList<>();
    boolean completed;
    boolean failNextSend;
    RuntimeException unexpected;

    @Override
    public void send(SseEventBuilder builder) throws IOException {
        if (unexpected != null) {
            throw unexpected;
        }
        if (failNextSend) {
            failNextSend = false;
            throw new IOException("client went away");
        }
        Set<org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.DataWithMediaType> parts = builder.build();
        frames.add(parts.stream().map(part -> String.valueOf(part.getData())).collect(Collectors.joining()));
    }

    @Override
    public void complete() {
        completed = true;
    }

    @Override
    public void completeWithError(Throwable ex) {
        completed = true;
    }

    /** Ids of the status events sent, in order. */
    List<Long> sentIds() {
        return frames.stream()
                .filter(frame -> frame.startsWith("id:"))
                .map(frame -> Long.parseLong(frame.substring(3, frame.indexOf('\n'))))
                .toList();
    }
}
