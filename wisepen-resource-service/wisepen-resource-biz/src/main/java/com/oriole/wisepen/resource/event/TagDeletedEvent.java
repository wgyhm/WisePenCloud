package com.oriole.wisepen.resource.event;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class TagDeletedEvent {
    private List<String> deletedTagIds;
    private Boolean isPersonalTag;
    private Boolean isPathTag;
}