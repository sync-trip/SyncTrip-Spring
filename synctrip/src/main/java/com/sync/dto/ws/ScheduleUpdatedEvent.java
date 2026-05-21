package com.sync.dto.ws;

public record ScheduleUpdatedEvent(
        Long bandId,
        Long editorUserId
) {}