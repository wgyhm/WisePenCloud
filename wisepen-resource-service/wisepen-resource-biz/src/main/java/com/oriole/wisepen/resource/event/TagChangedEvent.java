package com.oriole.wisepen.resource.event;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class TagChangedEvent {
    private List<String> changedTagIds;
    private Boolean isPersonalTag;
}