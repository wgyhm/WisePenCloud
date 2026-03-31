package com.oriole.wisepen.resource.domain.base;

import com.oriole.wisepen.resource.enums.VisibilityMode;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class TagInfoBase extends TagSpaceBase{
    private String tagName;
    private String tagDesc;
    private String tagIcon;
    private String tagColor;
}