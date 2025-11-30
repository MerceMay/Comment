package com.mercemay.comment.dto;

import com.mercemay.comment.entity.User;
import lombok.Data;

@Data
public class UserDTO {
    private Long id;
    private String nickName;
    private String icon;
}
