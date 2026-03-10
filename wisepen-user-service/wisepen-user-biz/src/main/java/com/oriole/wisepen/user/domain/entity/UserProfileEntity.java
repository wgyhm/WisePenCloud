package com.oriole.wisepen.user.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.oriole.wisepen.user.api.enums.DegreeLevel;
import com.oriole.wisepen.user.api.enums.GenderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_user_profile")
public class UserProfileEntity implements Serializable {

    @TableId(type = IdType.INPUT)
    private Long userId;

    private GenderType sex;

    private String university;
    private String college;

    private String major;

    private String className;

    private Integer enrollmentYear;

    private DegreeLevel degreeLevel;

    private String academicTitle;
}